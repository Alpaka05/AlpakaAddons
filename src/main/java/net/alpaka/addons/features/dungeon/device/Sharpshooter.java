package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;

/**
 * Sharpshooter (section 4): marks the lit target (emerald among the blue terracotta at Z 50) and
 * the ones already hit. No aim points between targets (critic.md C7).
 *
 * Spec: terminals.md 4.4.
 *
 * Absolute F7/M7 boss coordinates; ticked only during the Goldor phase ({@link GoldorPhase#inP3()}),
 * and draws only near its own device. Not built yet: until then it draws nothing.
 */
public final class Sharpshooter extends DungeonSolver {

    @Override
    public String name() {
        return "Sharpshooter";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveSharpshooter;
    }
}
