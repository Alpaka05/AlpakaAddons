package net.alpaka.addons.features.profile;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AtomicJsonFile;
import net.alpaka.addons.features.market.ItemIcons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pets as the game shows them: the head of the pet, or of the skin it wears.
 *
 * Neither Elite nor Hypixel's resources carry the heads, so they come from the NEU item repository
 * (NotEnoughUpdates-REPO), which the profile viewer mods all draw theirs from: one small file per
 * pet and rarity, and one per skin. A head's texture never changes, so every one found is kept in
 * {@code config/alpaka-pet-heads.json} and asked for only once.
 */
public final class PetIcons {
    private PetIcons() {}

    private static final String NEU_ITEMS = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
    private static final Pattern TEXTURE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)");
    /** Where a pet that does not come in its own rarity looks for a head: the rarities around it. */
    private static final int[] FALLBACK = {4, 5, 3, 2, 1, 0};
    private static final Gson GSON = new Gson();

    /** Head textures by NEU id ("GOLDEN_DRAGON;4", "PET_SKIN_GOLDEN_DRAGON_ANCIENT"); "" for none. */
    private static Map<String, String> textures;
    /** The built heads, by texture. */
    private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ASKED = new ConcurrentHashMap<>();

    /**
     * The pet's head: the skin's when it wears one, else the pet's own. Null while it is being
     * looked up, or when there is none; a skin not found yet shows the plain pet meanwhile.
     */
    public static ItemStack icon(ProfileData.Pet pet) {
        String texture = null;
        if (pet.skin() != null) {
            String skin = texture("PET_SKIN_" + pet.skin());
            if (skin != null && !skin.isEmpty()) texture = skin;
        }
        if (texture == null) {
            texture = petTexture(pet.type(), ProfileData.rarityRank(pet.tier()));
            if (texture == null || texture.isEmpty()) return null;
        }
        ItemStack built = STACKS.get(texture);
        if (built != null) return built;
        try {
            built = ItemIcons.head(texture);
        } catch (RuntimeException notBoundYet) {
            // Items get their components once a world is joined; until then, no icon.
            return null;
        }
        STACKS.put(texture, built);
        return built;
    }

    /** The texture of the pet in its rarity or the nearest one it comes in; null while looking. */
    private static String petTexture(String type, int rarity) {
        String own = texture(type + ";" + Math.max(0, rarity));
        if (own == null) return null;
        if (!own.isEmpty()) return own;
        for (int r : FALLBACK) {
            if (r == rarity) continue;
            String other = texture(type + ";" + r);
            if (other == null) return null;
            if (!other.isEmpty()) return other;
        }
        return "";
    }

    /** What is known about this id: its texture, "" for none, or null while it is asked for. */
    private static synchronized String known(String id) {
        return textures().get(id);
    }

    /** The texture of this NEU item, asked for in the background the first time; null until known. */
    private static String texture(String id) {
        String value = known(id);
        if (value != null) return value;
        if (ASKED.putIfAbsent(id, true) == null) {
            ProfileApi.neuItem(NEU_ITEMS + id.replace(";", "%3B") + ".json").whenComplete((body, failure) -> {
                if (failure != null && !(failure.getCause() instanceof ProfileApi.NotFound) && !(failure instanceof ProfileApi.NotFound)) {
                    // Offline or the repository slow: try again on a later look.
                    ASKED.remove(id);
                    return;
                }
                String found = failure == null ? extract(body) : null;
                remember(id, found == null ? "" : found);
            });
        }
        return null;
    }

    /** The texture value in a NEU item file, or null without one. */
    static String extract(String body) {
        if (body == null) return null;
        Matcher m = TEXTURE.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static synchronized void remember(String id, String texture) {
        textures().put(id, texture);
        CompletableFuture.runAsync(PetIcons::save);
    }

    private static synchronized Map<String, String> textures() {
        if (textures == null) {
            textures = new ConcurrentHashMap<>();
            try {
                String json = AtomicJsonFile.read(file());
                if (json != null) {
                    Map<String, String> loaded = GSON.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
                    if (loaded != null) textures.putAll(loaded);
                }
            } catch (IOException | JsonParseException e) {
                AlpakaAddons.LOGGER.warn("Couldn't read the pet heads; asking for them again", e);
            }
        }
        return textures;
    }

    private static synchronized void save() {
        try {
            AtomicJsonFile.write(file(), GSON.toJson(textures));
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Couldn't save the pet heads", e);
        }
    }

    private static File file() {
        return FabricLoader.getInstance().getConfigDir().resolve("alpaka-pet-heads.json").toFile();
    }
}
