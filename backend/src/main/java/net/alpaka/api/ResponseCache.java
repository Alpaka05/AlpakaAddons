package net.alpaka.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Hypixel's answers, kept for a while by the path asked for, so a profile opened again, or by a
 * second player, costs no request. Hypixel's policy asks projects to cache as well as they can.
 * The oldest entries go first once it is full.
 */
final class ResponseCache {
    record Entry(int status, String body, long fetchedAt) {}

    private final int maxEntries;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Entry> entries;

    ResponseCache(int maxEntries, LongSupplier clock) {
        this.maxEntries = maxEntries;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ResponseCache.Entry> eldest) {
                return size() > ResponseCache.this.maxEntries;
            }
        };
    }

    /** The answer for the path if it is younger than {@code maxAgeMs}, else null. */
    synchronized Entry get(String path, long maxAgeMs) {
        Entry e = entries.get(path);
        if (e == null) return null;
        if (clock.getAsLong() - e.fetchedAt() > maxAgeMs) {
            entries.remove(path);
            return null;
        }
        return e;
    }

    synchronized void put(String path, int status, String body) {
        entries.put(path, new Entry(status, body, clock.getAsLong()));
    }

    synchronized int size() {
        return entries.size();
    }
}
