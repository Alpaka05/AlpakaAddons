package net.alpaka.addons.features.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTreesTest {

    @Test
    void readsNeusLayout() {
        // Cut down from NEU's hotmlayout.json.
        SkillTrees.Tree tree = SkillTrees.parse("""
                {"prelude": [], "hotm": {"powders": {}, "perks": {
                  "mining_speed": {"name": "Mining Speed", "x": 3, "y": 9, "maxLevel": 50, "item": "(npi level0 maxLevel)"},
                  "pickobulus": {"name": "Pickobulus", "x": 5, "y": 8, "maxLevel": 1, "item": "(api level0)"},
                  "gemstone_infusion": {"name": "Gemstone Infusion", "x": 0, "y": 0, "maxLevel": 1, "item": "(api level0)"},
                  "sky_mall": {"name": "Sky Mall", "x": 0, "y": 6, "maxLevel": 1, "item": "(npi level0 maxLevel)"}}}}
                """, "hotm");
        assertEquals(4, tree.perks().size());
        assertEquals(6, tree.columns());
        assertEquals(10, tree.rows());
        // Top tier first.
        assertEquals("gemstone_infusion", tree.perks().get(0).id());
        assertTrue(SkillTrees.at(tree, 5, 8).ability());
        assertFalse(SkillTrees.at(tree, 0, 6).ability());
        assertEquals(50, SkillTrees.at(tree, 3, 9).maxLevel());
        assertNull(SkillTrees.at(tree, 1, 1));
    }

    @Test
    void seaCreatureKillsTakeTheLargerCount() {
        JsonObject kills = JsonParser.parseString("""
                {"lord_jawbus": 54, "lord_jawbus_600": 122, "flaming_worm": 15, "flaming_worm_50": 6497,
                 "pond_squid": 7935, "pond_squid_300": 9, "thunder_400": 385}""").getAsJsonObject();
        assertEquals(122, SeaCreatures.kills(kills, "lord_jawbus"));
        assertEquals(6497, SeaCreatures.kills(kills, "flaming_worm"));
        assertEquals(385, SeaCreatures.kills(kills, "thunder"));
        // Plhlegblast is the level-300 squid, looked up by that id alone.
        assertEquals(9, SeaCreatures.kills(kills, "pond_squid_300"));
        assertEquals(0, SeaCreatures.kills(kills, "ragnarok"));
    }
}
