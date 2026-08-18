package net.alpaka.addons.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;

/**
 * Version strings for display in menus, read from the running build rather than hard-coded so a
 * label can never drift out of date after a version bump.
 */
public final class ModVersion {
    private static final String MOD_ID = "alpaka";

    private static String modVersion;
    private static String minecraftVersion;

    private ModVersion() {}

    /** The mod's own version, e.g. {@code 1.0.64}, taken from the jar's fabric.mod.json. */
    public static String mod() {
        if (modVersion == null) {
            modVersion = FabricLoader.getInstance()
                    .getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        }
        return modVersion;
    }

    /** The Minecraft version the game is running, e.g. {@code 26.1.2}. */
    public static String minecraft() {
        if (minecraftVersion == null) {
            minecraftVersion = SharedConstants.getCurrentVersion().name();
        }
        return minecraftVersion;
    }
}
