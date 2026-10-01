package net.alpaka.addons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.slayer.SkyblockProfileTracker;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.features.slayer.SlayerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-account, per-profile record of what a player has actually done.
 *
 * Kept apart from {@link AlpakaConfig} because the two answer different questions. Settings describe
 * how this installation should behave and belong to the instance - signing in with a second account
 * should not undo somebody's HUD layout. Kills, drops, best times and lifetime XP describe one
 * player on one Skyblock profile, and showing an alt's totals under another account's name is
 * simply wrong.
 *
 * Hypixel keeps slayer progress per profile too, so the profile is part of the key rather than just
 * the account: the same player on Kiwi and on Coconut has genuinely different totals.
 */
public class AlpakaStats {

    /**
     * Where the record used to live: inside the instance's own config folder.
     *
     * Still read, but only to move it out. Kept per instance, a second launcher on the same machine
     * - or a reinstall - started the player from zero, which is the opposite of what a lifetime kill
     * count is for.
     */
    private static final File LEGACY_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("alpaka-stats.json").toFile();

    private static final String FILE_NAME = "alpaka-stats.json";

    /** Copy of the last good file, kept beside it. See {@link #load()}. */
    private static final String BACKUP_NAME = "alpaka-stats.json.bak";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * The folder the record is kept in.
     *
     * Outside any one instance by default, so every launcher and every reinstall on this machine
     * sees the same history. {@link AlpakaConfig#statsDirectory} can point it somewhere else - at a
     * folder a cloud client syncs, which is what carries the record to another PC without this mod
     * needing a server or an account of its own.
     *
     * That override deliberately lives in the per-instance settings rather than in the record: it is
     * a fact about this machine, and the path to a synced folder is not the same on the next one.
     */
    public static File directory() {
        String override = AlpakaConfig.instance.statsDirectory;
        if (override != null && !override.isBlank()) return new File(override.trim());
        return defaultDirectory();
    }

    /** The per-user application-data folder for this OS, which is shared across instances. */
    private static File defaultDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) return new File(appData, "AlpakaAddons");
        } else if (os.contains("mac")) {
            return new File(home, "Library/Application Support/AlpakaAddons");
        } else {
            String dataHome = System.getenv("XDG_DATA_HOME");
            if (dataHome != null && !dataHome.isBlank()) return new File(dataHome, "AlpakaAddons");
            return new File(home, ".local/share/AlpakaAddons");
        }
        return new File(home, ".alpaka-addons");
    }

    public static File file() {
        return new File(directory(), FILE_NAME);
    }

    /** Bucket used when the account cannot be read at all, which should not happen in practice. */
    private static final String UNKNOWN_ACCOUNT = "unknown-account";

    /**
     * Bucket used before the profile has been announced.
     *
     * Normally only reached in the seconds between joining and Hypixel saying which profile it is,
     * and only for an account that has never been seen before - otherwise {@link Account#lastProfile}
     * covers the gap with the profile that account was last on, which is nearly always the right one.
     *
     * It can also hold a whole session's kills when the announcement never reaches the mod: another
     * mod's chat filter hiding that line did exactly that (see {@code SkyblockProfileTracker}).
     * Whatever ends up here is therefore folded into the next profile that is recognised, by
     * {@link #rescueStranded}; the bucket is a waiting room, never a destination.
     */
    private static final String UNKNOWN_PROFILE = "unknown-profile";

    /** One Skyblock profile's record. */
    public static class ProfileStats {
        public Map<SlayerType, AlpakaConfig.SlayerData> slayerBossMap = new HashMap<>();
    }

    /** One Minecraft account's profiles. */
    public static class Account {
        public Map<String, ProfileStats> profiles = new HashMap<>();

        /**
         * The profile this account was last seen on, so a brief unknown gap does not open a bucket.
         *
         * Always a real profile or null, never the placeholder. An earlier build stored the
         * placeholder here, and a file that said so kept every following session in the placeholder
         * for as long as the announcement stayed hidden - the memory of "unknown" is worth nothing.
         */
        public String lastProfile = null;
    }

    /** Keyed by account UUID rather than name, so a name change does not orphan the record. */
    public Map<String, Account> accounts = new HashMap<>();

    /**
     * Whether the pre-split record has already been taken over.
     *
     * Without this the legacy map would be copied into every new profile bucket the player visits,
     * handing an untouched profile someone else's kill count.
     */
    public boolean legacyImported = false;

    public static AlpakaStats instance = new AlpakaStats();

    /** The active account's UUID, or a placeholder while the game has no user. */
    private static String accountKey() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc == null ? null : mc.getUser();
        if (user == null || user.getProfileId() == null) return UNKNOWN_ACCOUNT;
        return user.getProfileId().toString();
    }

    /** The record for the account and profile in play, created on first use. */
    private static ProfileStats current() {
        retryLoadIfAwaiting();
        String account = accountKey();
        Account entry = instance.accounts.computeIfAbsent(account, key -> new Account());

        String profile = SkyblockProfileTracker.INSTANCE.currentFor(account);
        if (profile == null) profile = entry.lastProfile;

        boolean known = profile != null;
        if (!known) profile = UNKNOWN_PROFILE;

        // Only a real profile is remembered; see Account#lastProfile for what remembering the
        // placeholder did.
        if (known && !profile.equals(entry.lastProfile)) {
            entry.lastProfile = profile;
        }

        ProfileStats stats = entry.profiles.computeIfAbsent(profile, key -> new ProfileStats());

        // Both of these need a real profile. Filing a record under the placeholder is how the whole
        // history once ended up somewhere no profile could ever reach again.
        if (known) {
            rescueStranded(entry, stats);
            importLegacyInto(stats);
        }
        return stats;
    }

    /**
     * Folds a record left in the placeholder bucket into the profile it belongs to.
     *
     * The placeholder fills when something asks for the record before Hypixel has said which profile
     * this is: the HUD and the timers read it from the first frame, and the announcement arrives
     * seconds after the join - or never, while another mod hides that chat line. Whatever is filed
     * there was earned by this account on the profile it was on at the time, and the profile that is
     * announced next is the best available answer to which one that was. For one player on one
     * profile, which is what produced every stranded record so far, it is simply right.
     *
     * Merged rather than moved: kills add up, drop positions shift behind the kills the profile
     * already had, the better best time wins. An earlier version only moved into a profile that had
     * recorded nothing yet, so as not to combine two records on a guess - and so left every later
     * stranded kill in the placeholder for good. The alternative to merging is not "kept safe
     * somewhere", it is "never counted", which is the worse outcome for a lifetime kill count.
     */
    private static void rescueStranded(Account entry, ProfileStats target) {
        ProfileStats stranded = entry.profiles.get(UNKNOWN_PROFILE);
        if (stranded == null) return;

        // Tested on recorded progress rather than on the map being empty: slayerBossMap() seeds every
        // slayer with a blank entry on first use, so a placeholder that was merely read is not empty.
        if (!hasProgress(stranded.slayerBossMap)) {
            entry.profiles.remove(UNKNOWN_PROFILE);
            return;
        }

        int moved = 0;
        for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> e : stranded.slayerBossMap.entrySet()) {
            AlpakaConfig.SlayerData from = e.getValue();
            if (from == null) continue;
            AlpakaConfig.SlayerData into =
                    target.slayerBossMap.computeIfAbsent(e.getKey(), key -> new AlpakaConfig.SlayerData());
            moved += from.kills;
            mergeInto(into, from);
        }
        entry.profiles.remove(UNKNOWN_PROFILE);
        AlpakaAddons.LOGGER.info("Folded {} stranded slayer kills from the placeholder bucket into profile {}",
                moved, entry.lastProfile);
        markDirty();
    }

    /** Adds one slayer's stranded record to the profile's own. */
    private static void mergeInto(AlpakaConfig.SlayerData into, AlpakaConfig.SlayerData from) {
        int base = into.kills;
        into.kills += from.kills;

        // A drop is stored as the kill count it happened at, so the stranded ones are re-based behind
        // the kills the profile already had. That is exact when the stranded kills are the most
        // recent ones, which they are whenever the placeholder filled in this session; if an older
        // stranded record is folded in after the profile has moved on, the totals are still right
        // and only the "bosses since last drop" figure for those items comes out a little short.
        if (from.drops != null) {
            if (into.drops == null) into.drops = new HashMap<>();
            for (Map.Entry<String, Integer> drop : from.drops.entrySet()) {
                if (drop.getValue() == null) continue;
                into.drops.put(drop.getKey(), base + drop.getValue());
            }
        }

        if (from.bestBossMs > 0 && (into.bestBossMs <= 0 || from.bestBossMs < into.bestBossMs)) {
            into.bestBossMs = from.bestBossMs;
        }

        // Lifetime XP is read off the Slayer menu as an absolute figure, so the larger reading is the
        // newer one - and both were taken on the same profile.
        if (from.totalXp > into.totalXp) into.totalXp = from.totalXp;
        if (from.lastXpCreditedAtMs > into.lastXpCreditedAtMs) into.lastXpCreditedAtMs = from.lastXpCreditedAtMs;
    }

    private static AlpakaConfig.SlayerData copyOf(AlpakaConfig.SlayerData data) {
        return GSON.fromJson(GSON.toJson(data), AlpakaConfig.SlayerData.class);
    }

    /** Whether a record holds anything worth keeping. */
    private static boolean hasProgress(Map<SlayerType, AlpakaConfig.SlayerData> map) {
        for (AlpakaConfig.SlayerData data : map.values()) {
            if (data != null && (data.kills > 0 || (data.drops != null && !data.drops.isEmpty()))) return true;
        }
        return false;
    }

    /**
     * Hands the pre-split record to the first account and profile that asks for one.
     *
     * There is no way to know which account or profile earned it - it was never recorded - so the
     * player holding the game when the mod first runs after the update is the best available guess,
     * and is right for the single-account case that produced it. Done once, and only into a bucket
     * that is still empty, so it can never overwrite a record that already exists.
     *
     * Only ever called once the profile is actually known. Importing into the placeholder bucket put
     * a whole history somewhere the real profile would never look, which is the bug this guards.
     */
    private static void importLegacyInto(ProfileStats stats) {
        if (instance.legacyImported) return;
        // Not while waiting for the folder: the record there has usually taken over the legacy one
        // long ago, and this session's kills are folded into it once it appears. Importing now put
        // the whole pre-split history into the session, and folding the session in then counted it
        // a second time.
        if (awaitingStore) return;
        if (hasProgress(stats.slayerBossMap)) return;

        Map<SlayerType, AlpakaConfig.SlayerData> legacy = AlpakaConfig.instance.slayerBossMap;
        if (legacy == null || !hasProgress(legacy)) return;

        // Copied, so the record and the settings file never share the same objects.
        for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> e : legacy.entrySet()) {
            if (e.getValue() != null) stats.slayerBossMap.put(e.getKey(), copyOf(e.getValue()));
        }
        instance.legacyImported = true;
        AlpakaAddons.LOGGER.info("Imported the existing slayer record into the per-profile store");
        markDirty();
    }

    /**
     * The slayer record for whoever is playing right now.
     *
     * Every caller goes through this rather than holding onto the map, because the answer changes
     * when the player switches profile mid-session.
     */
    public static Map<SlayerType, AlpakaConfig.SlayerData> slayerBossMap() {
        ProfileStats stats = current();
        for (SlayerType type : SlayerType.values()) {
            stats.slayerBossMap.computeIfAbsent(type, key -> new AlpakaConfig.SlayerData());
        }
        return stats.slayerBossMap;
    }

    public static void load() {
        if (storeMissing()) {
            // Google Drive's mount, and every other cloud drive, comes up some seconds after login;
            // a game launched from the desktop straight after booting beats it. Reading nothing and
            // starting from zero is what made a whole history look wiped. Wait instead.
            awaitingStore = true;
            AlpakaAddons.LOGGER.warn("Slayer stats folder {} or its file is not there yet (drive not mounted or still syncing?); "
                    + "waiting for it before reading or writing the record", directory().getAbsolutePath());
            return;
        }
        awaitingStore = false;

        File file = file();
        migrateLegacy(file);

        AlpakaStats loaded = read(file);
        if (loaded != null) markFolderHadFile();
        if (loaded == null) {
            // A record that now lives in a folder something else may be syncing can be caught
            // half-written. The backup is the last copy that parsed, which beats starting over.
            loaded = read(new File(directory(), BACKUP_NAME));
            if (loaded != null) AlpakaAddons.LOGGER.warn("Slayer stats were unreadable; fell back to the backup");
        }
        if (loaded != null) instance = loaded;
        if (instance.accounts == null) instance.accounts = new HashMap<>();

        // Files written by an earlier build can remember the placeholder as the last profile. That
        // memory is worth nothing and would keep the session in the placeholder; see Account#lastProfile.
        for (Account account : instance.accounts.values()) {
            if (account == null) continue;
            if (account.profiles == null) account.profiles = new HashMap<>();
            if (UNKNOWN_PROFILE.equals(account.lastProfile)) account.lastProfile = null;
        }
    }

    /**
     * True while the record could not be read at start-up because its folder was not there.
     *
     * Only ever set for a folder the player chose: the default folder is created on demand and its
     * absence just means first use, but a chosen folder existed when it was chosen (the command checks),
     * so its absence means the drive behind it has not come up yet. While this is set nothing is
     * written, so a missing drive can never be "fixed" by a fresh empty record appearing on it, and
     * kills made in the meantime stay in memory until the folder shows up.
     */
    private static boolean awaitingStore = false;

    private static long nextStoreCheckNanos = 0L;

    /**
     * Whether a folder the player chose is not reachable right now: the folder is gone, or it is
     * there without the file it held before, which is what a cloud folder looks like while it is
     * still syncing.
     */
    private static boolean storeMissing() {
        String override = AlpakaConfig.instance.statsDirectory;
        if (override == null || override.isBlank()) return false;
        if (!directory().isDirectory()) return true;
        return AlpakaConfig.instance.statsDirectoryHadFile && !file().exists();
    }

    /** Remembers that the chosen folder holds the record now; see AlpakaConfig#statsDirectoryHadFile. */
    private static void markFolderHadFile() {
        String override = AlpakaConfig.instance.statsDirectory;
        if (override == null || override.isBlank() || AlpakaConfig.instance.statsDirectoryHadFile) return;
        AlpakaConfig.instance.statsDirectoryHadFile = true;
        AlpakaConfig.save();
    }

    /**
     * Reads the record once its folder turns up, and folds what this session did meanwhile into it.
     *
     * Called from every read and write of the record, which happens every frame while the HUD is
     * on, so the filesystem is only asked every couple of seconds.
     */
    public static void retryLoadIfAwaiting() {
        if (!awaitingStore) return;
        long now = System.nanoTime();
        if (now < nextStoreCheckNanos) return;
        nextStoreCheckNanos = now + 2_000_000_000L;
        if (storeMissing()) return;

        AlpakaStats session = instance;
        instance = new AlpakaStats();
        load();
        if (awaitingStore) {
            instance = session;
            return;
        }

        int folded = foldInto(instance, session);
        AlpakaAddons.LOGGER.info("Slayer stats folder {} is available now; loaded the record and folded {} kills from this session into it",
                directory().getAbsolutePath(), folded);
        SlayerDropTracker.sendModMessage("§aSlayer stats folder is available again; the record was loaded"
                + (folded > 0 ? " and §f" + folded + "§a kills from this session were added." : "."));
        markDirty();
    }

    /** Adds every account and profile of {@code from} to {@code into}; returns the kills moved. */
    private static int foldInto(AlpakaStats into, AlpakaStats from) {
        int moved = 0;
        if (from == null || from.accounts == null) return 0;
        for (Map.Entry<String, Account> accountEntry : from.accounts.entrySet()) {
            Account theirs = accountEntry.getValue();
            if (theirs == null || theirs.profiles == null) continue;
            Account ours = into.accounts.computeIfAbsent(accountEntry.getKey(), key -> new Account());
            if (ours.profiles == null) ours.profiles = new HashMap<>();
            if (theirs.lastProfile != null) ours.lastProfile = theirs.lastProfile;

            for (Map.Entry<String, ProfileStats> profileEntry : theirs.profiles.entrySet()) {
                ProfileStats source = profileEntry.getValue();
                if (source == null || source.slayerBossMap == null) continue;
                ProfileStats target = ours.profiles.computeIfAbsent(profileEntry.getKey(), key -> new ProfileStats());
                for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> e : source.slayerBossMap.entrySet()) {
                    AlpakaConfig.SlayerData data = e.getValue();
                    if (data == null) continue;
                    moved += data.kills;
                    mergeInto(target.slayerBossMap.computeIfAbsent(e.getKey(), key -> new AlpakaConfig.SlayerData()), data);
                }
            }
        }
        return moved;
    }

    private static long lastStoreMissingNoticeMs = 0L;

    /** Tells the player, at most every few minutes, that kills are waiting for the folder to appear. */
    private static void warnStoreMissing() {
        long now = System.currentTimeMillis();
        if (now - lastStoreMissingNoticeMs < SAVE_FAILURE_NOTICE_INTERVAL_MS) return;
        lastStoreMissingNoticeMs = now;
        boolean folderThere = directory().isDirectory();
        AlpakaAddons.LOGGER.warn("Slayer stats not saved: {} is still not there", (folderThere ? file() : directory()).getAbsolutePath());
        if (folderThere) {
            SlayerDropTracker.sendModMessage("§eThe slayer stats file is missing from §f" + directory().getAbsolutePath());
            SlayerDropTracker.sendModMessage("§7Is the folder still syncing? Kills are kept in memory and written as soon as the file is back. "
                    + "§f/alpakastats folder default §7switches to the shared folder instead.");
        } else {
            SlayerDropTracker.sendModMessage("§eSlayer stats folder is not available: §f" + directory().getAbsolutePath());
            SlayerDropTracker.sendModMessage("§7Is the drive mounted? Kills are kept in memory and written as soon as the folder appears. "
                    + "§f/alpakastats folder default §7switches to the shared folder instead.");
        }
    }

    /** Whether the record is waiting for its folder to appear. */
    public static boolean isAwaitingStore() {
        return awaitingStore;
    }

    /**
     * Moves a record left in the instance's own config folder to the shared one, once.
     *
     * Copied rather than moved: if this machine is later pointed back at an older build, or the
     * shared copy is lost, the original is still where that build would look for it.
     */
    private static void migrateLegacy(File target) {
        if (target.exists() || !LEGACY_FILE.exists()) return;
        try {
            File dir = target.getParentFile();
            if (dir != null) dir.mkdirs();
            java.nio.file.Files.copy(LEGACY_FILE.toPath(), target.toPath());
            AlpakaAddons.LOGGER.info("Moved the slayer record to the shared store at {}", target.getAbsolutePath());
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to move the slayer record to the shared store", e);
        }
    }

    private static AlpakaStats read(File file) {
        try {
            String json = AtomicJsonFile.read(file);
            if (json == null || json.isBlank()) return null;
            return GSON.fromJson(json, AlpakaStats.class);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to load stats from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static boolean dirty = false;
    private static long nextFlushAttemptNanos = 0L;

    /**
     * Records that the record changed; it is written at the end of the tick, once, however many
     * changes the tick made. A boss kill used to write the whole file two or three times over - the
     * kill, the XP and the best time each saved on their own - which hitched the game at the moment
     * the boss died, and more so on a cloud drive.
     */
    public static void markDirty() {
        dirty = true;
    }

    /**
     * Writes the record if anything changed. Called every client tick, and on disconnect and exit.
     * A failed write is retried every couple of seconds rather than every tick.
     */
    public static void flushIfDirty() {
        if (!dirty) return;
        if (awaitingStore) {
            // Rate-limited inside: says every few minutes that kills are waiting for the folder.
            warnStoreMissing();
            return;
        }
        long now = System.nanoTime();
        if (now < nextFlushAttemptNanos) return;
        dirty = false;
        if (!save()) {
            dirty = true;
            nextFlushAttemptNanos = now + 2_000_000_000L;
        }
    }

    /** Writes a pending change now, ignoring the retry pause. For disconnecting and quitting. */
    public static void flushNow() {
        if (!dirty || awaitingStore) return;
        dirty = false;
        if (!save()) dirty = true;
    }

    /** Sibling file used to keep two local instances from saving at the same moment. */
    private static final String LOCK_NAME = "alpaka-stats.lock";

    /**
     * Writes the record now, keeping what other sessions have put there. Returns whether it was
     * written. Gameplay code calls {@link #markDirty()} instead and lets the tick write it.
     *
     * The file may be shared - by a second instance on this machine, or through a sync folder with
     * another PC - so it can have moved on since this session read it. The file is re-read and merged
     * slayer by slayer before writing (see {@link #mergeFromDisk}), under a lock that keeps two local
     * instances from interleaving the read and the write.
     *
     * Two machines playing the same profile at the same time can still lose the smaller of their two
     * sets of new kills, since only the larger count survives the merge; nothing short of one file
     * per machine fixes that.
     */
    public static boolean save() {
        retryLoadIfAwaiting();
        if (awaitingStore) {
            warnStoreMissing();
            return false;
        }
        // The folder or its file went away mid-session. Nothing is written until it is back, so an
        // empty folder can never receive a fresh record in place of the real one; memory still holds
        // everything, and the next save after it returns writes it.
        if (storeMissing()) {
            warnStoreMissing();
            return false;
        }

        File file = file();
        File dir = file.getAbsoluteFile().getParentFile();
        try {
            if (dir != null && !dir.isDirectory()) java.nio.file.Files.createDirectories(dir.toPath());

            try (java.nio.channels.FileChannel channel = openLock(dir);
                 java.nio.channels.FileLock lock = channel == null ? null : tryLock(channel)) {
                AlpakaStats onDisk = read(file);
                mergeFromDisk(onDisk);
                // Only a copy that parsed becomes the backup, so a damaged file can never replace
                // the last good one.
                if (onDisk != null) AtomicJsonFile.backup(file, new File(dir, BACKUP_NAME));
                AtomicJsonFile.write(file, GSON.toJson(instance));
            }
            markFolderHadFile();
            dirty = false;
            return true;
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save stats to {}", file.getAbsolutePath(), e);
            warnSaveFailed(file, e);
            return false;
        }
    }

    private static java.nio.channels.FileChannel openLock(File dir) {
        if (dir == null) return null;
        try {
            return java.nio.channels.FileChannel.open(new File(dir, LOCK_NAME).toPath(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
        } catch (Exception e) {
            // Some cloud drives refuse this; the save goes ahead without the lock.
            return null;
        }
    }

    /**
     * Best effort: a few short tries, then carry on unlocked. Another instance only holds the lock
     * for the few milliseconds its own save takes.
     */
    private static java.nio.channels.FileLock tryLock(java.nio.channels.FileChannel channel) {
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                java.nio.channels.FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (Exception e) {
                return null;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** How often, at most, a failing save is announced in chat; a save follows every kill and drop. */
    private static final long SAVE_FAILURE_NOTICE_INTERVAL_MS = 5 * 60_000L;

    private static long lastSaveFailureNoticeMs = 0L;

    /**
     * Tells the player, in chat, that the record is not being written.
     *
     * The log alone was not enough. A folder setting that pointed at a file instead of a directory
     * made every save fail for a whole night, and 99 boss kills went unrecorded with nothing on
     * screen to say so; the player found out the next day, when a restart came up empty.
     */
    private static void warnSaveFailed(File file, Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastSaveFailureNoticeMs < SAVE_FAILURE_NOTICE_INTERVAL_MS) return;
        lastSaveFailureNoticeMs = now;

        String reason = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        SlayerDropTracker.sendModMessage("§cSlayer stats could not be saved to §f" + file.getAbsolutePath());
        SlayerDropTracker.sendModMessage("§c" + reason);
        SlayerDropTracker.sendModMessage("§7Kills since the last successful save exist only in memory. "
                + "§f/alpakastats folder default §7or a valid folder fixes this without a restart.");
    }

    /**
     * Folds what is on disk into memory, slayer by slayer, for every account and profile.
     *
     * Each slayer keeps the higher kill count, the later position of each drop, the better best time
     * and the newer XP reading. Kills only ever go up, so whichever side is behind simply catches up.
     * This used to keep memory's copy of every account and profile other than the one in play, so two
     * instances on different accounts each wrote back a stale copy of the other and erased its kills.
     *
     * The placeholder bucket of the account in play is this session's, whatever the disk says: only
     * that account writes it, and it has usually just been folded into its profile by
     * {@link #rescueStranded}; taking a stale copy back from disk would count that record twice.
     */
    private static void mergeFromDisk(AlpakaStats onDisk) {
        if (onDisk == null || onDisk.accounts == null) return;

        String playing = accountKey();
        for (Map.Entry<String, Account> entry : onDisk.accounts.entrySet()) {
            Account theirs = entry.getValue();
            if (theirs == null || theirs.profiles == null) continue;
            boolean isPlaying = entry.getKey().equals(playing);

            Account ours = instance.accounts.computeIfAbsent(entry.getKey(), key -> new Account());
            if (ours.profiles == null) ours.profiles = new HashMap<>();
            // Which profile another account was last on is for that account's own session to say.
            if (!isPlaying && theirs.lastProfile != null) ours.lastProfile = theirs.lastProfile;

            for (Map.Entry<String, ProfileStats> profileEntry : theirs.profiles.entrySet()) {
                String name = profileEntry.getKey();
                ProfileStats disk = profileEntry.getValue();
                if (disk == null || disk.slayerBossMap == null) continue;
                if (isPlaying && UNKNOWN_PROFILE.equals(name)) continue;

                ProfileStats mine = ours.profiles.get(name);
                if (mine == null || mine.slayerBossMap == null) {
                    ours.profiles.put(name, disk);
                } else {
                    mergeNewer(mine, disk, name);
                }
            }
        }

        if (onDisk.legacyImported) instance.legacyImported = true;
    }

    /** Merges one profile's record from disk into memory's; see {@link #mergeFromDisk}. */
    private static void mergeNewer(ProfileStats mine, ProfileStats disk, String profile) {
        for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> e : disk.slayerBossMap.entrySet()) {
            AlpakaConfig.SlayerData theirs = e.getValue();
            if (theirs == null) continue;
            AlpakaConfig.SlayerData ours = mine.slayerBossMap.get(e.getKey());
            if (ours == null) {
                mine.slayerBossMap.put(e.getKey(), theirs);
                continue;
            }

            if (theirs.kills > ours.kills) {
                AlpakaAddons.LOGGER.info("Took the {} {} kill count from disk ({} kills) over the one in memory ({} kills)",
                        profile, e.getKey(), theirs.kills, ours.kills);
                ours.kills = theirs.kills;
            }
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
            if (theirs.lastXpCreditedAtMs > ours.lastXpCreditedAtMs) ours.lastXpCreditedAtMs = theirs.lastXpCreditedAtMs;
        }
    }
}
