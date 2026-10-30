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
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the strongest deployable of each family whose buff reaches the player, once a client tick.
 *
 * All of them are armor stands. A power orb, lantern or Umberella has a name tag stand reading
 * "Plasmaflux 52s", which gives the kind and the exact time left. A flare is a bare stand wearing a
 * head, recognised by the head's skin; Hypixel shows no time for it, so its three minutes are
 * counted from when the stand appeared on this client. A flare that was already up before you came
 * near therefore shows a little more time than it has left. The Totem of Corruption and the Black
 * Hole are a stack of tag stands, one of them naming the owner; only the player's own count, as
 * nobody else gets their effect.
 */
public final class DeployableTracker {
    private DeployableTracker() {}

    /** The widest range of any deployable; nothing further away can reach the player. */
    private static final double SCAN_RADIUS = 41.0;

    /** "Plasmaflux 52s", once the color codes are gone. */
    private static final Pattern TIMED_TAG = Pattern.compile("^(.+?) (\\d+)s$");
    /** The totem's time line: "Remaining: 2m 30s" or "Remaining: 5s". */
    private static final Pattern TOTEM_REMAINING = Pattern.compile("^Remaining: (?:(\\d+)m )?(\\d+)s$");
    /** The owner line under a totem ("Owner: ") or a black hole ("Spawned by: "). */
    private static final Pattern OWNER_TAG = Pattern.compile("^(?:Owner|Spawned by): (.+)$");
    /** How far apart the stands of one totem or black hole may be, in blocks. */
    private static final double STACK_REACH = 3.0;

    /** What one stand is, worked out once per stand; null means it is not a flare. */
    private static final Map<Integer, Deployable> FLARE_BY_ID = new HashMap<>();
    /** Above this many remembered stands the map is cleared rather than grown without end. */
    private static final int FLARE_CACHE_LIMIT = 512;
    private static ClientLevel cachedLevel = null;

    /** A deployable in range and its time left. */
    public record Active(Deployable type, int secondsLeft) {}

    /** The strongest of each family in range, in family order; empty when there is none. */
    private static List<Active> active = List.of();

    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /** The deployables whose buffs the player has now, one per family, in family order. */
    public static List<Active> active() {
        return active;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (!AlpakaConfig.instance.deployableTimerEnabled || level == null || player == null
                || !SkyblockUtils.isOnSkyblock()) {
            active = List.of();
            return;
        }
        if (level != cachedLevel || FLARE_BY_ID.size() > FLARE_CACHE_LIMIT) {
            FLARE_BY_ID.clear();
            cachedLevel = level;
        }

        EnumMap<Deployable.Family, Active> best = new EnumMap<>(Deployable.Family.class);
        List<ArmorStand> totems = new ArrayList<>();
        List<ArmorStand> blackHoles = new ArrayList<>();
        List<ArmorStand> stacked = new ArrayList<>();
        Boolean inMineshaft = null;

        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(SCAN_RADIUS))) {
            Deployable type;
            int seconds;
            Component name = stand.getCustomName();
            if (name != null) {
                String text = SkyblockUtils.cleanColor(name.getString());
                if (text.equals(Deployable.TOTEM_OF_CORRUPTION.displayName)) {
                    totems.add(stand);
                    continue;
                }
                if (text.startsWith(Deployable.BLACK_HOLE.displayName)) {
                    blackHoles.add(stand);
                    continue;
                }
                if (text.startsWith("Remaining: ") || OWNER_TAG.matcher(text).matches()) {
                    stacked.add(stand);
                    continue;
                }
                Matcher m = TIMED_TAG.matcher(text);
                if (!m.matches()) continue;
                type = Deployable.fromTagName(m.group(1));
                if (type == null) continue;
                seconds = Integer.parseInt(m.group(2));
            } else {
                type = flareOf(stand);
                if (type == null) continue;
                seconds = type.durationSeconds - stand.tickCount / 20;
            }
            if (seconds <= 0) continue;
            if (stand.distanceToSqr(player) > (double) type.range * type.range) {
                // The shaft is wider than the entities a client is sent, so a lantern seen at all counts.
                if (!type.wholeMineshaft()) continue;
                if (inMineshaft == null) inMineshaft = inMineshaft();
                if (!inMineshaft) continue;
            }
            offer(best, type, seconds);
        }

        String me = player.getGameProfile().name();
        for (ArmorStand totem : totems) {
            if (totem.distanceToSqr(player) > square(Deployable.TOTEM_OF_CORRUPTION.range)) continue;
            if (!ownedBy(totem, stacked, me)) continue;
            int seconds = totemSeconds(totem, stacked);
            if (seconds > 0) offer(best, Deployable.TOTEM_OF_CORRUPTION, seconds);
        }
        for (ArmorStand hole : blackHoles) {
            if (hole.distanceToSqr(player) > square(Deployable.BLACK_HOLE.range)) continue;
            if (!ownedBy(hole, stacked, me)) continue;
            Matcher m = TIMED_TAG.matcher(SkyblockUtils.cleanColor(hole.getCustomName().getString()));
            // For about a second after it is placed the tag is just "Black Hole", with no time yet.
            int seconds = m.matches() ? Integer.parseInt(m.group(2)) : Deployable.BLACK_HOLE.durationSeconds;
            if (seconds > 0) offer(best, Deployable.BLACK_HOLE, seconds);
        }

        active = best.isEmpty() ? List.of() : List.copyOf(best.values());
    }

    private static void offer(EnumMap<Deployable.Family, Active> best, Deployable type, int seconds) {
        Active current = best.get(type.family);
        if (current == null || type.beats(current.type())
                || (type == current.type() && seconds > current.secondsLeft())) {
            best.put(type.family, new Active(type, seconds));
        }
    }

    /** Whether one of the tags stacked on {@code main} names the player as its owner. */
    private static boolean ownedBy(ArmorStand main, List<ArmorStand> stacked, String me) {
        for (ArmorStand stand : stacked) {
            if (stand.distanceToSqr(main) > STACK_REACH * STACK_REACH) continue;
            Matcher m = OWNER_TAG.matcher(SkyblockUtils.cleanColor(stand.getCustomName().getString()));
            if (!m.matches()) continue;
            // The name may carry a rank in front ("[MVP+] Name"), so only its last word counts.
            String owner = m.group(1).trim();
            int space = owner.lastIndexOf(' ');
            if (owner.substring(space + 1).equals(me)) return true;
        }
        return false;
    }

    /** The seconds on the "Remaining:" tag stacked on a totem, or 0 if there is none. */
    private static int totemSeconds(ArmorStand totem, List<ArmorStand> stacked) {
        for (ArmorStand stand : stacked) {
            if (stand.distanceToSqr(totem) > STACK_REACH * STACK_REACH) continue;
            Matcher m = TOTEM_REMAINING.matcher(SkyblockUtils.cleanColor(stand.getCustomName().getString()));
            if (!m.matches()) continue;
            int minutes = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
            return minutes * 60 + Integer.parseInt(m.group(2));
        }
        return 0;
    }

    /** Whether the sidebar puts the player in a Glacite Mineshaft. */
    private static boolean inMineshaft() {
        for (String line : SkyblockUtils.getSidebarLines()) {
            if (line.contains("Mineshaft")) return true;
        }
        return false;
    }

    private static double square(int blocks) {
        return (double) blocks * blocks;
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
