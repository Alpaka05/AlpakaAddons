package net.alpaka.addons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Locale;

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
    private static File legacyFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("alpaka-stats.json").toFile();
    }

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
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
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
    static final String UNKNOWN_PROFILE = "unknown-profile";

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

    /**
     * The layout of the record this version writes. A file with a higher number comes from a newer
     * version: what that version added is kept as it is (StatsMerge#keepUnknown), and the player is
     * told once that this version cannot show it.
     */
    static final int SCHEMA_VERSION = 2;

    /** The layout the file was written in; files from before this field count as 1. */
    public int schemaVersion = SCHEMA_VERSION;

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

        StatsMerge.mergeBestTimes(into, from);
        StatsMerge.mergeTotalXp(into, from);
        if (from.lastXpCreditedAtMs > into.lastXpCreditedAtMs) into.lastXpCreditedAtMs = from.lastXpCreditedAtMs;
    }

    private static AlpakaConfig.SlayerData copyOf(AlpakaConfig.SlayerData data) {
        return GSON.fromJson(GSON.toJson(data), AlpakaConfig.SlayerData.class);
    }

    /**
     * Whether a record holds anything worth keeping: kills, drops, a best time or a lifetime XP
     * reading. Counting kills and drops alone, a new account's bests and XP from a session without
     * the drop tracker were thrown away with the placeholder bucket.
     */
    static boolean hasProgress(Map<SlayerType, AlpakaConfig.SlayerData> map) {
        for (AlpakaConfig.SlayerData data : map.values()) {
            if (data == null) continue;
            if (data.kills > 0 || (data.drops != null && !data.drops.isEmpty()) || data.totalXp >= 0L) return true;
            if (data.bestBossMsByTier != null
                    && data.bestBossMsByTier.values().stream().anyMatch(best -> best != null && best > 0L)) return true;
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
        Store store = Store.current();
        java.util.concurrent.Future<Loaded> reading = WRITER.submit(() -> readStore(store));
        Loaded result = takeLoaded(reading, WRITER_WAIT_MS);
        if (result != null) {
            apply(result);
            return;
        }
        // A drive that stopped answering. Carry on as if its folder were not there yet; the record
        // is taken over when the read comes back (retryLoadIfAwaiting).
        awaitingStore = true;
        awaitingPresence = Presence.NO_FOLDER;
        pendingLoad = reading;
        AlpakaAddons.LOGGER.warn("Slayer stats folder {} did not answer within {} ms; waiting for it before reading or writing the record",
                store.directory().getAbsolutePath(), WRITER_WAIT_MS);
    }

    /**
     * Called every client tick: looks for a folder the record is waiting for, writes the record if it
     * changed, and shows what had to wait until the player was in game.
     */
    public static void tick() {
        retryLoadIfAwaiting();
        flushIfDirty();
        showQueuedNotices();
    }

    /** Whether the record's folder can be used right now; see {@link Store#check()}. */
    private enum Presence { READY, NO_FOLDER, NO_FILE }

    /**
     * The folder the record goes to, taken on the game thread so that checking and using it can
     * happen off it: on a drive that stopped answering, even asking whether a file exists hangs.
     */
    private record Store(File directory, File file, boolean chosen, boolean hadFile) {
        static Store current() {
            String override = AlpakaConfig.instance.statsDirectory;
            boolean chosen = override != null && !override.isBlank();
            return new Store(AlpakaStats.directory(), AlpakaStats.file(), chosen, AlpakaConfig.instance.statsDirectoryHadFile);
        }

        /**
         * Whether a folder the player chose is reachable: not when the folder is gone, nor when it
         * is there without the file it held before, which is what a cloud folder looks like while
         * it is still syncing. The default folder is created on demand, so it always counts.
         */
        Presence check() {
            if (!chosen) return Presence.READY;
            if (!directory.isDirectory()) return Presence.NO_FOLDER;
            return hadFile && !file.exists() ? Presence.NO_FILE : Presence.READY;
        }
    }

    /** What looking for the record found: the record (null for none) and the layout it was in. */
    private record Loaded(Presence presence, AlpakaStats stats, boolean fromFile, int schema) {}

    /** A read that failed outright counts as a folder that is not there, so nothing is written over it. */
    private static final Loaded UNREADABLE = new Loaded(Presence.NO_FOLDER, null, false, 1);

    /** Reads the record from its folder; touches nothing but the files, so it runs on {@link #WRITER}. */
    private static Loaded readStore(Store store) {
        Presence presence = store.check();
        if (presence != Presence.READY) return new Loaded(presence, null, false, 1);
        migrateLegacy(store.file());
        OnDisk read = read(store.file());
        boolean fromFile = read != null;
        if (read == null) {
            // A record that now lives in a folder something else may be syncing can be caught
            // half-written. The backup is the last copy that parsed, which beats starting over.
            read = read(new File(store.directory(), BACKUP_NAME));
            if (read != null) AlpakaAddons.LOGGER.warn("Slayer stats were unreadable; fell back to the backup");
        }
        return read == null
                ? new Loaded(Presence.READY, null, false, 1)
                : new Loaded(Presence.READY, read.stats(), fromFile, read.schema());
    }

    /** The read's result, or null while it is still running after {@code waitMs}. */
    private static Loaded takeLoaded(java.util.concurrent.Future<Loaded> reading, long waitMs) {
        try {
            return reading.get(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            AlpakaAddons.LOGGER.warn("Could not read the slayer stats", e.getCause());
            return UNREADABLE;
        }
    }

    /** Takes over what {@link #readStore} found; on the game thread. */
    private static void apply(Loaded result) {
        lastPresence = result.presence();
        if (result.presence() != Presence.READY) {
            // Google Drive's mount, and every other cloud drive, comes up some seconds after login;
            // a game launched from the desktop straight after booting beats it. Reading nothing and
            // starting from zero is what made a whole history look wiped. Wait instead.
            awaitingStore = true;
            awaitingPresence = result.presence();
            AlpakaAddons.LOGGER.warn("Slayer stats folder {} or its file is not there yet (drive not mounted or still syncing?); "
                    + "waiting for it before reading or writing the record", directory().getAbsolutePath());
            return;
        }
        awaitingStore = false;
        if (result.fromFile()) markFolderHadFile();
        if (result.stats() != null) instance = result.stats();
        if (instance.accounts == null) instance.accounts = new HashMap<>();

        // Files written by an earlier build can remember the placeholder as the last profile. That
        // memory is worth nothing and would keep the session in the placeholder; see Account#lastProfile.
        for (Account account : instance.accounts.values()) {
            if (account == null) continue;
            if (account.profiles == null) account.profiles = new HashMap<>();
            if (UNKNOWN_PROFILE.equals(account.lastProfile)) account.lastProfile = null;
        }
        noteSchema(result.schema());
        foldUnsaved();
    }

    /**
     * True while the record could not be read at start-up because its folder was not there.
     *
     * Only ever set for a folder the player chose: the default folder is created on demand and its
     * absence just means first use, but a chosen folder existed when it was chosen (the command checks),
     * so its absence means the drive behind it has not come up yet. While this is set nothing is
     * written, so a missing drive can never be "fixed" by a fresh empty record appearing on it, and
     * kills made in the meantime stay in memory until the folder shows up - or, at quit, go into
     * {@link #unsavedFile()}.
     */
    private static boolean awaitingStore = false;

    /** What the last look for the folder found, while {@link #awaitingStore}. */
    private static Presence awaitingPresence = Presence.NO_FOLDER;

    /** What the last read or write found, so the folder can be asked about without touching the drive. */
    private static Presence lastPresence = Presence.READY;

    /**
     * Whether the chosen folder or its file was missing at the last read or write - as the writer
     * found it, so asking cannot hang the game on a drive that stopped answering. For whatever else
     * keeps files in the same folder (the slayer log, #184) and has to wait for it just as well.
     */
    private static boolean storeMissing() {
        return lastPresence != Presence.READY;
    }

    private static long nextStoreCheckNanos = 0L;

    /** A look for the folder in progress on the writer, while the record waits for it. */
    private static java.util.concurrent.Future<Loaded> pendingLoad = null;

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
     * on, so the folder is only looked for every couple of seconds - and on the writer, since asking
     * a drive that stopped answering hangs the thread that asks.
     */
    public static void retryLoadIfAwaiting() {
        if (!awaitingStore) return;
        if (pendingLoad == null) {
            long now = System.nanoTime();
            if (now < nextStoreCheckNanos) return;
            nextStoreCheckNanos = now + 2_000_000_000L;
            lookForStore();
            return;
        }
        if (pendingLoad.isDone()) finishLookForStore(0L);
    }

    /** Starts reading the record from its folder, on the writer. */
    private static void lookForStore() {
        Store store = Store.current();
        pendingLoad = WRITER.submit(() -> readStore(store));
    }

    /**
     * Takes the record over if the look for its folder found it, waiting up to {@code waitMs} for
     * the look to finish.
     */
    private static void finishLookForStore(long waitMs) {
        Loaded result = takeLoaded(pendingLoad, waitMs);
        if (result == null) return;
        pendingLoad = null;
        if (result.presence() != Presence.READY) {
            awaitingPresence = result.presence();
            lastPresence = result.presence();
            return;
        }

        AlpakaStats session = instance;
        instance = new AlpakaStats();
        apply(result);

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
    private static void warnStoreMissing(Presence presence) {
        long now = System.currentTimeMillis();
        if (now - lastStoreMissingNoticeMs < SAVE_FAILURE_NOTICE_INTERVAL_MS) return;
        lastStoreMissingNoticeMs = now;
        boolean folderThere = presence == Presence.NO_FILE;
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
        File legacy = legacyFile();
        if (target.exists() || !legacy.exists()) return;
        try {
            File dir = target.getParentFile();
            if (dir != null) dir.mkdirs();
            java.nio.file.Files.copy(legacy.toPath(), target.toPath());
            AlpakaAddons.LOGGER.info("Moved the slayer record to the shared store at {}", target.getAbsolutePath());
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to move the slayer record to the shared store", e);
        }
    }

    /** What a record file held: the record as this version reads it, and the file's own JSON. */
    private record OnDisk(AlpakaStats stats, JsonObject json) {
        /** The layout the file says it is in; see {@link #SCHEMA_VERSION}. */
        int schema() {
            return StatsMerge.schemaOf(json);
        }
    }

    /** Reads a record file; null when it is missing, empty or does not parse. */
    private static OnDisk read(File file) {
        try {
            String text = AtomicJsonFile.read(file);
            if (text == null || text.isBlank()) return null;
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            AlpakaStats stats = parse(json);
            return stats == null ? null : new OnDisk(stats, json);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to load stats from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /** The record in {@code json}, leaving out the slayers this version does not know (see StatsMerge#knownOnly). */
    private static AlpakaStats parse(JsonObject json) {
        AlpakaStats stats = GSON.fromJson(StatsMerge.knownOnly(json), AlpakaStats.class);
        if (stats != null && stats.accounts == null) stats.accounts = new HashMap<>();
        return stats;
    }

    private static boolean newerSchemaNoticed = false;

    /**
     * Says once that the file was written by a newer version of the mod. What that version added is
     * kept as it is on every write (StatsMerge#keepUnknown); this one just cannot show it.
     */
    private static void noteSchema(int schema) {
        if (schema <= SCHEMA_VERSION || newerSchemaNoticed) return;
        newerSchemaNoticed = true;
        AlpakaAddons.LOGGER.warn("Slayer stats file {} comes from a newer AlpakaAddons (layout {}, this version writes {}); "
                + "what this version does not know is kept as it is", file().getAbsolutePath(), schema, SCHEMA_VERSION);
        QUEUED_NOTICES.add("§eYour slayer stats file comes from a newer AlpakaAddons. §7Everything in it is kept, "
                + "but this version cannot show what is new; update the mod to see it.");
    }

    /** Chat lines that came up before the player was in game - at start-up, mostly. */
    private static final java.util.List<String> QUEUED_NOTICES = new java.util.ArrayList<>();

    private static void showQueuedNotices() {
        if (QUEUED_NOTICES.isEmpty() || Minecraft.getInstance().player == null) return;
        for (String line : QUEUED_NOTICES) SlayerDropTracker.sendModMessage(line);
        QUEUED_NOTICES.clear();
    }

    /**
     * Where a session leaves the kills its chosen folder could not take at quit: in the default
     * folder, which is local. Folded into the record by the next start that reads it.
     */
    private static File unsavedFile() {
        return new File(defaultDirectory(), "alpaka-stats.unsaved.json");
    }

    /**
     * Quitting with changes the chosen folder never got - it did not come up, or it stopped
     * answering - writes them to {@link #unsavedFile()}. They used to be lost: nothing is written
     * while the folder is missing, and the game is gone before it shows up.
     *
     * Each session is one entry. One that never found its folder holds only its own kills, which
     * are added on top of the record later; one whose writes stopped holds the whole record, which
     * is merged like a save.
     */
    private static void writeUnsaved() {
        String override = AlpakaConfig.instance.statsDirectory;
        // Without a chosen folder it was the default folder itself that did not answer.
        if (override == null || override.isBlank()) return;
        File file = unsavedFile();
        try {
            JsonObject unsaved = null;
            try {
                String text = AtomicJsonFile.read(file);
                if (text != null && !text.isBlank()) unsaved = JsonParser.parseString(text).getAsJsonObject();
            } catch (Exception unreadable) {
                AtomicJsonFile.quarantine(file);
            }
            if (unsaved == null) unsaved = new JsonObject();
            JsonArray sessions = unsaved.has("sessions") && unsaved.get("sessions").isJsonArray()
                    ? unsaved.getAsJsonArray("sessions") : new JsonArray();
            JsonObject entry = new JsonObject();
            entry.addProperty("sessionOnly", awaitingStore);
            entry.addProperty("folder", directory().getAbsolutePath());
            entry.addProperty("savedAtMs", System.currentTimeMillis());
            entry.add("record", GSON.toJsonTree(instance));
            sessions.add(entry);
            unsaved.add("sessions", sessions);
            AtomicJsonFile.write(file, GSON.toJson(unsaved));
            AlpakaAddons.LOGGER.warn("Slayer stats folder {} was not available at quit; kept the record in {} until the next start",
                    directory().getAbsolutePath(), file.getAbsolutePath());
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Could not keep the unsaved slayer stats in {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * Folds what earlier sessions left in {@link #unsavedFile()} into the record, once.
     *
     * The file is moved aside (as {@code .folded}) before it is read, so two instances starting
     * together cannot both add the same kills; the moved copy stays for recovering by hand.
     */
    private static void foldUnsaved() {
        File file = unsavedFile();
        if (!file.isFile()) return;
        File folded = new File(file.getPath() + ".folded");
        try {
            java.nio.file.Files.move(file.toPath(), folded.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Another instance took it first, or it cannot be moved; left for the next start.
            return;
        }
        JsonArray entries;
        try {
            entries = JsonParser.parseString(AtomicJsonFile.read(folded)).getAsJsonObject().getAsJsonArray("sessions");
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Could not read the unsaved slayer stats in {}", folded.getAbsolutePath(), e);
            return;
        }
        if (entries == null) return;
        int kills = 0;
        int sessions = 0;
        for (JsonElement element : entries) {
            // One by one, so a damaged entry costs only itself.
            try {
                JsonObject entry = element.getAsJsonObject();
                AlpakaStats record = parse(entry.getAsJsonObject("record"));
                if (record == null) continue;
                if (entry.has("sessionOnly") && entry.get("sessionOnly").getAsBoolean()) {
                    kills += foldInto(instance, record);
                } else {
                    StatsMerge.mergeFromDisk(instance, record, "", UNKNOWN_PROFILE);
                }
                sessions++;
            } catch (Exception e) {
                AlpakaAddons.LOGGER.error("Skipped a damaged entry in {}", folded.getAbsolutePath(), e);
            }
        }
        if (sessions == 0) return;
        AlpakaAddons.LOGGER.info("Folded {} unsaved session(s) ({} added kills) from {} into the slayer record",
                sessions, kills, folded.getAbsolutePath());
        QUEUED_NOTICES.add("§aSlayer stats the game could not save when it last closed were added to the record"
                + (kills > 0 ? " (§f" + kills + "§a kills)." : "."));
        markDirty();
    }

    private static boolean dirty = false;
    private static long nextFlushAttemptNanos = 0L;

    /**
     * At least this long between two background saves. A drop, the kill and the XP that follows it
     * arrive within a second or two; one write covers them all.
     */
    private static final long MIN_FLUSH_SPACING_NANOS = 1_000_000_000L;

    /** A failed write is retried after this, rather than every tick. */
    private static final long RETRY_PAUSE_NANOS = 2_000_000_000L;

    /**
     * How long leaving a server, quitting, starting or a command waits for the writer. A network or
     * cloud drive that stopped answering held the game frozen for as long as it hung.
     */
    private static final long WRITER_WAIT_MS = 3_000L;

    /**
     * Reads and writes the record off the game thread.
     *
     * Saving re-reads the file, merges it, keeps a backup and writes it atomically - and may wait for
     * another instance's lock. Done on the client thread after every kill, that showed as a hitch the
     * moment the boss died, and a long one on a cloud drive. One thread, so two writes never overlap.
     * Daemon, so it never holds the game open; the exit path waits for it a little in
     * {@link #flushOnExit()}.
     */
    private static final java.util.concurrent.ExecutorService WRITER =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "AlpakaAddons stats writer");
                thread.setDaemon(true);
                return thread;
            });

    /** How a background write ended: what the file held (null if nothing readable), or that the folder was not there. */
    private record Written(Presence presence, OnDisk onDisk) {}

    /** The background save in progress, with what is needed to finish it on the game thread. */
    private record PendingSave(java.util.concurrent.Future<Written> result, File file, String account) {}

    private static PendingSave pendingSave = null;

    /**
     * Records that the record changed; it is written from the tick, once, however many changes came
     * in. A boss kill used to write the whole file two or three times over - the kill, the XP and the
     * best time each saved on their own - which hitched the game at the moment the boss died, and
     * more so on a cloud drive.
     */
    public static void markDirty() {
        dirty = true;
    }

    /**
     * Starts a background write if anything changed.
     *
     * The game thread only takes a snapshot of the record; checking the folder, reading, merging and
     * writing the file happen on {@link #WRITER}. What the file held is folded back into memory once
     * the write is done, on the game thread, so the live record is never touched from two threads.
     */
    private static void flushIfDirty() {
        collectPendingSave();
        if (!dirty || pendingSave != null) return;
        if (awaitingStore) {
            // Rate-limited inside: says every few minutes that kills are waiting for the folder.
            warnStoreMissing(awaitingPresence);
            return;
        }
        long now = System.nanoTime();
        if (now < nextFlushAttemptNanos) return;
        nextFlushAttemptNanos = now + MIN_FLUSH_SPACING_NANOS;
        startWrite();
    }

    /** Hands a snapshot of the record to the writer. */
    private static void startWrite() {
        dirty = false;
        Store store = Store.current();
        String account = accountKey();
        com.google.gson.JsonElement snapshot = GSON.toJsonTree(instance);
        java.util.concurrent.Future<Written> result = WRITER.submit(() -> {
            // The folder or its file going away mid-session stops every write until it is back, so
            // an empty folder can never receive a fresh record in place of the real one.
            Presence presence = store.check();
            if (presence != Presence.READY) return new Written(presence, null);
            return new Written(Presence.READY, writeMerged(store.file(), GSON.fromJson(snapshot, AlpakaStats.class), account));
        });
        pendingSave = new PendingSave(result, store.file(), account);
    }

    /** Finishes a background save that is done; the tick's way, which never waits. */
    private static void collectPendingSave() {
        PendingSave pending = pendingSave;
        if (pending != null && pending.result().isDone()) awaitPendingSave(System.nanoTime());
    }

    /**
     * Finishes the background save, waiting for it until {@code deadlineNanos} at most. Returns
     * whether the writer is free. If it is not - a drive that stopped answering - the record counts
     * as unsaved, and the write is still collected by a later tick should it ever finish.
     */
    private static boolean awaitPendingSave(long deadlineNanos) {
        PendingSave pending = pendingSave;
        if (pending == null) return true;

        Written written;
        try {
            written = pending.result().get(Math.max(0L, deadlineNanos - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            dirty = true;
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dirty = true;
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            pendingSave = null;
            Throwable cause = e.getCause() == null ? e : e.getCause();
            AlpakaAddons.LOGGER.error("Failed to save stats to {}", pending.file().getAbsolutePath(), cause);
            warnSaveFailed(pending.file(), cause);
            dirty = true;
            nextFlushAttemptNanos = System.nanoTime() + RETRY_PAUSE_NANOS;
            return true;
        }
        pendingSave = null;

        lastPresence = written.presence();
        if (written.presence() != Presence.READY) {
            // Memory still holds everything; the next save after the folder is back writes it.
            dirty = true;
            nextFlushAttemptNanos = System.nanoTime() + RETRY_PAUSE_NANOS;
            warnStoreMissing(written.presence());
            return true;
        }
        OnDisk onDisk = written.onDisk();
        if (onDisk != null) {
            StatsMerge.mergeFromDisk(instance, onDisk.stats(), pending.account(), UNKNOWN_PROFILE);
            noteSchema(onDisk.schema());
        }
        // Only for the folder that was written; the setting may have moved on meanwhile.
        if (pending.file().equals(file())) markFolderHadFile();
        return true;
    }

    /**
     * Writes a pending change now, ignoring the retry pause, and waits for it - a few seconds at
     * most, so a drive that stopped answering cannot freeze leaving a server. Whatever is not written
     * by then stays marked as changed for the tick to write.
     */
    public static void flushNow() {
        long deadline = System.nanoTime() + WRITER_WAIT_MS * 1_000_000L;
        if (!awaitPendingSave(deadline)) return;
        if (!dirty || awaitingStore) return;
        startWrite();
        awaitPendingSave(deadline);
    }

    /**
     * Quitting: writes what changed like {@link #flushNow()}. What the chosen folder could not take -
     * it never came up, or it stopped answering - goes into {@link #unsavedFile()} for the next start.
     */
    public static void flushOnExit() {
        flushNow();
        if (dirty) writeUnsaved();
    }

    /** Sibling file used to keep two local instances from saving at the same moment. */
    private static final String LOCK_NAME = "alpaka-stats.lock";

    /**
     * Writes the record now, keeping what other sessions have put there, and waits for it a few
     * seconds at most. Returns whether it was written. Gameplay code calls {@link #markDirty()}
     * instead and lets the tick write it in the background.
     *
     * The file may be shared - by a second instance on this machine, or through a sync folder with
     * another PC - so it can have moved on since this session read it. The file is re-read and merged
     * slayer by slayer before writing (see {@link StatsMerge#mergeFromDisk}), under a lock that keeps two local
     * instances from interleaving the read and the write.
     *
     * Two machines playing the same profile at the same time can still lose the smaller of their two
     * sets of new kills, since only the larger count survives the merge; nothing short of one file
     * per machine fixes that.
     */
    public static boolean save() {
        long deadline = System.nanoTime() + WRITER_WAIT_MS * 1_000_000L;
        if (!awaitPendingSave(deadline)) return false;
        if (awaitingStore) {
            // The folder command has just pointed the record somewhere else: look there now rather
            // than on the next retry.
            lookForStore();
            finishLookForStore(Math.max(0L, (deadline - System.nanoTime()) / 1_000_000L));
            if (awaitingStore) {
                warnStoreMissing(awaitingPresence);
                return false;
            }
        }
        startWrite();
        return awaitPendingSave(deadline) && !dirty;
    }

    /**
     * Folds the file into {@code record} and writes the result back, under the file lock. Returns
     * what the file held, or null if there was nothing readable. Touches nothing but its arguments
     * and the file, so it runs on the writer, on a snapshot.
     *
     * The result is put back together on the file's own JSON as well: what a newer version of the
     * mod added - fields, slayers - means nothing to this one, and a plain typed write dropped it.
     * An older copy of the mod on a second launcher wiped the per-tier bests that way.
     */
    private static OnDisk writeMerged(File file, AlpakaStats record, String account) throws java.io.IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory()) java.nio.file.Files.createDirectories(dir.toPath());

        try (java.nio.channels.FileChannel channel = openLock(dir);
             java.nio.channels.FileLock lock = channel == null ? null : tryLock(channel)) {
            OnDisk onDisk = read(file);
            StatsMerge.mergeFromDisk(record, onDisk == null ? null : onDisk.stats(), account, UNKNOWN_PROFILE);
            record.schemaVersion = SCHEMA_VERSION;
            JsonObject out = GSON.toJsonTree(record).getAsJsonObject();
            if (onDisk != null) {
                StatsMerge.keepUnknown(onDisk.json(), out);
                // Only a copy that parsed becomes the backup, so a damaged file can never replace
                // the last good one.
                AtomicJsonFile.backup(file, new File(dir, BACKUP_NAME));
            }
            AtomicJsonFile.write(file, GSON.toJson(out));
            return onDisk;
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
    private static void warnSaveFailed(File file, Throwable e) {
        long now = System.currentTimeMillis();
        if (now - lastSaveFailureNoticeMs < SAVE_FAILURE_NOTICE_INTERVAL_MS) return;
        lastSaveFailureNoticeMs = now;

        String reason = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        SlayerDropTracker.sendModMessage("§cCouldn't save the slayer stats to §f" + file.getAbsolutePath());
        SlayerDropTracker.sendModMessage("§c" + reason);
        SlayerDropTracker.sendModMessage("§7Kills since the last successful save exist only in memory. "
                + "§f/alpakastats folder default §7or a valid folder fixes this without a restart.");
    }
}
