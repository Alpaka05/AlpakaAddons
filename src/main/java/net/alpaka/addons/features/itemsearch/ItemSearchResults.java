package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search window's list: every item from every remembered container and the player's own
 * inventory, the same item from several places folded into one entry with all its places.
 */
public final class ItemSearchResults {
    private ItemSearchResults() {}

    /** How the list is ordered. */
    public enum Sort {
        AMOUNT("Amount"), NAME("Name");

        public final String label;

        Sort(String label) {
            this.label = label;
        }

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** One place an entry's item lies, and how many of it are there. */
    public record Place(ItemSearchSource source, int page, String name, BlockPos pos, BlockPos pos2, long count) {}

    /** One line of the list: an item, how many there are in all, and where. */
    public static final class Entry {
        public final String key;
        public final ItemStack stack;
        public final String name;
        public final List<String> lore;
        public final String id;
        public final List<Place> places = new ArrayList<>();
        public long total;

        Entry(String key, ItemStack stack) {
            this.key = key;
            this.stack = stack;
            this.name = SkyblockUtils.cleanColor(stack.getHoverName().getString());
            this.lore = ItemSearchTracker.loreOf(stack);
            this.id = ItemSearchTracker.skyblockId(stack);
        }

        /** The place with the most of it, which a click opens. */
        public Place best() {
            return places.get(0);
        }
    }

    /**
     * The key items are folded together by: an item with its own unique id stays alone, anything else
     * is one entry per SkyBlock id and name, so a Hyperion with other enchantments is its own line
     * while 3 stacks of Enchanted Diamonds are one.
     */
    static String groupKey(ItemStack stack) {
        String uuid = ItemSearchTracker.uuid(stack);
        if (uuid != null) return "uuid:" + uuid;
        String id = ItemSearchTracker.skyblockId(stack);
        String name = SkyblockUtils.cleanColor(stack.getHoverName().getString());
        return (id != null ? id : stack.getItem().toString()) + "|" + name;
    }

    /** Every item in the chosen place, or everywhere for null, folded and in no particular order. */
    public static List<Entry> collect(ItemSearchSource only) {
        Map<String, Entry> byKey = new LinkedHashMap<>();
        if (only == null || only == ItemSearchSource.INVENTORY) addInventory(byKey);
        dropBrokenChests();
        // The set worn shows in the wardrobe too; it is counted once, as worn.
        java.util.Set<String> worn = wornKeys();
        for (ItemSearchStore.Container c : ItemSearchStore.all()) {
            if (only != null && c.source != only) continue;
            Map<String, Long> here = new LinkedHashMap<>();
            Map<String, ItemStack> stacks = new LinkedHashMap<>();
            for (ItemSearchStore.Item item : c.items) {
                if (item.count() <= 0) continue;
                String key = groupKey(item.stack());
                if (c.source == ItemSearchSource.WARDROBE && worn.contains(key)) continue;
                here.merge(key, item.count(), Long::sum);
                stacks.putIfAbsent(key, item.stack());
            }
            for (Map.Entry<String, Long> e : here.entrySet()) {
                add(byKey, e.getKey(), stacks.get(e.getKey()),
                        new Place(c.source, c.page, c.place(), c.pos, c.pos2, e.getValue()));
            }
        }
        List<Entry> list = new ArrayList<>(byKey.values());
        for (Entry entry : list) entry.places.sort(Comparator.comparingLong((Place p) -> -p.count()));
        return list;
    }

    /** The player's inventory and what they wear, read live. The ninth hotbar slot is the SkyBlock Menu. */
    private static void addInventory(Map<String, Entry> byKey) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Map<String, Long> here = new LinkedHashMap<>();
        Map<String, ItemStack> stacks = new LinkedHashMap<>();
        Map<String, Boolean> worn = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            if (i == 8) continue;
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            String key = groupKey(stack);
            here.merge(key, (long) stack.getCount(), Long::sum);
            stacks.putIfAbsent(key, stack.copyWithCount(1));
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String key = groupKey(stack);
            here.merge(key, (long) stack.getCount(), Long::sum);
            stacks.putIfAbsent(key, stack.copyWithCount(1));
            worn.put(key, true);
        }
        for (Map.Entry<String, Long> e : here.entrySet()) {
            String name = worn.containsKey(e.getKey()) ? "Worn" : "Inventory";
            add(byKey, e.getKey(), stacks.get(e.getKey()),
                    new Place(ItemSearchSource.INVENTORY, 0, name, null, null, e.getValue()));
        }
    }

    /** The group keys of the armor the player wears that are unique items, which can only be in one place. */
    private static java.util.Set<String> wornKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return keys;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && ItemSearchTracker.uuid(stack) != null) keys.add(groupKey(stack));
        }
        return keys;
    }

    private static void add(Map<String, Entry> byKey, String key, ItemStack stack, Place place) {
        Entry entry = byKey.computeIfAbsent(key, k -> new Entry(k, stack));
        entry.places.add(place);
        entry.total += place.count();
    }

    /** On the island, a remembered chest whose block is no longer a chest was broken: it is forgotten. */
    private static void dropBrokenChests() {
        if (!ItemSearchTracker.onOwnIsland()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        List<String> broken = new ArrayList<>();
        for (ItemSearchStore.Container c : ItemSearchStore.all()) {
            if (c.source != ItemSearchSource.CHEST || c.pos == null || !level.isLoaded(c.pos)) continue;
            if (!(level.getBlockState(c.pos).getBlock() instanceof ChestBlock)) broken.add(c.key());
        }
        for (String key : broken) ItemSearchStore.remove(key);
    }

    /** The entries that match the query, in the chosen order. */
    public static List<Entry> filter(List<Entry> all, String query, Sort sort) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : all) {
            if (ItemSearchMenus.matches(query, entry.name, entry.lore)) out.add(entry);
        }
        Comparator<Entry> byName = Comparator.comparing((Entry e) -> e.name.toLowerCase(Locale.ROOT));
        Comparator<Entry> order = switch (sort) {
            case AMOUNT -> Comparator.comparingLong((Entry e) -> -e.total).thenComparing(byName);
            case NAME -> byName.thenComparingLong(e -> -e.total);
        };
        out.sort(order);
        return out;
    }
}
