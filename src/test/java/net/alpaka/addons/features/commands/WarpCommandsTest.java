package net.alpaka.addons.features.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpCommandsTest {

    @Test
    void shortensDestinationsTheServerDoesNotKnow() {
        Set<String> known = Set.of("hub", "garden", "party", "warp");
        assertEquals("warp isle", WarpShortcut.rewrite("isle", known::contains, false));
        assertEquals("warp dh", WarpShortcut.rewrite("DH", known::contains, false));
        assertEquals("warp crimson", WarpShortcut.rewrite(" crimson ", known::contains, false));
        // The server's own commands keep their meaning.
        assertNull(WarpShortcut.rewrite("hub", known::contains, false));
        assertNull(WarpShortcut.rewrite("garden", known::contains, false));
        // Not a destination, or more than a name.
        assertNull(WarpShortcut.rewrite("party warp", known::contains, false));
        assertNull(WarpShortcut.rewrite("isle 2", known::contains, false));
        assertNull(WarpShortcut.rewrite("pv", known::contains, false));
    }

    @Test
    void barnTeleportsToTheBarnOnTheGarden() {
        Set<String> known = Set.of("warp");
        assertEquals("plottp barn", WarpShortcut.rewrite("barn", known::contains, true));
        assertEquals("warp barn", WarpShortcut.rewrite("barn", known::contains, false));
        assertEquals("warp isle", WarpShortcut.rewrite("isle", known::contains, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"warp isle", "is", "hub", "warpforge", "WARP hub", "garden"})
    void recognisesWarps(String command) {
        assertTrue(WarpCooldownQueue.isWarp(command));
    }

    @ParameterizedTest
    @ValueSource(strings = {"party warp", "pc hi", "warpout", "visit Friend"})
    void leavesOtherCommandsAlone(String command) {
        assertFalse(WarpCooldownQueue.isWarp(command));
    }

    @Test
    void readsHypixelsRefusal() {
        var matcher = WarpCooldownQueue.REFUSAL.matcher("You may only use this command after 4s on the server!");
        assertTrue(matcher.matches());
        assertEquals("4", matcher.group("seconds"));
        assertFalse(WarpCooldownQueue.REFUSAL.matcher("[MVP+] Someone: You may only use this command after 4s on the server!").matches());
    }
}
