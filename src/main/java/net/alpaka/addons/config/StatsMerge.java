package net.alpaka.addons.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import net.alpaka.addons.features.slayer.SlayerType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Folding the slayer record on disk into the one in memory before a save.
 *
 * Kept apart from {@link AlpakaStats} so it can be tested without the game: it touches nothing but
 * the record's own data classes.
 */
final class StatsMerge {
    private StatsMerge() {}

    /**
     * Folds {@code onDisk} into {@code into}, slayer by slayer, for every account and profile.
     *
     * Each slayer keeps the higher kill count, the later position of each drop, the better best time
     * and the newer XP reading. Kills only ever go up, so whichever side is behind simply catches up.
     * This used to keep memory's copy of every account and profile other than the one in play, so two
     * instances on different accounts each wrote back a stale copy of the other and erased its kills.
     *
     * The placeholder bucket of {@code playingAccount} is this session's, whatever the disk says: only
     * that account writes it, and it has usually just been folded into its profile; taking a stale
     * copy back from disk would count that record twice.
     */
    static void mergeFromDisk(AlpakaStats into, AlpakaStats onDisk, String playingAccount, String placeholderProfile) {
        if (onDisk == null || onDisk.accounts == null) return;
        if (into.accounts == null) into.accounts = new HashMap<>();

        for (Map.Entry<String, AlpakaStats.Account> entry : onDisk.accounts.entrySet()) {
            AlpakaStats.Account theirs = entry.getValue();
            if (theirs == null || theirs.profiles == null) continue;
            boolean isPlaying = entry.getKey().equals(playingAccount);

            AlpakaStats.Account ours = into.accounts.computeIfAbsent(entry.getKey(), key -> new AlpakaStats.Account());
            if (ours.profiles == null) ours.profiles = new HashMap<>();
            // Which profile another account was last on is for that account's own session to say.
            if (!isPlaying && theirs.lastProfile != null) ours.lastProfile = theirs.lastProfile;

            for (Map.Entry<String, AlpakaStats.ProfileStats> profileEntry : theirs.profiles.entrySet()) {
                String name = profileEntry.getKey();
                AlpakaStats.ProfileStats disk = profileEntry.getValue();
                if (disk == null || disk.slayerBossMap == null) continue;
                if (isPlaying && placeholderProfile.equals(name)) continue;

                AlpakaStats.ProfileStats mine = ours.profiles.get(name);
                if (mine == null || mine.slayerBossMap == null) {
                    ours.profiles.put(name, disk);
                } else {
                    mergeProfile(mine, disk);
                }
            }
        }

        if (onDisk.legacyImported) into.legacyImported = true;
    }

