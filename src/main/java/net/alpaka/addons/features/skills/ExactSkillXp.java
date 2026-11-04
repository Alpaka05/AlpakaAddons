package net.alpaka.addons.features.skills;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.profile.Levels;
import net.alpaka.addons.features.tablist.TabListParser;
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows a skill gain in the action bar as XP instead of a percentage: "+12.5 Farming (45.32%)"
 * becomes "+12.5 Farming (1,948,760/4,300,000)", or "(1.95M/4.3M)" rounded.
 *
 * Hypixel only sends the percentage. The XP is worked out from it with the skill's level, read from
 * the tab list's Skills widget, and the skill XP table; without the level the action bar is left as
 * it is. The percentage has two decimals, so the last digits are an estimate, which is what the
 * rounded format is for.
 *
 * Only what is drawn changes (see HudOverlayMessageMixin): every feature that reads the action bar
 * through Fabric's message events still gets Hypixel's own text.
 */
public final class ExactSkillXp {
    private ExactSkillXp() {
    }

    /** "+12.5 Farming (45.32%)"; group 1 the skill, group 2 the percentage without the sign. */
    private static final Pattern GAIN = Pattern.compile("\\+[\\d.,]+ ([A-Z][a-z]+) \\(([\\d.]+)%\\)");

    /** The skills Levels has a table for; Hunting and anything new keep their percentage. */
    private static final Set<String> SKILLS = Set.of(
            "farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry",
            "runecrafting", "social");

    /** The last level and percentage seen per skill, to see a level up before the tab list does. */
    private record Seen(int level, double percent) {
    }

    private static final Map<String, Seen> SEEN = new HashMap<>();

    public static void register() {
        // Another server can be another profile, with other levels.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SEEN.clear());
    }

    /** The action bar as it is drawn: with XP in place of the percentage when the feature is on. */
    public static Component rewrite(Component message) {
        if (message == null || !AlpakaConfig.instance.exactSkillXpEnabled) return message;
        List<Piece> pieces = new ArrayList<>();
        message.visit((style, text) -> {
            pieces.add(new Piece(style, text));
            return Optional.empty();
        }, Style.EMPTY);
        StringBuilder full = new StringBuilder();
        for (Piece piece : pieces) full.append(piece.text());

        Matcher m = GAIN.matcher(full);
        if (!m.find()) return message;
        String skill = m.group(1).toLowerCase(Locale.ROOT);
        if (!SKILLS.contains(skill)) return message;
        double percent;
        try {
            percent = Double.parseDouble(m.group(2));
        } catch (NumberFormatException e) {
            return message;
        }
        Integer level = level(skill, tabLevel(m.group(1)), percent);
        if (level == null) return message;
        long needed = Levels.skillXpForNext(skill, level);
        if (needed <= 0) return message;
        String shown = format(needed * percent / 100.0, needed, AlpakaConfig.instance.exactSkillXpRounded);
        // The percentage and its sign, so the parentheses keep Hypixel's style.
        return replace(pieces, m.start(2), m.end(2) + 1, shown);
    }

    /**
     * The skill's level for this percentage. The tab list is refreshed later than the action bar and
     * can still show the old level after a level up, so the level never goes down here, and a
     * percentage that fell is the next level. Null while no level is known at all.
     */
    static Integer level(String skill, Integer tabLevel, double percent) {
        Seen seen = SEEN.get(skill);
        int level = Math.max(tabLevel == null ? 0 : tabLevel, seen == null ? 0 : seen.level());
        if (level == 0) return null;
        if (seen != null && percent < seen.percent() && level == seen.level()) level++;
        SEEN.put(skill, new Seen(level, percent));
        return level;
    }

    static void forget() {
        SEEN.clear();
    }

    /** "1,948,760/4,300,000", or "1.95M/4.3M" rounded. */
    static String format(double current, long needed, boolean rounded) {
        if (rounded) return Coins.compact(current) + "/" + Coins.compact(needed);
        return String.format(Locale.US, "%,d/%,d", Math.round(current), needed);
    }

    /** The level the tab list's Skills widget shows for {@code skill} ("Farming"), or null. */
    private static Integer tabLevel(String skill) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui == null) return null;
        List<String> lines = new ArrayList<>();
        for (PlayerInfo info : ((PlayerTabOverlayAccessor) mc.gui.hud.getTabList()).alpaka$getPlayerInfos()) {
            lines.add(TabListParser.plain(mc.gui.hud.getTabList().getNameForDisplay(info)));
        }
        return levelInTab(lines, skill);
    }

    /**
     * The level in a Skills widget line: " Farming 50: 42.1%", " Mining 14: 22,922/75k", or on the
     * widget's title line when it shows one skill ("Skills: Farming 50: 42.1%"). A maxed skill
     * ("Mining 60: MAX") has no next level, so it counts as none.
     */
    static Integer levelInTab(List<String> lines, String skill) {
        Pattern line = Pattern.compile("(?:^|\\s)" + Pattern.quote(skill) + " (\\d+): (?!MAX)");
        for (String text : lines) {
            Matcher m = line.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /** The action bar with {@code start} to {@code end} of its text replaced, in the style found at start. */
    static Component replace(List<Piece> pieces, int start, int end, String replacement) {
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Piece piece : pieces) {
            String text = piece.text();
            int from = pos;
            int to = pos + text.length();
            pos = to;
            if (to <= start || from >= end) {
                if (!text.isEmpty()) out.append(Component.literal(text).withStyle(piece.style()));
                continue;
            }
            StringBuilder kept = new StringBuilder();
            if (from < start) kept.append(text, 0, start - from);
            if (from <= start) kept.append(replacement);
            if (to > end) kept.append(text, end - from, text.length());
            if (!kept.isEmpty()) out.append(Component.literal(kept.toString()).withStyle(piece.style()));
        }
        return out;
    }

    record Piece(Style style, String text) {
    }
}
