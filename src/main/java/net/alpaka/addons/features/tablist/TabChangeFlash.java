package net.alpaka.addons.features.tablist;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Remembers what each tab row said and lights a row up for a moment when it says something else.
 *
 * A row is known by where it sits - its category, its widget and its label ("Bank", "Gems") - so a
 * value that changes keeps its key. Values are only compared while the list is up, which means a
 * row that changed while it was closed lights up as the list opens: what is new since the last look.
 *
 * Countdowns ("in 8m", "2h 13m", "0:45") change on their own all the time and would keep the
 * list blinking; a row whose new text holds one is not lit.
 */
public final class TabChangeFlash {

    /** How long a changed row stays lit, fading out over that time. */
    static final long FLASH_MS = 1500L;

    private static final Pattern COUNTDOWN = Pattern.compile("\\b\\d+\\s*[smhd]\\b|\\d+:\\d{2}");

    private final Map<String, String> lastValue = new HashMap<>();
    private final Map<String, Long> changedAtMs = new HashMap<>();

    /** Forgets every row, for a new server: there, everything is new and nothing has changed. */
    public void reset() {
        lastValue.clear();
        changedAtMs.clear();
    }

    /** The row's current text; starts its light when the text differs from last time. */
    public void observe(String key, String value, long nowMs) {
        String previous = lastValue.put(key, value);
        if (previous != null && !previous.equals(value) && !COUNTDOWN.matcher(value).find()) {
            changedAtMs.put(key, nowMs);
        }
    }

    /** How strongly the row is lit right now, 1 just after its change down to 0. */
    public float strength(String key, long nowMs) {
        Long changed = changedAtMs.get(key);
        if (changed == null) return 0.0f;
        float progress = (nowMs - changed) / (float) FLASH_MS;
        if (progress >= 1.0f) {
            changedAtMs.remove(key);
            return 0.0f;
        }
        float remaining = 1.0f - Math.max(0.0f, progress);
        return remaining * remaining;
    }

    /**
     * The key a row is known by: category, the widget's own label (its first row's, up to the
     * colon) and the row's label, or its place in the widget for a row without one.
     */
    static String key(String category, String widgetLabel, String row, int index) {
        int colon = row.indexOf(':');
        String label = colon > 0 ? row.substring(0, colon).trim() : "#" + index;
        return category + "/" + widgetLabel + "/" + label;
    }

    /** A row's label: the text up to its colon, or all of it. */
    static String label(String row) {
        int colon = row.indexOf(':');
        return (colon > 0 ? row.substring(0, colon) : row).trim();
    }
}
