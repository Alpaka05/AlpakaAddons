package net.alpaka.addons.features.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reading Elite's profile answers without a crash when a field is missing. A profile with an API
 * switched off, or one that never touched a part of the game, simply leaves fields out; every read
 * here falls back to zero, empty or null instead.
 */
final class Json {
    private Json() {}

    /** The element at a dotted path such as "stats.dungeons.floors", or null. */
    static JsonElement at(JsonElement root, String path) {
        JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current == null || current.isJsonNull() ? null : current;
    }

    static JsonObject obj(JsonElement root, String path) {
        JsonElement e = at(root, path);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    static JsonArray arr(JsonElement root, String path) {
        JsonElement e = at(root, path);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /** A number at the path; numbers sent as strings ("52029215") count too. NaN when there is none. */
    static double num(JsonElement root, String path) {
        JsonElement e = at(root, path);
        if (e == null || !e.isJsonPrimitive()) return Double.NaN;
        try {
            return e.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException ex) {
            return Double.NaN;
        }
    }

    static double num(JsonElement root, String path, double fallback) {
        double v = num(root, path);
        return Double.isNaN(v) ? fallback : v;
    }

    static String str(JsonElement root, String path) {
        JsonElement e = at(root, path);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static boolean bool(JsonElement root, String path) {
        JsonElement e = at(root, path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    /** The sum of every number directly inside the object at the path. */
    static double sum(JsonElement root, String path) {
        double total = 0;
        for (var entry : obj(root, path).entrySet()) {
            JsonElement v = entry.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) total += v.getAsDouble();
        }
        return total;
    }
}
