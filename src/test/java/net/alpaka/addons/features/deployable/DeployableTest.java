package net.alpaka.addons.features.deployable;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployableTest {

    @Test
    void orbsAreFoundByTheirNameTag() {
        assertEquals(Deployable.PLASMAFLUX, Deployable.orbFromName("Plasmaflux"));
        assertEquals(Deployable.MANA_FLUX, Deployable.orbFromName("Mana Flux"));
        // A flare's name never stands on an orb's tag.
        assertNull(Deployable.orbFromName("SOS Flare"));
        assertNull(Deployable.orbFromName("Zombie"));
    }

    @Test
    void flaresAreFoundByTheirSkin() {
        String sos = new String(Base64.getDecoder().decode(Deployable.SOS_FLARE.textureValue()), StandardCharsets.UTF_8);
        assertEquals(Deployable.SOS_FLARE, Deployable.flareFromTexture(sos));
        String orb = new String(Base64.getDecoder().decode(Deployable.OVERFLUX.textureValue()), StandardCharsets.UTF_8);
        assertNull(Deployable.flareFromTexture(orb));
    }

    @Test
    void theStrongerBuffWins() {
        assertTrue(Deployable.PLASMAFLUX.beats(Deployable.SOS_FLARE));
        assertTrue(Deployable.SOS_FLARE.beats(Deployable.OVERFLUX));
        assertTrue(Deployable.WARNING_FLARE.beats(Deployable.RADIANT));
        assertFalse(Deployable.RADIANT.beats(Deployable.MANA_FLUX));
        assertTrue(Deployable.RADIANT.beats(null));
    }

    @Test
    void timeReadsInMinutesFromAMinuteOn() {
        assertEquals("45s", Deployable.formatTime(45));
        assertEquals("1m 00s", Deployable.formatTime(60));
        assertEquals("2m 59s", Deployable.formatTime(179));
    }
}
