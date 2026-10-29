package net.alpaka.addons.features.tablist;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabListParserTest {

    /** One Hypixel column: the title, the rows, and blank slots up to 20. */
    private static List<Component> column(String title, String... rows) {
        List<Component> slots = new ArrayList<>();
        slots.add(Component.literal(title));
        for (String row : rows) slots.add(Component.literal(row));
        while (slots.size() < TabListParser.COLUMN_SLOTS) slots.add(Component.literal(""));
        return slots;
    }

    @Test
    void columnsBecomeCategoriesAndRepeatedTitlesMerge() {
        List<Component> slots = new ArrayList<>();
        slots.addAll(column("§b§lPlayers §r§f(3)", "§r[312] §6Alpaka ♲", "[10] Bob"));
        slots.addAll(column("§b§lPlayers §r§f(3)", "[200] [MVP+] Carl"));
        slots.addAll(column("     §e§lInfo", "§b§lArea: §r§7Hub", " §7Server: §8mini12A", "", "§e§lSkills:", " Combat 50"));

        List<TabListParser.Category> categories = TabListParser.parse(slots);
        assertEquals(2, categories.size());

        TabListParser.Category players = categories.get(0);
        assertEquals("Players (3)", TabListParser.plain(players.title()));
        assertEquals(3, players.lineCount());
        assertEquals("Alpaka", players.sections().get(0).get(0).player());
        assertEquals("Carl", players.sections().get(1).get(0).player());

        TabListParser.Category info = categories.get(1);
        assertEquals("Info", TabListParser.plain(info.title()));
        assertEquals(2, info.sections().size());
        TabListParser.Line server = info.sections().get(0).get(1);
        assertTrue(server.indented());
        assertNull(server.player());
        assertEquals("Server: mini12A", TabListParser.plain(server.text()));
    }

    @Test
    void emptyColumnsAreDropped() {
        List<Component> slots = new ArrayList<>(column("Players (1)", "[1] Solo"));
        slots.addAll(column("     "));
        assertEquals(1, TabListParser.parse(slots).size());
    }

    @Test
    void trimKeepsColorCodesAndStyles() {
        assertEquals("§7Server", TabListParser.trim(Component.literal("§7 Server  §r")).getString());
        Component styled = Component.literal("  ").append(Component.literal("Hub "));
        assertEquals("Hub", TabListParser.trim(styled).getString());
    }

    @Test
    void advertsAreFilteredFromHeaderAndFooter() {
        Component footer = Component.literal("\n§a§lActive Effects\n§7No effects active.\n\n\n§d§lCookie Buff\n§7Not active!\n\n§aRanks, Boosters & MORE! §c§lSTORE.HYPIXEL.NET");
        List<Component> lines = TabListParser.headerFooterLines(footer, true);
        List<String> plain = lines.stream().map(TabListParser::plain).toList();
        assertEquals(List.of("Active Effects", "No effects active.", "", "Cookie Buff", "Not active!"), plain);

        List<Component> withAds = TabListParser.headerFooterLines(footer, false);
        assertTrue(TabListParser.isAdvert(withAds.get(withAds.size() - 1)));

        Component header = Component.literal("§bYou are playing on §e§lMC.HYPIXEL.NET\n");
        assertTrue(TabListParser.headerFooterLines(header, true).isEmpty());
        assertFalse(TabListParser.headerFooterLines(header, false).isEmpty());
    }

    @Test
    void slideComesOutAfterTheFirstFrame() {
        long frame = 16_000_000L;
        long now = 1_000_000_000L;
        while (CustomTabFeature.advanceSlide(false, true, now)) now += frame;
        now += 5_000_000_000L;
        // The first frame of opening has no time behind it yet; the next one must move the list out.
        assertFalse(CustomTabFeature.advanceSlide(true, true, now));
        assertTrue(CustomTabFeature.advanceSlide(true, true, now + frame));
        // Closed again, it goes away within the slide's length.
        now += frame;
        for (int i = 0; i < 20; i++) CustomTabFeature.advanceSlide(false, true, now += frame);
        assertFalse(CustomTabFeature.advanceSlide(false, true, now + frame));
    }

    @Test
    void scaleIsRoundedToWholeScreenPixels() {
        assertEquals(0.75f, CustomTabFeature.crisp(0.87f, 4.0f, true));
        assertEquals(1.0f, CustomTabFeature.crisp(1.0f, 3.0f, true));
        assertEquals(1.0f, CustomTabFeature.crisp(1.1f, 2.0f, false));
        assertEquals(1.5f, CustomTabFeature.crisp(1.3f, 2.0f, false));
        // Under one screen pixel per text pixel nothing whole is left to round to.
        assertEquals(0.8f, CustomTabFeature.crisp(0.8f, 1.0f, true));
    }

    @Test
    void eachWidgetGetsABoxOfItsOwn() {
        List<Component> slots = new ArrayList<>();
        slots.addAll(column("Info", "Area: Hub", " Server: x", "", "Profile: Peach", " Bank: 1B", "", "Pet:", " Wisp"));
        List<TabListParser.Category> categories = TabListParser.parse(slots);

        CustomTabLayout plain = CustomTabLayout.build(List.of(), categories, List.of(), false, 1000, 300, false, c -> 50);
        assertTrue(plain.boxes.isEmpty());

        CustomTabLayout boxed = CustomTabLayout.build(List.of(), categories, List.of(), false, 1000, 300, false, true, c -> 50);
        assertEquals(3, boxed.boxes.size());
        for (int i = 1; i < boxed.boxes.size(); i++) {
            assertTrue(boxed.boxes.get(i).y0() > boxed.boxes.get(i - 1).y1(), "boxes overlap");
        }
        assertTrue(boxed.height > plain.height);
    }

    @Test
    void layoutStacksOrSpreadsCategories() {
        List<Component> slots = new ArrayList<>();
        slots.addAll(column("Players (2)", "[1] A", "[2] B"));
        slots.addAll(column("Info", "Area: Hub", " Server: x"));
        List<TabListParser.Category> categories = TabListParser.parse(slots);

        CustomTabLayout wide = CustomTabLayout.build(List.of(), categories, List.of(), false, 1000, 300, false, c -> 50);
        CustomTabLayout tall = CustomTabLayout.build(List.of(), categories, List.of(), true, 1000, 300, false, c -> 50);
        assertTrue(wide.width > tall.width);
        assertTrue(tall.height > wide.height);
    }
}
