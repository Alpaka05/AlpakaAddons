package net.alpaka.addons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SkyblockUtils {

    /**
     * The Skyblock area marker Hypixel prefixes the zone line with, e.g. "⏣ Torrhus Canyon".
     * Present on effectively every Skyblock sidebar and, unlike the title, never renamed.
     */
    private static final String AREA_MARKER = "⏣";

    /**
     * How long an {@link #isOnSkyblock()} verdict is reused.
     *
     * The answer changes only when the player changes server, which no amount of polling will catch
     * sooner than the next sidebar packet anyway, so half a second is plenty.
     */
    private static final long SKYBLOCK_CACHE_MS = 500L;

    private static long skyblockCheckedAtMs = 0L;
    private static boolean cachedOnSkyblock = false;

    /**
     * The level the cached verdict belongs to; a world change invalidates it immediately.
     *
     * Weak on purpose. Nothing calls in while the player sits in the main menu, so a strong field
     * would keep the whole ClientLevel of the world they just left alive until they load another.
     */
    private static java.lang.ref.WeakReference<Object> cachedForLevel = null;

    /**
     * Whether the player is on Hypixel Skyblock, cached for {@link #SKYBLOCK_CACHE_MS}.
     *
     * The cache is what makes this callable from a render hook at all. Working the answer out means
     * a scoreboard lookup, a color strip and possibly a walk over every sidebar line, and
     * {@code EntityRendererMixin} asks twice per entity per frame - so an uncached call was hundreds
     * of scoreboard walks a frame in a busy slayer lobby.
     */
    public static boolean isOnSkyblock() {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc.level;

        long now = System.currentTimeMillis();
        Object cachedLevel = cachedForLevel == null ? null : cachedForLevel.get();
        if (level == cachedLevel && now - skyblockCheckedAtMs < SKYBLOCK_CACHE_MS) {
            return cachedOnSkyblock;
        }

        skyblockCheckedAtMs = now;
        cachedForLevel = level == null ? null : new java.lang.ref.WeakReference<>(level);
        cachedOnSkyblock = computeOnSkyblock();
        return cachedOnSkyblock;
    }

    /** How long an {@link #isOnGarden()} verdict is reused; the tab list walk is not free. */
    private static final long GARDEN_CACHE_MS = 1000L;

    private static long gardenCheckedAtMs = 0L;
    private static boolean cachedOnGarden = false;
    private static java.lang.ref.WeakReference<Object> gardenCachedForLevel = null;

    /**
     * Whether the player is on their Garden, cached for {@link #GARDEN_CACHE_MS}. Asked every tick
     * by Always Sprint.
     *
     * The sidebar's zone line reads "The Garden" at the barn and "Plot - ..." on a plot; the tab
     * list's "Area: Garden" widget is the fallback for when the sidebar is hidden or reshaped.
     */
    public static boolean isOnGarden() {
        Object level = Minecraft.getInstance().level;
        long now = System.currentTimeMillis();
        Object cachedLevel = gardenCachedForLevel == null ? null : gardenCachedForLevel.get();
        if (level == cachedLevel && now - gardenCheckedAtMs < GARDEN_CACHE_MS) {
            return cachedOnGarden;
        }

        gardenCheckedAtMs = now;
        gardenCachedForLevel = level == null ? null : new java.lang.ref.WeakReference<>(level);
        cachedOnGarden = computeOnGarden();
        return cachedOnGarden;
    }

    private static boolean computeOnGarden() {
        if (!isOnSkyblock()) return false;
        for (String line : getSidebarLines()) {
            if (line.contains(AREA_MARKER) && (line.contains("The Garden") || line.contains("Plot - "))) {
                return true;
            }
        }
        for (String line : getTabListLines()) {
            if (line.startsWith("Area: Garden")) return true;
        }
        return false;
    }

    /** The End island's zones, as the sidebar's zone line names them. */
    private static final String[] END_ISLAND_ZONES = {"The End", "Dragon's Nest", "Void Sepulture", "Zealot Bruiser Hideout"};

    private static long endIslandCheckedAtMs = 0L;
    private static boolean cachedOnEndIsland = false;
    private static java.lang.ref.WeakReference<Object> endIslandCachedForLevel = null;

    /**
     * Whether the player is on SkyBlock's End island, cached like {@link #isOnGarden()}. Asked every
     * frame by Sky Effects: Hypixel sends the island as an Overworld-like dimension, so nothing in
     * the world itself tells it apart.
     *
     * The sidebar's zone line names the part of the island ("The End", "Void Sepulture", ...); the
     * tab list's "Area: The End" widget is the fallback.
     */
    public static boolean isOnEndIsland() {
        Object level = Minecraft.getInstance().level;
        long now = System.currentTimeMillis();
        Object cachedLevel = endIslandCachedForLevel == null ? null : endIslandCachedForLevel.get();
        if (level == cachedLevel && now - endIslandCheckedAtMs < GARDEN_CACHE_MS) {
            return cachedOnEndIsland;
        }

        endIslandCheckedAtMs = now;
        endIslandCachedForLevel = level == null ? null : new java.lang.ref.WeakReference<>(level);
        cachedOnEndIsland = computeOnEndIsland();
        return cachedOnEndIsland;
    }

    private static boolean computeOnEndIsland() {
        if (!isOnSkyblock()) return false;
        for (String line : getSidebarLines()) {
            if (isEndIslandZoneLine(line)) return true;
        }
        for (String line : getTabListLines()) {
            if (line.startsWith("Area: The End")) return true;
        }
        return false;
    }

    /** Whether a sidebar line, color codes stripped, is the zone line of one of the End island's zones. */
    static boolean isEndIslandZoneLine(String line) {
        int marker = line.indexOf(AREA_MARKER);
        if (marker < 0) return false;
        String zone = line.substring(marker + AREA_MARKER.length()).trim();
        for (String endZone : END_ISLAND_ZONES) {
            if (zone.startsWith(endZone)) return true;
        }
        return false;
    }

    /**
     * Detection is deliberately not based on the sidebar title alone. Hypixel renames that title
     * during events - it reads "BLAZE SIMULATOR" during one - which used to make this return false in
     * the middle of normal play and silently switch off every feature gated on it. The zone marker is
     * checked as well, which survives those renames.
     */
    private static boolean computeOnSkyblock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        var serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            String ip = serverData.ip.toLowerCase(Locale.ROOT);
            if (!ip.contains("hypixel.net") && !ip.contains("localhost")) {
                return false;
            }
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return false;

        String title = cleanColor(objective.getDisplayName().getString()).toLowerCase(Locale.ROOT);
        if (title.contains("skyblock") || title.contains("rift")) return true;

        for (String line : getSidebarLines()) {
            if (line.contains(AREA_MARKER)) return true;
        }
        return false;
    }

    /**
     * How long one sidebar snapshot is shared, in milliseconds. One client tick.
     *
     * The sidebar only changes when the server sends a scoreboard packet, so re-reading it more
     * often than the client ticks cannot produce a newer answer - it only repeats the work. Four
     * separate features poll this on timers of their own that do not line up, so without a shared
     * snapshot each of them paid for its own walk over the scoreboard.
     */
    private static final long SIDEBAR_CACHE_MS = 50L;

    private static long sidebarReadAtMs = 0L;
    private static List<String> cachedSidebarLines = List.of();
    private static Object cachedSidebarLevel = null;

    /**
     * The sidebar's lines with color codes stripped, title first, at most one scoreboard walk per
     * client tick however many callers ask.
     *
     * Hypixel writes the player's current zone onto one of these lines, which makes this the
     * client-visible, server-provided answer to "where am I" - no world or chunk scanning involved.
     *
     * This is what lets the slayer quest detector sample every tick during a fight instead of four
     * times a second: the extra polling reads a snapshot that was going to be built anyway, so the
     * cost is the cheap scan over the lines rather than another walk over the scoreboard.
     */
    public static List<String> getSidebarLines() {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc.level;

        long now = System.currentTimeMillis();
        if (level == cachedSidebarLevel && now - sidebarReadAtMs < SIDEBAR_CACHE_MS) {
            return cachedSidebarLines;
        }

        sidebarReadAtMs = now;
        cachedSidebarLevel = level;
        cachedSidebarLines = readSidebarLines();
        return cachedSidebarLines;
    }

    /** Builds the snapshot. Callers want {@link #getSidebarLines()}, which shares one per tick. */
    private static List<String> readSidebarLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();

        try {
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective == null) return List.of();

            List<String> lines = new ArrayList<>();
            lines.add(cleanColor(objective.getDisplayName().getString()));

            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                lines.add(cleanColor(lineText(scoreboard, entry)));
            }
            return lines;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Reassembles one sidebar row. Hypixel splits the visible text across the entry's team prefix
     * and suffix, so reading only the entry name would miss most of the line.
     */
    private static String lineText(Scoreboard scoreboard, PlayerScoreEntry entry) {
        PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
        if (team != null) {
            String prefix = team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
            String name = entry.ownerName() != null ? entry.ownerName().getString() : "";
            String suffix = team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
            return prefix + name + suffix;
        }
        return entry.ownerName() != null ? entry.ownerName().getString() : entry.owner();
    }

    /**
     * Strips every legacy formatting code, not just the ones Minecraft itself defines.
     *
     * Scoreboard entries have to be unique strings, so Hypixel pads each sidebar row with otherwise
     * meaningless codes drawn from outside the standard set - {@code §g}-{@code §j}, {@code §p}-{@code
     * §z} - and drops them anywhere in the line, including mid-word. A real captured row reads
     * {@code "Inferno Demonl§jord IV"}. Matching only {@code §[0-9A-FK-OR]} left those in place and
     * silently broke every substring test done against a sidebar line.
     */
    public static String cleanColor(String input) {
        if (input == null) return "";
        return COLOR_PATTERN.matcher(input).replaceAll("").trim();
    }

    /**
     * The formatting codes {@link #cleanColor} strips, compiled once.
     *
     * {@code String.replaceAll} compiles its pattern on every single call, which is invisible until
     * the caller sits in a render hook - this one is reached per sidebar line and, through
     * {@code DamageTagFeature}, per entity per frame.
     */
    private static final java.util.regex.Pattern COLOR_PATTERN = java.util.regex.Pattern.compile("§.");

    /**
     * Whether {@code needle} appears in {@code text} once formatting codes are ignored, without
     * building the stripped string.
     *
     * Hypixel drops codes anywhere in a line, mid-word included, so a plain {@code contains} on the
     * raw text misses matches - but stripping first allocates a fresh String, and the callers doing
     * this run once per entity per frame. Walking both strings at once gets the same answer for no
     * allocation at all.
     *
     * Case-sensitive, matching the {@code contains} calls it replaces.
     */
    public static boolean containsIgnoringFormatting(String text, String needle) {
        if (text == null || needle == null) return false;
        int textLength = text.length();
        int needleLength = needle.length();
        if (needleLength == 0) return true;

        for (int start = 0; start < textLength; start++) {
            if (text.charAt(start) == '§') {
                start++; // Skip the code's second character too.
                continue;
            }

            int at = start;
            int matched = 0;
            while (at < textLength && matched < needleLength) {
                char c = text.charAt(at);
                if (c == '§') {
                    at += 2;
                    continue;
                }
                if (c != needle.charAt(matched)) break;
                at++;
                matched++;
            }
            if (matched == needleLength) return true;
        }
        return false;
    }

    /**
     * Every line the player list is currently showing, color codes stripped.
     *
     * Hypixel puts widgets into the tab list alongside the player rows, and the slayer quest is one
     * of them - which matters because the sidebar does not always carry the quest, while the tab
     * widget does. SkyHanni reads the same two sources for the same reason, sidebar first and tab
     * list as the fallback.
     *
     * Purely a read of a list the client is already displaying; nothing is requested from the
     * server. It walks every entry and builds a String for each, so - like the sidebar - one
     * snapshot per client tick is shared by everyone who asks.
     */
    public static List<String> getTabListLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return List.of();

        Object connection = mc.player.connection;
        long now = System.currentTimeMillis();
        if (connection == cachedTabConnection && now - tabReadAtMs < SIDEBAR_CACHE_MS) {
            return cachedTabLines;
        }
        tabReadAtMs = now;
        cachedTabConnection = connection;
        cachedTabLines = readTabListLines(mc);
        return cachedTabLines;
    }

    private static long tabReadAtMs = 0L;
    private static List<String> cachedTabLines = List.of();
    private static Object cachedTabConnection = null;

    /** Builds the snapshot. Callers want {@link #getTabListLines()}, which shares one per tick. */
    private static List<String> readTabListLines(Minecraft mc) {
        try {
            List<String> lines = new ArrayList<>();
            for (var info : mc.player.connection.getListedOnlinePlayers()) {
                Component name = info.getTabListDisplayName();
                if (name == null) continue;
                lines.add(cleanColor(name.getString()));
            }
            return List.copyOf(lines);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}