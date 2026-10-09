package net.alpaka.addons.features.chat;

import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * The channels the chat can be narrowed to, and how a Hypixel line is sorted into one.
 *
 * Sorting works on the line as Hypixel sent it, with the colour codes stripped, so it does not care
 * what the mod's own formatting later does to the copy that gets drawn - the custom guild tag
 * replaces "Guild >" on screen, but the stored message still starts with it and lands on the guild
 * tab all the same.
 *
 * In three steps. A channel's own prefix decides first: {@code Party > name: msg},
 * {@code Guild > name [Rank]: msg}, {@code Officer > ...}, {@code Co-op > name: msg}, and
 * {@code From name: msg} / {@code To name: msg} for private messages, with "From stash:" being the
 * one look-alike to keep out. Then anything a player said in public goes to All, whatever it is
 * about. Only what is left - Hypixel's own notices - is matched against the shapes those notices
 * take: joins, leaves, invites, disbands, the party list, guild promotions and quests. Matching the
 * channel's name anywhere in the line, as this used to, put public chat that merely mentioned
 * "the party" or "my guild" on those tabs.
 */
public enum ChatTab {
    ALL("All"),
    PARTY("Party"),
    GUILD("Guild"),
    COOP("Co-op"),
    PRIVATE("PMs");

    public final String label;

    ChatTab(String label) {
        this.label = label;
    }

    private static final Pattern COLOR_CODES = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private static final Pattern PARTY_CHAT = Pattern.compile("^(?:Party|Party Finder) > ");
    private static final Pattern GUILD_CHAT = Pattern.compile("^(?:Guild|Officer) > ");
    private static final Pattern COOP_CHAT = Pattern.compile("^Co-op > ");
    private static final Pattern PRIVATE_CHAT = Pattern.compile("^(?!From stash: )(?:From|To) (?:\\[[^\\]]+\\] )?[^:]{1,40}: ");

    /**
     * A line a player wrote in public: an optional SkyBlock level, an optional emblem, an optional
     * rank, the name, an optional suffix tag, then a colon. Modelled on SkyHanni's generic chat
     * pattern.
     */
    private static final Pattern PLAYER_LINE = Pattern.compile(
            "^(?:\\[\\d+\\] )?(?:\\S )?(?:\\[[^\\]]+\\] )?[A-Za-z0-9_]{1,16}(?: \\[[^\\]]+\\])?: ");

    /** An optional rank and a player name, as notices write a player. */
    private static final String NAME = "(?:\\[[^\\]]+\\] )?[A-Za-z0-9_]{1,16}";

    /** Hypixel's party notices. */
    private static final Pattern PARTY_NOTICE = Pattern.compile(
            "^(?:" + NAME + " (?:joined|has left|has been removed from|has disbanded) the party"
                    + "|" + NAME + " invited " + NAME + " to the party"
                    + "|" + NAME + " has invited you to join their party"
                    + "|The party (?:was|has been) (?:disbanded|transferred)"
                    + "|The party leader"
                    + "|You (?:left|have been kicked from|have been removed from) the party"
                    + "|You have joined " + NAME + "'s? party"
                    + "|You are not (?:currently )?in a party"
                    + "|Party (?:Leader|Moderators|Members|Finder)"
                    + ")");

    /** Hypixel's guild notices. */
    private static final Pattern GUILD_NOTICE = Pattern.compile(
            "^(?:" + NAME + " (?:joined|left) the guild"
                    + "|" + NAME + " was (?:promoted|demoted) from"
                    + "|" + NAME + " was kicked from the guild"
                    + "|" + NAME + " has (?:invited you to join their|requested to join your) guild"
                    + "|The guild has (?:completed|reached|been)"
                    + "|You (?:left|have joined|have been kicked from) the guild"
                    + "|Guild (?:Name|MOTD|Level|Quest|Experience)"
                    + ")");

    /** A party invite waiting for the player: never filtered away, whatever tab is up. */
    private static final Pattern PARTY_INVITE = Pattern.compile(NAME + " has invited you to join their party");

    /** The tab a message belongs on besides All. Never returns null; a line nobody claims is ALL. */
    public static ChatTab classify(Component message) {
        return classify(strip(message.getString()));
    }

    /** {@link #classify(Component)} for colour-stripped, trimmed text. */
    public static ChatTab classify(String text) {
        if (text.isEmpty()) return ALL;
        if (PARTY_CHAT.matcher(text).find()) return PARTY;
        if (GUILD_CHAT.matcher(text).find()) return GUILD;
        if (COOP_CHAT.matcher(text).find()) return COOP;
        if (PRIVATE_CHAT.matcher(text).find()) return PRIVATE;
        if (PLAYER_LINE.matcher(text).find()) return ALL;
        if (PARTY_NOTICE.matcher(text).find()) return PARTY;
        if (GUILD_NOTICE.matcher(text).find()) return GUILD;
        return ALL;
    }

    /** Whether a line must stay visible on every tab: an invite the player has to answer. */
    public static boolean isUrgent(String text) {
        return PARTY_INVITE.matcher(text).find();
    }

    static String strip(String raw) {
        return COLOR_CODES.matcher(raw).replaceAll("").trim();
    }
}
