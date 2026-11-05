package net.alpaka.api;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The dashboard's answers, kept for a while by the player asked for, so a profile opened again, or
 * by a second player, costs no request. Hypixel's policy asks projects to cache as well as they
 * can. Bounded by size rather than count, since one player's answer may be much bigger than
 * another's; the least recently used go first once it is full.
 */
final class ResponseCache {
    record Entry(int status, String body, long fetchedAt) {}

    private final long maxChars;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long chars;

    ResponseCache(long maxChars, LongSupplier clock) {
        this.maxChars = maxChars;
        this.clock = clock;
    }

    /** The answer for the key if it is younger than {@code maxAgeMs}, else null. */
    synchronized Entry get(String key, long maxAgeMs) {
        Entry e = entries.get(key);
        if (e == null) return null;
        if (clock.getAsLong() - e.fetchedAt() > maxAgeMs) return null;
        return e;
    }

    synchronized void put(String key, int status, String body) {
        if (body.length() > maxChars) return;
        Entry old = entries.put(key, new Entry(status, body, clock.getAsLong()));
        if (old != null) chars -= old.body().length();
        chars += body.length();
        Iterator<Map.Entry<String, Entry>> eldest = entries.entrySet().iterator();
        while (chars > maxChars && eldest.hasNext()) {
            chars -= eldest.next().getValue().body().length();
            eldest.remove();
        }
    }

    synchronized int size() {
        return entries.size();
    }
}
