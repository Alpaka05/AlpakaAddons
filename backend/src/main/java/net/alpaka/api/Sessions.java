package net.alpaka.api;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The players logged in: a random token for each, handed out once Mojang confirmed the account.
 * Kept in memory only; after a restart the mod simply logs in again.
 */
final class Sessions {
    record Session(String uuid, long expiresAt) {}

    static final long LIFETIME_MS = 12 * 60 * 60_000L;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> byToken = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    Sessions(LongSupplier clock) {
        this.clock = clock;
    }

    String issue(String uuid) {
        long now = clock.getAsLong();
        byToken.values().removeIf(s -> s.expiresAt() <= now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byToken.put(token, new Session(uuid, now + LIFETIME_MS));
        return token;
    }

    /** The player a token belongs to, or null for an unknown or expired one. */
    String uuidOf(String token) {
        if (token == null || token.isEmpty()) return null;
        Session s = byToken.get(token);
        if (s == null) return null;
        if (s.expiresAt() <= clock.getAsLong()) {
            byToken.remove(token);
            return null;
        }
        return s.uuid();
    }

    int size() {
        return byToken.size();
    }
}
