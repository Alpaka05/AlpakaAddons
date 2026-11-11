package net.alpaka.addons.features.slothighlight;

import net.alpaka.addons.features.slothighlight.SlotHighlight.Item;
import net.alpaka.addons.features.slothighlight.SlotHighlight.Mark;
import net.alpaka.addons.features.slothighlight.SlotHighlight.Place;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotHighlightTest {

    private static Item item(String name, String... lore) {
        return new Item(10, name, List.of(lore), null);
    }

    @Test
    void recognizesMenuTitles() {
        assertEquals(Place.ACTIVE_PET, SlotHighlight.placeOf("Pets"));
        assertEquals(Place.ACTIVE_PET, SlotHighlight.placeOf("(1/2) Pets"));
        assertEquals(Place.ACTIVE_PET, SlotHighlight.placeOf("Pets: \"rabbit\" (1/2)"));
        assertEquals(Place.LEVEL_TASKS, SlotHighlight.placeOf("Guide ➜ Starter"));
        assertEquals(Place.LEVEL_TASKS, SlotHighlight.placeOf("Tasks ➜ Core"));
        assertEquals(Place.LEVEL_TASKS, SlotHighlight.placeOf("Core ➜ Bank Upgrades"));
        assertEquals(Place.AUCTIONS, SlotHighlight.placeOf("Manage Auctions"));
        assertEquals(Place.BAZAAR_ORDERS, SlotHighlight.placeOf("Co-op Bazaar Orders"));
        assertEquals(Place.CROESUS, SlotHighlight.placeOf("(2/3) Croesus"));
        assertEquals(Place.CROESUS, SlotHighlight.placeOf("Vesuvius"));
        assertEquals(Place.SLAYER_REWARDS, SlotHighlight.placeOf("Zombie Slayer LVL Rewards"));
        assertNull(SlotHighlight.placeOf("Heart of the Forest"));
        assertNull(SlotHighlight.placeOf("Pet Sitter"));
        assertNull(SlotHighlight.placeOf("SkyBlock Menu"));
    }

    @Test
    void activePetFromPetInfoOrLore() {
        assertTrue(SlotHighlight.isActivePet("{\"type\":\"RABBIT\",\"active\":true,\"tier\":\"LEGENDARY\"}", List.of()));
        assertTrue(SlotHighlight.isActivePet(null, List.of("Click to despawn!")));
        assertFalse(SlotHighlight.isActivePet("{\"type\":\"RABBIT\",\"active\":false}", List.of("Left-click to summon!")));
    }

    @Test
    void unfinishedTasks() {
        assertTrue(SlotHighlight.isUnfinishedTask("✖ Craft a Wooden Pickaxe", List.of()));
        assertFalse(SlotHighlight.isUnfinishedTask("✔ Craft a Wooden Pickaxe", List.of()));
        assertTrue(SlotHighlight.isUnfinishedTask("Bank Upgrades", List.of("This task can only be completed once!")));
        assertTrue(SlotHighlight.isUnfinishedTask("Core Tasks", List.of("Total Progress: 42.5%")));
        assertFalse(SlotHighlight.isUnfinishedTask("Core Tasks", List.of("Total Progress: 100%")));
        assertFalse(SlotHighlight.isUnfinishedTask("Farming", List.of("Progress to Complete Category: 100%")));
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
