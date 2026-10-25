package net.alpaka.addons.config;

import com.google.gson.Gson;
import net.alpaka.addons.features.slayer.SlayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatsMergeTest {
    private static final Gson GSON = new Gson();
    private static final String PLACEHOLDER = AlpakaStats.UNKNOWN_PROFILE;

    private static AlpakaStats record(String account, String profile, SlayerType type, int kills) {
        AlpakaStats stats = new AlpakaStats();
        add(stats, account, profile, type, kills);
        return stats;
    }

    private static void add(AlpakaStats stats, String account, String profile, SlayerType type, int kills) {
        AlpakaStats.Account entry = stats.accounts.computeIfAbsent(account, key -> new AlpakaStats.Account());
        entry.lastProfile = profile;
        AlpakaStats.ProfileStats profileStats = entry.profiles.computeIfAbsent(profile, key -> new AlpakaStats.ProfileStats());
        AlpakaConfig.SlayerData data = new AlpakaConfig.SlayerData();
        data.kills = kills;
        profileStats.slayerBossMap.put(type, data);
    }

    private static AlpakaConfig.SlayerData slayer(AlpakaStats stats, String account, String profile, SlayerType type) {
        return stats.accounts.get(account).profiles.get(profile).slayerBossMap.get(type);
    }

    private static int kills(AlpakaStats stats, String account, String profile, SlayerType type) {
        return slayer(stats, account, profile, type).kills;
    }

    private static AlpakaStats copy(AlpakaStats stats) {
        return GSON.fromJson(GSON.toJson(stats), AlpakaStats.class);
    }

    /** One save: read the file, merge it in, write memory back. */
    private static AlpakaStats save(AlpakaStats memory, AlpakaStats disk, String playing) {
        StatsMerge.mergeFromDisk(memory, copy(disk), playing, PLACEHOLDER);
        return copy(memory);
    }

    @Test
    void twoInstancesOnDifferentAccountsKeepEachOthersKills() {
        AlpakaStats disk = record("main", "kiwi", SlayerType.BLAZE, 100);
        add(disk, "alt", "kiwi", SlayerType.BLAZE, 50);

        AlpakaStats mainSession = copy(disk);
        AlpakaStats altSession = copy(disk);

        slayer(altSession, "alt", "kiwi", SlayerType.BLAZE).kills = 55;
        disk = save(altSession, disk, "alt");

        slayer(mainSession, "main", "kiwi", SlayerType.BLAZE).kills = 101;
        disk = save(mainSession, disk, "main");
        assertEquals(55, kills(disk, "alt", "kiwi", SlayerType.BLAZE), "main's save wrote back alt's stale count");

        slayer(altSession, "alt", "kiwi", SlayerType.BLAZE).kills = 56;
        disk = save(altSession, disk, "alt");

        assertEquals(101, kills(disk, "main", "kiwi", SlayerType.BLAZE));
        assertEquals(56, kills(disk, "alt", "kiwi", SlayerType.BLAZE));
    }

    @Test
    void sameAccountOtherProfileIsTakenFromDiskWhenNewer() {
        AlpakaStats disk = record("main", "kiwi", SlayerType.ENDERMAN, 10);
        add(disk, "main", "peach", SlayerType.ENDERMAN, 20);
        AlpakaStats session = copy(disk);

        // Another PC plays peach meanwhile.
        AlpakaStats other = copy(disk);
        slayer(other, "main", "peach", SlayerType.ENDERMAN).kills = 30;
        disk = save(other, disk, "main");

        slayer(session, "main", "kiwi", SlayerType.ENDERMAN).kills = 11;
        disk = save(session, disk, "main");

        assertEquals(11, kills(disk, "main", "kiwi", SlayerType.ENDERMAN));
        assertEquals(30, kills(disk, "main", "peach", SlayerType.ENDERMAN));
    }

    @Test
    void slayerMergeKeepsHigherKillsLaterDropBetterTimeNewerXp() {
        AlpakaConfig.SlayerData ours = new AlpakaConfig.SlayerData();
        ours.kills = 40;
        ours.drops.put("Archfiend Dice", 12);
        ours.drops.put("Wisp's Ice-Flavored Water I", 39);
        ours.bestBossMsByTier.put(4, 31_000L);
        ours.bestBossMsByTier.put(5, 52_000L);
        ours.totalXp = 1_000L;

        AlpakaConfig.SlayerData theirs = new AlpakaConfig.SlayerData();
        theirs.kills = 45;
        theirs.drops.put("Archfiend Dice", 44);
        theirs.drops.put("Wisp's Ice-Flavored Water I", 20);
        theirs.drops.put("Flawed Opal Gemstone", 3);
        theirs.bestBossMsByTier.put(4, 29_500L);
        theirs.bestBossMsByTier.put(5, 60_000L);
        theirs.bestBossMsByTier.put(3, 9_000L);
        theirs.totalXp = 1_200L;

        StatsMerge.mergeSlayer(ours, theirs);

        assertEquals(45, ours.kills);
        assertEquals(44, ours.drops.get("Archfiend Dice"));
        assertEquals(39, ours.drops.get("Wisp's Ice-Flavored Water I"));
        assertEquals(3, ours.drops.get("Flawed Opal Gemstone"));
        assertEquals(29_500L, ours.bestBossMsByTier.get(4));
        assertEquals(52_000L, ours.bestBossMsByTier.get(5));
        assertEquals(9_000L, ours.bestBossMsByTier.get(3));
        assertEquals(1_200L, ours.totalXp);
    }

    @Test
    void unsetBestTimeOnDiskDoesNotReplaceARealOne() {
        AlpakaConfig.SlayerData ours = new AlpakaConfig.SlayerData();
        ours.bestBossMsByTier.put(4, 31_000L);
        StatsMerge.mergeSlayer(ours, new AlpakaConfig.SlayerData());
        assertEquals(31_000L, ours.bestBossMsByTier.get(4));
    }

    @Test
    void placeholderBucketOfThePlayingAccountIsNotTakenBack() {
        AlpakaStats disk = record("main", PLACEHOLDER, SlayerType.WOLF, 7);
        AlpakaStats session = new AlpakaStats();
        session.accounts.put("main", new AlpakaStats.Account());

        StatsMerge.mergeFromDisk(session, disk, "main", PLACEHOLDER);

        assertNull(session.accounts.get("main").profiles.get(PLACEHOLDER));
    }

    /** One profile's Blaze record on disk, with a tier 4 best of 30 s set at {@code setAtMs} (0: not known). */
    private static AlpakaStats diskWithBest(long setAtMs) {
        AlpakaStats disk = record("main", "kiwi", SlayerType.BLAZE, 100);
        AlpakaConfig.SlayerData data = slayer(disk, "main", "kiwi", SlayerType.BLAZE);
        data.bestBossMsByTier.put(4, 30_000L);
        if (setAtMs > 0) data.bestBossAtMsByTier.put(4, setAtMs);
        return disk;
    }

    /** This session cleared its bests at {@code atMs}, as /alpakaslayer reset pb does. */
    private static void clearBests(AlpakaStats stats, long atMs) {
        AlpakaConfig.SlayerData data = slayer(stats, "main", "kiwi", SlayerType.BLAZE);
        data.bestBossMsByTier.clear();
        data.bestBossAtMsByTier.clear();
        data.bestsClearedAtMs = atMs;
    }

    @Test
    void aClearedBestStaysClearedThroughTheSave() {
        AlpakaStats disk = diskWithBest(1_000L);
        AlpakaStats session = copy(disk);
        clearBests(session, 2_000L);

        // The background write folds the file into a snapshot, writes that, and the file's copy is
        // folded back into memory afterwards. Neither may bring the best back.
        AlpakaStats written = save(session, disk, "main");
        assertNull(slayer(written, "main", "kiwi", SlayerType.BLAZE).bestBossMsByTier.get(4));
        StatsMerge.mergeFromDisk(session, copy(disk), "main", PLACEHOLDER);
        assertNull(slayer(session, "main", "kiwi", SlayerType.BLAZE).bestBossMsByTier.get(4));
    }

    @Test
    void aClearAlsoTakesBackBestsFromBeforeTheirTimeWasKept() {
        AlpakaStats disk = diskWithBest(0L);
        AlpakaStats session = copy(disk);
        clearBests(session, 2_000L);
        assertNull(slayer(save(session, disk, "main"), "main", "kiwi", SlayerType.BLAZE).bestBossMsByTier.get(4));
    }

    @Test
    void aBestSetAfterTheClearStands() {
        AlpakaStats disk = diskWithBest(1_000L);
        AlpakaStats session = copy(disk);
        clearBests(session, 2_000L);
        AlpakaConfig.SlayerData mine = slayer(session, "main", "kiwi", SlayerType.BLAZE);
        mine.bestBossMsByTier.put(4, 40_000L);
        mine.bestBossAtMsByTier.put(4, 3_000L);

        AlpakaStats written = save(session, disk, "main");
        assertEquals(40_000L, slayer(written, "main", "kiwi", SlayerType.BLAZE).bestBossMsByTier.get(4));
    }

    @Test
    void aClearByAnotherInstanceTakesBackTheBestHeldHere() {
        AlpakaStats session = diskWithBest(1_000L);
        AlpakaStats disk = copy(session);
        clearBests(disk, 2_000L);

        StatsMerge.mergeFromDisk(session, copy(disk), "main", PLACEHOLDER);
        AlpakaConfig.SlayerData mine = slayer(session, "main", "kiwi", SlayerType.BLAZE);
        assertNull(mine.bestBossMsByTier.get(4));
        assertEquals(2_000L, mine.bestsClearedAtMs);
    }

    @Test
    void aCorrectedXpTotalSurvivesTheSave() {
        AlpakaStats disk = record("main", "kiwi", SlayerType.VAMPIRE, 10);
        AlpakaConfig.SlayerData onDisk = slayer(disk, "main", "kiwi", SlayerType.VAMPIRE);
        onDisk.totalXp = 10_500L;
        onDisk.totalXpObservedAtMs = 1_000L;

        // The Slayer menu said less, long after the last credit: the total had drifted high.
        AlpakaStats session = copy(disk);
        AlpakaConfig.SlayerData mine = slayer(session, "main", "kiwi", SlayerType.VAMPIRE);
        mine.totalXp = 1_050L;
        mine.totalXpObservedAtMs = 2_000L;

        assertEquals(1_050L, slayer(save(session, disk, "main"), "main", "kiwi", SlayerType.VAMPIRE).totalXp);
        StatsMerge.mergeFromDisk(session, copy(disk), "main", PLACEHOLDER);
        assertEquals(1_050L, mine.totalXp);
    }

    @Test
    void aNewerXpReadingOnDiskWinsOverAHigherOldOne() {
        AlpakaConfig.SlayerData ours = new AlpakaConfig.SlayerData();
        ours.totalXp = 1_300L;
        ours.totalXpObservedAtMs = 1_000L;
        AlpakaConfig.SlayerData theirs = new AlpakaConfig.SlayerData();
        theirs.totalXp = 1_200L;
        theirs.totalXpObservedAtMs = 2_000L;

        StatsMerge.mergeSlayer(ours, theirs);
        assertEquals(1_200L, ours.totalXp);
        assertEquals(2_000L, ours.totalXpObservedAtMs);
    }

    @Test
    void killsCreditedOnTheSameReadingKeepTheHigherTotal() {
        AlpakaConfig.SlayerData ours = new AlpakaConfig.SlayerData();
        ours.totalXp = 1_050L;
        ours.totalXpObservedAtMs = 1_000L;
        AlpakaConfig.SlayerData theirs = new AlpakaConfig.SlayerData();
        theirs.totalXp = 1_100L;
        theirs.totalXpObservedAtMs = 1_000L;

        StatsMerge.mergeSlayer(ours, theirs);
        assertEquals(1_100L, ours.totalXp);
    }
}
