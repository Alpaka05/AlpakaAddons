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
        ProfileApi.account(query).thenCompose(account -> {
            readAccount(account);
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
        });
    }

    private void readAccount(JsonElement account) {
        uuid = Json.str(account, "id");
        playerName = Json.str(account, "name");
        skinHash = Json.str(account, "skin.texture");
        List<ProfileRef> refs = new ArrayList<>();
        for (JsonElement p : Json.arr(account, "profiles")) {
            String id = Json.str(p, "profileId");
            if (id == null || Json.bool(p, "wasRemoved")) continue;
            refs.add(new ProfileRef(id, Json.str(p, "profileName"), Json.str(p, "gameMode"), Json.bool(p, "selected")));
        }
        // The selected profile first, the others by name, as the game's profile menu shows them.
        refs.sort(Comparator.comparing((ProfileRef r) -> !r.selected()).thenComparing(r -> r.name() == null ? "" : r.name()));
        profiles = List.copyOf(refs);
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

    /**
     * Elite's skill history: the last 29 days, then two more stretches before it, so the graphs
     * reach back about three months from the first look.
     */
    private void loadSkillHistory(ProfileData parsed) {
        String id = parsed.profileId;
        Map<String, Integer> maxLevels = new HashMap<>();
        for (ProfileData.Level s : parsed.skills) maxLevels.put(s.id(), s.maxLevel());
        List<CompletableFuture<JsonElement>> parts = List.of(
                ProfileApi.skillGraph(parsed.uuid, id, 0),
                ProfileApi.skillGraph(parsed.uuid, id, 29),
                ProfileApi.skillGraph(parsed.uuid, id, 58));
        CompletableFuture.allOf(parts.stream().map(f -> f.exceptionally(e -> null)).toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    if (!id.equals(profileId)) return;
                    List<JsonElement> answers = new ArrayList<>();
                    for (CompletableFuture<JsonElement> f : parts) {
                        JsonElement e = f.getNow(null);
                        if (e != null) answers.add(e);
                    }
                    eliteSeries = skillSeries(answers, maxLevels);
                    version++;
                });
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
     * Elite's skill history, oldest first.
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
