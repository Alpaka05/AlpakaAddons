package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;

/**
 * Simon Says (section 1): remembers the sequence the 4 x 4 display at X 111 flashes (obsidian
 * turning to sea lantern and back, from onBlockChange) and marks the buttons at X 110 to press next.
 * No sound per step, and no click is ever refused.
 *
 * Spec: terminals.md 4.1 (positions, mechanics, the first-round quirk; four rounds since 0.27.2).
 *
 * Absolute F7/M7 boss coordinates; ticked only during the Goldor phase ({@link GoldorPhase#inP3()}),
 * and draws only near its own device. Not built yet: until then it draws nothing.
 */
public final class SimonSays extends DungeonSolver {

    @Override
    public String name() {
        return "Simon Says";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveSimonSays;
    }
}
