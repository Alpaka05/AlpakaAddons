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
     * The accessories to get, most Magical Power first. Only the top of each upgrade line counts:
     * owning any of its versions turns it into an upgrade of the best one owned, and a line already
     * owned at its top is complete.
     */
    static List<Gap> accessories(Set<String> owned, Map<String, String> tiers, Neu neu, Function<String, String> names) {
        List<Gap> gaps = new ArrayList<>();
        for (var e : tiers.entrySet()) {
            String id = e.getKey();
            if (owned.contains(id) || neu.ignored().contains(id)) continue;
            boolean top = true;
            for (String higher : neu.upgrades().getOrDefault(id, List.of())) {
                if (tiers.containsKey(higher)) {
                    top = false;
                    break;
                }
            }
            if (!top) continue;
            String best = null;
            for (String lower : owned) {
                if (!neu.upgrades().getOrDefault(lower, List.of()).contains(id)) continue;
                if (best == null || magicalPower(tiers.get(lower)) > magicalPower(tiers.get(best))) best = lower;
            }
            int power = magicalPower(e.getValue()) - (best == null ? 0 : magicalPower(tiers.get(best)));
            String name = names.apply(id);
            gaps.add(new Gap(id, name == null ? ProfileData.title(id) : name, e.getValue(), Math.max(0, power), best));
        }
        gaps.sort(Comparator.comparingInt(Gap::power).reversed().thenComparing(Gap::name));
        return gaps;
    }

    /** The pets the profile has none of, rarest first. */
    static List<MissingPet> pets(Set<String> owned, Neu neu) {
        List<MissingPet> missing = new ArrayList<>();
        for (var e : neu.petTiers().entrySet()) {
            if (!owned.contains(e.getKey())) missing.add(new MissingPet(e.getKey(), ProfileData.title(e.getKey()), e.getValue()));
        }
        missing.sort(Comparator.comparingInt((MissingPet p) -> ProfileData.rarityRank(p.tier())).reversed().thenComparing(MissingPet::name));
        return missing;
    }
}
