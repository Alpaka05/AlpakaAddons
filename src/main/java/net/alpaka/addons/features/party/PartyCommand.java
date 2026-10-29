package net.alpaka.addons.features.party;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * The "!commands" party members can ask for, each with its aliases and the command it answers
 * with. The answer is worked out when the player presses Y, not when the line comes in, so a TPS or
 * a position is the one of that moment.
 *
 * The set follows Odin's chat commands: the leader's chores (warp, transfer, all invite, kick...),
 * joining a dungeon or Kuudra run, a few measurements for the party chat, and some dice for fun.
 */
public enum PartyCommand {
    WARP(Group.LEADER, "Warp", List.of("warp", "w", "pw", "warpus"), c -> "party warp"),
    TRANSFER(Group.LEADER, "Transfer", List.of("pt", "ptme", "transfer"),
            c -> "party transfer " + c.targetOrSender()),
    ALL_INVITE(Group.LEADER, "All Invite", List.of("allinvite", "allinv", "allinvites"),
            c -> "party settings allinvite"),
    INVITE(Group.LEADER, "Invite", List.of("invite", "inv"),
            c -> isName(c.firstArg()) ? "party invite " + c.firstArg() : null),
    KICK(Group.LEADER, "Kick", List.of("kick", "k"),
            c -> isName(c.firstArg()) ? "party kick " + c.firstArg() : null),
    KICK_OFFLINE(Group.LEADER, "Kick Offline", List.of("kickoffline", "ko"), c -> "party kickoffline"),
    PROMOTE(Group.LEADER, "Promote", List.of("promote"), c -> "party promote " + c.targetOrSender()),
    DEMOTE(Group.LEADER, "Demote", List.of("demote"), c -> "party demote " + c.targetOrSender()),
    JOIN_INSTANCE(Group.LEADER, "Join Floor/Tier (!f7, !m3, !t5)", List.of(), c -> {
        String instance = instanceFor(c.name());
        return instance == null ? null : "joininstance " + instance;
    }),
    TPS(Group.INFO, "TPS", List.of("tps"), c -> "pc TPS: " + Measurements.tps()),
    PING(Group.INFO, "Ping", List.of("ping"), c -> "pc Ping: " + Measurements.ping()),
    FPS(Group.INFO, "FPS", List.of("fps"), c -> "pc FPS: " + Measurements.fps()),
    COORDS(Group.INFO, "Coords", List.of("coords", "coord", "co"), c -> "pc " + Measurements.coords()),
    LOCATION(Group.INFO, "Location", List.of("location", "loc"), c -> "pc Location: " + Measurements.location()),
    HOLDING(Group.INFO, "Holding", List.of("holding"), c -> "pc Holding: " + Measurements.holding()),
    TIME(Group.INFO, "Time", List.of("time"), c -> "pc Time: " + Measurements.time()),
    HELP(Group.INFO, "Help", List.of("help", "h"), c -> "pc Commands: " + PartyCommandsFeature.enabledList()),
    DICE(Group.FUN, "Dice", List.of("dice", "roll"), c -> {
        int sides = rollSides(c.firstArg());
        return "pc " + c.sender() + " rolled " + Measurements.random(sides) + " (1-" + sides + ")";
    }),
    COIN_FLIP(Group.FUN, "Coin Flip", List.of("cf", "coinflip", "flip"),
            c -> "pc " + (Measurements.random(2) == 1 ? "Heads" : "Tails")),
    EIGHT_BALL(Group.FUN, "8-Ball", List.of("8ball", "eightball"),
            c -> "pc " + eightBall());

    /** What a command is for; the config lists them in these groups. */
    public enum Group { LEADER, INFO, FUN }

    private static final List<String> EIGHT_BALL_ANSWERS = List.of(
            "It is certain.", "It is decidedly so.", "Without a doubt.", "Yes, definitely.",
            "You may rely on it.", "As I see it, yes.", "Most likely.", "Outlook good.", "Yes.",
            "Signs point to yes.", "Reply hazy, try again.", "Ask again later.", "Better not tell you now.",
            "Cannot predict now.", "Concentrate and ask again.", "Don't count on it.", "My reply is no.",
            "My sources say no.", "Outlook not so good.", "Very doubtful.");