    /** Merges one profile's record from disk into memory's. */
    static void mergeProfile(AlpakaStats.ProfileStats mine, AlpakaStats.ProfileStats disk) {
        for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> e : disk.slayerBossMap.entrySet()) {
            AlpakaConfig.SlayerData theirs = e.getValue();
            if (theirs == null) continue;
            AlpakaConfig.SlayerData ours = mine.slayerBossMap.get(e.getKey());
            if (ours == null) {
                mine.slayerBossMap.put(e.getKey(), theirs);
            } else {
                mergeSlayer(ours, theirs);
            }
        }
    }

    /** Keeps the faster of the two best times for every tier either side has one for. */
    static void mergeBestTimes(AlpakaConfig.SlayerData into, AlpakaConfig.SlayerData from) {
        if (from.bestBossMsByTier == null) return;
        if (into.bestBossMsByTier == null) into.bestBossMsByTier = new HashMap<>();
        for (Map.Entry<Integer, Long> best : from.bestBossMsByTier.entrySet()) {
            Long theirs = best.getValue();
            if (best.getKey() == null || theirs == null || theirs <= 0) continue;
            Long ours = into.bestBossMsByTier.get(best.getKey());
            if (ours == null || ours <= 0 || theirs < ours) into.bestBossMsByTier.put(best.getKey(), theirs);
        }
    }

    /** One slayer: the higher kills, the later drop, the better best time, the newer XP. */
    static void mergeSlayer(AlpakaConfig.SlayerData ours, AlpakaConfig.SlayerData theirs) {
        if (theirs.kills > ours.kills) ours.kills = theirs.kills;
        if (theirs.drops != null) {
            if (ours.drops == null) ours.drops = new HashMap<>();
            for (Map.Entry<String, Integer> drop : theirs.drops.entrySet()) {
                if (drop.getValue() == null) continue;
                Integer at = ours.drops.get(drop.getKey());
                if (at == null || drop.getValue() > at) ours.drops.put(drop.getKey(), drop.getValue());
            }
        }
        mergeBestTimes(ours, theirs);
        if (theirs.totalXp > ours.totalXp) ours.totalXp = theirs.totalXp;
        if (theirs.lastXpCreditedAtMs > ours.lastXpCreditedAtMs) {
            ours.lastXpCreditedAtMs = theirs.lastXpCreditedAtMs;
            ours.recentlyCreditedXp = theirs.recentlyCreditedXp;
        }
    }

    /**
     * Puts back into {@code written} - the record as this version writes it - what {@code disk}
     * holds that this version does not know: fields a newer version added to the record, an
     * account's, a profile's or a slayer's, and slayers it does not know. Without this, an older
     * copy of the mod writing the shared file (a second launcher, the backport line) threw away
     * whatever the newer one had added; the per-tier bests went that way once.
     *
     * Only unknown fields and unknown slayers come back. Entries of the maps this version does know
     * (accounts, profiles, drops, bests) are what the typed merge decided, and bringing a missing
     * one back from the file would undo a cleared best. The schema version never goes down.
     */
    static void keepUnknown(JsonObject disk, JsonObject written) {
        copyUnknownFields(disk, written, AlpakaStats.class);
        int theirs = schemaOf(disk);
        if (theirs > schemaOf(written)) written.addProperty("schemaVersion", theirs);
        JsonObject diskAccounts = object(disk, "accounts");
        JsonObject ourAccounts = object(written, "accounts");
        if (diskAccounts == null || ourAccounts == null) return;
        for (Map.Entry<String, JsonElement> account : diskAccounts.entrySet()) {
            JsonObject diskAccount = asObject(account.getValue());
            JsonObject ourAccount = object(ourAccounts, account.getKey());
            if (diskAccount == null || ourAccount == null) continue;
            copyUnknownFields(diskAccount, ourAccount, AlpakaStats.Account.class);

            JsonObject diskProfiles = object(diskAccount, "profiles");
            JsonObject ourProfiles = object(ourAccount, "profiles");
            if (diskProfiles == null || ourProfiles == null) continue;
            for (Map.Entry<String, JsonElement> profile : diskProfiles.entrySet()) {
                JsonObject diskProfile = asObject(profile.getValue());
                JsonObject ourProfile = object(ourProfiles, profile.getKey());
                if (diskProfile == null || ourProfile == null) continue;
                copyUnknownFields(diskProfile, ourProfile, AlpakaStats.ProfileStats.class);

                JsonObject diskSlayers = object(diskProfile, "slayerBossMap");
                JsonObject ourSlayers = object(ourProfile, "slayerBossMap");
                if (diskSlayers == null || ourSlayers == null) continue;
                for (Map.Entry<String, JsonElement> slayer : diskSlayers.entrySet()) {
                    if (!isSlayer(slayer.getKey())) {
                        // A slayer only a newer version knows: kept whole.
                        if (!ourSlayers.has(slayer.getKey())) ourSlayers.add(slayer.getKey(), slayer.getValue());
                        continue;
                    }
                    JsonObject diskSlayer = asObject(slayer.getValue());
                    JsonObject ourSlayer = object(ourSlayers, slayer.getKey());
                    if (diskSlayer != null && ourSlayer != null) {
                        copyUnknownFields(diskSlayer, ourSlayer, AlpakaConfig.SlayerData.class);
                    }
                }
            }
        }
    }

    /** The layout a record says it is in; files from before the field count as 1. */
    static int schemaOf(JsonObject json) {
        JsonElement version = json.get("schemaVersion");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) return 1;
        return version.getAsInt();
    }

    /**
     * A copy of {@code json} without the slayers this version does not know, for reading it into the
     * typed record. Gson reads an unknown enum key as null: one such slayer turned into a "null"
     * entry that was written back as such, and two made the whole file unreadable ("duplicate key").
     * {@link #keepUnknown} puts them back on the next write.
     */
    static JsonObject knownOnly(JsonObject json) {
        JsonObject copy = json.deepCopy();
        JsonObject accounts = object(copy, "accounts");
        if (accounts == null) return copy;
        for (Map.Entry<String, JsonElement> account : accounts.entrySet()) {
            JsonObject profiles = object(asObject(account.getValue()), "profiles");
            if (profiles == null) continue;
            for (Map.Entry<String, JsonElement> profile : profiles.entrySet()) {
                JsonObject slayers = object(asObject(profile.getValue()), "slayerBossMap");
                if (slayers == null) continue;
                for (String key : new java.util.ArrayList<>(slayers.keySet())) {
                    if (!isSlayer(key)) slayers.remove(key);
                }
            }
        }
        return copy;
    }

    private static final Map<Class<?>, Set<String>> KNOWN_FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Copies the members of {@code from} that are no field of {@code type} into {@code into}. */
    private static void copyUnknownFields(JsonObject from, JsonObject into, Class<?> type) {
        Set<String> known = KNOWN_FIELDS.computeIfAbsent(type, StatsMerge::serializedFields);
        for (Map.Entry<String, JsonElement> member : from.entrySet()) {
            if (!known.contains(member.getKey()) && !into.has(member.getKey())) {
                into.add(member.getKey(), member.getValue());
            }
        }
    }

    /** The names Gson reads and writes for {@code type}'s fields, alternates included. */
    private static Set<String> serializedFields(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) continue;
            SerializedName serialized = field.getAnnotation(SerializedName.class);
            if (serialized == null) {
                names.add(field.getName());
            } else {
                names.add(serialized.value());
                names.addAll(java.util.List.of(serialized.alternate()));
            }
        }
        return names;
    }

    private static boolean isSlayer(String name) {
        for (SlayerType type : SlayerType.values()) {
            if (type.name().equals(name)) return true;
        }
        return false;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? asObject(parent.get(key)) : null;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }
}
