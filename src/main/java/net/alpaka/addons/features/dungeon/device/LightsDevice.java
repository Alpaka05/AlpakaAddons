package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;

/**
 * Lights (section 2): marks the levers on the wall at Z 142 that still have to be switched.
 * Which model is current (six levers all on, or a lamp grid) is still open.
 *
 * Spec: terminals.md 4.2 and its Critic additions B.
 *
 * Absolute F7/M7 boss coordinates; ticked only during the Goldor phase ({@link GoldorPhase#inP3()}),
 * and draws only near its own device. Not built yet: until then it draws nothing.
 */
public final class LightsDevice extends DungeonSolver {

    @Override
    public String name() {
        return "Lights";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveLightsDevice;
    }
}
