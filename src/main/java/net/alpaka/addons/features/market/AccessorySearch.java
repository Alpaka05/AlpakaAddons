package net.alpaka.addons.features.market;

import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Accessory Bag's search: Hypixel asks for it on the same sign as the markets' searches, and
 * the input bar then offers every accessory in the game instead of every item, as nothing else
 * finds anything there. Which items are accessories, Hypixel's item list says.
 */
public final class AccessorySearch {
    private AccessorySearch() {}

    /** "Accessory Bag", or "Accessory Bag (1/6)" with more than one page. */
    private static final Pattern TITLE = Pattern.compile("^Accessory Bag(?: \\(\\d{1,4}/\\d+\\))?$");
    /** How long after the bag closed a sign still counts as its search: Hypixel may close the chest before it opens the sign. */
    private static final long RECENT_MS = 3000L;

    private static long leftAtMs = 0L;

    /** Accessory names and their ids, and the list they were built from. Render thread only. */
    private static Map<String, String> byName = null;
    private static List<String> names = null;
    private static Map<String, String> builtFrom = null;

    static boolean isBagTitle(String plainTitle) {
        return plainTitle != null && TITLE.matcher(plainTitle.trim()).matches();
    }

    /** Lets the bag count as just left when its menu closes, for the sign Hypixel opens next. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> && isBagTitle(SkyblockUtils.cleanColor(screen.getTitle().getString()))) {
                ScreenEvents.remove(screen).register(s -> leftAtMs = System.currentTimeMillis());
            }
        });
    }

    /** Whether a sign opening now is the Accessory Bag's search: the bag is open, or closed a moment ago. */
    static boolean from(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> && isBagTitle(SkyblockUtils.cleanColor(screen.getTitle().getString()))) {
            return true;
        }
        return System.currentTimeMillis() - leftAtMs < RECENT_MS;
    }

    /** Every accessory's name, Rift ones left out; null while Hypixel's list loads or Network Features is off. */
    static List<String> names() {
        Map<String, String> accessories = ItemIcons.accessories();
        if (accessories == null) return null;
        build(accessories);
        return names;
    }

    /** The id to show an accessory's icon by; null for a name that is no accessory's, such as a typed part of one. */
    static String tag(String name) {
        Map<String, String> map = byName;
        return map == null || name == null ? null : map.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static void build(Map<String, String> accessories) {
        if (builtFrom == accessories) return;
        Map<String, String> map = new LinkedHashMap<>();
        List<String> list = new ArrayList<>();
        for (String id : accessories.keySet().stream().sorted().toList()) {
            String name = ItemIcons.name(id);
            if (name == null || name.isBlank()) continue;
            if (map.putIfAbsent(name.toLowerCase(Locale.ROOT), id) == null) list.add(name);
        }
        byName = map;
        names = List.copyOf(list);
        builtFrom = accessories;
    }
}
