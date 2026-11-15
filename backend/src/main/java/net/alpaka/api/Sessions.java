package net.alpaka.api;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.LongSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The players logged in: a token for each, handed out once Mojang confirmed the account. A token
 * says whose it is and until when, signed with the server's secret, so nothing is kept about it
 * here and it outlives a restart of the server: the mod keeps its token on disk and doesn't have
 * to log in again after every update of this service.
 */
final class Sessions {
    static final long LIFETIME_MS = 12 * 60 * 60_000L;

    private static final Base64.Encoder ENCODE = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODE = Base64.getUrlDecoder();

    private final byte[] secret;
    private final LongSupplier clock;

    Sessions(byte[] secret, LongSupplier clock) {
        if (secret.length < 32) throw new IllegalArgumentException("The token secret needs 32 bytes at least");
        this.secret = secret.clone();
        this.clock = clock;
    }

    /** "<uuid>.<expires at>.<signature>", the first two as they are, the signature in Base64. */
    String issue(String uuid) {
        String claim = uuid + "." + (clock.getAsLong() + LIFETIME_MS);
        return claim + "." + ENCODE.encodeToString(sign(claim));
    }

    /** The player a token belongs to, or null for a forged, broken or expired one. */
    String uuidOf(String token) {
        if (token == null || token.length() > 200) return null;
        int last = token.lastIndexOf('.');
        int first = token.indexOf('.');
        if (first <= 0 || last <= first) return null;
        String claim = token.substring(0, last);
        byte[] given;
        try {
            given = DECODE.decode(token.substring(last + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!MessageDigest.isEqual(sign(claim), given)) return null;
        String uuid = Ids.uuid(token.substring(0, first));
        long expiresAt;
        try {
            expiresAt = Long.parseLong(token.substring(first + 1, last));
        } catch (NumberFormatException e) {
            return null;
        }
        return uuid != null && clock.getAsLong() < expiresAt ? uuid : null;
    }

    private byte[] sign(String claim) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(claim.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every JDK", e);
        }
    }
}
