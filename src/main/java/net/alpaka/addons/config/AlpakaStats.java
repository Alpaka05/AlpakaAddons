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

    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("alpaka-stats.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                AlpakaStats loaded = GSON.fromJson(reader, AlpakaStats.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                AlpakaAddons.LOGGER.error("Failed to load stats", e);
            }
        }
        if (instance.accounts == null) instance.accounts = new HashMap<>();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.error("Failed to save stats", e);
        }
    }
}
