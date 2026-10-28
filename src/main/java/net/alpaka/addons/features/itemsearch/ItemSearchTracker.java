package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads what Item Search remembers out of the menus the player opens: when an Ender Chest page, a
 * backpack, a sack, the wardrobe, the Accessory Bag, the Personal Vault or a chest on the player's
 * own island closes, what was in it last is saved over what was there before.
 *
 * Only reads: the menus are the ones the player opened, and nothing is clicked or sent. Sacks also
 * follow Hypixel's "[Sacks]" chat line, whose hover text lists every item that went in or out, so an
 * amount stays right without opening the sack again.
 */
public final class ItemSearchTracker {
    private ItemSearchTracker() {}

    /** A chest on the island the player just right-clicked, waiting for its menu to open. */
    private static BlockPos pendingChest = null;
    private static BlockPos pendingChest2 = null;
    private static long pendingChestAt = 0L;
    private static final long CHEST_WAIT_MS = 3_000L;

    /** Whether the menu's contents should be read when it closes. */
    static boolean active() {
        return AlpakaConfig.instance.itemSearchEnabled && SkyblockUtils.isOnSkyblock();
    }

    /** A block right-clicked: remembered when it is a chest on the player's own island. */
    static void onUseBlock(BlockPos pos, BlockState state) {
        if (!active() || !(state.getBlock() instanceof ChestBlock) || !onOwnIsland()) return;
        BlockPos first = pos.immutable();
        BlockPos second = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? null : ChestBlock.getConnectedBlockPos(first, state).immutable();
        // A large chest is saved under the same block whichever half was clicked.
        if (second != null && second.asLong() < first.asLong()) {
            BlockPos swap = first;
            first = second;
            second = swap;
        }
        pendingChest = first;
        pendingChest2 = second;
        pendingChestAt = System.currentTimeMillis();
        ItemSearchHighlight.chestOpened(first);
    }

