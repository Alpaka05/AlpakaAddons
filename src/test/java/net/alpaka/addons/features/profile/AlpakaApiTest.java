package net.alpaka.addons.features.profile;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpakaApiTest {
    @Test
    void cloudflaresPagesAreToldFromTheApisAnswers() {
        // Cloudflare's 502 is a page, not the API's JSON: lost on the way, worth one more try.
        assertTrue(AlpakaApi.onTheWay(502, null));
        assertTrue(AlpakaApi.onTheWay(524, JsonParser.parseString("\"<html>\"")));
        // The API's own 502 says why, and is no failure on the way.
        assertFalse(AlpakaApi.onTheWay(502, JsonParser.parseString("{\"success\":false,\"cause\":\"Hypixel can't be reached\"}")));
        assertFalse(AlpakaApi.onTheWay(404, null));
        assertEquals("Couldn't get through to the AlpakaAddons API just now (502). Try again in a moment.", AlpakaApi.reason(502, null));
        assertEquals("Hypixel can't be reached. Try again in a minute.",
                AlpakaApi.reason(502, JsonParser.parseString("{\"success\":false,\"cause\":\"Hypixel can't be reached\"}")));
    }
}
