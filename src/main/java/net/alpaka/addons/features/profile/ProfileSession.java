package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Everything the dashboard knows about one player, filled in the background: the account and its
 * profiles first, then the chosen profile, then Elite's skill history and - when their pages are
 * opened - the networth's items and the collection tiers. The screen reads it every frame and never
 * waits for it.
 */
public final class ProfileSession {

    public enum State { LOADING, READY, FAILED }

    public record ProfileRef(String id, String name, String gameMode, boolean selected) {}

    /** What was asked for: a name or a UUID. */
    public final String query;
    /** Whether this is the player's own account, whose visits are remembered. */
    public final boolean own;

    public volatile State state = State.LOADING;
    public volatile String error;
    public volatile String uuid;
    public volatile String playerName;
    /** The texture hash of the player's skin, for the face in the header. */
    public volatile String skinHash;
    public volatile List<ProfileRef> profiles = List.of();
    public volatile String profileId;
    public volatile ProfileData data;
    /** The numbers at the previous visit, for "since your last visit"; null on the first. */
    public volatile ProfileHistory.Snapshot previousVisit;
    public volatile boolean refreshing;

    public volatile State networthState;
    public volatile JsonElement networth;
    public volatile State collectionsState;
    public volatile JsonElement collections;

    /** Elite's skill history, by metric id; filled in after the profile. */
    private volatile Map<String, List<ProfileHistory.Point>> eliteSeries = Map.of();
    /** Soopy's stat history, by metric id; filled in page by page after the profile. */
    private volatile Map<String, List<ProfileHistory.Point>> soopySeries = Map.of();
    /** Whether Soopy's pages are still coming in. */
    public volatile boolean historyLoading;
    /** Bumped whenever something a graph draws from changes, to drop cached series. */
    private volatile int version;
    private final Map<String, List<ProfileHistory.Point>> seriesCache = new HashMap<>();
    private int seriesVersion = -1;

    public ProfileSession(String query, boolean own) {
        this.query = query;
        this.own = own;
        load(null, false);
    }

    /** Shows another of the player's profiles. */
    public void switchProfile(String id) {
        if (id == null || id.equals(profileId) || state == State.LOADING) return;
        profileId = id;
        data = null;
        networth = null;
        networthState = null;
        state = State.LOADING;
        load(id, false);
    }

    /** Asks Elite again, past the minute answers are kept. */
    public void refresh() {
        if (refreshing || state == State.LOADING) return;
        refreshing = true;
        if (uuid != null) ProfileApi.forget(uuid);
        networth = null;
        networthState = null;
        load(profileId, true);
    }

    private void load(String wantedProfile, boolean fresh) {
        // The Bingo event decides which Bingo profiles are still worth listing; without an answer
        // none are, which is right outside an event.
        CompletableFuture<JsonElement> bingo = ProfileApi.bingo().exceptionally(e -> null);
        ProfileApi.account(query).thenCombine(bingo, (account, event) -> {
            readAccount(account, event, System.currentTimeMillis());
            return account;
        }).thenCompose(account -> {
            String id = wantedProfile != null ? wantedProfile : pickProfile();
            if (id == null) throw new ProfileApi.NotFound(playerName + " has no SkyBlock profiles.");
            profileId = id;
            return ProfileApi.member(uuid, id, fresh);
        }).whenComplete((member, failure) -> {
            refreshing = false;
            if (failure != null) {
                fail(failure);
                return;
            }
            if (!(member instanceof JsonObject object)) {
                fail(new IllegalStateException("Elite sent no profile."));
                return;
            }
            ProfileData parsed = new ProfileData(object, playerName);
            ProfileHistory.record(parsed);
            if (own) previousVisit = ProfileHistory.visit(parsed);
            data = parsed;
            error = null;
            state = State.READY;
            version++;
            loadSkillHistory(parsed);
            loadSoopyHistory(parsed);
        });
    }

    private void readAccount(JsonElement account, JsonElement bingoEvent, long now) {
        uuid = Json.str(account, "id");
        playerName = Json.str(account, "name");
        skinHash = Json.str(account, "skin.texture");
        profiles = profiles(account, bingoEvent, now);
    }

