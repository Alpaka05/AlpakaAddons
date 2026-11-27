package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The skins of the player's own pets, from the profile kept on disk ({@link ProfileCache}).
 *
 * The game says which pet is out and whether it wears a skin, but not which one: the tab list and
 * the chat only add a ✦, and the pet in the world carries no owner. The profile lists every pet
 * with its skin. It is the one the Profile Dashboard last loaded, so a skin put on since shows once
 * the dashboard was opened again; nothing is asked of the network here.
 */
public final class KeptPets {
    private KeptPets() {}

    /** One pet of the profile: its type ("GOLDEN_DRAGON"), rarity (0 Common to 5 Mythic), skin id or null, and whether it was out. */
    record Kept(String type, int rarity, String skin, boolean active) {}

    /** How long a read of the file is good for; the dashboard writes a new one whenever it loads. */
    private static final long REREAD_MS = 60_000L;

    private static volatile List<Kept> pets = List.of();
    private static volatile String readFor;
    private static volatile long readAt;
    private static volatile boolean reading;

    /**
     * The id of the skin ("GOLDEN_DRAGON_ANCIENT") on the player's pet of this type and rarity (below
     * 0: unknown), or null when the kept profile has none. Reads the file in the background, so the
     * first answers after joining are null.
     */
    public static String skin(String type, int rarity) {
        refresh();
        return pick(pets, type, rarity);
    }

    private static void refresh() {
        String account = AlpakaApi.currentUuid();
        if (account == null || reading) return;
        long now = System.currentTimeMillis();
        if (account.equals(readFor) && now - readAt < REREAD_MS) return;
        reading = true;
        CompletableFuture.supplyAsync(() -> parse(ProfileCache.read(account))).whenComplete((read, failure) -> {
            pets = read == null ? List.of() : read;
            readFor = account;
            readAt = System.currentTimeMillis();
            reading = false;
        });
    }

    /** The pets in the API's answer as {@link ProfileCache} keeps it; empty without one. */
    static List<Kept> parse(JsonElement answer) {
        List<Kept> out = new ArrayList<>();
        if (answer == null) return out;
        for (JsonElement e : Json.arr(Json.at(answer, "profile.member"), "pets_data.pets")) {
            String type = Json.str(e, "type");
            if (type == null) continue;
            out.add(new Kept(type, ProfileData.rarityRank(Json.str(e, "tier")), Json.str(e, "skin"), Json.bool(e, "active")));
        }
        return out;
    }

    /**
     * The skin of the pet meant. Several pets of a type can wear different skins: the one in the
     * rarity asked for goes first, and among those the one that was out when the profile was loaded.
     * A rarity that no skinned one has is not held against them: a Tier Boost shows a pet one rarity
     * above the one the profile lists.
     */
    static String pick(List<Kept> pets, String type, int rarity) {
        Kept best = null;
        int bestScore = -1;
        for (Kept pet : pets) {
            if (pet.skin() == null || pet.skin().isEmpty() || !pet.type().equals(type)) continue;
            int score = (rarity >= 0 && pet.rarity() == rarity ? 2 : 0) + (pet.active() ? 1 : 0);
            if (score > bestScore) {
                best = pet;
                bestScore = score;
            }
        }
        return best == null ? null : best.skin();
    }
}
