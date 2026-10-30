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
    void timedTagsAreFoundByTheirName() {
        assertEquals(Deployable.PLASMAFLUX, Deployable.fromTagName("Plasmaflux"));
        assertEquals(Deployable.MANA_FLUX, Deployable.fromTagName("Mana Flux"));
        assertEquals(Deployable.GLACITE_LANTERN, Deployable.fromTagName("Glacite Lantern"));
        assertEquals(Deployable.WILL_O_WISP, Deployable.fromTagName("Will-o'-wisp"));
        assertEquals(Deployable.UMBERELLA, Deployable.fromTagName("Umberella"));
        // A flare's name never stands on a tag, and the totem and black hole are read on their own.
        assertNull(Deployable.fromTagName("SOS Flare"));
        assertNull(Deployable.fromTagName("Black Hole"));
        assertNull(Deployable.fromTagName("Totem of Corruption"));
        assertNull(Deployable.fromTagName("Zombie"));
    }

    @Test
    void lanternsRankByTier() {
        assertTrue(Deployable.WILL_O_WISP.beats(Deployable.GLACITE_LANTERN));
        assertTrue(Deployable.GLACITE_LANTERN.beats(Deployable.TITANIUM_LANTERN));
        assertTrue(Deployable.MITHRIL_LANTERN.beats(Deployable.DWARVEN_LANTERN));
        assertTrue(Deployable.GLACITE_LANTERN.wholeMineshaft());
        assertFalse(Deployable.TITANIUM_LANTERN.wholeMineshaft());
    }

    @Test
    void everyHeadHasATexture() {
        for (Deployable d : Deployable.values()) {
            if (d == Deployable.TOTEM_OF_CORRUPTION) assertNull(d.textureValue());
            else assertTrue(d.textureValue() != null && !d.textureValue().isEmpty(), d.name());
        }
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
