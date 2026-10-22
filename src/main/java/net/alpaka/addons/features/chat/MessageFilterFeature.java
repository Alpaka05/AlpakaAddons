package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Hides Hypixel's chat spam, one switch per kind of message.
 *
 * Hiding works by answering Fabric's {@code ALLOW_GAME} with no, so the line never reaches the chat.
 * Fabric then fires {@code GAME_CANCELED} instead of {@code GAME}; the mod's own listeners that need
 * one of these lines (the profile name for the slayer record) listen to both, so hiding the profile
 * lines does not cost them anything.
 *
 * The patterns follow SkyHanni (ChatFilter, PetStoragePatterns, WatchdogHider), matched against the
 * color-stripped line and anchored at both ends: a player typing the same words has their name and a
 * colon in front and is never hidden.
 * Action bar text is left alone.
 */
public final class MessageFilterFeature {

    private MessageFilterFeature() {
    }

    /** The kinds of message the filter can hide; each has its own switch. */
    public enum Kind {
        EMPTY,
        WELCOME,
        GUILD_EXP,
        KILL_COMBO,
        PROFILE_JOIN,
        REWARD_BUNDLE,
        /**
         * "Talk to the Summer Sloth in the Hub!" - only hidden right after a {@link #REWARD_BUNDLE}
         * line, since quests and NPCs use the same wording for things worth reading.
         */
        REWARD_BUNDLE_HINT,
        AUTOPET,
        FIRE_SALE,
        /** The weekly "[WATCHDOG ANNOUNCEMENT]" ban count, and the lines that come with it. */
        WATCHDOG
    }

    private static final Pattern WELCOME = Pattern.compile("^Welcome to Hypixel SkyBlock!$");
    /** "You earned 2 GEXP + 210 Event EXP from playing SkyBlock!", either part may be missing. */
    private static final Pattern GUILD_EXP = Pattern.compile(
            "^You earned [\\d,]+ (?:GEXP|Event EXP) (?:\\+ [\\d,]+ Event EXP )?from playing [A-Za-z ]+!$");
    /** "+175 Kill Combo", "+5 Kill Combo +3% ✯ Magic Find" and the expiry line. */
    private static final Pattern KILL_COMBO = Pattern.compile(
            "^\\+[\\d,]+ Kill Combo(?: .*)?$|^Your Kill Combo has expired! You reached a [\\d,]+ Kill Combo!$");
    private static final Pattern PROFILE_JOIN = Pattern.compile("^You are playing on profile: .+$|^Profile ID: [0-9a-fA-F-]+$");
    /** "You haven't claimed your Summer Rewards yet!" */
    private static final Pattern REWARD_BUNDLE = Pattern.compile("^You haven't claimed your [A-Za-z ]+ Rewards yet!$");
    private static final Pattern REWARD_BUNDLE_HINT = Pattern.compile("^Talk to the .+ in the .+!$");

    /** "Autopet equipped your [Lvl 100] Mosquito! VIEW RULE", sometimes with a "(2)" count. */
    private static final Pattern AUTOPET = Pattern.compile("^Autopet equipped your \\[Lvl \\d+\\] .+! VIEW RULE(?: \\(\\d+\\))?$");
    /**
     * The Community Shop's fire sale lines, all starting with the hot springs sign: the announcement
     * (which can arrive as one message with the items below it), the items and the reminders.
     */
    private static final Pattern FIRE_SALE = Pattern.compile(
            "^A FIRE SALE A(?:\\n.*)*$"
                    + "|^♨ (?:A )?Fire Sales? for .+ (?:are|is) starting soon!$"
                    + "|^♨ Fire Sales? for .+ ended!$"
                    + "|^♨ .+ (?:Skin|Rune|Dye) (?:for a limited time )?\\(.+ left\\)!?$"
                    + "|^♨ Visit the Community Shop in the next .+ to grab yours!(?: \\[WARP\\])?$"
                    + "|^♨ And \\d+ more!$"
                    + "|^♨ Selling multiple items for a limited time!$");
    private static final String WATCHDOG_HEADER = "[WATCHDOG ANNOUNCEMENT]";
    /**
     * Hypixel sends the announcement line by line and closes it with a blank line. The lines after
     * the header count as part of it up to that blank line, but never more than this many, so a
     * missing blank line cannot swallow the chat.
     */
    static final int WATCHDOG_MAX_LINES = 10;

