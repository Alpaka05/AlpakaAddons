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
 * The chat formats follow the patterns in the SkyHanni PlayerChatManager:
 * {@code §9Party §8> name§f: msg}, {@code §2Guild > name §e[Rank]§f: msg}, {@code §3Officer > ...},
 * and {@code From name: msg} / {@code To name: msg} for private messages, with "From stash:" being
 * the one look-alike to keep out. The system lines around them - joins, leaves, invites, the party
 * list - have no marker, so those are caught by the channel's name in a phrase that only Hypixel's
 * own notices use.
 */
public enum ChatTab {
    ALL("All"),
    PARTY("Party"),
    GUILD("Guild"),
    PRIVATE("PMs");

    public final String label;

    ChatTab(String label) {
        this.label = label;
    }

    private static final Pattern COLOR_CODES = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private static final Pattern PARTY_CHAT = Pattern.compile("^(?:Party|Party Finder) > ");
    private static final Pattern GUILD_CHAT = Pattern.compile("^(?:Guild|Officer) > ");
    private static final Pattern PRIVATE_CHAT = Pattern.compile("^(?!From stash: )(?:From|To) (?:\\[[^\\]]+\\] )?[^:]{1,40}: ");

    /** Hypixel's party notices: joins, leaves, disbands, invites, the /pl listing, party finder. */
    private static final Pattern PARTY_SYSTEM = Pattern.compile(
            "(?i)\\b(?:the party|your party|party leader|party members?|party moderators?|party finder"
                    + "|party invite|party warp|party chat|in a party|party!|party is now)");

    /** Guild notices: member joins and leaves, promotions, quests, the guild bank and MOTD. */
    private static final Pattern GUILD_SYSTEM = Pattern.compile(
            "(?i)\\b(?:the guild|your guild|guild!|guild quest|guild bank|guild level|guild experience"
                    + "|guild rank|guild motd|guild members?|guild tag|guild chat|in a guild|guild is now)");

    /** The tab a message belongs on besides All. Never returns null; a line nobody claims is ALL. */
    public static ChatTab classify(Component message) {
        String text = COLOR_CODES.matcher(message.getString()).replaceAll("").trim();
        if (text.isEmpty()) return ALL;
        if (PARTY_CHAT.matcher(text).find()) return PARTY;
        if (GUILD_CHAT.matcher(text).find()) return GUILD;
        if (PRIVATE_CHAT.matcher(text).find()) return PRIVATE;
        if (PARTY_SYSTEM.matcher(text).find()) return PARTY;
        if (GUILD_SYSTEM.matcher(text).find()) return GUILD;
        return ALL;
    }
}
