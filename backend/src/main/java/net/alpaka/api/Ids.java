package net.alpaka.api;

import java.util.Locale;
import java.util.regex.Pattern;

/** Player and profile ids as they may be asked for: a UUID, with or without its dashes. */
final class Ids {
    private Ids() {}

    private static final Pattern PLAIN = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern DASHED = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern SERVER_ID = Pattern.compile("[0-9a-f]{40}");

    /** The id without dashes, in lower case; null for anything that is no UUID. */
    static String uuid(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase(Locale.ROOT);
        if (DASHED.matcher(s).matches()) s = s.replace("-", "");
        return PLAIN.matcher(s).matches() ? s : null;
    }

    static boolean username(String raw) {
        return raw != null && USERNAME.matcher(raw).matches();
    }

    /** A challenge as {@link Challenges} hands them out, used as the server id of the login to Mojang. */
    static boolean serverId(String raw) {
        return raw != null && SERVER_ID.matcher(raw).matches();
    }
}
