package net.alpaka.addons.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the blurred panels out of Gnetum's HUD cache.
 *
 * Gnetum draws the HUD into a framebuffer of its own every few frames and blits that over the
 * world in between. While it draws, its framebuffer stands in for the game's main target, so a
 * blur panel drawn there copied Gnetum's empty buffer instead of the world: the chat went black,
 * and the inventory HUD flickered as its cached and uncached frames took turns.
 *
 * Gnetum has an answer for elements that cannot be cached - it already turns caching off by itself
 * for elements with a blend it cannot reproduce - and an element with caching off is drawn in the
 * frame's own GUI pass, where the blur works. A panel asks for that here. A HUD the player forced
 * into the cache in Gnetum's settings stays cached; its panel then draws without blur.
 *
 * Gnetum is not a dependency: its class is only looked up, by name, when the mod is installed.
 */
public final class GnetumCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/Gnetum");
    private static final String REASON = "AlpakaAddons blur panel samples the world behind it";

    private static boolean resolved;
    private static MethodHandle rendering;
    private static MethodHandle uncached;
    private static MethodHandle forceCached;
    private static MethodHandle disable;

    private GnetumCompat() {}

    /**
     * Called as a blur panel is submitted: if Gnetum is drawing a cached HUD element right now,
     * that element is taken out of the cache, so this and every later panel of it reach the
     * frame's own GUI pass.
     */
    public static void keepOutOfCache() {
        if (!resolve()) return;
        try {
            if (!(boolean) rendering.invokeExact()) return;
            if ((boolean) uncached.invokeExact() || (boolean) forceCached.invokeExact()) return;
            disable.invokeExact(REASON);
        } catch (Throwable e) {
            LOGGER.warn("Could not take a blur panel out of Gnetum's HUD cache", e);
            rendering = null;
        }
    }

    /**
     * Whether Gnetum is drawing a cached HUD element right now, into its own framebuffer: what is
     * submitted lands in its HUD image, which is drawn over the HUD elements it does not cache.
     */
    public static boolean isCaching() {
        if (!resolve()) return false;
        try {
            return (boolean) rendering.invokeExact() && !(boolean) uncached.invokeExact();
        } catch (Throwable e) {
            LOGGER.warn("Could not ask Gnetum whether it caches the current HUD element", e);
            rendering = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) return rendering != null;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("gnetum")) return false;
        try {
            Class<?> gnetum = Class.forName("me.decce.gnetum.Gnetum");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType bool = MethodType.methodType(boolean.class);
            rendering = lookup.findStaticGetter(gnetum, "rendering", boolean.class);
            uncached = lookup.findStatic(gnetum, "isCurrentElementUncached", bool);
            forceCached = lookup.findStatic(gnetum, "isCurrentElementForceCached", bool);
            disable = lookup.findStatic(gnetum, "disableCachingForCurrentElement",
                    MethodType.methodType(void.class, String.class));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Gnetum is installed but its API was not found; blur panels may stay cached", e);
            rendering = null;
            return false;
        }
    }
}
