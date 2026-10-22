package net.alpaka.addons.features.market;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.List;

/**
 * Market menus filled locally with items written the way Hypixel writes them, for the screenshot
 * tour: the screens can be looked at without a server. Nothing here is sent anywhere - the chests
 * are not the player's open menu, so a click on them goes nowhere.
 */
public final class MarketPreview {
    private MarketPreview() {}

    private static ItemStack item(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.literal(line).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    private static ItemStack glint(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static ItemStack pane(Item item) {
        return item(item, " ");
    }

    private static SimpleContainer filled(int rows, Item pane) {
        SimpleContainer container = new SimpleContainer(rows * 9);
        for (int i = 0; i < rows * 9; i++) container.setItem(i, pane(pane));
        return container;
    }

    private static Inventory inventory() {
        Minecraft mc = Minecraft.getInstance();
        Inventory inventory = mc.player.getInventory();
        if (inventory.getItem(0).isEmpty()) {
            inventory.setItem(0, item(Items.IRON_SWORD, "§6Hyperion", "§7Damage: §c+260"));
            inventory.setItem(1, item(Items.BOW, "§6Juju Shortbow", "§7Damage: §c+310"));
            inventory.setItem(2, new ItemStack(Items.ENDER_PEARL, 16));
            inventory.setItem(4, item(Items.PLAYER_HEAD, "§5Spirit Pet"));
            inventory.setItem(8, item(Items.NETHER_STAR, "§aSkyBlock Menu §7(Click)"));
            inventory.setItem(9, new ItemStack(Items.WHEAT, 64));
            inventory.setItem(10, glint(new ItemStack(Items.HAY_BLOCK, 12)));
            inventory.setItem(14, item(Items.DIAMOND_CHESTPLATE, "§6Necron's Chestplate"));
        }
        return inventory;
    }

    private static MarketScreen screen(int rows, SimpleContainer container, String title, MarketKind kind) {
        MenuType<ChestMenu> type = switch (rows) {
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        ChestMenu menu = new ChestMenu(type, 9_000, inventory(), container, rows);
        return MarketScreen.preview(menu, inventory(), title, kind);
    }

    // ---- The Auction House ----------------------------------------------------------------------

    private record Auction(Item item, String name, String tier, String price, boolean bin, String ends, String seller, String tag) {
        Auction(Item item, String name, String tier, String price, boolean bin, String ends, String seller) {
            this(item, name, tier, price, bin, ends, seller, null);
        }
    }

    private static final Auction[] AUCTIONS = {
            new Auction(Items.IRON_SWORD, "§6Hyperion §6✪✪✪✪✪", "§6§lLEGENDARY DUNGEON SWORD", "1,140,000,000", true, "13h 22m 5s", "§b[MVP§c+§b] Technosmith", "HYPERION"),
            new Auction(Items.DIAMOND_SHOVEL, "§5Aspect of the Void", "§5§lEPIC SWORD", "1,250,000", true, "2d 4h", "§a[VIP] Llamaqueen"),
            new Auction(Items.BOW, "§6Terminator", "§6§lLEGENDARY BOW", "1,630,000,000", false, "4h 11m", "§6[MVP§0++§6] Bowspam"),
            new Auction(Items.IRON_SWORD, "§6Livid Dagger", "§6§lLEGENDARY DUNGEON SWORD", "6,900,000", true, "21h 5m", "§7Kaleido"),
            new Auction(Items.FISHING_ROD, "§aGrappling Hook", "§a§lUNCOMMON", "74,000", true, "1d 2h", "§b[MVP] Pigeonfan"),
            new Auction(Items.GOLDEN_SWORD, "§fRogue Sword", "§f§lCOMMON SWORD", "1,200", true, "2m 41s", "§7Newbie2026"),
            new Auction(Items.IRON_SWORD, "§dGiant's Sword ✦", "§d§lMYTHIC DUNGEON SWORD", "41,500,000", true, "9h 40m", "§b[MVP§c+§b] Alpaka05"),
            new Auction(Items.GOLDEN_SWORD, "§6Midas' Sword", "§6§lLEGENDARY SWORD", "22,750,000", false, "18m 3s", "§a[VIP§6+§a] Goldrush"),
            new Auction(Items.DIAMOND_SWORD, "§5Shadow Fury", "§5§lEPIC DUNGEON SWORD", "18,900,000", true, "6h 6m", "§7Shadowcat"),
            new Auction(Items.IRON_SWORD, "§9Zombie Sword", "§9§lRARE SWORD", "212,000", true, "3d", "§7Zombiekid"),
            new Auction(Items.BLAZE_ROD, "§6Fire Veil Wand", "§6§lLEGENDARY DUNGEON WAND", "3,400,000", true, "11h", "§b[MVP] Pyro"),
            new Auction(Items.STONE_SWORD, "§5Ornate Zombie Sword", "§5§lEPIC SWORD", "455,000", true, "40m 2s", "§7Ornament"),
            new Auction(Items.DIAMOND_SWORD, "§6Aspect of the Dragons", "§6§lLEGENDARY SWORD", "940,000", true, "1d 9h", "§a[VIP] Dragonslayer"),
            new Auction(Items.BONE, "§6Bonemerang", "§6§lLEGENDARY DUNGEON SWORD", "2,150,000", false, "5h 51m", "§7Skeleman"),
            new Auction(Items.IRON_SWORD, "§5Spirit Sceptre", "§5§lEPIC DUNGEON SWORD", "7,800,000", true, "14h", "§b[MVP§c+§b] Batwing"),
            new Auction(Items.BOW, "§6Juju Shortbow", "§6§lLEGENDARY BOW", "21,000,000", true, "2h 2m", "§7Jujutsu"),
            new Auction(Items.GOLDEN_AXE, "§6Ragnarock Axe", "§6§lLEGENDARY AXE", "8,250,000", true, "7h 45m", "§a[VIP] Viking"),
            new Auction(Items.IRON_SWORD, "§9Silver Fang", "§9§lRARE DUNGEON SWORD", "86,000", true, "22h", "§7Wolfpack"),
            new Auction(Items.WOODEN_SWORD, "§aAspect of the Jerry", "§a§lUNCOMMON SWORD", "9,500", true, "4m", "§7Jerryfan"),
            new Auction(Items.DIAMOND_SWORD, "§dDark Claymore", "§d§lMYTHIC DUNGEON SWORD", "212,000,000", true, "1d", "§6[MVP§0++§6] Claymore"),
            new Auction(Items.IRON_SWORD, "§5Flower of Truth", "§5§lEPIC DUNGEON SWORD", "1,900,000", false, "3h", "§7Florist"),
            new Auction(Items.GOLDEN_SWORD, "§6Pigman Sword", "§6§lLEGENDARY SWORD", "2,640,000", true, "8h 8m", "§7Oinker"),
            new Auction(Items.STICK, "§9Ember Rod", "§9§lRARE SWORD", "310,000", true, "19h", "§7Emberly"),
            new Auction(Items.SHEARS, "§5Valkyrie", "§5§lEPIC DUNGEON SWORD", "245,000,000", true, "6h 30m", "§b[MVP] Valkyra"),
    };

    private static ItemStack auction(Auction a, boolean full) {
        List<String> lore = new ArrayList<>();
        if (full) {
            lore.add("§7Gear Score: §d1,045 §8(4,108)");
            lore.add("§7Damage: §c+260 §e(+30) §8(+1,128.4)");
            lore.add("§7Strength: §c+150 §e(+30) §6[+5] §8(+637.5)");
            lore.add("§7Intelligence: §a+600 §9(+125) §8(+2,940)");
            lore.add("");
            lore.add("§6Ability: Wither Impact  §e§lRIGHT CLICK");
            lore.add("§7Teleports §a10 blocks §7ahead of you.");
            lore.add("");
        } else {
            lore.add("§7Damage: §c+" + (120 + a.name().length() * 3));
            lore.add("");
        }
        lore.add(a.tier());
        lore.add("§8§m-----------------");
        lore.add("§7Seller: " + a.seller());
        if (a.bin()) {
            lore.add("§7Buy it now: §6" + a.price() + " coins");
        } else {
            lore.add("§7Bids: §a" + (3 + a.name().length() % 9) + " bids");
            lore.add("");
            lore.add("§7Top bid: §6" + a.price() + " coins");
            lore.add("§7Bidder: §b[MVP] Outbidder");
        }
        lore.add("");
        lore.add("§7Ends in: §e" + a.ends());
        lore.add("");
        lore.add("§eClick to inspect!");
        ItemStack stack = item(a.item(), a.name(), lore.toArray(new String[0]));
        if (a.name().contains("✪") || a.name().contains("✦")) glint(stack);
        if (a.tag() != null) {
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
            data.putString("id", a.tag());
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
        }
        return stack;
    }

    private static final int[] GRID = {11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33, 34, 38, 39, 40, 41, 42, 43};

    public static MarketScreen auctionHouse() {
        SimpleContainer c = filled(4, Items.STAINED_GLASS_PANE.black());
        c.setItem(11, item(Items.GOLD_BLOCK, "§6Auctions Browser", "§7Find items for sale by players", "§7across Hypixel SkyBlock!", "",
                "§7Items offered here are for", "§7auction, meaning you have to", "§7place the top bid to acquire them!", "", "§eClick to browse!"));
        c.setItem(13, item(Items.GOLDEN_CARROT, "§eView Bids", "§7You placed §e2 bids §7that are", "§7still running.", "", "§eClick to view!"));
        c.setItem(15, item(Items.GOLDEN_HORSE_ARMOR, "§aManage Auctions", "§7You own §e3 auctions §7in", "§7progress or which recently ended.",
                "", "§eClick to manage!"));
        c.setItem(31, item(Items.BARRIER, "§cClose"));
        return screen(4, c, "Auction House", MarketKind.AUCTION_HOUSE);
    }

    public static MarketScreen auctionBrowser() {
        SimpleContainer c = filled(6, Items.STAINED_GLASS_PANE.black());
        for (int row = 0; row < 6; row++) c.setItem(row * 9 + 1, pane(Items.STAINED_GLASS_PANE.orange()));
        c.setItem(0, glint(item(Items.GOLDEN_SWORD, "§6Weapons", "§8Category", "", "§7Examples:", "§8▪ §7Swords", "§8▪ §7Bows", "",
                "§aCurrently browsing!")));
        c.setItem(9, item(Items.DIAMOND_CHESTPLATE, "§bArmor", "§8Category", "", "§eClick to view items!"));
        c.setItem(18, item(Items.EMERALD, "§2Accessories", "§8Category", "", "§eClick to view items!"));
        c.setItem(27, item(Items.APPLE, "§cConsumables", "§8Category", "", "§eClick to view items!"));
        c.setItem(36, item(Items.COBBLESTONE, "§eBlocks", "§8Category", "", "§eClick to view items!"));
        c.setItem(45, item(Items.STICK, "§dTools & Misc", "§8Category", "", "§eClick to view items!"));
        for (int i = 0; i < GRID.length; i++) c.setItem(GRID[i], auction(AUCTIONS[i], false));
        c.setItem(46, item(Items.ARROW, "§aPrevious Page", "§ePage 1"));
        c.setItem(47, item(Items.OAK_SIGN, "§aSearch", "§7Find items by name, type, lore", "§7or enchantments.", "", "§eClick to search!"));
        c.setItem(48, item(Items.BOOK, "§aAdvanced Search", "§7Filter by stats and enchants.", "", "§eClick to open!"));
        c.setItem(49, item(Items.ARROW, "§aGo Back", "§7To Auction House"));
        c.setItem(50, item(Items.HOPPER, "§aSort", "", "§7 Highest Bid", "§b▶ Lowest Price", "§7 Ending soon", "§7 Most Bids", "",
                "§bRight-click to go backwards!", "§eClick to switch sort!"));
        c.setItem(51, item(Items.ENDER_EYE, "§aItem Tier", "§7Show only items of the given", "§7rarity.", "", "§b▶ No filter",
                "§f Common", "§a Uncommon", "§9 Rare", "§5 Epic", "§6 Legendary", "", "§eClick to switch filter!"));
        c.setItem(52, item(Items.GOLD_BLOCK, "§aBIN Filter", "§7Pick which auctions to show!", "", "§7 Show All", "§b▶ BIN Only",
                "§7 Auctions Only", "", "§eClick to switch filter!"));
        c.setItem(53, item(Items.ARROW, "§aNext Page", "§ePage 3"));
        return screen(6, c, "Auctions Browser", MarketKind.AUCTION_HOUSE);
    }

    public static MarketScreen auctionView() {
        SimpleContainer c = filled(6, Items.STAINED_GLASS_PANE.black());
        c.setItem(13, auction(AUCTIONS[0], true));
        c.setItem(29, item(Items.GOLD_NUGGET, "§6Buy Item Right Now", "§7Price: §61,140,000,000 coins", "", "§eClick to purchase!"));
        c.setItem(33, item(Items.MAP, "§aBid History", "§7No bids have been placed on", "§7this item yet.", "", "§eClick to view!"));
        c.setItem(49, item(Items.ARROW, "§aGo Back", "§7To Auctions Browser"));
        return screen(6, c, "BIN Auction View", MarketKind.AUCTION_HOUSE);
    }

    public static MarketScreen auctionConfirm() {
        SimpleContainer c = filled(3, Items.STAINED_GLASS_PANE.black());
        c.setItem(11, item(Items.DYED_TERRACOTTA.green(), "§aConfirm", "§7Purchasing: §6Hyperion", "§7Cost: §61,140,000,000 coins"));
        c.setItem(15, item(Items.DYED_TERRACOTTA.red(), "§cCancel", "§7Back to the auction."));
        return screen(3, c, "Confirm Purchase", MarketKind.AUCTION_HOUSE);
    }

    // ---- The Bazaar -----------------------------------------------------------------------------

    private record Product(Item item, String name, double buy, double sell, boolean enchanted) {}

    private static final Product[] FARMING = {
            new Product(Items.WHEAT, "§fWheat", 5.3, 4.9, false),
            new Product(Items.WHEAT, "§aEnchanted Wheat", 852.4, 801.0, true),
            new Product(Items.HAY_BLOCK, "§aEnchanted Hay Bale", 153_420.2, 147_002.5, true),
            new Product(Items.CARROT, "§fCarrot", 2.6, 2.1, false),
            new Product(Items.CARROT, "§aEnchanted Carrot", 401.1, 389.7, true),
            new Product(Items.GOLDEN_CARROT, "§9Enchanted Golden Carrot", 61_250.0, 58_100.4, true),
            new Product(Items.POTATO, "§fPotato", 2.2, 1.8, false),
            new Product(Items.POTATO, "§aEnchanted Potato", 361.8, 340.0, true),
            new Product(Items.BAKED_POTATO, "§9Enchanted Baked Potato", 58_844.0, 56_012.9, true),
            new Product(Items.PUMPKIN, "§fPumpkin", 9.8, 8.4, false),
            new Product(Items.PUMPKIN, "§aEnchanted Pumpkin", 1_540.5, 1_489.0, true),
            new Product(Items.CARVED_PUMPKIN, "§9Polished Pumpkin", 251_330.0, 240_500.0, true),
            new Product(Items.MELON_SLICE, "§fMelon Slice", 1.9, 1.6, false),
            new Product(Items.MELON_SLICE, "§aEnchanted Melon", 310.4, 300.1, true),
            new Product(Items.MELON, "§9Enchanted Melon Block", 49_800.0, 47_350.0, true),
            new Product(Items.SUGAR_CANE, "§fSugar Cane", 4.1, 3.6, false),
            new Product(Items.SUGAR, "§aEnchanted Sugar", 653.2, 621.8, true),
            new Product(Items.SUGAR_CANE, "§9Enchanted Sugar Cane", 104_220.0, 99_870.5, true),
            new Product(Items.COCOA_BEANS, "§fCocoa Beans", 3.4, 2.9, false),
            new Product(Items.COCOA_BEANS, "§aEnchanted Cocoa Bean", 540.0, 512.3, true),
            new Product(Items.CACTUS, "§fCactus", 3.0, 2.4, false),
            new Product(Items.DYE.green(), "§aEnchanted Cactus Green", 520.7, 489.9, true),
            new Product(Items.RED_MUSHROOM, "§fRed Mushroom", 12.1, 9.8, false),
            new Product(Items.NETHER_WART, "§fNether Wart", 4.4, 3.9, false),
    };

    private static ItemStack product(Product p) {
        ItemStack stack = item(p.item(), p.name(), "§8Farming Commodity", "", "§7Buy price: §6" + Coins.full(p.buy()) + " coins",
                "§7Sell price: §6" + Coins.full(p.sell()) + " coins", "", "§eClick to view details!");
        return p.enchanted() ? glint(stack) : stack;
    }

    public static MarketScreen bazaar() {
        SimpleContainer c = filled(6, Items.STAINED_GLASS_PANE.black());
        for (int row = 0; row < 6; row++) c.setItem(row * 9 + 1, pane(Items.STAINED_GLASS_PANE.yellow()));
        c.setItem(0, glint(item(Items.GOLDEN_HOE, "§eFarming", "§8Category", "", "§aCurrently viewing!")));
        c.setItem(9, item(Items.DIAMOND_PICKAXE, "§bMining", "§8Category", "", "§eClick to view!"));
        c.setItem(18, item(Items.IRON_SWORD, "§cCombat", "§8Category", "", "§eClick to view!"));
        c.setItem(27, item(Items.FISHING_ROD, "§6Woods & Fishes", "§8Category", "", "§eClick to view!"));
        c.setItem(36, item(Items.ENCHANTING_TABLE, "§dOddities", "§8Category", "", "§eClick to view!"));
        for (int i = 0; i < GRID.length; i++) c.setItem(GRID[i], product(FARMING[i]));
        c.setItem(47, item(Items.OAK_SIGN, "§aSearch", "§7Find products by name!", "", "§eClick to search!"));
        c.setItem(48, item(Items.CHEST, "§bSell Inventory Now", "§7Instantly sell anything in your", "§7inventory that can be sold on", "§7the Bazaar.", "",
                "§7You earn: §61,764 coins", "", "§eClick to sell!"));
        c.setItem(49, item(Items.BARRIER, "§cClose"));
        c.setItem(50, item(Items.BOOK, "§aManage Orders", "§7You don't have any ongoing", "§7orders.", "", "§eClick to manage!"));
        c.setItem(51, item(Items.CAULDRON, "§bSell Sacks Now", "§7Instantly sell anything in your", "§7sacks.", "", "§eClick to sell!"));
        c.setItem(52, item(Items.REDSTONE_TORCH, "§aBazaar Settings", "§7Configure the Bazaar.", "", "§eClick to open!"));
        return screen(6, c, "Bazaar ➜ Farming", MarketKind.BAZAAR);
    }

    public static MarketScreen bazaarProduct() {
        SimpleContainer c = filled(4, Items.STAINED_GLASS_PANE.black());
        // As on Hypixel: the product itself names no price, the instant buttons do.
        c.setItem(13, glint(item(Items.HAY_BLOCK, "§aEnchanted Hay Bale", "§8Collection Item", "", "§a§lUNCOMMON")));
        c.setItem(10, item(Items.GOLDEN_HORSE_ARMOR, "§aBuy Instantly", "§8Enchanted Hay Bale", "", "§7Price per unit: §6153,420.2 coins",
                "§7Stack price: §69,818,892.8 coins", "", "§eClick to pick amount!"));
        c.setItem(11, item(Items.HOPPER, "§6Sell Instantly", "§8Enchanted Hay Bale", "", "§7Inventory: §a12 items", "",
                "§7Amount: §a12§7x", "§7Price per unit: §6147,002.5 coins", "§7Total: §61,764,030 coins", "", "§eClick to sell!"));
        c.setItem(15, item(Items.FILLED_MAP, "§aCreate Buy Order", "§8Enchanted Hay Bale", "", "§7Top Orders:",
                "§8- §6147,002.5 coins §7each | §a1,640§7x", "§8- §6147,000 coins §7each | §a320§7x", "", "§eClick to setup Buy Order!"));
        c.setItem(16, item(Items.MAP, "§6Create Sell Offer", "§8Enchanted Hay Bale", "", "§7Top Offers:",
                "§8- §6153,420.2 coins §7each | §a960§7x", "§8- §6153,500 coins §7each | §a64§7x", "", "§eClick to setup Sell Offer!"));
        c.setItem(30, item(Items.ARROW, "§aGo Back", "§7To Farming"));
        c.setItem(31, item(Items.PAPER, "§aView Graphs", "§7See the price history.", "", "§eClick to view!"));
        return screen(4, c, "Farming ➜ Enchanted Hay Bale", MarketKind.BAZAAR);
    }

    public static MarketScreen bazaarOrders() {
        SimpleContainer c = filled(6, Items.STAINED_GLASS_PANE.black());
        c.setItem(10, glint(item(Items.HAY_BLOCK, "§a§lBUY §aEnchanted Hay Bale", "§8Worth 245.5M coins", "", "§7Order amount: §a1,600§7x",
                "§7Filled: §61,200§7/1,600 §8(75%)", "", "§7Price per unit: §6153,420.2 coins", "", "§eClick to view options!")));
        c.setItem(11, glint(item(Items.MELON, "§6§lSELL §9Enchanted Melon Block", "§8Worth 31.9M coins", "", "§7Offer amount: §a640§7x",
                "§7Filled: §6640§7/640 §a§l100%!", "", "§7Price per unit: §649,800 coins", "", "§eClick to claim!")));
        c.setItem(12, item(Items.WHEAT, "§a§lBUY §fWheat", "§8Worth 37.1k coins", "", "§7Order amount: §a7,000§7x",
                "§7Filled: §6420§7/7,000 §8(6%)", "", "§7Price per unit: §65.3 coins", "", "§eClick to view options!"));
        c.setItem(13, glint(item(Items.SUGAR, "§6§lSELL §aEnchanted Sugar", "§8Worth 417.4k coins", "", "§7Offer amount: §a640§7x",
                "§7Filled: §60§7/640 §8(0%)", "", "§7Price per unit: §6652.2 coins", "", "§eClick to view options!")));
        c.setItem(49, item(Items.ARROW, "§aGo Back", "§7To Bazaar"));
        return screen(6, c, "Your Bazaar Orders", MarketKind.BAZAAR);
    }

    // ---- The input bar --------------------------------------------------------------------------

    /** The input bar on a sign with these four lines, written like one of Hypixel's prompts. */
    public static MarketInputScreen input(String[] lines, MarketKind context, String typed) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = mc.player.blockPosition().above(2);
        SignBlockEntity sign = new SignBlockEntity(pos, Blocks.OAK_SIGN.defaultBlockState());
        sign.setLevel(mc.level);
        SignText text = new SignText();
        for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.literal(lines[i]));
        sign.setText(text, true);
        sign.setAllowedPlayerEditor(mc.player.getUUID());
        if (context == MarketKind.AUCTION_HOUSE && Markets.recentSearches(context).isEmpty()) {
            for (String q : List.of("juju shortbow", "terminator", "aotv", "hyperion")) Markets.rememberSearch(context, q);
        }
        MarketInputScreen screen = new MarketInputScreen(sign, true, false, SignPrompt.of(lines), context);
        return typed == null ? screen : screen.typed(typed);
    }

    /** The price sign of "Create BIN Auction", as Hypixel writes it, with this typed. */
    public static MarketInputScreen binPrice(String typed) {
        Markets.menuOpened("Create BIN Auction");
        return input(new String[] {"", "^^^^^^^^^^^^^^^", "Your auction", "starting bid"}, MarketKind.AUCTION_HOUSE, typed);
    }

    /**
     * Opens a sign with these lines the way the game opens one Hypixel sends - through the player's
     * sign opening, where the input bar hooks in - so the tour sees whether it is recognised.
     */
    public static void openLikeHypixel(String[] lines) {
        Minecraft mc = Minecraft.getInstance();
        MarketInputScreen made = input(lines, null, null);
        Markets.devAnywhere = true;
        try {
            mc.player.openTextEdit(made.sign(), true);
        } finally {
            Markets.devAnywhere = false;
        }
    }

    /** A click on a card of the open market screen, answered the way Hypixel answers it. */
    public static void pressLikeHypixel(int slot) {
        if (Minecraft.getInstance().gui.screen() instanceof MarketScreen screen) screen.devPressLikeHypixel(slot);
    }

    /** The inventory drawer open or shut, for the screenshot tour. */
    public static void inventory(boolean open) {
        MarketScreen.devInventory(open);
    }

    /** A market switched to Hypixel's own menu, or back, for the screenshot tour. */
    public static void classic(MarketKind kind, boolean classic) {
        MarketScreen.devClassic(kind, classic);
    }
}
