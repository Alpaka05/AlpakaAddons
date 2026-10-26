package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One member of one SkyBlock profile, as the dashboard shows it: read once from Elite's answer into
 * plain fields, so drawing a frame never touches JSON. The answer itself stays at hand in
 * {@link #raw} for the cards that only show a value or two.
 */
public final class ProfileData {

    /** A skill, Catacombs or a class: the level, and how far along the way to the next it is. */
    public record Level(String id, String name, int level, int maxLevel, double xp, double xpCurrent, double xpForNext) {
        public boolean maxed() {
            return level >= maxLevel || xpForNext <= 0;
        }

        /** 0 to 1 towards the next level; 1 when there is none. */
        public double progress() {
            return maxed() ? 1.0 : Math.max(0, Math.min(1, xpCurrent / xpForNext));
        }
    }

    public record Floor(String label, boolean master, int completions, long fastestS, long fastestSPlus, int bestScore) {}

    /** A slayer boss. Elite's {@code xpForNext} is the XP still missing to the next level. */
    public record Slayer(String id, String name, int level, int maxLevel, double xp, double xpForNext, double progress, int[] kills) {
        public boolean maxed() {
            return level >= maxLevel || xpForNext <= 0;
        }
    }

    /** A pet. {@code exp} is all the XP on it; {@code expForMax} what its max level takes, 0 when unknown. */
    public record Pet(String type, String name, String tier, int level, int maxLevel, double progress, String heldItem,
                      String skin, boolean active, double exp, double expForMax) {}

    /** One collection: what it is, how much was gathered, and the tier reached of how many. */
    public record Collection(String id, String name, String category, long amount, int tier, int maxTier, long nextAt,
                             long prevAt) {
        public boolean maxed() {
            return maxTier > 0 && tier >= maxTier;
        }
    }

    public final JsonObject raw;
    public final String uuid;
    public final String playerName;
    public final String profileId;
    public final String profileName;
    /** Seconds since the epoch at which Elite last read the profile from Hypixel. */
    public final long lastUpdated;

    public final double networth;
    public final double liquidNetworth;
    public final double purse;
    public final double bank;
    public final double personalBank;
    public final double skyblockLevel;
    public final boolean skillsApi;
    public final boolean inventoryApi;
    public final boolean collectionsApi;

    public final List<Level> skills = new ArrayList<>();
    public final double skillAverage;
    public final double skillTotalXp;

    public final Level catacombs;
    public final List<Level> classes = new ArrayList<>();
    public final String selectedClass;
    public final double classAverage;
    public final List<Floor> floors = new ArrayList<>();
    public final int totalRuns;

    public final List<Slayer> slayers = new ArrayList<>();
    public final double slayerXp;

    public final List<Pet> pets = new ArrayList<>();
    public final int petsMaxed;
    public final int petSkins;
    public final int petScore;

    public final double magicalPower;
    public final double farmingWeight;
    public final int bestiaryMilestone;

    ProfileData(JsonObject raw, String playerName) {
        this.raw = raw;
        this.uuid = Json.str(raw, "playerUuid");
        this.playerName = playerName;
        this.profileId = Json.str(raw, "profileId");
        this.profileName = Json.str(raw, "profileName");
        this.lastUpdated = (long) Json.num(raw, "lastUpdated", 0);

        this.networth = Json.num(raw, "networth.normal", 0);
        this.liquidNetworth = Json.num(raw, "networth.liquid", 0);
        this.purse = Json.num(raw, "purse", 0);
        this.bank = Json.num(raw, "bankBalance", 0);
        this.personalBank = Json.num(raw, "personalBank", 0);
        this.skyblockLevel = Json.num(raw, "skyblockXp", 0) / 100.0;
        this.skillsApi = Json.bool(raw, "api.skills");
        this.inventoryApi = Json.bool(raw, "api.inventories");
        this.collectionsApi = Json.bool(raw, "api.collections");

        // Skills, in the order the game's skill menu lists them.
        JsonObject levels = Json.obj(raw, "stats.skills.levels");
        for (String id : SKILL_ORDER) {
            JsonObject s = Json.obj(levels, id);
            if (s.isEmpty()) continue;
            skills.add(level(id, title(id), s));
        }
        this.skillAverage = Json.num(raw, "stats.skills.average", 0);
        this.skillTotalXp = Json.num(raw, "stats.skills.totalXp", 0);

        JsonObject dungeons = Json.obj(raw, "stats.dungeons");
        double cataXp = Json.num(dungeons, "catacombsExperience", 0);
        int cataLevel = (int) Json.num(dungeons, "catacombsLevel", 0);
        this.catacombs = new Level("catacombs", "Catacombs", cataLevel, 50, cataXp,
                cataXp - Levels.total(Levels.CATACOMBS, cataLevel),
                cataLevel < 50 ? Levels.CATACOMBS[cataLevel] : 0);
        for (String id : CLASSES) {
            JsonObject c = Json.obj(dungeons, "classLevels." + id);
            if (c.isEmpty()) continue;
            Level parsed = level(id, title(id), c);
            classes.add(new Level(parsed.id(), parsed.name(), parsed.level(), 50, parsed.xp(), parsed.xpCurrent(), parsed.xpForNext()));
        }
        this.selectedClass = Json.str(dungeons, "selectedClass");
        this.classAverage = Json.num(dungeons, "classAverage", 0);
        int runs = 0;
        for (int tier = 0; tier <= 7; tier++) {
            JsonObject f = Json.obj(dungeons, "floors." + tier);
            if (f.isEmpty() && tier > 0) {
                // A floor never played is still listed, so the grid keeps its shape.
                floors.add(new Floor(tier == 0 ? "Entrance" : "F" + tier, false, 0, 0, 0, 0));
                continue;
            }
            int completions = (int) Json.num(f, "completions", 0);
            runs += completions;
            floors.add(new Floor(tier == 0 ? "Entrance" : "F" + tier, false, completions,
                    (long) Json.num(f, "fastestTimeS", 0), (long) Json.num(f, "fastestTimeSPlus", 0), (int) Json.num(f, "bestScore", 0)));
        }
        for (int tier = 1; tier <= 7; tier++) {
            JsonObject f = Json.obj(dungeons, "masterFloors." + tier);
            int completions = (int) Json.num(f, "completions", 0);
            runs += completions;
            floors.add(new Floor("M" + tier, true, completions,
                    (long) Json.num(f, "fastestTimeS", 0), (long) Json.num(f, "fastestTimeSPlus", 0), (int) Json.num(f, "bestScore", 0)));
        }
        this.totalRuns = runs;

        JsonObject bosses = Json.obj(raw, "stats.slayer.bosses");
        for (String[] boss : SLAYERS) {
            JsonObject b = Json.obj(bosses, boss[0]);
            if (b.isEmpty()) continue;
            int[] kills = new int[5];
            JsonObject k = Json.obj(b, "bossKills");
            for (int t = 0; t < 5; t++) kills[t] = (int) Json.num(k, String.valueOf(t), 0);
            slayers.add(new Slayer(boss[0], boss[1], (int) Json.num(b, "level", 0), (int) Json.num(b, "maxLevel", 9),
                    Json.num(b, "xp", 0), Json.num(b, "xpForNext", 0), Math.max(0, Math.min(1, Json.num(b, "progress", 0))), kills));
        }
        this.slayerXp = Json.num(raw, "stats.slayer.totalXp", 0);

        for (JsonElement e : Json.arr(raw, "pets")) {
            String type = Json.str(e, "type");
            if (type == null) continue;
            pets.add(new Pet(type, title(type), Json.str(e, "tier"), (int) Json.num(e, "level", 1),
                    (int) Json.num(e, "maxLevel", 100), Json.num(e, "progress", 0), Json.str(e, "heldItem"),
                    Json.str(e, "skin"), Json.bool(e, "active"), Json.num(e, "exp", 0), Json.num(e, "xpMaxLevel", 0)));
        }
        pets.sort((a, b) -> {
            int r = Integer.compare(rarityRank(b.tier()), rarityRank(a.tier()));
            if (r != 0) return r;
            return Integer.compare(b.level(), a.level());
        });
        this.petsMaxed = (int) Json.num(raw, "petSummary.maxLevelPets", 0);
        this.petSkins = (int) Json.num(raw, "petSummary.totalSkins", 0);
        this.petScore = (int) Json.num(raw, "stats.miscStats.highestPetScore", 0);

        this.magicalPower = Json.num(raw, "stats.accessories.highestMagicalPower", 0);
        this.farmingWeight = Json.num(raw, "farmingWeight.totalWeight", 0);
        this.bestiaryMilestone = (int) Json.num(raw, "stats.bestiary.milestoneLevel", 0);
    }

    private static Level level(String id, String name, JsonObject s) {
        return new Level(id, name, (int) Json.num(s, "level", 0), (int) Json.num(s, "maxLevel", 60),
                Json.num(s, "xp", 0), Json.num(s, "xpCurrent", 0), Json.num(s, "xpForNext", 0));
    }

    /** The collections with their tiers, named and grouped as Hypixel's resources have them. */
    public List<Collection> collections(JsonElement resources) {
        List<Collection> out = new ArrayList<>();
        JsonObject amounts = Json.obj(raw, "collections");
        JsonObject tiers = Json.obj(raw, "collectionTiers");
        for (var category : Json.obj(resources, "collections").entrySet()) {
            String categoryName = Json.str(category.getValue(), "name");
            for (var item : Json.obj(category.getValue(), "items").entrySet()) {
                String id = item.getKey();
                long amount = (long) Json.num(amounts, id, 0);
                int maxTier = (int) Json.num(item.getValue(), "maxTiers", 0);
                int tier = (int) Json.num(tiers, id, -1);
                List<Long> required = new ArrayList<>();
                for (JsonElement t : Json.arr(item.getValue(), "tiers")) required.add((long) Json.num(t, "amountRequired", 0));
                if (tier < 0) {
                    tier = 0;
                    for (long need : required) if (amount >= need) tier++;
                }
                long nextAt = tier < required.size() ? required.get(tier) : 0;
                long prevAt = tier > 0 && tier - 1 < required.size() ? required.get(tier - 1) : 0;
                out.add(new Collection(id, Json.str(item.getValue(), "name"), categoryName == null ? category.getKey() : categoryName,
                        amount, tier, maxTier, nextAt, prevAt));
            }
        }
        return out;
    }

    /**
     * What goes into the history, keyed by {@link Metrics} ids. Only numbers this profile actually
     * has: with the skills API off there are no skill entries, rather than zeros that would draw a
     * cliff into the graph.
     */
    public Map<String, Double> metrics() {
        Map<String, Double> m = new LinkedHashMap<>();
        if (networth > 0) m.put(Metrics.NETWORTH, networth);
        if (liquidNetworth > 0) m.put(Metrics.LIQUID, liquidNetworth);
        m.put(Metrics.PURSE, purse);
        m.put(Metrics.BANK, bank + personalBank);
        m.put(Metrics.SB_LEVEL, skyblockLevel);
        if (skillsApi) {
            for (Level s : skills) m.put(Metrics.SKILL + s.id(), s.xp());
            m.put(Metrics.SKILL_AVG, skillAverage);
        }
        m.put(Metrics.CATACOMBS, catacombs.xp());
        for (Level c : classes) m.put(Metrics.CLASS + c.id(), c.xp());
        m.put(Metrics.RUNS, (double) totalRuns);
        for (Slayer s : slayers) m.put(Metrics.SLAYER + s.id(), s.xp());
        m.put(Metrics.SLAYER_TOTAL, slayerXp);
        m.put(Metrics.MAGICAL_POWER, magicalPower);
        m.put(Metrics.PETS, (double) pets.size());
        m.put(Metrics.PETS_MAXED, (double) petsMaxed);
        if (farmingWeight > 0) m.put(Metrics.FARMING_WEIGHT, farmingWeight);
        m.put(Metrics.BESTIARY, (double) bestiaryMilestone);
        double fairy = Json.num(raw, "stats.playerStats.fairySouls.collected", Double.NaN);
        if (!Double.isNaN(fairy)) m.put(Metrics.FAIRY_SOULS, fairy);
        double kuudra = Json.num(raw, "stats.crimsonIsle.totalKuudraRuns", Double.NaN);
        if (!Double.isNaN(kuudra)) m.put(Metrics.KUUDRA, kuudra);
        double trophy = Json.num(raw, "stats.fishing.totalTrophyFishCaught", Double.NaN);
        if (!Double.isNaN(trophy)) m.put(Metrics.TROPHY_FISH, trophy);
        double chocolate = Json.num(raw, "chocolateFactory.totalChocolate", Double.NaN);
        if (!Double.isNaN(chocolate) && chocolate > 0) m.put(Metrics.CHOCOLATE, chocolate);
        double garden = Json.num(raw, "garden.experience", Double.NaN);
        if (!Double.isNaN(garden)) m.put(Metrics.GARDEN, garden);
        double minions = Json.num(raw, "minionSummary.uniqueTiersCrafted", Double.NaN);
        if (!Double.isNaN(minions)) m.put(Metrics.MINIONS, minions);
        return m;
    }

    // ---- Names ----------------------------------------------------------------------------------

    static final String[] SKILL_ORDER = {"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy",
            "carpentry", "runecrafting", "social", "taming", "hunting"};
    static final String[] CLASSES = {"healer", "mage", "berserk", "archer", "tank"};
    static final String[][] SLAYERS = {
            {"zombie", "Revenant Horror"}, {"spider", "Tarantula Broodfather"}, {"wolf", "Sven Packmaster"},
            {"enderman", "Voidgloom Seraph"}, {"blaze", "Inferno Demonlord"}, {"vampire", "Riftstalker Bloodfiend"}};
    static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"};

    static int rarityRank(String tier) {
        if (tier == null) return -1;
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(tier)) return i;
        return -1;
    }

    /** The color SkyBlock writes a rarity in. */
    public static int rarityColor(String tier) {
        if (tier == null) return 0xFFAAAAAA;
        return switch (tier) {
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
            default -> 0xFFFFFFFF;
        };
    }

    /** "GOLDEN_DRAGON" and "berserk" as "Golden Dragon" and "Berserk". */
    public static String title(String id) {
        if (id == null || id.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
