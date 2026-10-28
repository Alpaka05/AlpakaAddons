package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import org.junit.jupiter.api.Test;

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
    void chronomatronRecordsStepsAndUsesThemUp() {
        ExperimentSolver.Chronomatron chrono = new ExperimentSolver.Chronomatron();
        // Step one lights a column of two, step two a single block, then the same block again.
        chrono.onShown(20, true, false);
        chrono.onShown(29, true, false);
        chrono.onShown(20, false, true);
        chrono.onShown(29, false, true);
        chrono.onShown(22, true, false);
        chrono.onShown(22, false, true);
        chrono.onShown(22, true, false);
        chrono.onShown(22, false, true);

        assertEquals(NOW, chrono.highlight(20));
        assertEquals(NOW, chrono.highlight(29));
        assertEquals(LATER, chrono.highlight(22));

        chrono.onClicked(23);
        assertEquals(NOW, chrono.highlight(20), "a wrong click changes nothing");
        chrono.onClicked(29);
        assertEquals(NOW, chrono.highlight(22));
        assertEquals(0, chrono.highlight(20));
        chrono.onClicked(22);
        chrono.onClicked(22);
        assertEquals(0, chrono.highlight(22));
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
