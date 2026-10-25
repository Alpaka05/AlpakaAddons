package net.alpaka.addons.features.chat;

import net.alpaka.addons.utils.SkyblockUtils;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One filter the player made themselves in the Message Filter's sub menu, usually from a message
 * they copied out of the chat. Saved in the config as it is, so the fields stay plain.
 */
public final class CustomMessageFilter {

    /** How the filter's text is compared with a message. */
    public enum Mode {
        EQUALS("Equals", "Hides a message that is exactly this text."),
        STARTS_WITH("Starts With", "Hides every message that starts with this text."),
        CONTAINS("Contains", "Hides every message that contains this text anywhere."),
        ENDS_WITH("Ends With", "Hides every message that ends with this text."),
        REGEX("Regex", "A regular expression; it matches anywhere unless anchored with ^ and $.");

        public final String label;
        public final String hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    public String text = "";
    public Mode mode = Mode.EQUALS;
    public boolean ignoreCase = false;
    public boolean enabled = true;

    // The compiled regex, kept until the text or the case switch changes; not saved.
    private transient Pattern compiled;
    private transient String compiledFrom;
    private transient boolean compiledIgnoreCase;
    private transient boolean invalid;

    public CustomMessageFilter() {
    }

    public CustomMessageFilter(String text, Mode mode) {
        this.text = text;
        this.mode = mode;
    }

    /**
     * Whether a color-stripped message is hidden by this filter.
     *
     * Messages are compared without color codes and outer spaces, and so is the filter's own text
     * unless it is a regex: a message copied with its color codes, or centered with spaces the way
     * Hypixel centers its notices, still matches the line it was copied from.
     */
    public boolean matches(String message) {
        if (!enabled || text == null || text.isBlank() || mode == null) return false;
        if (mode == Mode.REGEX) {
            Pattern pattern = pattern();
            return pattern != null && pattern.matcher(message).find();
        }
        String wanted = SkyblockUtils.cleanColor(text);
        if (wanted.isEmpty()) return false;
        String haystack = message;
        if (ignoreCase) {
            wanted = wanted.toLowerCase(Locale.ROOT);
            haystack = haystack.toLowerCase(Locale.ROOT);
        }
        return switch (mode) {
            case EQUALS -> haystack.equals(wanted);
            case STARTS_WITH -> haystack.startsWith(wanted);
            case CONTAINS -> haystack.contains(wanted);
            case ENDS_WITH -> haystack.endsWith(wanted);
            case REGEX -> false;
        };
    }

    /** False only for a regex that does not compile; the sub menu marks such a filter. */
    public boolean isValid() {
        return mode != Mode.REGEX || text == null || text.isBlank() || pattern() != null;
    }

    private Pattern pattern() {
        if (!text.equals(compiledFrom) || ignoreCase != compiledIgnoreCase) {
            compiledFrom = text;
            compiledIgnoreCase = ignoreCase;
            try {
                compiled = Pattern.compile(text, ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
                invalid = false;
            } catch (PatternSyntaxException e) {
                compiled = null;
                invalid = true;
            }
        }
        return invalid ? null : compiled;
    }
}
