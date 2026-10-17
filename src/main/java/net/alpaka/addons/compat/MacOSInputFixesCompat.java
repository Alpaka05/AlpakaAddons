package net.alpaka.addons.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells apart the two copies of a Tab press that MacOS Input Fixes makes on a Mac.
 *
 * That mod reads Tab and Escape from macOS itself and feeds them into KeyboardHandler.keyPress a
 * second time; the press GLFW delivered is cancelled at the head of keyPress, so the game acts on
 * its copy alone. A hook of our own at the same head sees both, and the Peek Chat tab cycling went
 * two tabs a press: past one, then back as it wrapped around. In the chat screen, which only sees
 * what keyPress lets through, Tab already stepped one tab at a time.
 *
 * Mirrors that mod's own check: on a Mac, with its keyboard hook in place (it puts it there whenever
 * the game runs on GLFW), a Tab or Escape arriving outside its re-send is the one to be cancelled.
 *
 * MacOS Input Fixes is not a dependency: its class is only looked up, by name, when it is installed.
 */
public final class MacOSInputFixesCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/MacOSInputFixes");

    private static boolean resolved;
    private static MethodHandle allowInput;

    private MacOSInputFixesCompat() {}

    /** Whether this key press is the copy MacOS Input Fixes is about to cancel, and is to be ignored. */
    public static boolean isDiscardedCopy(int key) {
        if (key != GLFW.GLFW_KEY_TAB && key != GLFW.GLFW_KEY_ESCAPE) return false;
        if (!resolve()) return false;
        try {
            return !(boolean) allowInput.invokeExact();
        } catch (Throwable e) {
            LOGGER.warn("Could not ask MacOS Input Fixes whether a key press is its own", e);
            allowInput = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) return allowInput != null;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("macos_input_fixes")) return false;
        try {
            Class<?> common = Class.forName("com.hamarb123.macos_input_fixes.client.Common");
            if (!common.getField("IS_SYSTEM_MAC").getBoolean(null)) return false;
            allowInput = MethodHandles.publicLookup().findStatic(common, "allowInputOSX2",
                    MethodType.methodType(boolean.class));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("MacOS Input Fixes is installed but its API was not found; Tab may cycle chat tabs twice while peeking", e);
            allowInput = null;
            return false;
        }
    }
}
