package net.alpaka.addons.features.itemsearch;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiPageTest {

    private static String dump(List<WikiPage.Block> blocks) {
        StringBuilder out = new StringBuilder();
        for (WikiPage.Block b : blocks) {
            out.append(b.kind()).append(':');
            if (b.text() != null) out.append(b.text().getString().trim());
            if (b.value() != null) out.append('=').append(b.value().getString().trim());
            for (Component c : b.cells()) out.append('|').append(c.getString().trim());
            out.append('\n');
        }
        return out.toString();
    }

    @Test
    void reads_a_page_into_blocks() {
        String html = """
                <div class="mw-parser-output"><div class="infobox"><div class="infobox-title">Hyperion</div>
                <div class="infobox-row-container"><div class="infobox-row-label">Rarity</div><div class="infobox-row-value">
                <span class="color-gold">LEGENDARY</span></div></div>
                <div class="infobox-header">Auction House Prices</div>
                <div class="infobox-row-container"><div class="infobox-row-label">Lowest BIN</div><div class="infobox-row-value">??? Coins</div></div>
                </div>
                <div id="toc" class="toc"><h2 id="mw-toc-heading">Contents</h2><ul><li>Obtaining</li></ul></div>
                <p>The <b>Hyperion</b> is a <a href="/w/Sword" title="Sword">Sword</a>&#160;&amp; more&#91;1&#93;.</p>
                <div class="mw-heading mw-heading2"><h2 id="Obtaining">Obtaining</h2></div>
                <table class="wikitable"><tbody><tr><th>Requirement</th><th>Ingredients</th><th>Recipe</th></tr>
                <tr><td>None</td><td><ul><li>8x Eye</li><li>Blade</li></ul></td><td><div class="mcui">grid</div></td></tr></tbody></table>
                <h3>Tips</h3><ul><li>One<ul><li>Two</li></ul></li></ul>
                <h2>Navigation</h2><div class="navbox">lots of links</div></div>
                """;
        List<WikiPage.Block> blocks = WikiPage.parse(html);
        assertEquals("""
                FACT:Rarity=LEGENDARY
                PARAGRAPH:The Hyperion is a Sword & more[1].
                HEADING:Obtaining
                HEADER_ROW:|Requirement|Ingredients|Recipe
                ROW:|None|8x Eye, Blade|
                SUBHEADING:Tips
                BULLET:One
                BULLET:Two
                """, dump(blocks));
        // The second bullet is one level in; the link keeps where it goes, the rarity its color.
        assertEquals(1, blocks.get(7).depth());
        List<Style> styles = new ArrayList<>();
        blocks.get(1).text().visit((style, text) -> {
            if (text.contains("Sword")) styles.add(style);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        assertEquals(WikiPage.LINK + "Sword", styles.get(0).getInsertion());
        assertTrue(blocks.get(0).value().getString().contains("LEGENDARY"));
    }

    @Test
    void finds_titles_in_links() {
        assertEquals("L.A.S.R.'s Eye", WikiPage.titleOf("https://hypixelskyblock.minecraft.wiki/w/L.A.S.R.%27s_Eye#Usage"));
        assertEquals(null, WikiPage.titleOf("https://hypixel-skyblock.fandom.com/wiki/Hyperion"));
        assertEquals("https://hypixelskyblock.minecraft.wiki/w/Enchanted_Diamond", WikiPage.url("Enchanted Diamond"));
    }
}