    /** How long after a reward reminder its "Talk to the..." line still counts as part of it. */
    static final long HINT_WINDOW_MS = 3_000L;

    private static long lastRewardReminderMs = Long.MIN_VALUE / 2;
    private static int watchdogLinesLeft = 0;

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !shouldHide(message));
    }

    private static boolean shouldHide(Component message) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.messageFilterEnabled) return false;
        Kind kind = next(SkyblockUtils.cleanColor(message.getString()));
        if (kind == null) return false;

        long now = System.currentTimeMillis();
        if (kind == Kind.REWARD_BUNDLE) lastRewardReminderMs = now;
        return isHidden(kind, switchFor(kind, config), now - lastRewardReminderMs);
    }

    /** Like {@link #classify}, but also counts the lines that follow a Watchdog header as part of it. */
    static Kind next(String text) {
        if (watchdogLinesLeft > 0) {
            watchdogLinesLeft = text.isBlank() ? 0 : watchdogLinesLeft - 1;
            return Kind.WATCHDOG;
        }
        // A header with the rest of the announcement in the same message needs no follow-up lines.
        if (text.equals(WATCHDOG_HEADER)) watchdogLinesLeft = WATCHDOG_MAX_LINES;
        return classify(text);
    }

    static void resetWatchdog() {
        watchdogLinesLeft = 0;
    }

    /** Which kind of spam a color-stripped line is, or null for anything else. */
    static Kind classify(String text) {
        if (text.isBlank()) return Kind.EMPTY;
        if (WELCOME.matcher(text).matches()) return Kind.WELCOME;
        if (GUILD_EXP.matcher(text).matches()) return Kind.GUILD_EXP;
        if (KILL_COMBO.matcher(text).matches()) return Kind.KILL_COMBO;
        if (PROFILE_JOIN.matcher(text).matches()) return Kind.PROFILE_JOIN;
        if (REWARD_BUNDLE.matcher(text).matches()) return Kind.REWARD_BUNDLE;
        if (REWARD_BUNDLE_HINT.matcher(text).matches()) return Kind.REWARD_BUNDLE_HINT;
        if (AUTOPET.matcher(text).matches()) return Kind.AUTOPET;
        if (FIRE_SALE.matcher(text).matches()) return Kind.FIRE_SALE;
        if (text.startsWith(WATCHDOG_HEADER)) return Kind.WATCHDOG;
        return null;
    }

    /** Whether a line of this kind is hidden, given its switch and the time since the last reward reminder. */
    static boolean isHidden(Kind kind, boolean switchOn, long msSinceRewardReminder) {
        if (!switchOn) return false;
        return kind != Kind.REWARD_BUNDLE_HINT
                || (msSinceRewardReminder >= 0 && msSinceRewardReminder <= HINT_WINDOW_MS);
    }

    private static boolean switchFor(Kind kind, AlpakaConfig config) {
        return switch (kind) {
            case EMPTY -> config.messageFilterEmpty;
            case WELCOME -> config.messageFilterWelcome;
            case GUILD_EXP -> config.messageFilterGuildExp;
            case KILL_COMBO -> config.messageFilterKillCombo;
            case PROFILE_JOIN -> config.messageFilterProfileJoin;
            case REWARD_BUNDLE, REWARD_BUNDLE_HINT -> config.messageFilterRewardBundles;
            case AUTOPET -> config.messageFilterAutopet;
            case FIRE_SALE -> config.messageFilterFireSale;
            case WATCHDOG -> config.messageFilterWatchdog;
        };
    }
}
