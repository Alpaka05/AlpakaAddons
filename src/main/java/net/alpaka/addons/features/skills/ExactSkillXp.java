package net.alpaka.addons.features.skills;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.profile.Levels;
import net.alpaka.addons.features.tablist.TabListParser;
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

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
 * A maxed skill has no percentage: Hypixel shows the XP gained past the cap instead, over a 0
 * ("+12.5 Farming (5,183,244/0)"). That XP is counted on as overflow levels, as the Profile
 * Dashboard does, and the way to the next one is shown with the level in front:
 * "+12.5 Farming 60 (5,183,244/7,600,000)". This needs no tab list: the "/0" already says the
 * skill is maxed, and the cap is the skill's own (CAPS), unless the tab list shows a lower one
 * ("Farming 50: MAX" before Anita's perks). This XP is exact, so the rounded format only shortens it.
 *
 * Hypixel says nothing when an overflow level is reached, so with the level-up option on the chat
 * gets Hypixel's own SKILL LEVEL UP message for it, with the level-up sound (see {@link #levelUpLines}).
 *
 * Only what is drawn changes (see HudOverlayMessageMixin): every feature that reads the action bar
 * through Fabric's message events still gets Hypixel's own text.
 */
public final class ExactSkillXp {
    private ExactSkillXp() {
    }

    private static final Optional<Boolean> FOUND = Optional.of(Boolean.TRUE);
    private static final net.minecraft.network.chat.FormattedText.ContentConsumer<Boolean> HAS_PLUS =
            part -> part.indexOf('+') >= 0 ? FOUND : Optional.empty();

    /** "+12.5 Farming (45.32%)"; group 1 the skill, group 2 the percentage without the sign. */
    private static final Pattern GAIN = Pattern.compile("\\+[\\d.,]+ ([A-Z][a-z]+) \\(([\\d.]+)%\\)");

    /** The skills Levels has a table for (Hunting uses the normal one); anything new keeps its percentage. */
    private static final Set<String> SKILLS = Set.of(
            "farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry",
            "hunting", "runecrafting", "social");

    /**
     * Each skill's highest level, from Hypixel's /v2/resources/skyblock/skills. Farming's is 60 with
     * all of Anita's perks; a lower one comes from the tab list.
     */
    static final Map<String, Integer> CAPS = Map.ofEntries(
            Map.entry("farming", 60), Map.entry("mining", 60), Map.entry("combat", 60), Map.entry("foraging", 57),
            Map.entry("fishing", 50), Map.entry("enchanting", 60), Map.entry("alchemy", 50), Map.entry("taming", 60),
            Map.entry("carpentry", 50), Map.entry("hunting", 50), Map.entry("runecrafting", 25), Map.entry("social", 25));

    /** The last level and percentage seen per skill, to see a level up before the tab list does. */
    private record Seen(int level, double percent) {
    }

    private static final Map<String, Seen> SEEN = new HashMap<>();

    /** "+12.5 Farming (5,183,244/0)" of a maxed skill; group 1 the skill, group 2 the XP past the cap. */
    private static final Pattern MAXED_GAIN = Pattern.compile("\\+[\\d.,]+ ([A-Z][a-z]+) \\(([\\d,]+(?:\\.\\d+)?)/0\\)");

    /** The overflow level last shown per skill, with the cap it was counted from. */
    private record Reached(int cap, int level) {
    }

    private static final Map<String, Reached> REACHED = new HashMap<>();

    /** The cap per skill as the tab list last showed it maxed; the widget doesn't always list every skill. */
    private static final Map<String, Integer> CAP = new HashMap<>();

    /**
     * Forgets the levels seen so far. Called on every world change and disconnect (SessionLifecycle):
     * a profile switch or a server transfer on Hypixel is not always a new login, and the next
     * profile can have lower levels.
     */
    public static void reset() {
        SEEN.clear();
        CAP.clear();
        REACHED.clear();
    }

    /** The action bar as it is drawn: with XP in place of the percentage when the feature is on. */
    public static Component rewrite(Component message) {
        if (message == null || !AlpakaConfig.instance.exactSkillXpEnabled) return message;
        // Both patterns start with a '+'; most action bars (health, mana, defense) have none, and a
        // single character cannot be split across parts, so they are passed on without being taken apart.
        if (message.visit(HAS_PLUS).isEmpty()) return message;
        List<Piece> pieces = new ArrayList<>();
        message.visit((style, text) -> {
            pieces.add(new Piece(style, text));
            return Optional.empty();
        }, Style.EMPTY);
        StringBuilder full = new StringBuilder();
        for (Piece piece : pieces) full.append(piece.text());

        Matcher maxed = MAXED_GAIN.matcher(full);
        if (maxed.find()) return rewriteMaxed(message, pieces, maxed);
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

    /** A maxed skill's gain with the overflow level and the way to the next in place of "XP/0". */
    private static Component rewriteMaxed(Component unchanged, List<Piece> pieces, Matcher m) {
        String skill = m.group(1).toLowerCase(Locale.ROOT);
        if (!SKILLS.contains(skill)) return unchanged;
        Integer cap = cap(skill, tabCap(m.group(1)));
        if (cap == null) return unchanged;
        double pastCap;
        try {
            pastCap = Double.parseDouble(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return unchanged;
        }
        Levels.Overflow over = overflow(skill, cap, pastCap);
        if (over == null) return unchanged;
        if (leveledUp(skill, cap, over.level()) && AlpakaConfig.instance.exactSkillXpLevelUpMessage) {
            announce(m.group(1), over.level() - 1, over.level());
        }
        // The level goes behind the skill's name, the way to the next in the parentheses:
        // " 60 (5,183,244/7,600,000" in place of " (5,183,244/0".
        String shown = " " + over.level() + " (" + format(over.xpCurrent(), Math.round(over.xpForNext()),
                AlpakaConfig.instance.exactSkillXpRounded);
        return replace(pieces, m.end(1), m.end(2) + 2, shown);
    }

    /**
     * Whether {@code level} is one above the overflow level shown last for this skill. Only one: no
     * single gain is worth a whole overflow level (7.6M and up), so a bigger jump is a profile
     * switch without a world change, and so is a cap that changed in between. The first gain after
     * a world change has nothing to compare with.
     */
    static boolean leveledUp(String skill, int cap, int level) {
        Reached last = REACHED.put(skill, new Reached(cap, level));
        return last != null && last.cap() == cap && level == last.level() + 1;
    }

    /** Hypixel's border above and below a level-up message. */
    private static final String BORDER = "§3§l" + "▬".repeat(64);

    /**
     * Hypixel's SKILL LEVEL UP message, line by line as Hypixel sends it, for an overflow level:
     * the border, "SKILL LEVEL UP Combat 102➜103", the border. Hypixel lists the rewards between;
     * an overflow level has none.
     */
    static List<String> levelUpLines(String skill, int from, int to) {
        return List.of(BORDER, "  §r§b§lSKILL LEVEL UP §3" + skill + " §8" + from + "➜§3" + to, BORDER);
    }

    /** Puts the level-up message in the chat and plays the level-up sound; nothing goes to the server. */
    private static void announce(String skill, int from, int to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        for (String line : levelUpLines(skill, from, to)) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f));
    }

    /**
     * The skill's cap: the tab list's, or the one it showed last since the world changed, else the
     * skill's own highest level. Null only for a skill with no known cap.
     */
    static Integer cap(String skill, Integer tabCap) {
        if (tabCap != null) CAP.put(skill, tabCap);
        Integer seen = CAP.get(skill);
        return seen != null ? seen : CAPS.get(skill);
    }

    /**
     * The overflow level XP {@code pastCap} past {@code cap} makes: counted from the skill's total,
     * as the Profile Dashboard counts it. Null for skills with no overflow rule.
     */
    static Levels.Overflow overflow(String skill, int cap, double pastCap) {
        // The record is in the single billions; far past that is no real XP, and the level steps
        // would outgrow a long before the count got there.
        if (!(pastCap >= 0) || pastCap > 1e12) return null;
        return Levels.skillOverflow(skill, Levels.skillXpToReach(skill, cap) + pastCap);
    }

    /**
     * The skill's level for this percentage. The tab list is refreshed later than the action bar and
     * can still show the old level after a level up, so the level never goes down here, and a
     * percentage that fell is the next level. Null while no level is known at all.
     *
     * A seen level is only trusted up to one above the tab list's: a level up runs at most one ahead
     * of the tab, so anything higher is left over from another profile.
     */
    static Integer level(String skill, Integer tabLevel, double percent) {
        Seen seen = SEEN.get(skill);
        if (seen != null && tabLevel != null && seen.level() > tabLevel + 1) seen = null;
        int level = Math.max(tabLevel == null ? 0 : tabLevel, seen == null ? 0 : seen.level());
        if (level == 0) return null;
        if (seen != null && percent < seen.percent() && level == seen.level()) level++;
        SEEN.put(skill, new Seen(level, percent));
        return level;
    }

    /** "1,948,760/4,300,000", or "1.95M/4.3M" rounded. */
    static String format(double current, long needed, boolean rounded) {
        if (rounded) return Coins.compact(current) + "/" + Coins.compact(needed);
        return String.format(Locale.US, "%,d/%,d", Math.round(current), needed);
    }

    /** The level the tab list's Skills widget shows for {@code skill} ("Farming"), or null. */
    private static Integer tabLevel(String skill) {
        return levelInTab(tabLines(), skill);
    }

    /** The tab list's lines as plain text; none outside a world. */
    private static List<String> tabLines() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        if (mc.player == null || mc.gui == null) return lines;
        for (PlayerInfo info : ((PlayerTabOverlayAccessor) mc.gui.hud.getTabList()).alpaka$getPlayerInfos()) {
            lines.add(TabListParser.plain(mc.gui.hud.getTabList().getNameForDisplay(info)));
        }
        return lines;
    }

    /** The cap the tab list's Skills widget shows for a maxed {@code skill} ("Farming 60: MAX"), or null. */
    private static Integer tabCap(String skill) {
        return capInTab(tabLines(), skill);
    }

    /**
     * The tab line patterns per skill, compiled once: they are needed for every skill XP action bar,
     * several a second while farming or mining. Only skills in SKILLS get here in the game.
     */
    private static final Map<String, Pattern> CAP_LINES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Pattern> LEVEL_LINES = new java.util.concurrent.ConcurrentHashMap<>();

    static Integer capInTab(List<String> lines, String skill) {
        Pattern line = CAP_LINES.computeIfAbsent(skill,
                k -> Pattern.compile("(?:^|\\s)" + Pattern.quote(k) + " (\\d{1,3}): MAX"));
        for (String text : lines) {
            Matcher m = line.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /**
     * The level in a Skills widget line: " Farming 50: 42.1%", " Mining 14: 22,922/75k", or on the
     * widget's title line when it shows one skill ("Skills: Farming 50: 42.1%"). A maxed skill
     * ("Mining 60: MAX") has no next level, so it counts as none.
     */
    static Integer levelInTab(List<String> lines, String skill) {
        Pattern line = LEVEL_LINES.computeIfAbsent(skill,
                k -> Pattern.compile("(?:^|\\s)" + Pattern.quote(k) + " (\\d{1,3}): (?!MAX)"));
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
