package net.alpaka.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * At most {@code limit} uses per key in any window of {@code windowMs}: a player's requests, or the
 * logins from one address. Old keys are dropped as their window passes, so the map stays small.
 */
final class RateLimit {
    private final int limit;
    private final long windowMs;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> uses = new HashMap<>();

    RateLimit(int limit, long windowMs, LongSupplier clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /** Counts a use and says whether it is allowed; a refused one doesn't count. */
    synchronized boolean tryUse(String key) {
        long now = clock.getAsLong();
        if (uses.size() > 10_000) uses.values().removeIf(q -> q.isEmpty() || q.peekLast() <= now - windowMs);
        Deque<Long> q = uses.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && q.peekFirst() <= now - windowMs) q.pollFirst();
        if (q.size() >= limit) return false;
        q.addLast(now);
        return true;
    }

    /** Seconds until the key may be used again; 0 when it may now. */
    synchronized long retryAfterSeconds(String key) {
        Deque<Long> q = uses.get(key);
        if (q == null || q.size() < limit) return 0;
        long wait = q.peekFirst() + windowMs - clock.getAsLong();
        return Math.max(1, (wait + 999) / 1000);
    }
}
