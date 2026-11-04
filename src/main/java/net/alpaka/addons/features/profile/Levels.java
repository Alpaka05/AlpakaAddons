package net.alpaka.addons.features.profile;

/**
 * SkyBlock's experience tables, to turn an amount of XP from the history back into the level it
 * stood for then. The profile itself comes with its current levels; the history only keeps XP.
 *
 * Copied from Hypixel's own resources (/v2/resources/skyblock/skills) and the Catacombs table every
 * profile viewer uses; {@code LevelsTest} checks them against real profiles.
 */
public final class Levels {
    private Levels() {}

    /** XP needed for each level of a normal skill, level 1 first, up to 60. */
    static final long[] SKILL = {
            50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000, 50000,
            75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000, 1100000,
            1200000, 1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000, 2200000,
            2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000, 4300000,
            4600000, 4900000, 5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000};

    static final long[] RUNECRAFTING = {
            50, 100, 125, 160, 200, 250, 315, 400, 500, 625, 785, 1000, 1250, 1565, 2000, 2500, 3125, 4000, 5000,
            6250, 7850, 9800, 12250, 15300, 19050};

    static final long[] SOCIAL = {
            50, 100, 150, 250, 500, 750, 1000, 1250, 1500, 2000, 2500, 3000, 3750, 4500, 6000, 8000, 10000, 12500,
            15000, 20000, 25000, 30000, 35000, 40000, 50000};

    /** Catacombs and the five classes share one table; past 50 each level takes another 200M. */
    static final long[] CATACOMBS = {
            50, 75, 110, 160, 230, 330, 470, 670, 950, 1340, 1890, 2665, 3760, 5260, 7380, 10300, 14400, 20000,
            27600, 38000, 52500, 71500, 97000, 132000, 180000, 243000, 328000, 445000, 600000, 800000, 1065000,
            1410000, 1900000, 2500000, 3300000, 4300000, 5600000, 7200000, 9200000, 12000000, 15000000, 19000000,
            24000000, 30000000, 38000000, 48000000, 60000000, 75000000, 93000000, 116250000};
    static final long CATACOMBS_OVERFLOW = 200_000_000L;

    /** The table a skill levels by. */
    static long[] tableFor(String skill) {
        return switch (skill) {
            case "runecrafting" -> RUNECRAFTING;
            case "social" -> SOCIAL;
            default -> SKILL;
        };
    }

    /**
     * The level this much XP reaches, with the way to the next as the fraction: 1,000 XP in a skill
     * is 6.2. Never past {@code maxLevel}.
     */
    public static double skill(String skill, double xp, int maxLevel) {
        return fractional(tableFor(skill), xp, maxLevel, 0);
    }

    /** The Catacombs or class level, carried on past 50 at 200M a level as the profile viewers do. */
    public static double catacombs(double xp) {
        return fractional(CATACOMBS, xp, Integer.MAX_VALUE, CATACOMBS_OVERFLOW);
    }

    static double fractional(long[] table, double xp, int maxLevel, long overflow) {
        if (!(xp > 0)) return 0;
        double left = xp;
        int level = 0;
        for (long need : table) {
            if (level >= maxLevel) return level;
            if (left < need) return level + left / need;
            left -= need;
            level++;
        }
        if (overflow <= 0 || level >= maxLevel) return level;
        double extra = left / overflow;
        return Math.min(maxLevel, level + extra);
    }

    /**
     * The total XP of a normal skill that stands at {@code percent} of the way from {@code level} to
     * the next, as the tab list and the action bar show it. A maxed skill is the XP of its last level.
     */
    public static double skillXp(int level, double percent) {
        double base = total(SKILL, level);
        return level < SKILL.length ? base + SKILL[level] * percent / 100.0 : base;
    }

    /**
     * A level counted on past the cap: the whole level, the XP already into the next one and what
     * the next one takes.
     */
    public record Overflow(int level, double xpCurrent, double xpForNext) {
        /** The level with the way to the next as the fraction, 63.42. */
        public double exact() {
            return level + (xpForNext > 0 ? Math.max(0, Math.min(1, xpCurrent / xpForNext)) : 0);
        }

        public double progress() {
            return xpForNext > 0 ? Math.max(0, Math.min(1, xpCurrent / xpForNext)) : 1;
        }
    }

    /** What level 61 takes; each level after takes the slope more, see {@link #skillOverflow}. */
    static final long SKILL_PAST_60 = 7_600_000L;
    static final long SKILL_SLOPE = 600_000L;

    /**
     * The skill level this much XP would be with no cap, as SkyHanni counts it: the normal table up
     * to 60 whatever the skill's own cap, then 7.6M for level 61 and 600k more for each level after,
     * the step doubling every tenth level. Null for Runecrafting and Social, whose short tables have
     * no such rule.
     */
    public static Overflow skillOverflow(String skill, double xp) {
        if (tableFor(skill) != SKILL) return null;
        double left = Math.max(0, xp);
        int level = 0;
        for (long need : SKILL) {
            if (left < need) return new Overflow(level, left, need);
            left -= need;
            level++;
        }
        long slope = SKILL_SLOPE;
        double need = SKILL_PAST_60;
        while (left >= need) {
            left -= need;
            level++;
            need += slope;
            if (level % 10 == 0) slope *= 2;
        }
        return new Overflow(level, left, need);
    }

    /** The Catacombs or class level with no cap: the table up to 50, then 200M a level. */
    public static Overflow catacombsOverflow(double xp) {
        double left = Math.max(0, xp);
        int level = 0;
        for (long need : CATACOMBS) {
            if (left < need) return new Overflow(level, left, need);
            left -= need;
            level++;
        }
        int past = (int) Math.floor(left / CATACOMBS_OVERFLOW);
        return new Overflow(level + past, left - (double) past * CATACOMBS_OVERFLOW, CATACOMBS_OVERFLOW);
    }

    /**
     * The XP {@code skill} ("farming", "runecrafting") takes from {@code level} to the next, or 0
     * past the end of its table.
     */
    public static long skillXpForNext(String skill, int level) {
        long[] table = tableFor(skill);
        return level >= 0 && level < table.length ? table[level] : 0;
    }

    /** XP from zero to the start of {@code level}. */
    static long total(long[] table, int level) {
        long sum = 0;
        for (int i = 0; i < Math.min(level, table.length); i++) sum += table[i];
        return sum;
    }
}
