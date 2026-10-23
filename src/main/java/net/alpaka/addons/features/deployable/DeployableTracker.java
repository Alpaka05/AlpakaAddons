package net.alpaka.addons.features.deployable;

import com.mojang.authlib.properties.Property;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the strongest deployable whose buff reaches the player, once a client tick.
 *
 * Both kinds are armor stands. A power orb's name tag stand reads "Plasmaflux 52s", which gives
 * the kind and the exact time left. A flare is a bare stand wearing a head, recognised by the
 * head's skin; Hypixel shows no time for it, so its three minutes are counted from when the stand
 * appeared on this client. A flare that was already up before you came near therefore shows a
 * little more time than it has left.
 */
public final class DeployableTracker {
    private DeployableTracker() {}

    /** The widest range of any deployable; nothing further away can reach the player. */
    private static final double SCAN_RADIUS = 41.0;

    /** "Plasmaflux 52s", once the color codes are gone. */
    private static final Pattern ORB_TAG = Pattern.compile("^(.+?) (\\d+)s$");

    /** What one stand is, worked out once per stand; null means it is not a flare. */
    private static final Map<Integer, Deployable> FLARE_BY_ID = new HashMap<>();
    /** Above this many remembered stands the map is cleared rather than grown without end. */
    private static final int FLARE_CACHE_LIMIT = 512;
    private static ClientLevel cachedLevel = null;

    /** The deployable in range and its time left, or null. */
    public record Active(Deployable type, int secondsLeft) {}

    private static Active active = null;

    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /** The deployable whose buff the player has now, or null. */
    public static Active active() {
        return active;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (!AlpakaConfig.instance.deployableTimerEnabled || level == null || player == null
                || !SkyblockUtils.isOnSkyblock()) {
            active = null;
            return;
        }
        if (level != cachedLevel || FLARE_BY_ID.size() > FLARE_CACHE_LIMIT) {
            FLARE_BY_ID.clear();
            cachedLevel = level;
        }

        Deployable bestType = null;
        int bestSeconds = 0;
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(SCAN_RADIUS))) {
            Deployable type;
            int seconds;
            Component name = stand.getCustomName();
            if (name != null) {
                Matcher m = ORB_TAG.matcher(SkyblockUtils.cleanColor(name.getString()));
                if (!m.matches()) continue;
                type = Deployable.orbFromName(m.group(1));
                if (type == null) continue;
                seconds = Integer.parseInt(m.group(2));
            } else {
                type = flareOf(stand);
                if (type == null) continue;
                seconds = type.durationSeconds - stand.tickCount / 20;
            }
            if (seconds <= 0) continue;
            if (stand.distanceToSqr(player) > (double) type.range * type.range) continue;
            if (type.beats(bestType) || (type == bestType && seconds > bestSeconds)) {
                bestType = type;
                bestSeconds = seconds;
            }
        }
        active = bestType == null ? null : new Active(bestType, bestSeconds);
    }

    /** The flare a stand is, from the skin of the head it wears; remembered per stand. */
    private static Deployable flareOf(ArmorStand stand) {
        int id = stand.getId();
        if (FLARE_BY_ID.containsKey(id)) return FLARE_BY_ID.get(id);
        ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
        // The head can arrive a few ticks after the stand; until then there is nothing to remember.
        if (head.isEmpty()) return null;
        Deployable type = null;
        if (head.is(Items.PLAYER_HEAD)) {
            ResolvableProfile profile = head.get(DataComponents.PROFILE);
            if (profile != null) {
                for (Property property : profile.partialProfile().properties().get("textures")) {
                    type = Deployable.flareFromTexture(decode(property.value()));
                    if (type != null) break;
                }
            }
        }
        FLARE_BY_ID.put(id, type);
        return type;
    }

    private static String decode(String base64) {
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
