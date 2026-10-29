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
 * looked at, and every hour while its owner plays (see {@link ProfileTracker}). Elite keeps a
 * history of skills only; networth, Catacombs, slayers and everything else come from here.
 *
 * Kept in {@code config/alpaka-profile-history.json}, one entry per player and profile. Recent
 * snapshots are kept close together, older ones thinned to one a day, so a year of play stays a
 * few hundred kilobytes.
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

    private static Map<String, Entry> entries;
    private static boolean dirty;

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
                    }
                }
            } catch (IOException | JsonParseException e) {
                AlpakaAddons.LOGGER.warn("Couldn't read the profile history; starting a new one", e);
                AtomicJsonFile.quarantine(file);
            }
        }
        return entries;
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

    private static void saveAsync() {
        CompletableFuture.runAsync(ProfileHistory::save);
    }

    static synchronized void save() {
        if (!dirty || entries == null) return;
        dirty = false;
        try {
            AtomicJsonFile.write(file(), GSON.toJson(entries));
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Couldn't save the profile history", e);
        }
    }
}
