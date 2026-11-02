package net.alpaka.addons.features.profile;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildItemSearchServiceTest {
    @Test
    void rateLimitWaitUsesTheLaterOfRetryAfterAndProviderReset() {
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of("60"),
                "X-RateLimit-Reset", List.of("1300")), (a, b) -> true);
        assertEquals(1_300_000L, GuildItemSearchService.retryAt(headers, 1_000_000L));
    }

    @Test
    void supportsHttpDatesAndMissingHeaders() {
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of("Wed, 21 Oct 2015 07:28:00 GMT")), (a, b) -> true);
        assertEquals(1445412480000L, GuildItemSearchService.retryAt(headers, 0));
        assertEquals(10L, GuildItemSearchService.retryAt(HttpHeaders.of(Map.of(), (a, b) -> true), 10));
    }
}
