package net.alpaka.addons.config;

import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Settings saved under a feature's old name still load after the rename. Checked on the annotation
 * Gson reads, because AlpakaConfig itself cannot be initialized outside the game.
 */
class RenamedSettingsTest {

    private static SerializedName name(String field) throws Exception {
        Class<?> cfg = Class.forName("net.alpaka.addons.config.AlpakaConfig", false,
                RenamedSettingsTest.class.getClassLoader());
        return cfg.getField(field).getAnnotation(SerializedName.class);
    }

    @Test
    void ragAxeCountdownCarriesOverToRagnarock() throws Exception {
        SerializedName name = name("ragnarockEnabled");
        assertNotNull(name);
        assertEquals("ragnarockEnabled", name.value());
        assertTrue(Arrays.asList(name.alternate()).contains("ragAxeCountdownEnabled"));
    }
}
