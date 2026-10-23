package net.alpaka.addons.client.gui;

import net.alpaka.addons.config.AlpakaConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bringing stored values back into their slider's range, which the game does once at start-up. */
class SliderClampTest {
    private float stored;
    private final List<Float> written = new ArrayList<>();

    private ConfigOption slider(float min, float max) {
        return new ConfigOption("test", "Test", "", ConfigCategory.GENERAL,
                () -> stored, value -> { written.add(value); stored = value; }, min, max, null, "");
    }

    @Test
    void aValueOutsideTheRangeIsBroughtBack() {
        stored = 20.0f;
        assertTrue(slider(0.0f, 10.0f).clampStoredValue());
        assertEquals(10.0f, stored);

        stored = -3.0f;
        assertTrue(slider(1.0f, 15.0f).clampStoredValue());
        assertEquals(1.0f, stored);
    }

    @Test
    void aValueInsideIsNeverWritten() {
        stored = 4.37f;
        assertFalse(slider(1.0f, 15.0f).clampStoredValue());
        assertTrue(written.isEmpty(), "a setter's rounding must not touch a value that is fine");
    }

    @Test
    void everyDefaultLiesInsideItsSlider() {
        AlpakaConfig.instance = new AlpakaConfig();
        for (ConfigCategory category : ConfigCategory.values()) {
            for (ConfigOption option : AlpakaConfigRegistry.getOptions(category, "")) {
                if (option.getType() != ConfigOption.Type.SLIDER) continue;
                assertFalse(option.clampStoredValue(), option.getId() + " starts outside its slider, so the clamp would change a default");
            }
        }
    }
}
