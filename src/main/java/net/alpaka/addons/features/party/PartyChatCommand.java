package net.alpaka.addons.features.party;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A "!command" a party member wrote in party chat, read from the color-stripped line.
 *
 * Hypixel writes party chat as {@code Party > [RANK] Name: message}; a player without a rank has
 * no bracket, and Bingo and Ironman players carry a symbol between the name and the colon. Only the
 * line's own prefix counts: a player writing "Party > X: !warp" into all chat has their own name in
 * front and does not match.
 */
public record PartyChatCommand(String sender, String name, String args) {

    private static final Pattern LINE = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )?(?<sender>[A-Za-z0-9_]{1,16})(?: [ቾ⚒])?: !(?<name>[A-Za-z0-9]{1,16})(?: +(?<args>.*))?$");

    /** The command in a party chat line, or null when the line is none. */
    public static PartyChatCommand parse(String line) {
        if (line == null) return null;
        Matcher matcher = LINE.matcher(line.trim());
        if (!matcher.matches()) return null;
        String args = matcher.group("args");
        return new PartyChatCommand(matcher.group("sender"),
                matcher.group("name").toLowerCase(Locale.ROOT),
                args == null ? "" : args.trim());
    }

    /** The first word of the arguments, or an empty string. */
    public String firstArg() {
        int space = args.indexOf(' ');
        return space < 0 ? args : args.substring(0, space);
    }

    /** The player named in the arguments, or the sender when none is: "!pt" means "to me". */
    public String targetOrSender() {
        String target = firstArg();
        return PartyCommand.isName(target) ? target : sender;
    }
}
