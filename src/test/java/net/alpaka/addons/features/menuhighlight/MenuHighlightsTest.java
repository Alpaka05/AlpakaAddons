package net.alpaka.addons.features.menuhighlight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuHighlightsTest {

    @Test
    void recognizesPetsMenuTitles() {
        assertTrue(MenuHighlights.isPetsMenu("Pets"));
        assertTrue(MenuHighlights.isPetsMenu("(1/2) Pets"));
        assertTrue(MenuHighlights.isPetsMenu("Pets (2/3)"));
        assertTrue(MenuHighlights.isPetsMenu("Pets: \"rabbit\" (1/2)"));
        assertFalse(MenuHighlights.isPetsMenu("Pet Sitter"));
        assertFalse(MenuHighlights.isPetsMenu("Kat"));
    }

    @Test
    void activePetFromPetInfoOrLore() {
        assertTrue(MenuHighlights.isActivePet("{\"type\":\"RABBIT\",\"active\":true,\"tier\":\"LEGENDARY\"}", List.of()));
        assertTrue(MenuHighlights.isActivePet(null, List.of("Right-click to add this pet to", "Click to despawn!")));
        assertFalse(MenuHighlights.isActivePet("{\"type\":\"RABBIT\",\"active\":false}", List.of("Left-click to summon!")));
    }

    @Test
    void recognizesLevelingMenuTitles() {
        assertTrue(MenuHighlights.isLevelingMenu("Guide ➜ Starter"));
        assertTrue(MenuHighlights.isLevelingMenu("(1/2) Guide ➜ Early Game"));
        assertTrue(MenuHighlights.isLevelingMenu("Tasks ➜ Core"));
        assertTrue(MenuHighlights.isLevelingMenu("Skill Related Tasks"));
        assertTrue(MenuHighlights.isLevelingMenu("Core ➜ Bank Upgrades"));
        assertTrue(MenuHighlights.isLevelingMenu("Essence Shop ➜ Wither"));
        assertFalse(MenuHighlights.isLevelingMenu("SkyBlock Menu"));
        assertFalse(MenuHighlights.isLevelingMenu("Pets"));
    }

    @Test
    void unfinishedTasks() {
        assertTrue(MenuHighlights.isUnfinishedTask("✖ Craft a Wooden Pickaxe", List.of()));
        assertFalse(MenuHighlights.isUnfinishedTask("✔ Craft a Wooden Pickaxe", List.of()));
        assertTrue(MenuHighlights.isUnfinishedTask("Bank Upgrades", List.of("This task can only be completed once!")));
        assertTrue(MenuHighlights.isUnfinishedTask("Core Tasks", List.of("Total Progress: 42.5%")));
        assertFalse(MenuHighlights.isUnfinishedTask("Core Tasks", List.of("Total Progress: 100%")));
        assertTrue(MenuHighlights.isUnfinishedTask("Farming", List.of("Progress to Complete Category: 7%")));
        assertFalse(MenuHighlights.isUnfinishedTask("Farming", List.of("Progress to Complete Category: 100%")));
    }
}