    /**
     * The account's profiles worth showing, the selected one first and the others by name, as the
     * game's profile menu has them. A Bingo profile only counts while a Bingo event runs: once it
     * is over the profile is a leftover nobody looks at again. Should that leave nothing, everything
     * is shown after all.
     */
    static List<ProfileRef> profiles(JsonElement account, JsonElement bingoEvent, long now) {
        boolean bingoRunning = bingoEvent != null
                && Json.num(bingoEvent, "start", Double.MAX_VALUE) <= now && now <= Json.num(bingoEvent, "end", 0);
        List<ProfileRef> all = new ArrayList<>();
        List<ProfileRef> shown = new ArrayList<>();
        for (JsonElement p : Json.arr(account, "profiles")) {
            String id = Json.str(p, "profileId");
            if (id == null || Json.bool(p, "wasRemoved")) continue;
            ProfileRef ref = new ProfileRef(id, Json.str(p, "profileName"), Json.str(p, "gameMode"), Json.bool(p, "selected"));
            all.add(ref);
            if (bingoRunning || !"bingo".equals(ref.gameMode())) shown.add(ref);
        }
        List<ProfileRef> refs = shown.isEmpty() ? all : shown;
        refs.sort(Comparator.comparing((ProfileRef r) -> !r.selected()).thenComparing(r -> r.name() == null ? "" : r.name()));
        return List.copyOf(refs);
    }

    private String pickProfile() {
        List<ProfileRef> refs = profiles;
        if (refs.isEmpty()) return null;
        if (profileId != null) {
            for (ProfileRef r : refs) if (r.id().equals(profileId)) return profileId;
        }
        return refs.get(0).id();
    }

