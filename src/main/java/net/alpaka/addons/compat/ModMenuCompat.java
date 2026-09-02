package net.alpaka.addons.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The one place that names a Mod Menu class.
 *
 * Mod Menu is a compile-time dependency only. A reference to {@code ModsScreen} inside one of the
 * mod's own screens - even inside a button callback wrapped in try/catch - is not a runtime lookup
 * the catch could intercept: the JVM has to load ModsScreen to verify the enclosing class, so the
 * whole screen fails with a NoClassDefFoundError before any of its code runs. In the dev client,
 * where Mod Menu was absent, that took the pause screen down the moment it opened.
 *
 * Keeping the reference in this class alone, and only ever calling in here after
 * {@link #isLoaded()} said yes, means the class is never loaded without the mod being present.
 */
public final class ModMenuCompat {

    private ModMenuCompat() {}

    /** Whether Mod Menu is installed. Deliberately does not touch any Mod Menu class. */
    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("modmenu");
    }

    /** Opens Mod Menu's mod list over {@code parent}. Only call when {@link #isLoaded()} is true. */
    public static void openModsScreen(Screen parent) {
        ModsScreenOpener.open(parent);
    }

    /** Separate class so that ModMenuCompat itself verifies without Mod Menu on the classpath. */
    private static final class ModsScreenOpener {
        static void open(Screen parent) {
            Minecraft.getInstance().gui.setScreen(new com.terraformersmc.modmenu.gui.ModsScreen(parent));
        }
    }
}
