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
        ours.bestBossMs = 31_000L;
        ours.totalXp = 1_000L;

        AlpakaConfig.SlayerData theirs = new AlpakaConfig.SlayerData();
        theirs.kills = 45;
        theirs.drops.put("Archfiend Dice", 44);
        theirs.drops.put("Wisp's Ice-Flavored Water I", 20);
        theirs.drops.put("Flawed Opal Gemstone", 3);
        theirs.bestBossMs = 29_500L;
        theirs.totalXp = 1_200L;

        StatsMerge.mergeSlayer(ours, theirs);

        assertEquals(45, ours.kills);
        assertEquals(44, ours.drops.get("Archfiend Dice"));
        assertEquals(39, ours.drops.get("Wisp's Ice-Flavored Water I"));
        assertEquals(3, ours.drops.get("Flawed Opal Gemstone"));
        assertEquals(29_500L, ours.bestBossMs);
        assertEquals(1_200L, ours.totalXp);
    }

    @Test
    void unsetBestTimeOnDiskDoesNotReplaceARealOne() {
        AlpakaConfig.SlayerData ours = new AlpakaConfig.SlayerData();
        ours.bestBossMs = 31_000L;
        StatsMerge.mergeSlayer(ours, new AlpakaConfig.SlayerData());
        assertEquals(31_000L, ours.bestBossMs);
    }

    @Test
    void placeholderBucketOfThePlayingAccountIsNotTakenBack() {
        AlpakaStats disk = record("main", PLACEHOLDER, SlayerType.WOLF, 7);
        AlpakaStats session = new AlpakaStats();
        session.accounts.put("main", new AlpakaStats.Account());

        StatsMerge.mergeFromDisk(session, disk, "main", PLACEHOLDER);

        assertNull(session.accounts.get("main").profiles.get(PLACEHOLDER));
    }
}