    private static final String[] FLOORS = {"one", "two", "three", "four", "five", "six", "seven"};
    private static final Map<Character, String> KUUDRA_TIERS = Map.of(
            '1', "normal", '2', "hot", '3', "burning", '4', "fiery", '5', "infernal");

    private final Group group;
    private final String label;
    private final List<String> aliases;
    private final Function<PartyChatCommand, String> answer;

    PartyCommand(Group group, String label, List<String> aliases, Function<PartyChatCommand, String> answer) {
        this.group = group;
        this.label = label;
        this.aliases = aliases;
        this.answer = answer;
    }

    public Group group() {
        return group;
    }

    /** The name the config lists it under. */
    public String label() {
        return label;
    }

    /** How "!help" names it. */
    public String helpName() {
        return this == JOIN_INSTANCE ? "!f1-7 !m1-7 !t1-5" : "!" + aliases.get(0);
    }

    /** The command a party member's "!name" stands for, or null. */
    public static PartyCommand byAlias(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (instanceFor(lower) != null) return JOIN_INSTANCE;
        for (PartyCommand command : values()) {
            if (command.aliases.contains(lower)) return command;
        }
        return null;
    }

    /** The command to send in answer, without its slash, or null when the request makes no sense. */
    public String answer(PartyChatCommand request) {
        return answer.apply(request);
    }

    private static String eightBall() {
        return EIGHT_BALL_ANSWERS.get(Measurements.random(EIGHT_BALL_ANSWERS.size()) - 1);
    }

    static boolean isName(String text) {
        return text != null && text.matches("[A-Za-z0-9_]{1,16}");
    }

    /** "!dice 20" rolls a d20; anything else, or nothing, a d6. */
    static int rollSides(String arg) {
        try {
            int sides = Integer.parseInt(arg);
            return sides >= 2 && sides <= 1_000_000 ? sides : 6;
        } catch (NumberFormatException e) {
            return 6;
        }
    }

    /** Hypixel's instance id for "f7", "m3" or "t5", or null. */
    static String instanceFor(String name) {
        if (name.length() != 2) return null;
        char kind = name.charAt(0);
        char digit = name.charAt(1);
        if ((kind == 'f' || kind == 'm') && digit >= '1' && digit <= '7') {
            return (kind == 'm' ? "master_" : "") + "catacombs_floor_" + FLOORS[digit - '1'];
        }
        if (kind == 't' && KUUDRA_TIERS.containsKey(digit)) {
            return "kuudra_" + KUUDRA_TIERS.get(digit);
        }
        return null;
    }

    /** The live values the info commands post, kept apart so the table above stays testable. */
    static final class Measurements {
        static Supplier<String> tpsSource = PartyCommandsFeature::currentTps;
        static Supplier<String> pingSource = PartyCommandsFeature::currentPing;
        static Supplier<String> fpsSource = PartyCommandsFeature::currentFps;
        static Supplier<String> coordsSource = PartyCommandsFeature::currentCoords;
        static Supplier<String> locationSource = PartyCommandsFeature::currentLocation;
        static Supplier<String> holdingSource = PartyCommandsFeature::currentHolding;
        static Supplier<String> timeSource = PartyCommandsFeature::currentTime;
        static IntUnaryOperator randomSource =
                bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound) + 1;

        private Measurements() {}

        static String tps() { return tpsSource.get(); }
        static String ping() { return pingSource.get(); }
        static String fps() { return fpsSource.get(); }
        static String coords() { return coordsSource.get(); }
        static String location() { return locationSource.get(); }
        static String holding() { return holdingSource.get(); }
        static String time() { return timeSource.get(); }

        /** A whole number from 1 to {@code bound}. */
        static int random(int bound) { return randomSource.applyAsInt(bound); }
    }
}
