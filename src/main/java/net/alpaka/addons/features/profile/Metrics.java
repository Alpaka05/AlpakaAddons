package net.alpaka.addons.features.profile;

import net.alpaka.addons.features.market.Coins;

import java.util.Locale;

/**
 * The numbers the dashboard keeps a history of, by the ids they are stored under, with how each
 * is named and written. The ids are part of the history file, so they never change.
 */
public final class Metrics {
    private Metrics() {}

    public static final String NETWORTH = "nw";
    public static final String LIQUID = "nw.liquid";
    public static final String PURSE = "purse";
    public static final String BANK = "bank";
    public static final String SB_LEVEL = "sb.level";
    /** Followed by the skill's id: "skill.farming". Its XP. */
    public static final String SKILL = "skill.";
    public static final String SKILL_AVG = "skill.avg";
    public static final String CATACOMBS = "cata";
    /** Followed by the class's id: "class.mage". Its XP. */
    public static final String CLASS = "class.";
    public static final String RUNS = "dungeon.runs";
    /** Followed by the boss's id: "slayer.zombie". Its XP. */
    public static final String SLAYER = "slayer.";
    public static final String SLAYER_TOTAL = "slayer.total";
    public static final String MAGICAL_POWER = "mp";
    public static final String PETS = "pets";
    public static final String PETS_MAXED = "pets.maxed";
    public static final String FARMING_WEIGHT = "farming.weight";
    public static final String BESTIARY = "bestiary";
    public static final String FAIRY_SOULS = "fairy";
    public static final String KUUDRA = "kuudra";
    public static final String TROPHY_FISH = "trophy";
    public static final String CHOCOLATE = "chocolate";
    public static final String GARDEN = "garden";
    public static final String MINIONS = "minions";

    public enum Kind { COINS, XP, LEVEL, COUNT, DECIMAL }

    public static Kind kind(String id) {
        if (id.equals(SKILL_AVG) || id.equals(SB_LEVEL) || id.equals(FARMING_WEIGHT)) return Kind.DECIMAL;
        if (id.startsWith(SKILL) || id.startsWith(CLASS) || id.equals(CATACOMBS) || id.startsWith(SLAYER) || id.equals(GARDEN)) {
            return Kind.XP;
        }
        if (id.equals(NETWORTH) || id.equals(LIQUID) || id.equals(PURSE) || id.equals(BANK)) return Kind.COINS;
        return Kind.COUNT;
    }

    /** How the metric is called on a card or a graph. */
    public static String label(String id) {
        return switch (id) {
            case NETWORTH -> "Networth";
            case LIQUID -> "Liquid Networth";
            case PURSE -> "Purse";
            case BANK -> "Bank";
            case SB_LEVEL -> "SkyBlock Level";
            case SKILL_AVG -> "Skill Average";
            case CATACOMBS -> "Catacombs XP";
            case RUNS -> "Dungeon Runs";
            case SLAYER_TOTAL -> "Slayer XP";
            case MAGICAL_POWER -> "Magical Power";
            case PETS -> "Pets";
            case PETS_MAXED -> "Max Level Pets";
            case FARMING_WEIGHT -> "Farming Weight";
            case BESTIARY -> "Bestiary Milestone";
            case FAIRY_SOULS -> "Fairy Souls";
            case KUUDRA -> "Kuudra Runs";
            case TROPHY_FISH -> "Trophy Fish";
            case CHOCOLATE -> "Chocolate Produced";
            case GARDEN -> "Garden XP";
            case MINIONS -> "Unique Minions";
            default -> {
                if (id.startsWith(SKILL)) yield ProfileData.title(id.substring(SKILL.length())) + " XP";
                if (id.startsWith(CLASS)) yield ProfileData.title(id.substring(CLASS.length())) + " XP";
                if (id.startsWith(SLAYER)) {
                    String boss = id.substring(SLAYER.length());
                    for (String[] s : ProfileData.SLAYERS) if (s[0].equals(boss)) yield s[1] + " XP";
                }
                yield ProfileData.title(id);
            }
        };
    }

    /** A value short enough for a card: "1.25B", "53.4", "12,345". */
    public static String format(String id, double value) {
        if (Double.isNaN(value)) return "—";
        return switch (kind(id)) {
            case COINS, XP -> Coins.compact(value);
            case DECIMAL -> String.format(Locale.US, value >= 100 ? "%.1f" : "%.2f", value);
            default -> value >= 100_000 ? Coins.compact(value) : Coins.full(Math.floor(value));
        };
    }

    /** A change, signed: "+1.2M", "-3", "+0.45". */
    public static String delta(String id, double change) {
        if (Double.isNaN(change)) return "";
        String sign = change > 0 ? "+" : change < 0 ? "-" : "±";
        return sign + format(id, Math.abs(change));
    }

    /**
     * What a value of this metric meant in levels, for the graph's hover: 12.3M Farming XP is
     * "Level 41.27". Null for metrics that are not XP of something with levels.
     */
    public static String levelNote(String id, double value, int maxLevel) {
        double level = Double.NaN;
        if (id.startsWith(SKILL) && !id.equals(SKILL_AVG)) {
            level = Levels.skill(id.substring(SKILL.length()), value, maxLevel > 0 ? maxLevel : 60);
        } else if (id.equals(CATACOMBS) || id.startsWith(CLASS)) {
            level = Levels.catacombs(value);
        }
        return Double.isNaN(level) ? null : String.format(Locale.US, "Level %.2f", level);
    }

    /** "just now", "5m ago", "3h ago", "2d ago". */
    public static String ago(long timeMs) {
        long s = Math.max(0, (System.currentTimeMillis() - timeMs) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return s / 60 + "m ago";
        if (s < 86_400) return s / 3600 + "h ago";
        return s / 86_400 + "d ago";
    }

    /** A number of days as "5h", "3 days", "2 weeks", "4 months". */
    public static String duration(double days) {
        if (days < 1) return Math.max(1, Math.round(days * 24)) + "h";
        if (days < 14) return Math.round(days) + (Math.round(days) == 1 ? " day" : " days");
        if (days < 60) return Math.round(days / 7) + " weeks";
        if (days < 730) return Math.round(days / 30) + " months";
        return String.format(Locale.US, "%.1f years", days / 365);
    }

    /** Milliseconds as a run time, "4:31". */
    public static String clock(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + String.format(Locale.US, "%02d", s % 60);
    }
}
