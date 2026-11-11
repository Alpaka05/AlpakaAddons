package net.alpaka.addons.features.menuhighlight;

import net.alpaka.addons.features.menuhighlight.MenuHighlights.Item;
import net.alpaka.addons.features.menuhighlight.MenuHighlights.Mark;
import net.alpaka.addons.features.menuhighlight.MenuHighlights.Place;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuHighlightsTest {

    private static Item item(String name, String... lore) {
        return new Item(10, name, List.of(lore), null);
    }

    @Test
    void recognizesMenuTitles() {
        assertEquals(Place.ACTIVE_PET, MenuHighlights.placeOf("Pets"));
        assertEquals(Place.ACTIVE_PET, MenuHighlights.placeOf("(1/2) Pets"));
        assertEquals(Place.ACTIVE_PET, MenuHighlights.placeOf("Pets: \"rabbit\" (1/2)"));
        assertEquals(Place.LEVEL_TASKS, MenuHighlights.placeOf("Guide ➜ Starter"));
        assertEquals(Place.LEVEL_TASKS, MenuHighlights.placeOf("Tasks ➜ Core"));
        assertEquals(Place.LEVEL_TASKS, MenuHighlights.placeOf("Core ➜ Bank Upgrades"));
        assertEquals(Place.AUCTIONS, MenuHighlights.placeOf("Manage Auctions"));
        assertEquals(Place.BAZAAR_ORDERS, MenuHighlights.placeOf("Co-op Bazaar Orders"));
        assertEquals(Place.CROESUS, MenuHighlights.placeOf("(2/3) Croesus"));
        assertEquals(Place.CROESUS, MenuHighlights.placeOf("Vesuvius"));
        assertEquals(Place.SLAYER_REWARDS, MenuHighlights.placeOf("Zombie Slayer LVL Rewards"));
        assertNull(MenuHighlights.placeOf("Heart of the Forest"));
        assertNull(MenuHighlights.placeOf("Pet Sitter"));
        assertNull(MenuHighlights.placeOf("SkyBlock Menu"));
    }

    @Test
    void activePetFromPetInfoOrLore() {
        assertTrue(MenuHighlights.isActivePet("{\"type\":\"RABBIT\",\"active\":true,\"tier\":\"LEGENDARY\"}", List.of()));
        assertTrue(MenuHighlights.isActivePet(null, List.of("Click to despawn!")));
        assertFalse(MenuHighlights.isActivePet("{\"type\":\"RABBIT\",\"active\":false}", List.of("Left-click to summon!")));
    }

    @Test
    void unfinishedTasks() {
        assertTrue(MenuHighlights.isUnfinishedTask("✖ Craft a Wooden Pickaxe", List.of()));
        assertFalse(MenuHighlights.isUnfinishedTask("✔ Craft a Wooden Pickaxe", List.of()));
        assertTrue(MenuHighlights.isUnfinishedTask("Bank Upgrades", List.of("This task can only be completed once!")));
        assertTrue(MenuHighlights.isUnfinishedTask("Core Tasks", List.of("Total Progress: 42.5%")));
        assertFalse(MenuHighlights.isUnfinishedTask("Core Tasks", List.of("Total Progress: 100%")));
        assertFalse(MenuHighlights.isUnfinishedTask("Farming", List.of("Progress to Complete Category: 100%")));
        // The overview item in slot 4 is never a task.
        assertEquals(Mark.NONE, Place.LEVEL_TASKS.mark(new Item(4, "✖ Overview", List.of(), null)));
    }

    @Test
    void readyAndMissing() {
        assertEquals(Mark.READY, Place.AUCTIONS.mark(item("Hyperion", "Status: Sold!")));
        assertEquals(Mark.MISSING, Place.AUCTIONS.mark(item("Hyperion", "Status: Expired!")));
        assertEquals(Mark.READY, Place.BAZAAR_ORDERS.mark(item("BUY Enchanted Diamond", "Filled: 64/64 100%!")));
        assertEquals(Mark.NONE, Place.BAZAAR_ORDERS.mark(item("BUY Enchanted Diamond", "Filled: 12/64 18.7%")));
        assertEquals(Mark.READY, Place.CROESUS.mark(item("Floor VII", "No chests opened yet!")));
        assertEquals(Mark.NONE, Place.CROESUS.mark(item("Floor VII", "Opened Chest: Bedrock")));
        assertEquals(Mark.READY, Place.CROESUS.mark(item("Kuudra", "Infernal Tier")));
        assertEquals(Mark.NONE, Place.CROESUS.mark(item("Kuudra", "Infernal Tier", "No more chests to open!")));
        assertEquals(Mark.READY, Place.JACOB_CONTESTS.mark(item("Wheat", "You placed in the GOLD bracket!", "Click to claim reward!")));
        assertEquals(Mark.READY, Place.SLAYER_REWARDS.mark(item("LVL 5", "Click to claim rewards!")));
        assertEquals(Mark.READY, Place.COMMISSIONS.mark(item("Commission #1", "Mithril Miner", "COMPLETED")));
        assertEquals(Mark.READY, Place.CHOCOLATE_FACTORY.mark(item("Chocolate Milestones", "You have 2 unclaimed rewards!")));
        assertEquals(Mark.READY, Place.COMPOSTER_UPGRADES.mark(item("Speed", "Click to upgrade!")));
    }
}
