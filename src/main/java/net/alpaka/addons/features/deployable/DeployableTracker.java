package net.alpaka.addons.features.deployable;

import com.mojang.authlib.properties.Property;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
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

    /**
     * "Plasmaflux 52s", once the color codes are gone. The digits are bounded, here and below, so a
     * stray name cannot overflow parseInt: this runs every tick, and an exception there crashes the game.
     */
    private static final Pattern TIMED_TAG = Pattern.compile("^(.+?) (\\d{1,5})s$");
    /** The totem's time line: "Remaining: 2m 30s" or "Remaining: 5s". */
    private static final Pattern TOTEM_REMAINING = Pattern.compile("^Remaining: (?:(\\d{1,5})m )?(\\d{1,5})s$");
    /** The owner line under a totem ("Owner: ") or a black hole ("Spawned by: "). */
    private static final Pattern OWNER_TAG = Pattern.compile("^(?:Owner|Spawned by): (.+)$");
    /** How far apart the stands of one totem or black hole may be, in blocks. */
    private static final double STACK_REACH = 3.0;

    /** What one stand is, worked out once per stand; null means it is not a flare. */
    private static final Int2ObjectOpenHashMap<Deployable> FLARE_BY_ID = new Int2ObjectOpenHashMap<>();
    /** Above this many remembered stands the map is cleared rather than grown without end. */
    private static final int FLARE_CACHE_LIMIT = 512;
    /** Weak, so the world left behind is not kept alive until the next scan. */
    private static WeakReference<ClientLevel> cachedLevel = new WeakReference<>(null);

    /**
     * What each stand's name says, kept with the name object it was read from. Every stand in range
     * is read every tick, and a name only changes when the server sends a new one, which is a new
     * object; everything below depends on the name alone. Client thread only.
     */
    private static final Int2ObjectOpenHashMap<Tag> TAGS = new Int2ObjectOpenHashMap<>();
    private static final int TAG_CACHE_LIMIT = 1024;

    private enum TagKind { TOTEM, BLACK_HOLE, STACKED, TIMED, NONE }

    /**
     * One name tag, read once. {@code type}/{@code seconds}: the timed tag ("Plasmaflux 52s"), also
     * the time on a black hole (-1 while it shows none). {@code owner}: the last word of an owner
     * line, else null. {@code remaining}: the seconds of a totem's "Remaining:" line, else -1.
     */
    private record Tag(Component name, TagKind kind, Deployable type, int seconds, String owner, int remaining) {}

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
        if (level != cachedLevel.get() || FLARE_BY_ID.size() > FLARE_CACHE_LIMIT) {
            FLARE_BY_ID.clear();
            TAGS.clear();
            cachedLevel = new WeakReference<>(level);
        }
        // Stands that are gone are never taken out one by one; past the limit the whole map goes.
        if (TAGS.size() > TAG_CACHE_LIMIT) TAGS.clear();

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
                Tag tag = tagOf(stand, name);
                switch (tag.kind()) {
                    case TOTEM -> { totems.add(stand); continue; }
                    case BLACK_HOLE -> { blackHoles.add(stand); continue; }
                    case STACKED -> { stacked.add(stand); continue; }
                    case NONE -> { continue; }
                    case TIMED -> { }
                }
                type = tag.type();
                seconds = tag.seconds();
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
            Tag tag = tagOf(hole, hole.getCustomName());
            // For about a second after it is placed the tag is just "Black Hole", with no time yet.
            int seconds = tag.seconds() >= 0 ? tag.seconds() : Deployable.BLACK_HOLE.durationSeconds;
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
            String owner = tagOf(stand, stand.getCustomName()).owner();
            if (owner != null && owner.equals(me)) return true;
        }
        return false;
    }

    /** The seconds on the "Remaining:" tag stacked on a totem, or 0 if there is none. */
    private static int totemSeconds(ArmorStand totem, List<ArmorStand> stacked) {
        for (ArmorStand stand : stacked) {
            if (stand.distanceToSqr(totem) > STACK_REACH * STACK_REACH) continue;
            int remaining = tagOf(stand, stand.getCustomName()).remaining();
            if (remaining >= 0) return remaining;
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

    /** What a stand's name tag says; see {@link #TAGS}. */
    private static Tag tagOf(ArmorStand stand, Component name) {
        int id = stand.getId();
        Tag cached = TAGS.get(id);
        if (cached != null && cached.name() == name) return cached;
        Tag tag = parse(name, SkyblockUtils.cleanColor(name.getString()));
        TAGS.put(id, tag);
        return tag;
    }

    /** Reads one name tag (color codes gone) the way the scan used to read it every tick. */
    private static Tag parse(Component name, String text) {
        Matcher owner = OWNER_TAG.matcher(text);
        // The name may carry a rank in front ("[MVP+] Name"), so only its last word counts.
        String ownerWord = null;
        if (owner.matches()) {
            String full = owner.group(1).trim();
            ownerWord = full.substring(full.lastIndexOf(' ') + 1);
        }
        Matcher remainingMatch = TOTEM_REMAINING.matcher(text);
        int remaining = -1;
        if (remainingMatch.matches()) {
            int minutes = remainingMatch.group(1) == null ? 0 : Integer.parseInt(remainingMatch.group(1));
            remaining = minutes * 60 + Integer.parseInt(remainingMatch.group(2));
        }
        Matcher timed = TIMED_TAG.matcher(text);
        boolean isTimed = timed.matches();
        int seconds = isTimed ? Integer.parseInt(timed.group(2)) : -1;

        TagKind kind;
        Deployable type = null;
        if (text.equals(Deployable.TOTEM_OF_CORRUPTION.displayName)) {
            kind = TagKind.TOTEM;
        } else if (text.startsWith(Deployable.BLACK_HOLE.displayName)) {
            kind = TagKind.BLACK_HOLE;
        } else if (text.startsWith("Remaining: ") || ownerWord != null) {
            kind = TagKind.STACKED;
        } else if (isTimed && (type = Deployable.fromTagName(timed.group(1))) != null) {
            kind = TagKind.TIMED;
        } else {
            kind = TagKind.NONE;
        }
        return new Tag(name, kind, type, seconds, ownerWord, remaining);
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
