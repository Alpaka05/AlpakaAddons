package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExperimentSolverTest {
    private static final int NOW = AlpakaConfig.instance.experimentSolverNextColor;
    private static final int LATER = AlpakaConfig.instance.experimentSolverLaterColor;

    @Test
    void gamesByTitle() {
        assertEquals(ExperimentSolver.Game.CHRONOMATRON, ExperimentSolver.gameOf("Chronomatron (Supreme)"));
        assertEquals(ExperimentSolver.Game.ULTRASEQUENCER, ExperimentSolver.gameOf("Ultrasequencer (Metaphysical)"));
        assertEquals(ExperimentSolver.Game.SUPERPAIRS, ExperimentSolver.gameOf("Superpairs (Transcendent)"));
        assertNull(ExperimentSolver.gameOf("Experimentation Table"));
    }

    @Test
    void chronomatronRecordsColorsAndUsesThemUp() {
        ExperimentSolver.Chronomatron chrono = new ExperimentSolver.Chronomatron();
        // Round one: red lights a column of two. Then the player answers.
        chrono.onShown(20, "red");
        chrono.onShown(29, "red");
        chrono.onShown(20, null);
        chrono.onShown(29, null);
        chrono.startAnswering();
        assertEquals(NOW, chrono.highlight("red"));
        assertEquals(0, chrono.highlight("blue"));
        chrono.onClicked("red");
        assertEquals(0, chrono.highlight("red"));

        // Round two replays red and adds blue twice; blue lights before red has gone dark.
        chrono.startShowing();
        chrono.onShown(20, "red");
        chrono.onShown(29, "red");
        chrono.onShown(22, "blue");
        chrono.onShown(20, null);
        chrono.onShown(29, null);
        chrono.onShown(22, null);
        chrono.onShown(22, "blue");
        chrono.onShown(22, null);
        chrono.startAnswering();
        assertEquals(List.of("red", "blue", "blue"), chrono.sequence);

        assertEquals(NOW, chrono.highlight("red"));
        assertEquals(LATER, chrono.highlight("blue"));
        chrono.onClicked("green");
        assertEquals(NOW, chrono.highlight("red"), "a wrong click changes nothing");
        chrono.onClicked("red");
        assertEquals(NOW, chrono.highlight("blue"));
        chrono.onClicked("blue");
        assertEquals(NOW, chrono.highlight("blue"));
        chrono.onClicked("blue");
        assertEquals(0, chrono.highlight("blue"));
    }

    @Test
    void chronomatronRecordsAgainWhereTheReplayDisagrees() {
        ExperimentSolver.Chronomatron chrono = new ExperimentSolver.Chronomatron();
        chrono.onShown(20, "red");
        chrono.onShown(20, null);
        chrono.onShown(21, "lime");
        chrono.onShown(21, null);
        chrono.startAnswering();

        // The next replay starts with another color, as when the menu was opened mid-game.
        chrono.startShowing();
        chrono.onShown(23, "blue");
        chrono.onShown(23, null);
        chrono.startAnswering();
        assertEquals(List.of("blue"), chrono.sequence);
    }

    @Test
    void chronomatronIgnoresTheClickedBlockLightingUp() {
        // Recorded on Hypixel: the block the player clicks last turns terracotta without glint
        // just after the next pattern has started; it is no step of that pattern.
        ExperimentSolver.Chronomatron chrono = new ExperimentSolver.Chronomatron();
        chrono.onShown(20, "pink");
        chrono.onShown(20, null);
        chrono.startAnswering();
        chrono.onClicked("pink");

        chrono.startShowing();
        chrono.onShown(20, null); // the clicked block, lit without glint
        chrono.onShown(20, null);
        chrono.onShown(20, "pink");
        chrono.onShown(20, null);
        chrono.onShown(24, "red");
        chrono.onShown(24, null);
        chrono.startAnswering();
        assertEquals(List.of("pink", "red"), chrono.sequence);
        assertEquals(NOW, chrono.highlight("pink"));
        assertEquals(LATER, chrono.highlight("red"));
    }

    @Test
    void ultrasequencerClicksInOrder() {
        ExperimentSolver.Ultrasequencer ultra = new ExperimentSolver.Ultrasequencer();
        ultra.onShown(30, 2, null);
        ultra.onShown(12, 1, null);
        ultra.onShown(40, 3, null);
        assertEquals(NOW, ultra.highlight(12));
        assertEquals(LATER, ultra.highlight(30));
        assertEquals(0, ultra.highlight(40));
        ultra.onClicked(30);
        assertEquals(NOW, ultra.highlight(12), "clicking out of order does not advance");
        ultra.onClicked(12);
        assertEquals(NOW, ultra.highlight(30));
        assertEquals(LATER, ultra.highlight(40));
    }

    @Test
    void ultrasequencerSlotForgetsLastRoundsNumber() {
        ExperimentSolver.Ultrasequencer ultra = new ExperimentSolver.Ultrasequencer();
        ultra.onShown(12, 1, null);
        ultra.onShown(12, 4, null);
        assertEquals(1, ultra.slotByNumber.size());
        assertEquals(12, ultra.slotByNumber.get(4));
    }
}
