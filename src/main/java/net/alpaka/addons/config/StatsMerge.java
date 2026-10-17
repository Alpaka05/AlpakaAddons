package net.alpaka.addons.config;

import net.alpaka.addons.features.slayer.SlayerType;

import java.util.HashMap;
import java.util.Map;

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
        if (theirs.bestBossMs > 0 && (ours.bestBossMs <= 0 || theirs.bestBossMs < ours.bestBossMs)) {
            ours.bestBossMs = theirs.bestBossMs;
        }
        if (theirs.totalXp > ours.totalXp) ours.totalXp = theirs.totalXp;
        if (theirs.lastXpCreditedAtMs > ours.lastXpCreditedAtMs) {
            ours.lastXpCreditedAtMs = theirs.lastXpCreditedAtMs;
            ours.recentlyCreditedXp = theirs.recentlyCreditedXp;
        }
    }
}
