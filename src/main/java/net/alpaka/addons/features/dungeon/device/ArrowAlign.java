package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;

/**
 * Arrow Align (section 3): matches the arrows in the 5 x 5 item frames at X -2 against the nine
 * known layouts and labels each frame with the right-clicks it still needs, a little in front of
 * the frame. The player's own clicks (onOwnEntityUse) may count down the label until the server's
 * update arrives; no click is ever refused.
 *
 * Spec: terminals.md 4.3 and its Critic additions A (the nine patterns, Odin data, BSD-3).
 *
 * Absolute F7/M7 boss coordinates; ticked only during the Goldor phase ({@link GoldorPhase#inP3()}),
 * and draws only near its own device. Not built yet: until then it draws nothing.
 */
public final class ArrowAlign extends DungeonSolver {

    @Override
    public String name() {
        return "Arrow Align";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveArrowAlign;
    }
}
