package net.alpaka.addons.features.itemsearch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * For the screenshot tour: Item Search filled with made-up containers, as if the player had opened
 * a few backpacks, Ender Chest pages, sacks and island chests. Kept in memory only.
 */
public final class ItemSearchPreview {
    private ItemSearchPreview() {}

    private static ItemStack item(Item item, String id, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.literal(line).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemSearchStore.Item of(ItemStack stack, long count) {
        return new ItemSearchStore.Item(stack, count);
    }

    private static ItemSearchStore.Container container(ItemSearchSource source, int page, String label, BlockPos pos,
                                                      ItemSearchStore.Item... items) {
        return new ItemSearchStore.Container(source, page, label, pos, null, System.currentTimeMillis(), new ArrayList<>(List.of(items)));
    }

    /** The window over the made-up containers, with the query typed in when there is one. */
    public static ItemSearchScreen screen(String query) {
        ItemStack diamond = item(Items.DIAMOND, "ENCHANTED_DIAMOND", "§aEnchanted Diamond", "§9§lUNCOMMON");
        ItemStack coal = item(Items.COAL, "ENCHANTED_COAL", "§aEnchanted Coal", "§9§lUNCOMMON");
        ItemStack hype = item(Items.IRON_SWORD, "HYPERION", "§dHeroic Hyperion §6✪✪✪✪✪", "§7Damage: §c+270",
                "§6Ability: Wither Impact §e§lRIGHT CLICK", "§d§lMYTHIC DUNGEON SWORD");
        ItemStack term = item(Items.BOW, "TERMINATOR", "§6Terminator", "§7Damage: §c+310", "§6§lLEGENDARY DUNGEON BOW");
        ItemStack necron = item(Items.LEATHER_CHESTPLATE, "POWER_WITHER_CHESTPLATE", "§6Ancient Necron's Chestplate", "§6§lLEGENDARY DUNGEON CHESTPLATE");
        ItemStack ruby = item(Items.REDSTONE, "ROUGH_RUBY_GEM", "§fRough Ruby Gemstone", "§f§lCOMMON");
        ItemStack pearl = item(Items.ENDER_PEARL, "ENDER_PEARL", "§fEnder Pearl", "§f§lCOMMON");
        ItemStack pickonimbus = item(Items.DIAMOND_PICKAXE, "PICKONIMBUS", "§5Pickonimbus 2000", "§5§lEPIC PICKAXE");
        ItemStack talisman = item(Items.PLAYER_HEAD, "SPEED_TALISMAN", "§fSpeed Talisman", "§f§lCOMMON ACCESSORY");
        ItemStack sugar = item(Items.SUGAR_CANE, "ENCHANTED_SUGAR_CANE", "§9Enchanted Sugar Cane", "§9§lRARE");
        ItemStack mithril = item(Items.PRISMARINE_CRYSTALS, "MITHRIL_ORE", "§fMithril", "§f§lCOMMON");
        ItemStack bone = item(Items.BONE, "ENCHANTED_BONE", "§aEnchanted Bone", "§a§lUNCOMMON");

        ItemSearchStore.devPreview(List.of(
                container(ItemSearchSource.ENDER_CHEST, 1, null, null, of(diamond, 64), of(diamond, 64), of(hype, 1), of(pearl, 16)),
                container(ItemSearchSource.ENDER_CHEST, 2, null, null, of(coal, 640), of(talisman, 1)),
                container(ItemSearchSource.BACKPACK, 3, null, null, of(term, 1), of(diamond, 32), of(bone, 128)),
                container(ItemSearchSource.BACKPACK, 7, null, null, of(pickonimbus, 1), of(sugar, 320)),
                container(ItemSearchSource.SACKS, 1, "Mining Sack", null, of(coal, 28_183), of(mithril, 145_210), of(diamond, 1_204)),
                container(ItemSearchSource.SACKS, 1, "Gemstones Sack", null, of(ruby, 78_999)),
                container(ItemSearchSource.CHEST, 0, "Chest at 12, 70, -4", new BlockPos(12, 70, -4), of(bone, 2_304), of(sugar, 1_088)),
                container(ItemSearchSource.WARDROBE, 2, null, null, of(necron, 1)),
                container(ItemSearchSource.ACCESSORY_BAG, 1, null, null, of(talisman, 1))));
        return new ItemSearchScreen(query, null);
    }
}
