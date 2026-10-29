package net.alpaka.addons.features.itemsearch;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Where an item lies: one of the places Item Search knows, each with the command that opens it. The
 * order is the order of the categories in the search window.
 */
public enum ItemSearchSource {
    INVENTORY("Inventory"),
    ENDER_CHEST("Ender Chest"),
    BACKPACK("Backpacks"),
    SACKS("Sacks"),
    CHEST("Island Chests"),
    WARDROBE("Wardrobe"),
    ACCESSORY_BAG("Accessory Bag"),
    VAULT("Personal Vault");

    public final String label;

    ItemSearchSource(String label) {
        this.label = label;
    }

    /** The icon beside the category. A method rather than a field, so the tests can use the names without a game. */
    public Item icon() {
        return switch (this) {
            case INVENTORY -> Items.PLAYER_HEAD;
            case ENDER_CHEST -> Items.ENDER_CHEST;
            case BACKPACK -> Items.LEATHER_HORSE_ARMOR;
            case SACKS -> Items.BUNDLE;
            case CHEST -> Items.CHEST;
            case WARDROBE -> Items.LEATHER_CHESTPLATE;
            case ACCESSORY_BAG -> Items.EMERALD;
            case VAULT -> Items.IRON_DOOR;
        };
    }

    /**
     * The name of one container of this kind, as the tooltip and the footer show it: "Backpack 3",
     * "Ender Chest 2", "Mining Sack", "Chest at 12, 70, -4".
     */
    public String place(int page, String label) {
        return switch (this) {
            case INVENTORY -> label != null ? label : "Inventory";
            case ENDER_CHEST -> "Ender Chest " + page;
            case BACKPACK -> "Backpack " + page;
            case SACKS -> label != null ? label : "Sacks";
            case CHEST -> label != null ? label : "Island Chest";
            case WARDROBE -> "Wardrobe Page " + page;
            case ACCESSORY_BAG -> "Accessory Bag " + page;
            case VAULT -> "Personal Vault";
        };
    }

    /**
     * The Hypixel command that opens the container, without its slash, or null where there is none
     * to send: the inventory is open with E, and an island chest is found by walking to it.
     */
    public String command(int page) {
        return switch (this) {
            case INVENTORY, CHEST -> null;
            case ENDER_CHEST -> "ec " + page;
            case BACKPACK -> "bp " + page;
            case SACKS -> "sacks";
            case WARDROBE -> "wd " + page;
            case ACCESSORY_BAG -> "ab " + page;
            // The Personal Vault is a button in the bank's menu; there is no command of its own.
            case VAULT -> "bank";
        };
    }
}
