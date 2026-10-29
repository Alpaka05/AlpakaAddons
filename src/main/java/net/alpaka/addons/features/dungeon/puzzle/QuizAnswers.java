package net.alpaka.addons.features.dungeon.puzzle;

// Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md. The table itself lives
// in assets/alpaka/dungeon/quiz_answers.json; this class only reads it.

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which options answer a question of Oruo the Omniscient, the statue of the Quiz puzzle.
 *
 * The table is data (assets/alpaka/dungeon/quiz_answers.json) because Hypixel changes some answers
 * over time - the number of minions went from 59 to 60 to 61, the fairy souls from 266 to 273 or
 * 289 - and a fix should not need code. Each question lists every option that answers it; most have
 * one, some ("Which of these is not a dragon in The End?") have many, and one whose current answer is
 * in doubt lists both candidates. The caller only trusts an answer when exactly one of the three
 * options is listed, so a stale or doubtful entry shows nothing rather than the wrong button.
 *
 * Two kinds of question are worked out rather than listed: the SkyBlock year, from the clock, and the
 * fairy souls in a zone, from a table of zones.
 *
 * Questions and options are compared without case, apostrophes or repeated spaces: Hypixel and the
 * tables write "Spider's Den" and "Spiders Den" both ways.
 */
public final class QuizAnswers {

    /** Where the table is on the classpath, in the game and in tests alike. */
    public static final String RESOURCE = "/assets/alpaka/dungeon/quiz_answers.json";

    /** The question that is answered from the clock. */
    public static final String YEAR_QUESTION = "What SkyBlock year is it?";

    /**
     * The start of SkyBlock year 1 in Unix milliseconds: 2019-06-11 17:55 UTC, the epoch Skyblocker,
     * NEU and SkyHanni use. Odin and Skytils count from 18:00, five minutes later.
     */
    public static final long YEAR_EPOCH_MS = 1_560_275_700_000L;
    /** One SkyBlock year: 12 months of 31 days of 20 minutes. */
    public static final long YEAR_MS = 446_400_000L;
    /**
     * How close to the turn of a year the year is in doubt. The two epochs in use differ by five
     * minutes; within this much of the turn both years count, and with both among the options the
     * question is left unanswered instead of guessed.
     */
    public static final long YEAR_DOUBT_MS = 10 * 60 * 1000L;

    /** "How many Fairy Souls are there in (the) <zone>?", after {@link #normalise}. */
    private static final Pattern ZONE_QUESTION = Pattern.compile("^how many fairy souls are there in (?:the )?(.+)\\?$");

    private static QuizAnswers bundled;

    /** Normalised question to its normalised answers. */
    private final Map<String, Set<String>> questions;
    /** Normalised zone name (no leading "the") to its number of fairy souls. */
    private final Map<String, Integer> zones;

    private QuizAnswers(Map<String, Set<String>> questions, Map<String, Integer> zones) {
        this.questions = questions;
        this.zones = zones;
    }

    /** The table that ships with the mod; read once. Throws if it is missing or broken, which a test catches. */
    public static synchronized QuizAnswers bundled() {
        if (bundled == null) {
            try (InputStream in = QuizAnswers.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("missing " + RESOURCE);
                bundled = read(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + RESOURCE, e);
            }
        }
        return bundled;
    }

    /** A table from JSON in the layout of quiz_answers.json. */
    public static QuizAnswers read(Reader json) {
        JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
        Map<String, Set<String>> questions = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray("questions")) {
            JsonObject entry = element.getAsJsonObject();
            String question = normalise(entry.get("q").getAsString());
            JsonArray list = entry.getAsJsonArray("a");
            Set<String> answers = questions.computeIfAbsent(question, k -> new LinkedHashSet<>());
            for (JsonElement answer : list) answers.add(normaliseOption(answer.getAsString()));
        }
        Map<String, Integer> zones = new HashMap<>();
        JsonObject zoneTable = root.getAsJsonObject("fairySoulZones");
        if (zoneTable != null) {
            for (Map.Entry<String, JsonElement> e : zoneTable.entrySet()) {
                zones.put(zoneKey(normalise(e.getKey())), e.getValue().getAsInt());
            }
        }
        Map<String, Set<String>> frozen = new HashMap<>();
        questions.forEach((q, answers) -> frozen.put(q, Collections.unmodifiableSet(answers)));
        return new QuizAnswers(Map.copyOf(frozen), Map.copyOf(zones));
    }

    /**
     * Every option text that answers {@code question}, normalised ({@link #normaliseOption}), or null
     * for a question the table does not know.
     *
     * @param nowEpochMs the wall clock in Unix milliseconds, for the year question
     */
    public Set<String> candidates(String question, long nowEpochMs) {
        if (question == null) return null;
        String q = normalise(question);
        if (q.isEmpty()) return null;
        if (q.equals(normalise(YEAR_QUESTION))) {
            Set<String> years = new LinkedHashSet<>();
            for (int year : yearCandidates(nowEpochMs)) years.add(normaliseOption("Year " + year));
            return years;
        }
        Set<String> listed = questions.get(q);
        if (listed != null) return listed;
        Matcher zone = ZONE_QUESTION.matcher(q);
        if (zone.matches()) {
            Integer souls = zones.get(zoneKey(zone.group(1)));
            if (souls != null) return Set.of(normaliseOption(souls + " Fairy Souls"));
        }
        return null;
    }

    /** Whether {@code question} is one the table can answer (at any time). */
    public boolean knows(String question) {
        return candidates(question, YEAR_EPOCH_MS + YEAR_MS / 2) != null;
    }

    /** How many questions are listed, for tests and the debug output. */
    public int size() {
        return questions.size();
    }

    /**
     * The SkyBlock year at {@code nowEpochMs}, and the neighbouring year as well when the clock is
     * within {@link #YEAR_DOUBT_MS} of a turn of the year. Never empty.
     */
    public static List<Integer> yearCandidates(long nowEpochMs) {
        long since = nowEpochMs - YEAR_EPOCH_MS;
        int year = (int) Math.floorDiv(since, YEAR_MS) + 1;
        long intoYear = Math.floorMod(since, YEAR_MS);
        List<Integer> years = new ArrayList<>(2);
        if (intoYear < YEAR_DOUBT_MS) years.add(year - 1);
        years.add(year);
        if (YEAR_MS - intoYear <= YEAR_DOUBT_MS) years.add(year + 1);
        return years;
    }

    /**
     * How questions are compared: lower case, without apostrophes (straight or typographic), with
     * single spaces, trimmed.
     */
    public static String normalise(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(Locale.ROOT)
                .replace("’", "")
                .replace("‘", "")
                .replace("'", "");
        return t.replaceAll("\\s+", " ").trim();
    }

    /** How options are compared: like questions, and a trailing full stop or "!" does not count. */
    public static String normaliseOption(String text) {
        return normalise(text).replaceAll("[.!]+$", "").trim();
    }

    /** A zone name without a leading "the", so "The Hub" and "Hub" are one zone. */
    private static String zoneKey(String normalisedZone) {
        return normalisedZone.startsWith("the ") ? normalisedZone.substring(4) : normalisedZone;
    }
}
