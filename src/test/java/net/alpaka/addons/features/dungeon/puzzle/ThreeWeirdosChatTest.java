package net.alpaka.addons.features.dungeon.puzzle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Three Weirdos solver's chat side: what it keeps from the NPC lines and when it lets go of them. */
class ThreeWeirdosChatTest {

    @Test
    void keepsEachWeirdosStatement() {
        ThreeWeirdos solver = new ThreeWeirdos();
        assertNull(solver.debugState());
        solver.onChat("[NPC] Hugo: The reward is not in my chest. They are both lying.");
        solver.onChat("[NPC] Benson: One of us is telling the truth!");
        // Party chat and other NPCs change nothing.
        solver.onChat("Party > [MVP+] Foo: [NPC] Rose: The reward is not in my chest!");
        solver.onChat("[NPC] Mort: Good luck.");
        // Without the room the slots are not known yet, so nothing is decided.
        assertEquals("Hugo=empty @?, Benson=empty @? -> not known yet", solver.debugState());
    }

    @Test
    void aFailForgetsTheStatements() {
        ThreeWeirdos solver = new ThreeWeirdos();
        solver.onChat("[NPC] Hugo: The reward is not in my chest. They are both lying.");
        solver.onChat("PUZZLE FAIL! Alpaka05 was fooled by Hope! Yikes!");
        assertNull(solver.debugState());
    }

    @Test
    void aSolveForgetsTheStatements() {
        ThreeWeirdos solver = new ThreeWeirdos();
        solver.onChat("[NPC] Rose: At least one of them is lying, and the reward is not in Hugo's chest!");
        assertTrue(solver.debugState().startsWith("Rose=reward"));
        solver.onChat("PUZZLE SOLVED! Alpaka05 wasn't fooled by Rose! Good job!");
        assertNull(solver.debugState());
    }

    @Test
    void otherPuzzlesResultsDoNotCount() {
        ThreeWeirdos solver = new ThreeWeirdos();
        solver.onChat("[NPC] Hugo: The reward is not in my chest. They are both lying.");
        solver.onChat("PUZZLE SOLVED! Alpaka05 tied Tic Tac Toe! Good job!");
        solver.onChat("PUZZLE FAIL! Alpaka05 killed a Blaze in the wrong order! Yikes!");
        assertTrue(solver.debugState().startsWith("Hugo=empty"));
        solver.reset();
        assertNull(solver.debugState());
    }

    @Test
    void aWeirdoChangingTheirStoryKeepsTheLastLine() {
        // Only possible after an Architect's First Draft that came without a FAIL line; the newest line wins.
        ThreeWeirdos solver = new ThreeWeirdos();
        solver.onChat("[NPC] Hugo: The reward is in my chest.");
        solver.onChat("[NPC] Hugo: The reward is not in my chest!");
        assertEquals("Hugo=reward @? -> not known yet", solver.debugState());
    }
}
