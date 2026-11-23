package net.alpaka.api;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The server ids a login may use, handed out by this server, each good for one login within a
 * minute.
 *
 * Without them any id Mojang confirms would do, and joining any online-mode server makes the game
 * confirm one: that server's own hash, which the server knows. Its owner could then log in here as
 * every player who joined it. An id only this server hands out, used once, can't come from there.
 *
 * The other way round, the mod doesn't tell Mojang the challenge itself but {@link #loginServerId}
 * of it: otherwise whoever answers in this server's name could hand out a real server's hash as a
 * challenge and be let into that server as the player.
 */
final class Challenges {
    static final long LIFETIME_MS = 60_000L;
    /** More open challenges than this means someone is asking for them in bulk. */
    static final int MAX_OPEN = 10_000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> open = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    Challenges(LongSupplier clock) {
        this.clock = clock;
    }

    /** A new challenge, or null while too many are open. */
    String issue() {
        long now = clock.getAsLong();
        if (open.size() >= MAX_OPEN) open.values().removeIf(until -> until <= now);
        if (open.size() >= MAX_OPEN) return null;
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        open.put(id, now + LIFETIME_MS);
        return id;
    }

    /**
     * The server id the mod tells Mojang for a challenge: SHA-1 of the challenge behind a prefix of
     * this API's own, as 40 hex digits. The mod works it out the same way
     * ({@code AlpakaApi.loginServerId}).
     */
    static String loginServerId(String challenge) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(("alpaka-api-login:" + challenge).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the id was handed out and is still good; either way it can't be used again. */
    boolean consume(String id) {
        if (id == null) return false;
        Long until = open.remove(id);
        return until != null && until > clock.getAsLong();
    }
}
