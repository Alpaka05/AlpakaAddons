package net.alpaka.addons.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Entity id to a value, without boxing the id. For per-entity, per-frame caches, where a
 * {@code HashMap<Integer, T>} makes a new Integer on every lookup (entity ids are past the small
 * Integer cache). A small class so Kotlin callers get the primitive methods without overload trouble.
 */
public final class EntityCache<T> {
    private final Int2ObjectOpenHashMap<T> map = new Int2ObjectOpenHashMap<>();

    /** The value for {@code id}, or null. */
    public T get(int id) {
        return map.get(id);
    }

    public void put(int id, T value) {
        map.put(id, value);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean isNotEmpty() {
        return !map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }
}
