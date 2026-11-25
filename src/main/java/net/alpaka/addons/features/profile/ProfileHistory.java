package net.alpaka.addons.features.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AtomicJsonFile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The dashboard's own record of how a profile grew: a snapshot of its numbers each time it was
 * looked at. Nothing is asked for in the background while playing: that would be polling a
 * player's data, which Hypixel's API policy rules out. What other sites kept of
 * the past (Soopy, SkyKings) is added to it in the graphs.
 *
 * Kept in {@code config/alpaka-profile-history.json}, one entry per player and profile. Recent
 * snapshots are kept close together, older ones thinned to one a day, so a year of play stays a
 * few hundred kilobytes. Profiles nobody looked at for a year are dropped when the file is read.
 *
 * The file is read and written on a thread of its own: one load of the dashboard changes it several
 * times in a few seconds (the profile, the museum, the networth, the visit), and those are written
 * once, a moment after the last of them.
 */
public final class ProfileHistory {
    private ProfileHistory() {}

    /** One look at a profile: when, and its numbers by {@link Metrics} id. */
    public record Snapshot(long t, Map<String, Double> m) {}

    /** A point of a graph. */
    public record Point(long timeMs, double value) {}

    static final class Entry {
        String player;
        String profile;
        /** When a look last found this the player's selected profile; 0 for never. */
        long selectedAt;
        /** When the player was last looked up by name, in the search or with /ap; 0 for never. */
        long searchedAt;
        List<Snapshot> points = new ArrayList<>();
        /** When the dashboard was last opened on this profile, and the numbers then. */
        long lastViewed;
        Map<String, Double> lastViewedMetrics;
        /** The visit before that one: what "since your last visit" compares with. */
        long previousViewed;
        Map<String, Double> previousViewedMetrics;
    }

    private static final Gson GSON = new GsonBuilder().create();
    /** Two snapshots closer than this are one: the later replaces the earlier. */
    static final long MERGE_MS = 20 * 60_000L;
    static final long HOUR = 3_600_000L;
    static final long DAY = 24 * HOUR;

    /** A profile not looked at for this long is dropped from the file. */
    static final long FORGET_AFTER_MS = 365 * DAY;
    /** How long a change waits for others before the file is written. */
    private static final long SAVE_DELAY_MS = 3_000L;

