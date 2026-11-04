package net.alpaka.addons.features.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    private static final String MOLE_AND_CORE = """
            {"prelude": [], "hotm": {"powders": {
              "GEMSTONE": {"costLine": "§7Cost: §d{cost} Gemstone Powder"},
              "MITHRIL": {"costLine": "§7Cost: §2{cost} Mithril Powder"}}, "perks": {
              "mole": {"name": "Mole", "x": 3, "y": 6, "maxLevel": 200, "powder": "GEMSTONE",
                "item": "(npi level0 maxLevel)", "cost": "(pow (+ level 2) 2.2)",
                "stat": "(+ 50 (/ (* (- level 1) 350) 199))",
                "lore": ["§7Grants §e+{stat} Mining Spread§7 when", "§7mining Hard Stone."]},
              "core_of_the_mountain": {"name": "Core of the Mountain", "x": 3, "y": 5, "maxLevel": 10,
                "powder": "(if (lt level 4) MITHRIL (if (lt level 8) GEMSTONE GLACITE))", "item": ":REDSTONE_BLOCK",
                "cost": "(list.at (list.new 0 50000 100000 200000 300000 400000 600000 750000 1000000 1250000 0) level)",
                "lore": [{"text": "§8+§c1 Pickaxe Ability Level", "onlyIf": "(gt level 0)"},
                         {"text": "§8+§a1 Forge Slot", "onlyIf": "(gt level 1)"}]}}}}
            """;

    @Test
    void evaluatesNeusExpressions() {
        Map<String, Object> vars = Map.of("level", 3.0, "level0", 3.0);
        assertEquals(4.0, NeuLisp.eval("(if (lt level 4) (+ level 1) 0)", vars, id -> 0.0));
        assertEquals("GEMSTONE", NeuLisp.eval("(if (lt level 2) MITHRIL GEMSTONE)", vars, id -> 0.0));
        assertEquals(200000.0, NeuLisp.eval("(list.at (list.new 0 50000 100000 200000) level)", vars, id -> 0.0));
        assertEquals("250", NeuLisp.eval("(if (lt potm 2) \"200\" \"250\")", Map.of("potm", 3.0), id -> 0.0));
        assertNull(NeuLisp.eval("(unknown-function 1)", vars, id -> 0.0));
    }

    @Test
    void deeplyNestedExpressionsFailInsteadOfOverflowing() {
        Map<String, Object> vars = Map.of();
        assertEquals(1.0, NeuLisp.eval(nested(NeuLisp.MAX_DEPTH), vars, id -> 0.0));
        assertNull(NeuLisp.eval(nested(NeuLisp.MAX_DEPTH + 1), vars, id -> 0.0));
        // Deep enough to overflow the stack without the limit.
        assertNull(NeuLisp.eval(nested(200_000), vars, id -> 0.0));
    }

    /** {@code (+ (+ ... (+ 1)))}, {@code depth} lists deep. */
    private static String nested(int depth) {
        return "(+ ".repeat(depth) + "1" + ")".repeat(depth);
    }

    @Test
    void tooltipsShowTheLoreForTheLevel() {
        SkillTrees.Tree tree = SkillTrees.parse(MOLE_AND_CORE, "hotm");
        SkillTrees.Perk mole = SkillTrees.at(tree, 3, 6);
        JsonObject maxed = JsonParser.parseString("{\"mole\": 200, \"core_of_the_mountain\": 1}").getAsJsonObject();
        // As the game shows a maxed Mole: +400 Mining Spread, no cost.
        assertEquals(List.of("§aMole", "§7Level 200", "", "§7Grants §e+400 Mining Spread§7 when", "§7mining Hard Stone.", "", "§a§lENABLED"),
                SkillTrees.tooltip(tree, mole, maxed, "core_of_the_mountain", 10, true));
        // Under way: the cost of the next level in its powder.
        SkillTrees.Perk core = SkillTrees.at(tree, 3, 5);
        List<String> coreTip = SkillTrees.tooltip(tree, core, maxed, "core_of_the_mountain", 10, null);
        assertEquals("§eCore of the Mountain", coreTip.get(0));
        assertTrue(coreTip.contains("§8+§c1 Pickaxe Ability Level"));
        assertFalse(coreTip.contains("§8+§a1 Forge Slot"));
        assertEquals("§7Cost: §250,000 Mithril Powder", coreTip.get(coreTip.size() - 1));
    }
}