    private void fail(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ProfileApi.NotFound) {
            error = cause.getMessage();
        } else if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.ConnectException
                || cause instanceof java.io.IOException) {
            error = "Couldn't reach Elite. Check your connection and try again.";
        } else {
            error = cause.getMessage() == null ? "Something went wrong loading the profile." : cause.getMessage();
        }
        // A failed refresh keeps showing what was there.
        if (data == null) state = State.FAILED;
    }

    /** Elite hands out its history 29 days at a time; this many stretches make about a year. */
    private static final int ELITE_STRETCHES = 13;

    /**
     * Elite's skill history, 29 days at a time from now backwards, each stretch showing as it
     * arrives. Elite often has the months Soopy is missing: it records a profile whenever anyone
     * looks it up there, and many mods do. Two empty stretches in a row are taken as the start of
     * what Elite knows.
     */
    private void loadSkillHistory(ProfileData parsed) {
        Map<String, Integer> maxLevels = new HashMap<>();
        for (ProfileData.Level s : parsed.skills) maxLevels.put(s.id(), s.maxLevel());
        loadSkillStretch(parsed, maxLevels, 0, 0, new ArrayList<>());
    }

    private void loadSkillStretch(ProfileData parsed, Map<String, Integer> maxLevels, int stretch, int emptyInARow,
                                  List<JsonElement> answers) {
        String id = parsed.profileId;
        ProfileApi.skillGraph(parsed.uuid, id, stretch * 29).whenComplete((answer, failure) -> {
            if (!id.equals(profileId)) return;
            boolean empty = failure != null || answer == null || !answer.isJsonArray() || answer.getAsJsonArray().isEmpty();
            if (!empty) {
                answers.add(answer);
                eliteSeries = skillSeries(answers, maxLevels);
                version++;
            }
            int nextEmpty = empty ? emptyInARow + 1 : 0;
            if (stretch + 1 < ELITE_STRETCHES && nextEmpty < 2) loadSkillStretch(parsed, maxLevels, stretch + 1, nextEmpty, answers);
        });
    }

    /** At most this many of Soopy's pages, about a hundred snapshots each: several years. */
    private static final int SOOPY_MAX_PAGES = 15;

    /**
     * Soopy's history, newest page first, one after the other; each page shows in the graphs as it
     * arrives, so the recent past is there at once and older years fill in behind it.
     */
    private void loadSoopyHistory(ProfileData parsed) {
        historyLoading = true;
        loadSoopyPage(parsed, 0, new ArrayList<>(), System.currentTimeMillis());
    }

    private void loadSoopyPage(ProfileData parsed, int page, List<JsonElement> pages, long askedAt) {
        String id = parsed.profileId;
        ProfileApi.soopyHistory(parsed.uuid, id, page).whenComplete((answer, failure) -> {
            if (!id.equals(profileId)) return;
            boolean more = failure == null && !Json.arr(answer, "data").isEmpty();
            if (more) {
                pages.add(answer);
                Map<String, List<ProfileHistory.Point>> series = soopySeries(pages);
                calibrateNetworth(series, parsed.networth, askedAt);
                soopySeries = series;
                version++;
            }
            if (more && page + 1 < SOOPY_MAX_PAGES) {
                loadSoopyPage(parsed, page + 1, pages, askedAt);
            } else {
                historyLoading = false;
            }
        });
    }

    /**
     * Soopy's pages as series by metric id. A "NoChange" entry means the profile was looked at again
     * and its numbers were still those of the snapshot before, so it repeats them: the line stays
     * flat up to that moment instead of cutting across the gap. A skill at zero was the skills API
     * being off at the time, not a profile without XP, and is left out.
     */
    static Map<String, List<ProfileHistory.Point>> soopySeries(List<JsonElement> pages) {
        List<JsonElement> entries = new ArrayList<>();
        for (JsonElement page : pages) for (JsonElement e : Json.arr(page, "data")) entries.add(e);
        entries.sort(Comparator.comparingDouble(e -> Json.num(e, "last_save", 0)));
        Map<String, List<ProfileHistory.Point>> out = new HashMap<>();
        Map<String, Double> last = new HashMap<>();
        for (JsonElement e : entries) {
            long t = (long) Json.num(e, "last_save", 0);
            if (t <= 0) continue;
            String type = Json.str(e, "type");
            if ("Data".equals(type)) {
                last = new HashMap<>();
                put(last, Metrics.NETWORTH, Json.num(e, "networth"), true);
                put(last, Metrics.SB_LEVEL, Json.num(e, "sbLvl"), true);
                put(last, Metrics.SLAYER_TOTAL, Json.num(e, "slayer.totalExp"), false);
                for (String[] boss : ProfileData.SLAYERS) {
                    put(last, Metrics.SLAYER + boss[0], Json.num(e, "slayer." + boss[0] + ".xp"), false);
                }
                put(last, Metrics.CATACOMBS, Json.num(e, "dungeons.catacombs_xp"), false);
                for (String c : ProfileData.CLASSES) {
                    put(last, Metrics.CLASS + c, Json.num(e, "dungeons.class_levels." + c + ".xp"), false);
                }
                for (var skill : Json.obj(e, "skills").entrySet()) {
                    if (skill.getValue().isJsonPrimitive()) {
                        put(last, Metrics.SKILL + skill.getKey(), skill.getValue().getAsDouble(), true);
                    }
                }
            } else if (!"NoChange".equals(type)) {
                continue;
            }
            for (var m : last.entrySet()) {
                out.computeIfAbsent(m.getKey(), k -> new ArrayList<>()).add(new ProfileHistory.Point(t, m.getValue()));
            }
        }
        out.computeIfPresent(Metrics.NETWORTH, (k, v) -> withoutDips(v));
        return out;
    }

    /**
     * Soopy works networth out its own way, and lands well apart from Elite: 179B where Elite says
     * 259B for the same profile at the same moment. Put into one graph as they are, the switch from
     * one to the other reads as a jump that never happened. So Soopy's networth is scaled by how the
     * two compare now - its "realtime" snapshot from this very look against Elite's number - which
     * keeps the history's shape and lands it on Elite's. Without a snapshot of Soopy's from this
     * look there is nothing to compare with, and its networth is left out.
     */
    static void calibrateNetworth(Map<String, List<ProfileHistory.Point>> series, double eliteNow, long askedAt) {
        List<ProfileHistory.Point> networth = series.get(Metrics.NETWORTH);
        if (networth == null || networth.isEmpty()) return;
        ProfileHistory.Point now = null;
        for (ProfileHistory.Point p : networth) if (p.timeMs() >= askedAt - 10 * 60_000L) now = p;
        if (now == null || !(eliteNow > 0) || !(now.value() > 0)) {
            series.remove(Metrics.NETWORTH);
            return;
        }
        double factor = eliteNow / now.value();
        List<ProfileHistory.Point> scaled = new ArrayList<>(networth.size());
        for (ProfileHistory.Point p : networth) scaled.add(new ProfileHistory.Point(p.timeMs(), p.value() * factor));
        series.put(Metrics.NETWORTH, scaled);
    }

    /**
     * Drops short dips far below what came before and after: an inventory API switched off for a
     * while makes the networth read a tenth of itself, which says nothing about the profile. A dip
     * may be a few points long, since the looks that found nothing new repeat it.
     */
    static List<ProfileHistory.Point> withoutDips(List<ProfileHistory.Point> points) {
        List<ProfileHistory.Point> out = new ArrayList<>(points.size());
        int i = 0;
        while (i < points.size()) {
            ProfileHistory.Point p = points.get(i);
            if (!out.isEmpty()) {
                double before = out.get(out.size() - 1).value();
                int end = i;
                while (end < points.size() && end - i < 6 && points.get(end).value() < before * 0.5) end++;
                if (end > i && end < points.size() && points.get(end).value() >= before * 0.5) {
                    i = end;
                    continue;
                }
            }
            out.add(p);
            i++;
        }
        return out;
    }

    private static void put(Map<String, Double> into, String metric, double value, boolean zeroIsMissing) {
        if (Double.isNaN(value) || value < 0 || zeroIsMissing && value == 0) return;
        into.put(metric, value);
    }

    /** Elite's graph answers as series by metric id, the skill average worked out for each point. */
    static Map<String, List<ProfileHistory.Point>> skillSeries(List<JsonElement> answers, Map<String, Integer> maxLevels) {
        Map<String, List<ProfileHistory.Point>> out = new HashMap<>();
        for (JsonElement answer : answers) {
            if (answer == null || !answer.isJsonArray()) continue;
            for (JsonElement point : answer.getAsJsonArray()) {
                if (!Json.bool(point, "skillsApi")) continue;
                long t = (long) Json.num(point, "timestamp", 0) * 1000L;
                if (t <= 0) continue;
                JsonObject skills = Json.obj(point, "skills");
                int counted = 0;
                double levels = 0;
                for (var e : skills.entrySet()) {
                    if (!e.getValue().isJsonPrimitive()) continue;
                    double xp = e.getValue().getAsDouble();
                    out.computeIfAbsent(Metrics.SKILL + e.getKey(), k -> new ArrayList<>()).add(new ProfileHistory.Point(t, xp));
                    if (e.getKey().equals("runecrafting") || e.getKey().equals("social")) continue;
                    levels += Math.floor(Levels.skill(e.getKey(), xp, maxLevels.getOrDefault(e.getKey(), 60)));
                    counted++;
                }
                if (counted > 0) {
                    out.computeIfAbsent(Metrics.SKILL_AVG, k -> new ArrayList<>()).add(new ProfileHistory.Point(t, levels / counted));
                }
            }
        }
        return out;
    }

    /**
     * One metric over time for the profile on show: the dashboard's own snapshots together with
     * Elite's skill history and Soopy's stat history, oldest first.
     */
    public synchronized List<ProfileHistory.Point> series(String metric) {
        ProfileData d = data;
        if (d == null) return List.of();
        if (seriesVersion != version) {
            seriesCache.clear();
            seriesVersion = version;
        }
        return seriesCache.computeIfAbsent(metric, m -> {
            List<ProfileHistory.Point> merged = new ArrayList<>(ProfileHistory.series(d.uuid, d.profileId, m));
            merged.addAll(eliteSeries.getOrDefault(m, List.of()));
            merged.addAll(soopySeries.getOrDefault(m, List.of()));
            merged.sort(Comparator.comparingLong(ProfileHistory.Point::timeMs));
            // Elite and the snapshots often looked at almost the same moment; one point is enough.
            List<ProfileHistory.Point> out = new ArrayList<>(merged.size());
            for (ProfileHistory.Point p : merged) {
                if (!out.isEmpty() && p.timeMs() - out.get(out.size() - 1).timeMs() < 5 * 60_000L) {
                    out.set(out.size() - 1, p);
                } else {
                    out.add(p);
                }
            }
            return List.copyOf(out);
        });
    }

    /** The networth's items, asked for on the first look at its page. */
    public JsonElement networth() {
        ProfileData d = data;
        if (d != null && networthState == null) {
            networthState = State.LOADING;
            String id = d.profileId;
            ProfileApi.networth(d.uuid, id).whenComplete((answer, failure) -> {
                if (!id.equals(profileId)) return;
                networth = answer;
                networthState = failure == null ? State.READY : State.FAILED;
            });
        }
        return networth;
    }

    /** Lets the networth and the collections be asked for again after they failed. */
    public void retryFailed() {
        if (networthState == State.FAILED) networthState = null;
        if (collectionsState == State.FAILED) collectionsState = null;
    }

    /** The collection tiers from Hypixel's resources, asked for on the first look at their page. */
    public JsonElement collections() {
        if (collectionsState == null) {
            collectionsState = State.LOADING;
            ProfileApi.collections().whenComplete((answer, failure) -> {
                collections = answer;
                collectionsState = failure == null ? State.READY : State.FAILED;
            });
        }
        return collections;
    }

    /**
     * How fast a metric grew over the last {@code days}, per day, from its history; NaN without two
     * points at least half a day apart in that time.
     */
    public double ratePerDay(String metric, int days) {
        List<ProfileHistory.Point> points = series(metric);
        if (points.size() < 2) return Double.NaN;
        ProfileHistory.Point last = points.get(points.size() - 1);
        long since = last.timeMs() - days * ProfileHistory.DAY;
        ProfileHistory.Point first = null;
        for (ProfileHistory.Point p : points) {
            if (p.timeMs() >= since) {
                first = p;
                break;
            }
        }
        if (first == null || last.timeMs() - first.timeMs() < ProfileHistory.DAY / 2) return Double.NaN;
        return (last.value() - first.value()) / ((last.timeMs() - first.timeMs()) / (double) ProfileHistory.DAY);
    }
}