    private static Map<String, Entry> entries;
    private static boolean dirty;
    /** Whether a write is waiting for {@link #SAVE_DELAY_MS}. */
    private static final java.util.concurrent.atomic.AtomicBoolean saveQueued = new java.util.concurrent.atomic.AtomicBoolean();
    /** Reads and writes the file, one after the other, so an older write can never land last. */
    private static final java.util.concurrent.ExecutorService FILES = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka profile history");
        t.setDaemon(true);
        return t;
    });

    static File file() {
        return FabricLoader.getInstance().getConfigDir().resolve("alpaka-profile-history.json").toFile();
    }

    private static synchronized Map<String, Entry> entries() {
        if (entries == null) {
            entries = new HashMap<>();
            File file = file();
            try {
                String json = AtomicJsonFile.read(file);
                if (json != null) {
                    Map<String, Entry> loaded = GSON.fromJson(json, new TypeToken<Map<String, Entry>>() {}.getType());
                    if (loaded != null) {
                        loaded.values().removeIf(e -> e == null || e.points == null);
                        entries.putAll(loaded);
                        // Profiles of other players looked at once stay forever otherwise, and only
                        // a profile looked at again was ever thinned.
                        if (prune(entries, System.currentTimeMillis())) {
                            dirty = true;
                            saveAsync();
                        }
                    }
                }
            } catch (IOException | JsonParseException e) {
                AlpakaAddons.LOGGER.warn("Couldn't read the profile history; starting a new one", e);
                AtomicJsonFile.quarantine(file);
            }
        }
        return entries;
    }

    /**
     * Reads the file in the background, so neither the dashboard nor the name completion has to
     * wait for it on the render thread later.
     */
    public static void preload() {
        FILES.execute(ProfileHistory::entries);
    }

    /** Whether the file has been read. */
    private static synchronized boolean loaded() {
        return entries != null;
    }

    /**
     * Thins every profile's history and drops the profiles not looked at for {@link #FORGET_AFTER_MS};
     * returns whether anything changed.
     */
    static boolean prune(Map<String, Entry> entries, long now) {
        boolean changed = entries.values().removeIf(e -> lastSeen(e) < now - FORGET_AFTER_MS);
        for (Entry entry : entries.values()) {
            int before = entry.points.size();
            thin(entry.points, now);
            changed |= entry.points.size() != before;
        }
        return changed;
    }

    /** When the profile was last looked at, by any of the ways that is noted. */
    private static long lastSeen(Entry entry) {
        long last = Math.max(entry.lastViewed, entry.selectedAt);
        if (!entry.points.isEmpty()) last = Math.max(last, entry.points.get(entry.points.size() - 1).t());
        return last;
    }

    static String key(String uuid, String profileId) {
        return uuid + ":" + profileId;
    }

    /** Adds a look at this profile, merging it with one taken just before. */
    public static synchronized void record(ProfileData data) {
        if (data.uuid == null || data.profileId == null) return;
        Entry entry = entries().computeIfAbsent(key(data.uuid, data.profileId), k -> new Entry());
        entry.player = data.playerName;
        entry.profile = data.profileName;
        long when = data.lastUpdated > 0 ? data.lastUpdated * 1000L : System.currentTimeMillis();
        add(entry.points, new Snapshot(when, data.metrics()), MERGE_MS);
        thin(entry.points, System.currentTimeMillis());
        dirty = true;
        saveAsync();
    }

    /** Adds in time order; a snapshot within {@code mergeMs} of the one before it replaces that one. */
    static void add(List<Snapshot> points, Snapshot snapshot, long mergeMs) {
        int i = points.size();
        while (i > 0 && points.get(i - 1).t() > snapshot.t()) i--;
        if (i > 0 && snapshot.t() - points.get(i - 1).t() < mergeMs) {
            points.set(i - 1, snapshot);
        } else {
            points.add(i, snapshot);
        }
    }

    /**
     * Thins the history out the older it gets: everything of the last two days, one snapshot every
     * three hours for the month before, and one a day past that, always the last of its stretch.
     */
    static void thin(List<Snapshot> points, long now) {
        if (points.size() < 3) return;
        List<Snapshot> kept = new ArrayList<>(points.size());
        long lastSize = 0;
        long lastBucket = Long.MIN_VALUE;
        for (int i = points.size() - 1; i >= 0; i--) {
            Snapshot s = points.get(i);
            long age = now - s.t();
            long size = age < 2 * DAY ? 0 : age < 32 * DAY ? 3 * HOUR : DAY;
            if (size == 0) {
                kept.add(s);
                continue;
            }
            long bucket = Math.floorDiv(s.t(), size);
            if (size == lastSize && bucket == lastBucket) continue;
            lastSize = size;
            lastBucket = bucket;
            kept.add(s);
        }
        java.util.Collections.reverse(kept);
        points.clear();
        points.addAll(kept);
    }

    /** One metric over time, oldest first; empty when it was never seen. */
    public static synchronized List<Point> series(String uuid, String profileId, String metric) {
        Entry entry = entries().get(key(uuid, profileId));
        if (entry == null) return List.of();
        List<Point> out = new ArrayList<>();
        for (Snapshot s : entry.points) {
            Double v = s.m().get(metric);
            if (v != null && !v.isNaN()) out.add(new Point(s.t(), v));
        }
        return out;
    }

    /**
     * The profile of a player the dashboard last looked at, or null if it never did: the id of the
     * profile whose newest snapshot is the latest. {@code player} is a UUID without dashes, or a name.
     */
    public static synchronized String lastProfile(String player) {
        return lastProfile(entries(), player);
    }

    /** {@link #lastProfile(String)}, read off the calling thread when the file isn't in yet. */
    public static CompletableFuture<String> lastProfileAsync(String player) {
        if (loaded()) return CompletableFuture.completedFuture(lastProfile(player));
        return CompletableFuture.supplyAsync(() -> lastProfile(player), FILES);
    }

    static String lastProfile(Map<String, Entry> entries, String player) {
        if (player == null) return null;
        String best = null;
        long newest = Long.MIN_VALUE;
        // The profile last seen selected in the game is the one the API will pick; one only looked
        // at in the dashboard since is not.
        String selected = null;
        long selectedAt = 0;
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            // "uuid:profile"
            int cut = e.getKey().indexOf(':');
            Entry entry = e.getValue();
            if (cut <= 0 || entry.points.isEmpty()) continue;
            boolean byUuid = e.getKey().substring(0, cut).equalsIgnoreCase(player);
            if (!byUuid && (entry.player == null || !entry.player.equalsIgnoreCase(player))) continue;
            long t = entry.points.get(entry.points.size() - 1).t();
            if (t > newest) {
                newest = t;
                best = e.getKey().substring(cut + 1);
            }
            if (entry.selectedAt > selectedAt) {
                selectedAt = entry.selectedAt;
                selected = e.getKey().substring(cut + 1);
            }
        }
        return selected != null ? selected : best;
    }

    /**
     * The players looked up before, by name, for completing names and the dashboard's recent list:
     * the last searched for first, then those only ever opened some other way (the own profile from
     * its key), newest data first. Asked on the render thread: until the file is read, none, and
     * reading it starts.
     */
    public static synchronized java.util.List<String> players() {
        if (entries == null) {
            preload();
            return java.util.List.of();
        }
        return players(entries);
    }

    static java.util.List<String> players(Map<String, Entry> entries) {
        Map<String, Long> newest = new HashMap<>();
        Map<String, Long> searched = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Entry entry : entries.values()) {
            if (entry.player == null || entry.points.isEmpty()) continue;
            String key = entry.player.toLowerCase(java.util.Locale.ROOT);
            // A snapshot's time is when Hypixel last saved the profile, not when it was looked at,
            // so it only orders the players never searched for.
            long t = entry.points.get(entry.points.size() - 1).t();
            if (t >= newest.getOrDefault(key, Long.MIN_VALUE)) {
                newest.put(key, t);
                names.put(key, entry.player);
            }
            searched.merge(key, entry.searchedAt, Math::max);
        }
        java.util.List<String> out = new ArrayList<>(names.keySet());
        out.sort((a, b) -> {
            int bySearch = Long.compare(searched.get(b), searched.get(a));
            return bySearch != 0 ? bySearch : Long.compare(newest.get(b), newest.get(a));
        });
        out.replaceAll(names::get);
        return out;
    }

    /** Notes that the player was just looked up by name, for the order of {@link #players()}. */
    public static synchronized void searched(String uuid) {
        if (uuid == null) return;
        long now = System.currentTimeMillis();
        String prefix = uuid + ":";
        boolean any = false;
        for (Map.Entry<String, Entry> e : entries().entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                e.getValue().searchedAt = now;
                any = true;
            }
        }
        if (any) {
            dirty = true;
            saveAsync();
        }
    }

    /**
     * The players offered under the dashboard's player search, from {@link #players()}: those whose
     * name starts with what is typed come before those that only contain it, each kept in the order
     * they were last looked at. The player on show is left out, since looking them up again changes
     * nothing.
     */
    static java.util.List<String> recent(java.util.List<String> players, String typed, String shown, int max) {
        String query = typed == null ? "" : typed.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> starting = new ArrayList<>();
        java.util.List<String> containing = new ArrayList<>();
        for (String player : players) {
            if (shown != null && player.equalsIgnoreCase(shown)) continue;
            String name = player.toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith(query)) starting.add(player);
            else if (name.contains(query)) containing.add(player);
        }
        starting.addAll(containing);
        return starting.size() > max ? java.util.List.copyOf(starting.subList(0, max)) : starting;
    }

    /** Notes that a look found this the player's selected profile, for {@link #lastProfile}. */
    public static synchronized void selected(String uuid, String profileId) {
        if (uuid == null || profileId == null) return;
        Entry entry = entries().get(key(uuid, profileId));
        // Only a profile with a history: one never shown has nothing to remember it by.
        if (entry == null || entry.points.isEmpty()) return;
        entry.selectedAt = System.currentTimeMillis();
        dirty = true;
        saveAsync();
    }

    /** How many snapshots there are of this profile. */
    public static synchronized int size(String uuid, String profileId) {
        Entry entry = entries().get(key(uuid, profileId));
        return entry == null ? 0 : entry.points.size();
    }

    /**
     * Notes this look at the profile and returns the numbers of the visit before it, with when that
     * was; null on the first one. Looks within an hour of each other count as one visit, so opening
     * the dashboard twice in a row still compares with the time before.
     */
    public static synchronized Snapshot visit(ProfileData data) {
        Entry entry = entries().computeIfAbsent(key(data.uuid, data.profileId), k -> new Entry());
        long now = System.currentTimeMillis();
        if (entry.lastViewedMetrics == null || now - entry.lastViewed > HOUR) {
            entry.previousViewed = entry.lastViewed;
            entry.previousViewedMetrics = entry.lastViewedMetrics;
        }
        entry.lastViewed = now;
        entry.lastViewedMetrics = new LinkedHashMap<>(data.metrics());
        dirty = true;
        saveAsync();
        return entry.previousViewedMetrics == null ? null : new Snapshot(entry.previousViewed, entry.previousViewedMetrics);
    }

    /** Writes the file a moment from now, together with whatever else changes until then. */
    private static void saveAsync() {
        if (!saveQueued.compareAndSet(false, true)) return;
        java.util.concurrent.Executor later = CompletableFuture.delayedExecutor(SAVE_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS, FILES);
        later.execute(() -> {
            saveQueued.set(false);
            save();
        });
    }

    /** Writes what is waiting now, on exit; nothing if nothing changed. */
    public static void flush() {
        save();
    }

    /**
     * Writes the file. Only a copy is taken under the lock: turning a year of snapshots into JSON
     * and writing it to disk would keep the dashboard's graphs waiting meanwhile.
     */
    static void save() {
        Map<String, Entry> copy;
        synchronized (ProfileHistory.class) {
            if (!dirty || entries == null) return;
            dirty = false;
            copy = new HashMap<>(entries.size());
            for (Map.Entry<String, Entry> e : entries.entrySet()) copy.put(e.getKey(), copyOf(e.getValue()));
        }
        // One writer at a time: a write queued later always carries what an earlier one did too.
        synchronized (FILES) {
            try {
                AtomicJsonFile.write(file(), GSON.toJson(copy));
            } catch (IOException e) {
                AlpakaAddons.LOGGER.warn("Couldn't save the profile history", e);
            }
        }
    }

    /** A copy to write out; snapshots and their numbers are never changed once made, so they are shared. */
    private static Entry copyOf(Entry entry) {
        Entry out = new Entry();
        out.player = entry.player;
        out.profile = entry.profile;
        out.selectedAt = entry.selectedAt;
        out.points = new ArrayList<>(entry.points);
        out.lastViewed = entry.lastViewed;
        out.lastViewedMetrics = entry.lastViewedMetrics;
        out.previousViewed = entry.previousViewed;
        out.previousViewedMetrics = entry.previousViewedMetrics;
        return out;
    }
}
