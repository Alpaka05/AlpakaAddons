package net.alpaka.addons.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's keys, which are set in the config menu next to the feature they belong to instead of in
 * vanilla's Controls.
 *
 * A mapping registered here is never handed to Fabric, so Controls does not list it and options.txt
 * does not store it. The game still presses it like any other: a KeyMapping enters KeyMapping's own
 * tables when it is constructed, and those are what the keyboard and mouse handlers look keys up in.
 * The bound keys are kept in alpaka.json instead ({@link AlpakaConfig#keyBinds}); keys bound in
 * Controls before are taken over from options.txt once.
 */
public final class AlpakaKeyBinds {
    private AlpakaKeyBinds() {
    }

    private static final List<KeyMapping> MAPPINGS = new ArrayList<>();

    /** Keys that only act inside one of the mod's menus, by the menu they belong to; see {@link #registerMenuKey}. */
    private static final Map<KeyMapping, String> MENU_KEYS = new HashMap<>();

    /** options.txt's key lines, read once for the takeover; null until needed. */
    private static Map<String, String> legacy;

    /** Makes the mapping one of the mod's keys and binds it to the key saved for it. */
    public static <T extends KeyMapping> T register(T mapping) {
        MAPPINGS.add(mapping);
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.keyBindsImported && !config.keyBinds.containsKey(mapping.getName())) {
            String old = legacy().get("key_" + mapping.getName());
            if (old != null && !old.equals(mapping.getDefaultKey().getName())) {
                config.keyBinds.put(mapping.getName(), old);
            }
        }
        apply(mapping);
        KeyMapping.resetMapping();
        return mapping;
    }

    /**
     * Like {@link #register}, for a key that only acts inside one of the mod's menus (named by
     * menu), such as the market screen's. Such a key clashes only with the other keys of that menu
     * and with the inventory key, which closes it; the game's keys do nothing there.
     */
    public static <T extends KeyMapping> T registerMenuKey(T mapping, String menu) {
        MENU_KEYS.put(mapping, menu);
        return register(mapping);
    }

    /**
     * Called once every key is registered: the takeover from options.txt is done, and is not repeated
     * on the next start, when vanilla has already dropped the lines it no longer knows.
     */
    public static void finishRegistration() {
        legacy = null;
        if (AlpakaConfig.instance.keyBindsImported) return;
        AlpakaConfig.instance.keyBindsImported = true;
        AlpakaConfig.save();
    }

    /** Binds every key to what the config holds now; after the settings were reset, say. */
    public static void applyAll() {
        for (KeyMapping mapping : MAPPINGS) apply(mapping);
        KeyMapping.resetMapping();
    }

    /** Binds the key and saves it. The default key is saved as no entry, so a changed default reaches it. */
    public static void set(KeyMapping mapping, InputConstants.Key key) {
        if (key.equals(mapping.getDefaultKey())) {
            AlpakaConfig.instance.keyBinds.remove(mapping.getName());
        } else {
            AlpakaConfig.instance.keyBinds.put(mapping.getName(), key.getName());
        }
        mapping.setKey(key);
        KeyMapping.resetMapping();
        AlpakaConfig.save();
    }

    /**
     * The other key mapping, the mod's or the game's, bound to the same key; null if there is none.
     * Both still work, as in vanilla, but one press then does two things.
     */
    public static KeyMapping conflict(KeyMapping mapping) {
        if (mapping.isUnbound()) return null;
        String menu = MENU_KEYS.get(mapping);
        Minecraft mc = Minecraft.getInstance();
        if (menu != null) {
            for (KeyMapping other : MAPPINGS) {
                if (other != mapping && menu.equals(MENU_KEYS.get(other)) && mapping.same(other)) return other;
            }
            return mc.options != null && mapping.same(mc.options.keyInventory) ? mc.options.keyInventory : null;
        }
        for (KeyMapping other : MAPPINGS) {
            if (other != mapping && !MENU_KEYS.containsKey(other) && mapping.same(other)) return other;
        }
        if (mc.options == null) return null;
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != mapping && mapping.same(other)) return other;
        }
        return null;
    }

    private static void apply(KeyMapping mapping) {
        String stored = AlpakaConfig.instance.keyBinds.get(mapping.getName());
        InputConstants.Key key = mapping.getDefaultKey();
        if (stored != null) {
            try {
                key = InputConstants.getKey(stored);
            } catch (RuntimeException e) {
                // A hand-edited or unknown name; the default is better than no key at all.
                AlpakaAddons.LOGGER.warn("Unknown key '{}' for {}, using its default", stored, mapping.getName());
            }
        }
        mapping.setKey(key);
    }

    /** The key lines of options.txt, "key_key.alpaka.zoom" to "key.keyboard.c". */
    private static Map<String, String> legacy() {
        if (legacy != null) return legacy;
        legacy = new HashMap<>();
        Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        if (!Files.isRegularFile(options)) return legacy;
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.startsWith("key_key.alpaka.")) {
                    legacy.put(line.substring(0, colon), line.substring(colon + 1).trim());
                }
            }
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Could not read options.txt for the keys bound in Controls", e);
        }
        return legacy;
    }
}
