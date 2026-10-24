package net.alpaka.addons.features.itemvalue;

import net.alpaka.addons.AlpakaAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest of SkyBlock items written the way Hypixel writes them - names, a little lore and the data
 * the breakdown reads - for the screenshot tour, so the popup can be looked at without a server.
 * Nothing here is sent anywhere: the chest is not the player's open menu.
 */
public final class ItemValuePreview {
    private ItemValuePreview() {}

    private static ItemStack item(Item item, int count, String name, String data, String... lore) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.literal(line).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        if (data != null) {
            try {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(TagParser.parseCompoundFully(data)));
            } catch (Exception e) {
                AlpakaAddons.LOGGER.warn("Item Value preview: bad item data {}", data, e);
            }
        }
        return stack;
    }

    private static ItemStack glint(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** A chest with a fully upgraded Hyperion, a Terminator, a pet, a drill, a stack, a book and a menu button. */
    public static ContainerScreen chest() {
        SimpleContainer c = items();
        ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x3, 9_001, Minecraft.getInstance().player.getInventory(), c, 3);
        return new ContainerScreen(menu, Minecraft.getInstance().player.getInventory(), Component.literal("Chest"));
    }

    /**
     * The same items in the player's own inventory, a few stacks more, for the Chest Value panel in
     * the inventory. Only on this client: the tour's world never hears of them.
     */
    public static void fillInventory() {
        SimpleContainer c = items();
        net.minecraft.world.entity.player.Inventory inventory = Minecraft.getInstance().player.getInventory();
        for (int i = 10; i <= 15; i++) inventory.setItem(i - 1, c.getItem(i).copy());
        for (int i = 0; i < 4; i++) inventory.setItem(18 + i, c.getItem(14).copy());
        inventory.setItem(27, item(Items.WHEAT, 64, "§fWheat", "{id:\"WHEAT\"}", "", "§f§lCOMMON"));
    }

    private static SimpleContainer items() {
        SimpleContainer c = new SimpleContainer(27);
        c.setItem(10, glint(item(Items.IRON_SWORD, 1, "§dWithered Hyperion §6✪✪✪✪✪§c➎", """
                {id:"HYPERION",modifier:"withered",upgrade_level:10,rarity_upgrades:1,hot_potato_count:15,art_of_war_count:1,
                 ability_scroll:["IMPLOSION_SCROLL","SHADOW_WARP_SCROLL","WITHER_SHIELD_SCROLL"],
                 enchantments:{ultimate_wise:5,sharpness:7,smite:7,critical:6,cubism:5,ender_slayer:6,execute:5,experience:4,
                   fire_aspect:3,first_strike:4,giant_killer:6,impaling:3,lethality:6,looting:4,luck:6,scavenger:5,syphon:5,
                   thunderlord:6,vampirism:6,venomous:6,champion:10},
                 gems:{SAPPHIRE_0:"PERFECT",COMBAT_0:"PERFECT",COMBAT_0_gem:"JASPER",unlocked_slots:["SAPPHIRE_0","COMBAT_0"]}}""",
                "§7Gear Score: §d1,045", "§7Damage: §c+260 §e(+30)", "", "§d§l§ka§r §d§lMYTHIC DUNGEON SWORD §d§l§ka")));
        c.setItem(11, glint(item(Items.BOW, 1, "§dPrecise Terminator §6✪✪✪✪✪", """
                {id:"TERMINATOR",modifier:"precise",upgrade_level:5,rarity_upgrades:1,hot_potato_count:15,
                 enchantments:{ultimate_soul_eater:5,power:7,overload:5,chance:5,cubism:6,dragon_hunter:5,infinite_quiver:10,
                   piercing:1,snipe:4,toxophilite:10,impaling:3,flame:2,punch:2,aiming:5}}""",
                "§7Damage: §c+310", "", "§d§lMYTHIC DUNGEON BOW")));
        c.setItem(12, item(Items.PLAYER_HEAD, 1, "§7[Lvl 100] §6Golden Dragon", """
                {id:"PET",petInfo:'{"type":"GOLDEN_DRAGON","active":false,"exp":2.5E8,"tier":"LEGENDARY","heldItem":"PET_ITEM_TIER_BOOST","candyUsed":0}'}""",
                "§8Combat Pet", "", "§6§lLEGENDARY"));
        c.setItem(13, glint(item(Items.DIAMOND_PICKAXE, 1, "§dAuspicious Divan's Drill", """
                {id:"DIVAN_DRILL",modifier:"auspicious",rarity_upgrades:1,divan_powder_coating:1,
                 drill_part_engine:"amber_polished_drill_engine",drill_part_fuel_tank:"perfectly_cut_fuel_tank",
                 drill_part_upgrade_module:"goblin_omelette_blue_cheese",
                 enchantments:{efficiency:10,fortune:4,pristine:5,compact:10},
                 gems:{AMBER_0:"PERFECT",AMBER_1:"PERFECT",JADE_0:"PERFECT",JADE_1:"PERFECT",MINING_0:"PERFECT",MINING_0_gem:"TOPAZ",
                   unlocked_slots:["JADE_0","JADE_1","MINING_0"]}}""",
                "§7Mining Speed: §a+1,800", "", "§d§lMYTHIC DRILL")));
        c.setItem(14, glint(item(Items.DIAMOND, 64, "§aEnchanted Diamond", "{id:\"ENCHANTED_DIAMOND\"}", "", "§a§lUNCOMMON")));
        c.setItem(15, item(Items.ENCHANTED_BOOK, 1, "§fEnchanted Book", "{id:\"ENCHANTED_BOOK\",enchantments:{ultimate_chimera:5}}",
                "§d§lChimera V", "", "§f§lCOMMON"));
        c.setItem(16, item(Items.BARRIER, 1, "§cClose", null));
        // Two more stacks of the same diamonds, which Chest Value adds into one line.
        c.setItem(19, glint(item(Items.DIAMOND, 64, "§aEnchanted Diamond", "{id:\"ENCHANTED_DIAMOND\"}", "", "§a§lUNCOMMON")));
        c.setItem(20, glint(item(Items.DIAMOND, 23, "§aEnchanted Diamond", "{id:\"ENCHANTED_DIAMOND\"}", "", "§a§lUNCOMMON")));
        c.setItem(22, item(Items.ROTTEN_FLESH, 12, "§fRevenant Flesh", "{id:\"REVENANT_FLESH\"}", "", "§f§lCOMMON"));
        c.setItem(24, item(Items.PAPER, 1, "§9Mystery Note", "{id:\"ALPAKA_MYSTERY_NOTE\"}", "", "§9§lRARE"));
        return c;
    }

    /** The middle of a slot of the chest from {@link #chest}, in GUI pixels. */
    public static int[] slotCenter(int slot) {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        // A three-row chest is 176 by 168, centred; its slots start 8 in and 18 down.
        int left = (width - 176) / 2;
        int top = (height - 168) / 2;
        return new int[] {left + 8 + (slot % 9) * 18 + 8, top + 18 + (slot / 9) * 18 + 8};
    }

    /** Whether the popup has its prices, or has given up on them, so its picture can be taken. */
    public static boolean settled() {
        return ItemPrices.updatedAt() > 0 || ItemPrices.failed();
    }
}
