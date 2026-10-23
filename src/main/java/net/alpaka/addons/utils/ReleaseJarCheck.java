package net.alpaka.addons.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Whether a downloaded release jar may replace the running one, judged by its fabric.mod.json: that
 * it is this mod at the version the release names, and that this game has everything it depends on.
 *
 * The second half matters because the swap happens after the game has closed: a release that needs
 * a newer Fabric API, Fabric Loader, Kotlin, Java or Minecraft would delete the working jar and leave
 * a mods folder that Fabric refuses to start with. This check runs in the installer of the version
 * being replaced, so it has to be out before any release raises a minimum.
 *
 * Kept apart from UpdateInstaller and free of the game's classes, so tests can run it on a jar they
 * built themselves.
 */
final class ReleaseJarCheck {

    /** What the player knows these by; anything else is named by its mod id. */
    private static final Map<String, String> NAMES = Map.of(
            "fabricloader", "Fabric Loader",
            "fabric-api", "Fabric API",
            "fabric-language-kotlin", "Fabric Language Kotlin",
            "minecraft", "Minecraft",
            "java", "Java");

    private ReleaseJarCheck() {}

    /**
     * Why {@code jar} cannot replace the running mod, or null when it can.
     *
     * @param modId the mod the jar has to be.
     * @param version the version the release names. A build between releases names itself after
     *     its release ({@code 1.4.0+2.abc1234}), as the update check does.
     * @param installed the installed version of a mod id, or null when it is not installed. Fabric
     *     lists Minecraft, Java and itself as mods too, as "minecraft", "java" and "fabricloader".
     */
    static String problem(Path jar, String modId, String version, Function<String, Version> installed) {
        JsonObject json;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return "the download is not a Fabric mod";
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            return "the download is not a valid jar";
        }
        if (!modId.equals(text(json.get("id")))) return "the download is not Alpaka Addons";
        String declared = text(json.get("version"));
        if (declared == null || !version.equals(declared.split("\\+", 2)[0])) {
            return "the download says it is version " + declared + ", not " + version;
        }
        return dependencyProblem(json, installed);
    }

    /**
     * Why this game does not meet what a fabric.mod.json {@code depends} on, or has something it
     * {@code breaks} with; null when it does and has not. A list of ranges for one mod is met by any
     * of them, as Fabric reads it.
     */
    static String dependencyProblem(JsonObject modJson, Function<String, Version> installed) {
        try {
            JsonObject depends = object(modJson.get("depends"));
            if (depends != null) {
                for (Map.Entry<String, JsonElement> dependency : depends.entrySet()) {
                    String id = dependency.getKey();
                    List<String> ranges = ranges(dependency.getValue());
                    Version have = installed.apply(id);
                    String name = NAMES.getOrDefault(id, id);
                    if (have == null) {
                        return "it needs " + name + " " + String.join(" or ", ranges) + ", which isn't installed";
                    }
                    if (!matchesAny(ranges, have)) {
                        return "it needs " + name + " " + String.join(" or ", ranges) + " and you have "
                                + have.getFriendlyString() + " - update " + name + " first";
                    }
                }
            }
            JsonObject breaks = object(modJson.get("breaks"));
            if (breaks != null) {
                for (Map.Entry<String, JsonElement> conflict : breaks.entrySet()) {
                    Version have = installed.apply(conflict.getKey());
                    if (have != null && matchesAny(ranges(conflict.getValue()), have)) {
                        String name = NAMES.getOrDefault(conflict.getKey(), conflict.getKey());
                        return "it doesn't work with " + name + " " + have.getFriendlyString()
                                + " - update or remove " + name + " first";
                    }
                }
            }
            return null;
        } catch (VersionParsingException | RuntimeException e) {
            // A requirement this version cannot read is one it cannot vouch for.
            return "its requirements can't be read";
        }
    }

    private static boolean matchesAny(List<String> ranges, Version version) throws VersionParsingException {
        for (String range : ranges) {
            if (VersionPredicate.parse(range).test(version)) return true;
        }
        return false;
    }

    /** One range, or each of a list of them. */
    private static List<String> ranges(JsonElement value) {
        List<String> ranges = new ArrayList<>();
        if (value instanceof JsonArray array) {
            for (JsonElement element : array) ranges.add(element.getAsString());
        } else {
            ranges.add(value.getAsString());
        }
        if (ranges.isEmpty()) throw new IllegalArgumentException("an empty list of versions");
        return ranges;
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String text(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