    /** A menu closed: its contents are saved if it is one Item Search knows. */
    static void onMenuClosed(Component title, AbstractContainerMenu menu) {
        if (!active()) return;
        String text = SkyblockUtils.cleanColor(title.getString()).trim();
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) slots.add(slot);
        }
        long now = System.currentTimeMillis();

        ItemSearchMenus.Menu parsed = ItemSearchMenus.parseTitle(text);
        if (parsed == null) {
            if (pendingChest != null && now - pendingChestAt < CHEST_WAIT_MS && isChestTitle(title, text)) {
                List<ItemSearchStore.Item> items = new ArrayList<>();
                for (Slot slot : slots) add(items, slot.getItem());
                BlockPos pos = pendingChest;
                ItemSearchStore.put(new ItemSearchStore.Container(ItemSearchSource.CHEST, 0,
                        "Chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), pos, pendingChest2, now, items));
            }
            pendingChest = null;
            return;
        }

        int rows = slots.size() / 9;
        switch (parsed.source()) {
            // The top row is Hypixel's: the page buttons and the way back.
            case ENDER_CHEST, BACKPACK -> saveRange(parsed, slots, 9, slots.size(), now);
            // The bottom row is Hypixel's.
            case ACCESSORY_BAG, VAULT -> saveRange(parsed, slots, 0, Math.max(0, (rows - 1) * 9), now);
            case WARDROBE -> saveWardrobe(parsed.page(), slots, now);
            case SACKS -> saveSack(parsed, slots, now);
            default -> {}
        }
    }

    private static void saveRange(ItemSearchMenus.Menu menu, List<Slot> slots, int from, int to, long now) {
        List<ItemSearchStore.Item> items = new ArrayList<>();
        for (int i = from; i < to && i < slots.size(); i++) add(items, slots.get(i).getItem());
        ItemSearchStore.put(new ItemSearchStore.Container(menu.source(), menu.page(), null, null, null, now, items));
    }

    /**
     * The wardrobe: nine slots a page, each a column of helmet, chestplate, leggings and boots, with
     * its button under them. The set the player wears is left out - it is on the player, where the
     * inventory already counts it.
     */
    private static void saveWardrobe(int page, List<Slot> slots, long now) {
        if (slots.size() < 45) return;
        for (int column = 0; column < 9; column++) {
            int number = (page - 1) * 9 + column + 1;
            String button = SkyblockUtils.cleanColor(slots.get(36 + column).getItem().getHoverName().getString());
            if (button.contains("Equipped")) {
                ItemSearchStore.remove(ItemSearchStore.key(ItemSearchSource.WARDROBE, number, null, null));
                continue;
            }
            List<ItemSearchStore.Item> items = new ArrayList<>();
            for (int row = 0; row < 4; row++) add(items, slots.get(row * 9 + column).getItem());
            ItemSearchStore.put(new ItemSearchStore.Container(ItemSearchSource.WARDROBE, number, null, null, null, now, items));
        }
    }

    /** A sack: each slot an item kept in it, with the amount stored in its lore rather than in the stack. */
    private static void saveSack(ItemSearchMenus.Menu menu, List<Slot> slots, long now) {
        List<ItemSearchStore.Item> items = new ArrayList<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = loreOf(stack);
            String name = SkyblockUtils.cleanColor(stack.getHoverName().getString());
            for (ItemSearchMenus.Stored stored : ItemSearchMenus.parseSackLore(lore)) {
                ItemStack copy = stack.copyWithCount(1);
                // The lore is the sack's ("Stored", "Click to pick up"), not the item's.
                copy.remove(DataComponents.LORE);
                if (stored.variant() != null) {
                    Style style = stack.getHoverName().getStyle();
                    copy.set(DataComponents.CUSTOM_NAME, Component.literal(ItemSearchMenus.variantName(name, stored.variant())).withStyle(style));
                }
                items.add(new ItemSearchStore.Item(copy, stored.amount()));
            }
        }
        ItemSearchStore.put(new ItemSearchStore.Container(ItemSearchSource.SACKS, menu.page(), menu.label(), null, null, now, items));
    }

    /**
     * Adds a slot's stack if it is a SkyBlock item. Every real item carries Hypixel's id; the menu's
     * own glass panes, arrows and locked-slot markers do not, so they stay out.
     */
    private static void add(List<ItemSearchStore.Item> items, ItemStack stack) {
        if (stack.isEmpty() || skyblockId(stack) == null) return;
        items.add(new ItemSearchStore.Item(stack.copyWithCount(1), stack.getCount()));
    }

    /** Hypixel's id of a SkyBlock item, kept in its custom data; null for anything else. */
    static String skyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }

    /** The item's own unique id, which no two items share; null for stackable things. */
    static String uuid(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String uuid = data.copyTag().getStringOr("uuid", "");
        return uuid.isEmpty() ? null : uuid;
    }

    static List<String> loreOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(SkyblockUtils.cleanColor(line.getString()));
        return lines;
    }

    /** A chest's own menu: vanilla's "Chest" and "Large Chest", which Hypixel keeps on the island. */
    private static boolean isChestTitle(Component title, String text) {
        if (title instanceof MutableComponent mutable && mutable.getContents() instanceof TranslatableContents tr
                && tr.getKey().startsWith("container.chest")) {
            return true;
        }
        return text.equals("Chest") || text.equals("Large Chest");
    }

    /** Whether the player is on their own Private Island, where the sidebar's zone line reads "Your Island". */
    static boolean onOwnIsland() {
        if (!SkyblockUtils.isOnSkyblock()) return false;
        for (String line : SkyblockUtils.getSidebarLines()) {
            if (line.contains("⏣") && line.contains("Your Island")) return true;
        }
        return false;
    }

    /**
     * Hypixel's "[Sacks] +12 items, -3 items." line: its hover text says what went in and out, one
     * line each. Each change is added to the item in the sack it names, where that sack has been seen.
     */
    static void onChat(Component message) {
        if (!active()) return;
        String text = SkyblockUtils.cleanColor(message.getString());
        if (!text.startsWith("[Sacks]")) return;
        List<Component> hovers = new ArrayList<>();
        message.visit((style, part) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText show) hovers.add(show.value());
            return Optional.empty();
        }, Style.EMPTY);
        for (Component hover : hovers) {
            for (String line : SkyblockUtils.cleanColor(hover.getString()).split("\n")) {
                ItemSearchMenus.SackChange change = ItemSearchMenus.parseSackChange(line);
                if (change != null) applySackChange(change);
            }
        }
    }

    private static void applySackChange(ItemSearchMenus.SackChange change) {
        for (ItemSearchStore.Container c : ItemSearchStore.all()) {
            if (c.source != ItemSearchSource.SACKS || !change.sack().equals(c.label)) continue;
            for (int i = 0; i < c.items.size(); i++) {
                ItemSearchStore.Item item = c.items.get(i);
                if (!SkyblockUtils.cleanColor(item.stack().getHoverName().getString()).equals(change.item())) continue;
                c.items.set(i, new ItemSearchStore.Item(item.stack(), Math.max(0, item.count() + change.delta())));
                ItemSearchStore.markDirty();
                return;
            }
        }
    }
}
