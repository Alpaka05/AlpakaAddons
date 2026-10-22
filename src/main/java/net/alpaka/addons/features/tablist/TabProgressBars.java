package net.alpaka.addons.features.tablist;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the progress in Hypixel's tab widgets and turns it into a bar.
 *
 * Three shapes are read:
 * <ul>
 *   <li>Hypixel's own text bar, a run of pipes colored up to the progress and gray after it
 *       ("Mage Reputation: |||||||||||| (100%)"). The pipes are replaced by the bar.</li>
 *   <li>A count against its goal at the end of a row ("Fairy Souls: 29/29", "52/100 XP", the RNG
 *       meter's "25,850/188,040"), written without spaces around the slash. The bar goes after
 *       the row. "Bank: 1B / 7.5B" is the co-op and the personal bank side by side, not progress,
 *       and is left alone.</li>
 *   <li>A percentage at the end of a row in the Skills widget ("Farming 50: 42.1%"). Only there:
 *       elsewhere a percentage is a stat ("Crit Chance: 10%"), not progress.</li>
 * </ul>
 * The bar takes the color of the text it stands for, so a pink reputation bar stays pink.
 *
 * Hypixel puts its pipe bars on a row of their own. Such a row joins the value above it, so
 * "29,110" and the bar become one row: "29,110", the bar, "(100%)". A widget's title
 * ("Mage Reputation:") always keeps its row to itself.
 */
public final class TabProgressBars {

    /** Fewer pipes than this are text, not one of Hypixel's bars. */
    static final int MIN_PIPES = 5;

    private static final Pattern COUNT = Pattern.compile(
            "([\\d,]+(?:\\.\\d+)?[kKmMbB]?)/([\\d,]+(?:\\.\\d+)?[kKmMbB]?)(?:\\s*XP)?\\s*$");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)%\\s*$");
    private static final int DEFAULT_FILL = 0xFF55FF55;
    private static final int GRAY = 0xAAAAAA;
    private static final int DARK_GRAY = 0x555555;

    private TabProgressBars() {}

    /** One character of a row with the style it is drawn in, color codes already applied. */
    private record Glyph(int codepoint, Style style) {}

    /** The categories with a bar on every row that shows progress. */
    public static List<TabListParser.Category> apply(List<TabListParser.Category> categories) {
        List<TabListParser.Category> out = new ArrayList<>(categories.size());
        for (TabListParser.Category category : categories) {
            List<List<TabListParser.Line>> sections = new ArrayList<>(category.sections().size());
            for (List<TabListParser.Line> section : category.sections()) {
                boolean skills = !section.isEmpty()
                        && TabListParser.plain(section.get(0).text()).trim().startsWith("Skills");
                List<TabListParser.Line> lines = new ArrayList<>(section.size());
                for (TabListParser.Line line : section) lines.add(line.player() != null ? line : withBar(line, skills));
                sections.add(joinBarRows(lines));
            }
            out.add(new TabListParser.Category(category.title(), sections));
        }
        return out;
    }

    /** Rows that are nothing but a bar joined to the value above them; never to a title. */
    static List<TabListParser.Line> joinBarRows(List<TabListParser.Line> lines) {
        List<TabListParser.Line> out = new ArrayList<>(lines.size());
        for (TabListParser.Line line : lines) {
            int last = out.size() - 1;
            boolean barOnly = line.bar() != null && TabListParser.plain(line.text()).isBlank();
            if (!barOnly || last < 0 || out.get(last).bar() != null || out.get(last).player() != null
                    || TabListParser.plain(out.get(last).text()).trim().endsWith(":")) {
                out.add(line);
                continue;
            }
            TabListParser.Line above = out.remove(last);
            out.add(new TabListParser.Line(above.text(), above.indented(), null, line.bar(), line.after(), null));
        }
        return out;
    }

    /** The row with its bar, or the row as it was when it shows no progress. */
    static TabListParser.Line withBar(TabListParser.Line line, boolean inSkills) {
        List<Glyph> glyphs = glyphs(line.text());

        // Hypixel's pipes: the longest run of them stands for the bar.
        int runStart = -1, runEnd = -1;
        for (int i = 0; i < glyphs.size(); ) {
            if (glyphs.get(i).codepoint() != '|') {
                i++;
                continue;
            }
            int j = i;
            while (j < glyphs.size() && glyphs.get(j).codepoint() == '|') j++;
            if (j - i > runEnd - runStart) {
                runStart = i;
                runEnd = j;
            }
            i = j;
        }
        if (runEnd - runStart >= MIN_PIPES) {
            int filled = 0;
            int fill = DEFAULT_FILL;
            boolean fillFound = false;
            for (int i = runStart; i < runEnd; i++) {
                int rgb = rgb(glyphs.get(i).style());
                if (rgb == GRAY || rgb == DARK_GRAY) continue;
                filled++;
                if (!fillFound) {
                    fill = 0xFF000000 | rgb;
                    fillFound = true;
                }
            }
            Component before = TabListParser.trim(component(glyphs.subList(0, runStart)));
            Component after = TabListParser.trim(component(glyphs.subList(runEnd, glyphs.size())));
            return line.withBar(before, new TabListParser.Bar((float) filled / (runEnd - runStart), fill), after);
        }

        String plain = TabListParser.plain(line.text());
        Float fraction = null;
        Matcher count = COUNT.matcher(plain);
        if (count.find()) {
            double done = amount(count.group(1));
            double goal = amount(count.group(2));
            if (goal > 0 && done >= 0 && done <= goal) fraction = (float) (done / goal);
        } else if (inSkills) {
            Matcher percent = PERCENT.matcher(plain);
            if (percent.find()) fraction = Math.min(1.0f, Float.parseFloat(percent.group(1)) / 100.0f);
        }
        if (fraction == null) return line;
        return line.withBar(line.text(), new TabListParser.Bar(fraction, lastDigitColor(glyphs)), Component.empty());
    }

    /** "25,850", "1.5k", "7.5B" as a number. */
    static double amount(String text) {
        String digits = text.replace(",", "");
        double factor = 1.0;
        char last = Character.toLowerCase(digits.charAt(digits.length() - 1));
        if (last == 'k' || last == 'm' || last == 'b') {
            factor = last == 'k' ? 1e3 : last == 'm' ? 1e6 : 1e9;
            digits = digits.substring(0, digits.length() - 1);
        }
        try {
            return Double.parseDouble(digits) * factor;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    /** The color of the last digit in the row, which is the number the bar stands for. */
    private static int lastDigitColor(List<Glyph> glyphs) {
        for (int i = glyphs.size() - 1; i >= 0; i--) {
            if (!Character.isDigit(glyphs.get(i).codepoint())) continue;
            int rgb = rgb(glyphs.get(i).style());
            return rgb < 0 || rgb == GRAY || rgb == DARK_GRAY || rgb == 0xFFFFFF ? DEFAULT_FILL : 0xFF000000 | rgb;
        }
        return DEFAULT_FILL;
    }

    private static int rgb(Style style) {
        TextColor color = style.getColor();
        return color == null ? -1 : color.getValue() & 0xFFFFFF;
    }

    /** Every character of the component with the style it is drawn in, § codes resolved. */
    private static List<Glyph> glyphs(Component text) {
        List<Glyph> glyphs = new ArrayList<>();
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (index, style, codepoint) -> {
            glyphs.add(new Glyph(codepoint, style));
            return true;
        });
        return glyphs;
    }

    /** Characters back into a component, one part per run of the same style. */
    private static Component component(List<Glyph> glyphs) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        for (Glyph glyph : glyphs) {
            if (runStyle != null && !glyph.style().equals(runStyle)) {
                out.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = glyph.style();
            run.appendCodePoint(glyph.codepoint());
        }
        if (runStyle != null) out.append(Component.literal(run.toString()).withStyle(runStyle));
        return out;
    }
}
