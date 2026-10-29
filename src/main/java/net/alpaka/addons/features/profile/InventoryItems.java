package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Items from a player's inventories, as Elite hands them out - SkyBlock id, name, lore, count,
 * enchantments and attributes - turned into what the dashboard draws: the item as the game shows
 * it, and its tooltip as Hypixel wrote it.
 *
 * Heads that carry their own texture (pets, skinned items) wear it; everything else is drawn the
 * way the market's screens draw items ({@link ItemIcons#forItem}): from the NEU repository, or
 * Hypixel's list while that loads.
 */
final class InventoryItems {
    private InventoryItems() {}

    /** One built item: the look it was built from, so a better look arriving later replaces it. */
    private record Built(ItemStack base, ItemStack stack) {}

    /** By the item's JSON, which stays the same object for as long as its inventory is shown. */
    private static final Map<JsonElement, Built> STACKS = new IdentityHashMap<>();

    /** The item to draw, or empty while its look is still on its way. Render thread only. */
    static ItemStack stack(JsonElement item) {
        ItemStack base = base(item);
        Built built = STACKS.get(item);
        if (built != null && built.base() == base) return built.stack();
        if (base.isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = base.copyWithCount(Math.max(1, Math.min(99, (int) Json.num(item, "count", 1))));
        // Enchanted gear shimmers in the game; the looks are of the plain item.
        if (!Json.obj(item, "enchantments").isEmpty()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (STACKS.size() > 4000) STACKS.clear();
        STACKS.put(item, new Built(base, stack));
        return stack;
    }

    private static ItemStack base(JsonElement item) {
        String texture = Json.str(item, "attributes.skin_texture");
        if (texture != null && !texture.isBlank()) {
            Built built = STACKS.get(item);
            if (built != null) return built.base();
            try {
                return ItemIcons.head(texture);
            } catch (RuntimeException notBoundYet) {
                // Items only get their components once a world is joined.
                return ItemStack.EMPTY;
            }
        }
        String id = Json.str(item, "skyblockId");
        return ItemIcons.forItem(id, SkyblockUtils.cleanColor(Json.str(item, "name")));
    }

    /** The tooltip: the name and the lore, in Hypixel's own colors. */
    static List<Component> tooltip(JsonElement item) {
        List<Component> lines = new ArrayList<>();
        String name = Json.str(item, "name");
        lines.add(Component.literal(name == null ? "" : name));
        for (JsonElement line : Json.arr(item, "lore")) {
            if (line.isJsonPrimitive()) lines.add(Component.literal(line.getAsString()));
        }
        return lines;
    }

    /** Whether a slot holds an item at all; Elite lists empty slots as null. */
    static boolean present(JsonElement item) {
        return item != null && item.isJsonObject();
    }
}
