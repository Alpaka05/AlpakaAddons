package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.DungeonState;
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
 * The patterns follow SkyHanni (ChatFilter, PetStoragePatterns, WatchdogHider, SackApi,
 * DungeonChatFilter), matched against the
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
        WATCHDOG,
        /** "You have 2 items stashed away!" with its "CLICK HERE to pick them up!" line. */
        STASH,
        /** "[Sacks] +11 items. (Last 7s.)", the summary of what went into or out of the sacks. */
        SACKS
    }

    /** The groups of dungeon chatter, as SkyHanni splits them; each has its own switch. */
    public enum DungeonKind {
        /** Ready-up, the countdown and the potion notices before the run starts. */
        PREPARE,
        /** Mort's lines and the class buff summary at the start. */
        START,
        AMBIENCE,
        /** Superboom, Revive Stones, Wither Essence, orbs and the fairy's lines. */
        PICKUP,
        /** The "RIGHT CLICK on a WITHER door" key reminders. */
        REMINDER,
        BLESSING,
        /** "This chest has already been searched!" and similar refusals. */
        NOT_POSSIBLE,
        DAMAGE,
        /** Class abilities, healing, tethers and cooldown lines. */
        ABILITY,
        /** Puzzle solved lines and Oruo's quiz talk. */
        PUZZLE,
        /** Essence and team bonus lines at the end of a run. */
        END
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
    /**
     * The lines of the stash reminder Hypixel repeats every few minutes. They may come one by one or
     * as one message, centered with spaces, so each line is trimmed and checked on its own.
     */
    private static final Pattern STASH_LINE = Pattern.compile(
            "^You have [\\d,]+ (?:item|material)s? stashed away!$"
                    + "|^\\(This totals [\\d,]+ types? of (?:item|material)s? stashed!\\)$"
                    + "|^>>> CLICK HERE to pick (?:them|it) up! <<<$");
    /** "[Sacks] +11 items. (Last 7s.)", "[Sacks] +2,111 items, -4 items. (Last 30s.)". */
    private static final Pattern SACKS = Pattern.compile(
            "^\\[Sacks\\] [+-][\\d,]+ items?(?:, -[\\d,]+ items?)?\\. \\(Last [\\d,]+s\\.\\)$");

    /**
     * A player's name, maybe with a rank in front. Free parts at the start of a dungeon pattern are
     * written without colons ({@code [^:]+}), since a player typing the same words has a colon after
     * their name.
     */
    private static final String PLAYER = "(?:\\[[^\\]]+\\] )?[A-Za-z0-9_]{1,16}";

    /** Dungeon lines in the order they are checked; the first group that matches wins. */
    private static final java.util.List<java.util.Map.Entry<DungeonKind, Pattern>> DUNGEON = java.util.List.of(
            dungeon(DungeonKind.PREPARE,
                    "^[^:]+ has started the dungeon countdown\\. The dungeon will begin in 1 minute\\.$"
                            + "|^\\[NPC\\] Mort: Talk to me to change your class and ready up\\.$"
                            + "|^" + PLAYER + " is now ready!$"
                            + "|^Dungeon starts in \\d+ seconds?\\.$"
                            + "|^Your active Potion Effects have been paused and stored\\. They will be restored when you leave Dungeons! You are not allowed to use existing Potion Effects while in Dungeons\\.$"
                            + "|^You can no longer consume or splash any potions during the remainder of this Dungeon run!$"),
            dungeon(DungeonKind.START,
                    "^\\[NPC\\] Mort: (?:Here, I found this map when I first entered the dungeon\\.|You should find it useful if you get lost\\.|Good luck\\.)$"
                            + "|^\\[(?:Archer|Berserk|Healer|Mage|Tank)\\] [^:]+ \\S+ -> \\S+$"),
            dungeon(DungeonKind.AMBIENCE, "^A shiver runs down your spine\\.\\.\\.$"),
            dungeon(DungeonKind.PICKUP,
                    "^" + PLAYER + " has obtained (?:Superboom TNT(?: x2)?|Revive Stone|Premium Flesh|Beating Heart)!$"
                            + "|^RARE DROP! (?:Hunk of Blue Ice|Beating Heart) \\(.+\\)$"
                            + "|^(?:You|" + PLAYER + ") found a Wither Essence! Everyone gains an extra essence!$"
                            + "|^[^:]+ the Fairy: (?:You killed me! (?:Take this Revive Stone|I'll revive you|I'll revive your friend .+) so that my death is not in vain!|Have a great life!)$"
                            + "|^\\S+ You picked up an? .+ Orb from .+ healing you for .+ and granting you \\+.+ for \\d+ seconds\\.$"),
            dungeon(DungeonKind.REMINDER,
                    "^RIGHT CLICK on (?:a WITHER door|the BLOOD DOOR) to open it\\. This key can only be used to open 1 door!$"),
            dungeon(DungeonKind.BLESSING,
                    "^DUNGEON BUFF! .+$"
                            + "|^A Blessing of .+ was picked up!$"
                            + "|^" + PLAYER + " has obtained Blessing of .+!$"
                            + "|^ +(?:Also granted|Granted|Grants) you .+\\.$"
                            + "|^BUFF! You have gained Healing V!$"),
            dungeon(DungeonKind.NOT_POSSIBLE,
                    "^(?:You cannot hit the silverfish while it's moving!"
                            + "|You cannot move the silverfish in that direction!"
                            + "|There are blocks in the way!"
                            + "|This chest has already been searched!"
                            + "|This lever has already been used\\."
                            + "|You cannot do that in this room!"
                            + "|You do not have the key for this door!"
                            + "|You have already opened this dungeon chest!"
                            + "|You cannot use abilities in this room!"
                            + "|A mystical force in this room prevents you from using that ability!)$"),
            // Before DAMAGE: "Your Implosion hit 3 enemies for ..." is an ability's line, not damage taken.
            dungeon(DungeonKind.ABILITY,
                    "^Your [^:]+ hit [\\d,]+ enem(?:y|ies) for [\\d,.]+ damage\\.$"
                            + "|^BUFF! You were splashed by .+ with Healing VIII!$"
                            + "|^You were healed for .+ health by .+(?:!|'s Healing Bow and gained \\+.+ Strength for 10 seconds\\.)$"
                            + "|^You gained .+ HP worth of absorption for 3s from .+!$"
                            + "|^[^:]+ picked up your .+ Orb!$"
                            + "|^This ability is on cooldown for .+s\\.$"
                            + "|^[^:]+ healed you for .+ health!$"
                            + "|^Your bone plating reduced the damage you took by .+!$"
                            + "|^[^:]+ formed a tether with you!$"
                            + "|^Your tether with .+ healed you for .+ health\\.$"
                            + "|^Your Spirit Pet (?:healed .+ for .+ health!|hit .+ enem(?:y|ies) for .+ damage\\.)$"
                            + "|^You need at least .+ mana to activate this!$"
                            + "|^[^:]+ granted you .+ strength for 20 seconds!$"
                            + "|^Your fairy healed .+ for .+ health!$"
                            + "|^[^:]+ fairy healed you for .+ health!$"
                            + "|^Guided Sheep is now available!$"
                            + "|^Creeper Veil (?:Activated|De-activated)!$"
                            + "|^(?:Rapid Fire|Castle of Stone|Ragnarok|Thunderstorm) is ready to use! Press DROP to activate it!$"),
            dungeon(DungeonKind.DAMAGE,
                    "^[^:]+ used .+ on you!$"
                            + "|^[^:]+ (?:struck|hit) you for .+ damage[.!]$"
                            + "|^[^:]+ exploded, hitting you for .+ damage\\.$"
                            + "|^[^:]+ hit you with .+ for .+ damage!$"
                            + "|^[^:]+ struck you for .+!$"
                            + "|^The Mage's Magma burnt you for .+ true damage\\.$"
                            + "|^Mute silenced you!$"),
            dungeon(DungeonKind.PUZZLE,
                    "^PUZZLE SOLVED! [^:]+ (?:wasn't fooled by .+|tied Tic Tac Toe)! Good job!$"
                            + "|^\\[STATUE\\] Oruo the Omniscient: (?:.+ thinks the answer is .+! Lock in your party's answer in my Chamber!"
                            + "|Though I sit stationary in this prison that is The Catacombs, my knowledge knows no bounds\\."
                            + "|Prove your knowledge by answering 3 questions and I shall reward you in ways that transcend time!"
                            + "|Answer incorrectly, and your moment of ineptitude will live on for generations\\."
                            + "|2 questions left\\.\\.\\. Then you will have proven your worth to me!"
                            + "|One more question!"
                            + "|I bestow upon you all the power of a hundred years!"
                            + "|You've already proven enough to me! No need to press more of my buttons!"
                            + "|I've had enough of you and your party fiddling with my buttons\\. Scram!"
                            + "|Enough! My buttons are not to be pressed with such lack of grace!)$"),
            dungeon(DungeonKind.END,
                    "^[^:]+ unlocked .+ Essence x[\\d,]+!$"
                            + "|^ {4}.+ Essence x[\\d,]+$"
                            + "|^[^:]+ Experience \\(Team Bonus\\)$"));

    private static java.util.Map.Entry<DungeonKind, Pattern> dungeon(DungeonKind kind, String regex) {
        return java.util.Map.entry(kind, Pattern.compile(regex));
    }

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
        // Signed player chat, which Hypixel doesn't use but other servers do; only the player's own
        // filters can be about such a message.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                !AlpakaConfig.instance.messageFilterEnabled
                        || !matchesCustom(SkyblockUtils.cleanColor(message.getString()), AlpakaConfig.instance.messageFilterCustom));
    }

    private static boolean shouldHide(Component message) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.messageFilterEnabled) return false;
        String text = SkyblockUtils.cleanColor(message.getString());
        Kind kind = next(text);
        if (kind == null) {
            DungeonKind dungeonKind = DungeonState.inDungeon() ? classifyDungeon(text) : null;
            return (dungeonKind != null && switchFor(dungeonKind, config))
                    || matchesCustom(text, config.messageFilterCustom);
        }

        long now = System.currentTimeMillis();
        if (kind == Kind.REWARD_BUNDLE) lastRewardReminderMs = now;
        return isHidden(kind, switchFor(kind, config), now - lastRewardReminderMs)
                || matchesCustom(text, config.messageFilterCustom);
    }

    static boolean matchesCustom(String text, java.util.List<CustomMessageFilter> filters) {
        if (filters == null) return false;
        for (CustomMessageFilter filter : filters) {
            if (filter != null && filter.matches(text)) return true;
        }
        return false;
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
        if (isStashReminder(text)) return Kind.STASH;
        if (SACKS.matcher(text).matches()) return Kind.SACKS;
        return null;
    }

    /** Which group of dungeon chatter a color-stripped line is, or null for anything else. */
    static DungeonKind classifyDungeon(String text) {
        for (java.util.Map.Entry<DungeonKind, Pattern> entry : DUNGEON) {
            if (entry.getValue().matcher(text).matches()) return entry.getKey();
        }
        return null;
    }

    private static boolean isStashReminder(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !STASH_LINE.matcher(trimmed).matches()) return false;
        }
        return true;
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
            case STASH -> config.messageFilterStash;
            case SACKS -> config.messageFilterSacks;
        };
    }

    private static boolean switchFor(DungeonKind kind, AlpakaConfig config) {
        return switch (kind) {
            case PREPARE -> config.messageFilterDungeonPrepare;
            case START -> config.messageFilterDungeonStart;
            case AMBIENCE -> config.messageFilterDungeonAmbience;
            case PICKUP -> config.messageFilterDungeonPickup;
            case REMINDER -> config.messageFilterDungeonReminder;
            case BLESSING -> config.messageFilterDungeonBlessing;
            case NOT_POSSIBLE -> config.messageFilterDungeonNotPossible;
            case DAMAGE -> config.messageFilterDungeonDamage;
            case ABILITY -> config.messageFilterDungeonAbility;
            case PUZZLE -> config.messageFilterDungeonPuzzle;
            case END -> config.messageFilterDungeonEnd;
        };
    }
}
