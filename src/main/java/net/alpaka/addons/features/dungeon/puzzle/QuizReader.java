package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.room.PuzzleChat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows the Quiz in chat, one line at a time, and works out which of the three options answers the
 * question being asked. Pure, so the whole conversation can be replayed in tests.
 *
 * Oruo asks each question in a few chat lines (puzzles-chat.md 3.2): a "Question #1" heading, the
 * question itself on one or two lines (a long one wraps), and then the options, one line each,
 * "ⓐ Stalker", "ⓑ Master Necromancer", "ⓒ Professor". The lines arrive here with formatting and the
 * centring spaces removed, and blank lines never arrive at all.
 *
 * The question is taken from the last few plain lines before the first option, so a wrapped question
 * is put back together and a missed heading does not matter. Lines that someone typed - party chat,
 * NPCs, Oruo himself - always carry a "name: " and are never part of a question; Oruo's
 * "x thinks the answer is ⓐ ..." starts with his name too, so it is not taken for an option.
 *
 * An answer is only given once all three options are in and exactly one of them is listed for the
 * question. A question the table does not know, or one with two listed options, gets none: a wrong
 * button fails the puzzle for the whole party, and no mark is better than a wrong one.
 */
public final class QuizReader {

    /** Where the reader is with the current question. */
    public enum Status {
        /** No question being asked, or the last one was answered. */
        IDLE,
        /** A question was asked and its options are coming in. */
        OPTIONS,
        /** Exactly one option answers the question: {@link #answer()}. */
        ANSWER,
        /** More than one option is listed as an answer; nothing is marked. */
        AMBIGUOUS,
        /** The question, or every one of its options, is not in the table; nothing is marked. */
        UNKNOWN
    }

    /** The option letters, in order: ⓐ ⓑ ⓒ. */
    public static final String LETTERS = "ⓐⓑⓒ";

    private static final Pattern HEADING = Pattern.compile("^Question #[1-3]$");
    private static final Pattern OPTION = Pattern.compile("^([ⓐⓑⓒ])\\s+(.+)$");
    private static final String ORUO = "[STATUE] Oruo the Omniscient: ";
    /** Someone answered correctly: this question is over (the next one, or the end, follows). */
    private static final Pattern ANSWERED = Pattern.compile(
            "^\\[STATUE] Oruo the Omniscient: (?:\\[[^\\]]+] )?\\w{1,16} answered (?:Question #\\d|the final question) correctly!$");
    private static final Pattern BESTOWED = Pattern.compile(
            "^\\[STATUE] Oruo the Omniscient: I bestow upon you all the power of a hundred years!$");

    /** Plain lines kept from before the options; a question is at most three of them. */
    private static final int RECENT_LINES = 4;
    private static final int MAX_QUESTION_LINES = 3;

    private final QuizAnswers answers;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private String question = null;
    private Set<String> candidates = null;
    private final String[] options = new String[3];
    private int answer = -1;
    private Status status = Status.IDLE;

    public QuizReader(QuizAnswers answers) {
        this.answers = answers;
    }

    /**
     * One chat line, formatting stripped and trimmed.
     *
     * @param nowEpochMs the wall clock in Unix milliseconds, for the SkyBlock year question
     * @return the status after the line
     */
    public Status accept(String line, long nowEpochMs) {
        if (line == null || line.isEmpty()) return status;

        if (line.startsWith(ORUO)) {
            if (ANSWERED.matcher(line).matches() || BESTOWED.matcher(line).matches()
                    || PuzzleChat.QUIZ_FAILED.matcher(line).matches()) {
                clear();
            } else {
                // Oruo talking between questions, or someone's suggestion: never part of a question.
                recent.clear();
            }
            return status;
        }

        if (HEADING.matcher(line).matches()) {
            clear();
            return status;
        }

        Matcher option = OPTION.matcher(line);
        if (option.matches()) {
            int index = LETTERS.indexOf(option.group(1).charAt(0));
            if (index == 0) startOptions(nowEpochMs);
            if (status != Status.OPTIONS) return status;
            options[index] = option.group(2).trim();
            if (options[0] != null && options[1] != null && options[2] != null) decide();
            return status;
        }

        // Chat someone typed, an NPC, a party message: "name: text". A question has no ": ".
        if (line.contains(": ")) return status;
        recent.addLast(line);
        while (recent.size() > RECENT_LINES) recent.removeFirst();
        return status;
    }

    /** Forgets the current question; the next one starts fresh. */
    public void clear() {
        recent.clear();
        question = null;
        candidates = null;
        Arrays.fill(options, null);
        answer = -1;
        status = Status.IDLE;
    }

    public Status status() {
        return status;
    }

    /** The option that answers the question, 0..2 for ⓐ..ⓒ, or -1. */
    public int answer() {
        return status == Status.ANSWER ? answer : -1;
    }

    /** The question as asked, or null; for the log and the debug output. */
    public String question() {
        return question;
    }

    /** The options as written, null where one has not come yet. */
    public List<String> options() {
        return Arrays.asList(options.clone());
    }

    /**
     * The first option has come: the question is in the plain lines just before it. Tries the last
     * line that ends in "?" on its own, then with the line before it, and so on, and keeps the first
     * that the table knows; a wrapped question only fits when both its halves are taken.
     */
    private void startOptions(long nowEpochMs) {
        List<String> lines = new ArrayList<>(recent);
        recent.clear();
        Arrays.fill(options, null);
        answer = -1;
        int end = lines.size() - 1;
        while (end >= 0 && !lines.get(end).endsWith("?")) end--;
        if (end < 0) {
            // No new question; the same options shown again keep the question they belong to.
            status = question != null ? Status.OPTIONS : Status.IDLE;
            return;
        }
        question = null;
        candidates = null;
        String longest = null;
        for (int start = end; start >= 0 && start > end - MAX_QUESTION_LINES; start--) {
            String asked = String.join(" ", lines.subList(start, end + 1));
            longest = asked;
            Set<String> found = answers.candidates(asked, nowEpochMs);
            if (found != null) {
                question = asked;
                candidates = found;
                break;
            }
        }
        // Not in the table: keep the whole of it for the log line.
        if (question == null) question = longest;
        status = Status.OPTIONS;
    }

    private void decide() {
        if (candidates == null) {
            status = Status.UNKNOWN;
            return;
        }
        int found = -1;
        int count = 0;
        for (int i = 0; i < 3; i++) {
            if (candidates.contains(QuizAnswers.normaliseOption(options[i]))) {
                found = i;
                count++;
            }
        }
        if (count == 1) {
            answer = found;
            status = Status.ANSWER;
        } else {
            status = count == 0 ? Status.UNKNOWN : Status.AMBIGUOUS;
        }
    }
}
