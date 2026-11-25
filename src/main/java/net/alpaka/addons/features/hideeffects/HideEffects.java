package net.alpaka.addons.features.hideeffects;

import java.util.function.BooleanSupplier;
import net.alpaka.addons.config.AlpakaConfig;

/**
 * Hide Effects: the inventory pretends it has no effect list at all, so item list mods (REI, EMI,
 * JEI) stop keeping that strip free and the list fills it. The in-game HUD asks the same question
 * to decide whether its own effect icons are redundant; it gets vanilla's answer through
 * {@link #askVanilla}, so the icons stay hidden behind an open inventory as before.
 */
public final class HideEffects {

    private static boolean vanillaAnswer;

    private HideEffects() {
    }

    /** Whether the inventory should report that it shows no effects. */
    public static boolean hidesInventoryList() {
        return AlpakaConfig.instance.hideInventoryEffects && !vanillaAnswer;
    }

    /** Runs {@code query} with the inventory reporting its effect list as vanilla would. */
    public static boolean askVanilla(BooleanSupplier query) {
        boolean outer = vanillaAnswer;
        vanillaAnswer = true;
        try {
            return query.getAsBoolean();
        } finally {
            vanillaAnswer = outer;
        }
    }
}
