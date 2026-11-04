package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * What a profile doesn't have yet: the pets it never got, and the accessories missing from its bag
 * or owned only in a lower version.
 *
 * Which pets there are and which accessories replace which come from the NEU repository, as the
 * other profile viewers have them; the accessories themselves and their rarities from Hypixel's
 * item list.
 */
final class Missing {
    private Missing() {}

    /**
     * An accessory to get: its id, name and rarity, the Magical Power it adds, and the owned lower
     * version it would replace, null for one not owned in any version.
     */
    record Gap(String id, String name, String tier, int power, String upgradeOf) {}

    /** A pet the profile has none of, in the highest rarity it comes in. */
    record MissingPet(String type, String name, String tier) {}

    /** What the NEU repository says: accessory upgrades, accessories to leave out, and every pet's rarities. */
    record Neu(Map<String, List<String>> upgrades, Set<String> ignored, Map<String, String> petTiers) {}

    private static final String CONSTANTS = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/";
    private static volatile Neu neu;
    private static volatile boolean fetching;
    private static volatile long retryAt;

    /** The NEU lists, or null while they load; asking starts loading them. */
    static Neu neu() {
        Neu loaded = neu;
        if (loaded == null) load();
        return loaded;
    }

    private static synchronized void load() {
        if (fetching || neu != null || System.currentTimeMillis() < retryAt) return;
        fetching = true;
        CompletableFuture<String> misc = ProfileApi.neuItem(CONSTANTS + "misc.json");
        CompletableFuture<String> pets = ProfileApi.neuItem(CONSTANTS + "petnums.json");
        misc.thenCombine(pets, Missing::parse).whenComplete((parsed, failure) -> {
            if (failure != null) {
                AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't load NEU's pet and accessory lists: {}", failure.toString());
                retryAt = System.currentTimeMillis() + 60_000L;
            } else {
                neu = parsed;
            }
            fetching = false;
        });
    }

    static Neu parse(String misc, String petnums) {
        JsonElement m = JsonParser.parseString(misc);
        Map<String, List<String>> upgrades = new HashMap<>();
        for (var e : Json.obj(m, "talisman_upgrades").entrySet()) {
            List<String> higher = new ArrayList<>();
            if (e.getValue().isJsonArray()) for (JsonElement h : e.getValue().getAsJsonArray()) higher.add(h.getAsString());
            upgrades.put(e.getKey(), higher);
        }
        Set<String> ignored = new HashSet<>();
        for (JsonElement e : Json.arr(m, "ignored_talisman")) ignored.add(e.getAsString());
        Map<String, String> petTiers = new HashMap<>();
        JsonElement p = JsonParser.parseString(petnums);
        if (p.isJsonObject()) {
            for (var e : p.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                String best = null;
                for (String tier : ((JsonObject) e.getValue()).keySet()) {
                    if (ProfileData.rarityRank(tier) > ProfileData.rarityRank(best)) best = tier;
                }
                if (best != null) petTiers.put(e.getKey(), best);
            }
        }
        return new Neu(upgrades, ignored, petTiers);
    }

