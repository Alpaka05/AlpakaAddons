package net.alpaka.addons.utils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Entity ids remembered for a while, unboxed. Ids go in with the time they were seen, in time
 * order, so expiring them only looks at the oldest ones instead of walking every id each tick.
 */
public final class ExpiringIds {
    private final IntOpenHashSet ids = new IntOpenHashSet();
    /** Each id with its time, oldest first: the id in the low 32 bits, the time in the next queue slot. */
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

    public boolean contains(int id) {
        return ids.contains(id);
    }

    /** Remembers {@code id} as seen at {@code nowMs}; only for an id not remembered yet. */
    public void add(int id, long nowMs) {
        if (!ids.add(id)) return;
        queue.enqueue(id);
        queue.enqueue(nowMs);
    }

    /** Forgets every id seen more than {@code maxAgeMs} before {@code nowMs}. */
    public void expire(long nowMs, long maxAgeMs) {
        while (!queue.isEmpty()) {
            int id = (int) queue.firstLong();
            queue.dequeueLong();
            long seenAt = queue.firstLong();
            if (nowMs - seenAt <= maxAgeMs) {
                // Not due yet: put it back in front, the same pair in the same order.
                queue.enqueueFirst(id);
                break;
            }
            queue.dequeueLong();
            ids.remove(id);
        }
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    public void clear() {
        ids.clear();
        queue.clear();
    }
}
