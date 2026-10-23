package net.alpaka.addons.config;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Puts loaded settings back into a shape the mod can use, whatever a hand edit, a damaged file or an
 * older version left in them.
 *
 * - A float or double that is NaN or infinite gets its default back. Such a value loaded without
 *   complaint, and every save after it failed.
 * - A field that is null where a fresh config has a value gets that value: {@code "bridgeBotName":
 *   null} crashed the bridge formatter on every chat line. Legacy fields, whose default is null, are
 *   left alone; null is how they say they have been migrated.
 * - Lists and maps lose their null entries, and an array's null slot gets a fresh element, so a
 *   {@code null} item preset or command wheel page no longer crashes whatever reads it.
 * - The mod's own nested objects (an item preset, a slayer entry) are put right the same way.
 *
 * Goes over the fields by reflection, so a new setting is covered without being listed here.
 */
final class ConfigSanitizer {
    private ConfigSanitizer() {}

    /** Fixes {@code target} against {@code defaults}, a fresh object of the same class. Returns how many values changed. */
    static int sanitize(Object target, Object defaults) {
        int changed = 0;
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || Modifier.isFinal(modifiers)) continue;
                try {
                    field.setAccessible(true);
                    changed += sanitizeField(field, target, defaults);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // A field that cannot be read or set this way is left as it is.
                }
            }
        }
        return changed;
    }

    private static int sanitizeField(Field field, Object target, Object defaults) throws IllegalAccessException {
        Class<?> type = field.getType();
        Object fallback = defaults == null ? null : field.get(defaults);
        if (type == float.class) {
            if (Float.isFinite(field.getFloat(target))) return 0;
            field.setFloat(target, fallback == null ? 0.0f : (Float) fallback);
            return 1;
        }
        if (type == double.class) {
            if (Double.isFinite(field.getDouble(target))) return 0;
            field.setDouble(target, fallback == null ? 0.0 : (Double) fallback);
            return 1;
        }
        if (type.isPrimitive()) return 0;

        Object value = field.get(target);
        if (value == null || !isFinite(value)) {
            if (value == null && fallback == null) return 0;
            field.set(target, fallback);
            return 1;
        }
        return clean(value);
    }

    /** Fixes what {@code value} holds: entries of lists and maps, slots of arrays, fields of the mod's own objects. */
    private static int clean(Object value) {
        if (value instanceof Collection<?> collection) return cleanAll(collection);
        if (value instanceof Map<?, ?> map) {
            int changed = 0;
            Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<?, ?> entry = entries.next();
                if (entry.getKey() == null || entry.getValue() == null || !isFinite(entry.getValue())) {
                    entries.remove();
                    changed++;
                } else {
                    changed += clean(entry.getValue());
                }
            }
            return changed;
        }
        if (value.getClass().isArray()) {
            Class<?> component = value.getClass().getComponentType();
            if (component.isPrimitive()) return 0;
            int changed = 0;
            for (int i = 0; i < Array.getLength(value); i++) {
                Object element = Array.get(value, i);
                if (element == null) {
                    Object fresh = fresh(component);
                    if (fresh == null) continue;
                    Array.set(value, i, fresh);
                    changed++;
                } else {
                    changed += clean(element);
                }
            }
            return changed;
        }
        if (!(value instanceof Enum<?>) && isOurs(value.getClass())) {
            Object defaults = fresh(value.getClass());
            return defaults == null ? 0 : sanitize(value, defaults);
        }
        return 0;
    }

    private static int cleanAll(Collection<?> collection) {
        int changed = 0;
        Iterator<?> elements = collection.iterator();
        while (elements.hasNext()) {
            Object element = elements.next();
            if (element == null || !isFinite(element)) {
                elements.remove();
                changed++;
            } else {
                changed += clean(element);
            }
        }
        return changed;
    }

    private static boolean isFinite(Object value) {
        if (value instanceof Float f) return Float.isFinite(f);
        if (value instanceof Double d) return Double.isFinite(d);
        return true;
    }

    /** One of the mod's own setting classes (not an enum), whose fields are worth going into. */
    private static boolean isOurs(Class<?> type) {
        return !type.isEnum() && type.getName().startsWith("net.alpaka.addons.");
    }

    /** A new instance of {@code type} with its defaults, or null if it has no constructor without arguments. */
    private static Object fresh(Class<?> type) {
        if (!isOurs(type)) return null;
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