    /** The Magical Power an accessory of this rarity adds. */
    static int magicalPower(String tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case "COMMON", "SPECIAL" -> 3;
            case "UNCOMMON", "VERY_SPECIAL" -> 5;
            case "RARE" -> 8;
            case "EPIC" -> 12;
            case "LEGENDARY" -> 16;
            case "MYTHIC" -> 22;
            default -> 0;
        };
    }

    /**
     * Hats of Celebration: every colour, the 2022 Crab, the Sloth, Balloon and Cake Hats are one
     * accessory to the game, which counts only one of them. NEU lists the colours as upgrades of
     * one another but leaves out ids Hypixel has, like {@code PARTY_HAT_CRAB_ANIMATED}, so the
     * family goes by name.
     */
    private static final List<String> HAT_PREFIXES = List.of("PARTY_HAT_", "BALLOON_HAT_", "CAKE_HAT_");

    static boolean celebrationHat(String id) {
        for (String prefix : HAT_PREFIXES) if (id.startsWith(prefix)) return true;
        return false;
    }

    /**
     * The accessories to get, most Magical Power first. Each upgrade line counts once, by its best
     * version: owning one turns the line into an upgrade of the best one owned, and owning its top
     * makes it complete.
     *
     * Which version is best goes by rarity first and only then by NEU's order, as NEU's lists are
     * not always right: it has Powder of the People below Powder of the Few though it is the rarer,
     * and no step from the Pesthunter Artifact to the Relic. Within one rarity, the more versions
     * list one as their upgrade, the higher it stands - a God Badge above the Campfire Badges below.
     */
    static List<Gap> accessories(Set<String> owned, Map<String, String> tiers, Neu neu, Function<String, String> names) {
        // Lines as groups: everything NEU links by an upgrade, and all the hats.
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> below = new HashMap<>();
        for (var e : neu.upgrades().entrySet()) {
            for (String higher : e.getValue()) {
                union(parent, e.getKey(), higher);
                below.merge(higher, 1, Integer::sum);
            }
        }
        String firstHat = null;
        for (String id : tiers.keySet()) {
            if (!celebrationHat(id)) continue;
            if (firstHat == null) firstHat = id;
            else union(parent, firstHat, id);
        }
        Map<String, List<String>> lines = new HashMap<>();
        for (String id : tiers.keySet()) {
            if (neu.ignored().contains(id)) continue;
            lines.computeIfAbsent(find(parent, id), k -> new ArrayList<>()).add(id);
        }
        Comparator<String> rank = Comparator.comparingInt((String id) -> magicalPower(tiers.get(id)))
                .thenComparingInt(id -> celebrationHat(id) ? 0 : below.getOrDefault(id, 0));
        List<Gap> gaps = new ArrayList<>();
        for (List<String> line : lines.values()) {
            String top = line.stream().max(rank.thenComparing(Comparator.reverseOrder())).orElseThrow();
            String best = line.stream().filter(owned::contains).max(rank).orElse(null);
            if (best != null && rank.compare(best, top) >= 0) continue;
            int power = magicalPower(tiers.get(top)) - (best == null ? 0 : magicalPower(tiers.get(best)));
            String name = names.apply(top);
            gaps.add(new Gap(top, name == null ? ProfileData.title(top) : name, tiers.get(top), Math.max(0, power), best));
        }
        gaps.sort(Comparator.comparingInt(Gap::power).reversed().thenComparing(Gap::name));
        return gaps;
    }

    private static String find(Map<String, String> parent, String id) {
        String root = id;
        while (true) {
            String up = parent.get(root);
            if (up == null || up.equals(root)) break;
            root = up;
        }
        parent.put(id, root);
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a), rb = find(parent, b);
        if (!ra.equals(rb)) parent.put(ra, rb);
    }

    /**
     * Pets that are one pet under a different name per rarity: a Droplet Wisp becomes a Frost,
     * Glacial and then Subzero Wisp, so owning any of them is owning the pet.
     */
    private static final List<Set<String>> FAMILIES = List.of(
            Set.of("DROPLET_WISP", "FROST_WISP", "GLACIAL_WISP", "SUBZERO_WISP"));
    /** Only to be had on a Bingo profile. */
    private static final String BINGO_PET = "BINGO";

    /** The pets the profile has none of, rarest first; a family shows once, under its top name. */
    static List<MissingPet> pets(Set<String> owned, Neu neu, boolean bingoProfile) {
        List<MissingPet> missing = new ArrayList<>();
        for (var e : neu.petTiers().entrySet()) {
            String type = e.getKey();
            if (owned.contains(type) || (!bingoProfile && type.equals(BINGO_PET))) continue;
            Set<String> family = familyOf(type);
            if (family != null && (family.stream().anyMatch(owned::contains) || !type.equals(top(family, neu)))) continue;
            missing.add(new MissingPet(type, ProfileData.title(type), e.getValue()));
        }
        missing.sort(Comparator.comparingInt((MissingPet p) -> ProfileData.rarityRank(p.tier())).reversed().thenComparing(MissingPet::name));
        return missing;
    }

    /** How many pets the profile has at their max level and highest rarity, of how many there are. */
    record MaxPets(int maxed, int total) {}

    /**
     * Each pet counts once, a family under its top name, so a second copy or a lower Wisp adds
     * nothing. A pet the profile owns that NEU doesn't list yet still counts, at the rarity it has:
     * NEU lags behind new pets for a while.
     */
    static MaxPets maxPets(List<ProfileData.Pet> pets, Neu neu, boolean bingoProfile) {
        Map<String, String> topTiers = new HashMap<>();
        for (var e : neu.petTiers().entrySet()) {
            String type = e.getKey();
            if (!bingoProfile && type.equals(BINGO_PET)) continue;
            Set<String> family = familyOf(type);
            if (family != null && !type.equals(top(family, neu))) continue;
            topTiers.put(type, e.getValue());
        }
        Set<String> maxed = new HashSet<>();
        for (ProfileData.Pet p : pets) {
            if (!bingoProfile && p.type().equals(BINGO_PET)) continue;
            Set<String> family = familyOf(p.type());
            String type = family == null ? p.type() : top(family, neu);
            if (type == null) type = p.type();
            String top = topTiers.get(type);
            if (top == null) topTiers.put(type, top = p.tier());
            if (type.equals(p.type()) && p.level() >= p.maxLevel() && ProfileData.rarityRank(p.tier()) >= ProfileData.rarityRank(top)) {
                maxed.add(type);
            }
        }
        return new MaxPets(maxed.size(), topTiers.size());
    }

    private static Set<String> familyOf(String type) {
        for (Set<String> family : FAMILIES) if (family.contains(type)) return family;
        return null;
    }

    /** The family member that comes in the highest rarity. */
    private static String top(Set<String> family, Neu neu) {
        String best = null;
        for (String type : family) {
            String tier = neu.petTiers().get(type);
            if (tier != null && (best == null || ProfileData.rarityRank(tier) > ProfileData.rarityRank(neu.petTiers().get(best)))) best = type;
        }
        return best;
    }
}
