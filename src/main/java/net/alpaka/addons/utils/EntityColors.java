package net.alpaka.addons.utils;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Entity id to an int (an ARGB color, or a flag), without boxing. The highlight features look an
 * entity up per entity and frame, and a {@code HashMap<Integer, Integer>} makes a new Integer for
 * every id above 127, which is every entity. A small class so Kotlin callers get the primitive
 * methods without overload trouble.
 */
public final class EntityColors {
    private final Int2IntOpenHashMap map = new Int2IntOpenHashMap();

    public void put(int id, int value) {
        map.put(id, value);
    }

    public boolean has(int id) {
        return map.containsKey(id);
    }

    /** The value for {@code id}; only meaningful after {@link #has} said yes. */
    public int get(int id) {
        return map.get(id);
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

    /** Each id with its value. */
    public void forEach(Visitor visitor) {
        for (Int2IntMap.Entry e : Int2IntMaps.fastIterable(map)) visitor.visit(e.getIntKey(), e.getIntValue());
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(int id, int value);
    }
}
