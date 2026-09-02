package net.alpaka.addons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.slayer.SkyblockProfileTracker;
import net.alpaka.addons.features.slayer.SlayerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
     * Only reached in the seconds between joining and Hypixel saying which profile it is, and only
     * for an account that has never been seen before - otherwise {@link Account#lastProfile} covers
     * the gap with the profile that account was last on, which is nearly always the right one.
     */
    private static final String UNKNOWN_PROFILE = "unknown-profile";

    /** One Skyblock profile's record. */
    public static class ProfileStats {
        public Map<SlayerType, AlpakaConfig.SlayerData> slayerBossMap = new HashMap<>();
    }

    /** One Minecraft account's profiles. */
    public static class Account {
        public Map<String, ProfileStats> profiles = new HashMap<>();

        /** The profile this account was last seen on, so a brief unknown gap does not open a bucket. */
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
        String account = accountKey();
        Account entry = instance.accounts.computeIfAbsent(account, key -> new Account());

        String profile = SkyblockProfileTracker.INSTANCE.getCurrent();
        if (profile == null) profile = entry.lastProfile;

        boolean known = profile != null && !UNKNOWN_PROFILE.equals(profile);
        if (profile == null) profile = UNKNOWN_PROFILE;

        if (!profile.equals(entry.lastProfile)) {
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
     * Moves a record left in the placeholder bucket into the profile it belongs to.
     *
     * The placeholder fills when something asks for the record before Hypixel has said which profile
     * this is - which is exactly what happened to the very first import, stranding a full history
     * where no profile would ever look for it again.
     *
     * Only ever moves into a profile that has recorded nothing yet, so it cannot bury a real record
     * under one that was misfiled. A player who has already played on the profile since keeps what
     * they have, and the stranded copy stays put rather than being merged on a guess.
     */
    private static void rescueStranded(Account entry, ProfileStats target) {
        // Tested on recorded progress rather than on the map being empty: slayerBossMap() seeds every
        // slayer with a blank entry on first use, so an untouched profile stops being "empty" the
        // moment anything reads it, and a check for emptiness would only ever fire once.
        if (hasProgress(target.slayerBossMap)) return;

        ProfileStats stranded = entry.profiles.get(UNKNOWN_PROFILE);
        if (stranded == null || stranded.slayerBossMap.isEmpty()) return;
        if (!hasProgress(stranded.slayerBossMap)) return;

        target.slayerBossMap.putAll(stranded.slayerBossMap);
        entry.profiles.remove(UNKNOWN_PROFILE);
        AlpakaAddons.LOGGER.info("Moved the slayer record out of the placeholder profile bucket");
        save();
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
        if (hasProgress(stats.slayerBossMap)) return;

        Map<SlayerType, AlpakaConfig.SlayerData> legacy = AlpakaConfig.instance.slayerBossMap;
        if (legacy == null || !hasProgress(legacy)) return;

        stats.slayerBossMap.putAll(legacy);
        instance.legacyImported = true;
        AlpakaAddons.LOGGER.info("Imported the existing slayer record into the per-profile store");
        save();
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
        File file = file();
        migrateLegacy(file);

        AlpakaStats loaded = read(file);
        if (loaded == null) {
            // A record that now lives in a folder something else may be syncing can be caught
            // half-written. The backup is the last copy that parsed, which beats starting over.
            loaded = read(new File(directory(), BACKUP_NAME));
            if (loaded != null) AlpakaAddons.LOGGER.warn("Slayer stats were unreadable; fell back to the backup");
        }
        if (loaded != null) instance = loaded;
        if (instance.accounts == null) instance.accounts = new HashMap<>();
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
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, AlpakaStats.class);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to load stats from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Writes the record, keeping what other machines have put there.
     *
     * The file may be shared through a sync folder, so it can have moved on since this session read
     * it - the same player on their other PC, on another Skyblock profile. Blindly writing what is
     * in memory would throw that away, so the file is re-read and only the account and profile
     * <em>this</em> session is playing is overlaid onto it. That granularity is what makes the merge
     * sound rather than clever: this client is the only authority on the profile in front of it, and
     * has nothing to say about any other.
     *
     * It does not make two machines playing the same profile at the same time safe, and nothing
     * short of a server would.
     */
    public static void save() {
        File file = file();
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();

        mergeFromDisk(file);

        try {
            if (file.exists()) {
                java.nio.file.Files.copy(file.toPath(), new File(dir, BACKUP_NAME).toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Written aside and moved into place, so a reader - or a sync client - never sees a
            // half-written record where a complete one used to be.
            File temp = new File(dir, FILE_NAME + ".tmp");
            try (FileWriter writer = new FileWriter(temp)) {
                GSON.toJson(instance, writer);
            }

            try {
                java.nio.file.Files.move(temp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception moveFailed) {
                // The whole point of this setting is to put the file on a drive a cloud client
                // provides, and those are not always real filesystems - Google Drive's streaming
                // mount refuses some operations a local disk allows. Writing straight to the target
                // gives up the atomicity rather than giving up the save, and the backup taken above
                // is what covers the window this opens.
                AlpakaAddons.LOGGER.warn("Atomic replace not supported at {}; writing in place", dir.getAbsolutePath());
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(instance, writer);
                }
                temp.delete();
            }
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save stats to {}", file.getAbsolutePath(), e);
        }
    }

    /** Folds anything on disk that this session is not responsible for back into memory. */
    private static void mergeFromDisk(File file) {
        AlpakaStats onDisk = read(file);
        if (onDisk == null || onDisk.accounts == null) return;

        String account = accountKey();
        Account ours = instance.accounts.get(account);
        String profile = ours == null ? null : ours.lastProfile;

        for (Map.Entry<String, Account> entry : onDisk.accounts.entrySet()) {
            // Another account entirely: nothing this session did concerns it, take theirs.
            if (!entry.getKey().equals(account) || ours == null || profile == null) {
                instance.accounts.putIfAbsent(entry.getKey(), entry.getValue());
                continue;
            }

            for (Map.Entry<String, ProfileStats> profileEntry : entry.getValue().profiles.entrySet()) {
                if (profileEntry.getKey().equals(profile)) continue; // Ours; memory wins.
                ours.profiles.putIfAbsent(profileEntry.getKey(), profileEntry.getValue());
            }
        }

        if (onDisk.legacyImported) instance.legacyImported = true;
    }
}
