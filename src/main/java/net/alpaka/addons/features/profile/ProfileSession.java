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
    /** SkyKings' history, by metric id, when the player set a key. */
    private volatile Map<String, List<ProfileHistory.Point>> skykingsSeries = Map.of();
    /** What came of asking SkyKings, for the source note: null without a key. */
    public volatile String skykingsNote;
    /** The key SkyKings was last asked with, "" for none; see {@link #syncSkyKings}. */
    private volatile String skykingsKeyUsed = null;
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
            loadSkyKings(parsed);
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

    /**
     * Asks SkyKings again when the key in the config is not the one the profile on show was loaded
     * with. A session is kept for a few minutes so the dashboard reopens at once; without this, a
     * key entered in that time only counted after a Refresh, and the graphs stayed as they were.
     * Called every frame; a string comparison when nothing changed.
     */
    public void syncSkyKings() {
        ProfileData d = data;
        if (d == null || state != State.READY) return;
        String key = net.alpaka.addons.config.AlpakaConfig.instance.skykingsApiKey;
        String current = key == null ? "" : key.trim();
        if (!current.equals(skykingsKeyUsed)) loadSkyKings(d);
    }

    /** SkyKings' history for the profile on show, when the player set a key. */
    private void loadSkyKings(ProfileData parsed) {
        String key = net.alpaka.addons.config.AlpakaConfig.instance.skykingsApiKey;
        skykingsKeyUsed = key == null ? "" : key.trim();
        if (key == null || key.isBlank()) {
            skykingsNote = null;
            if (!skykingsSeries.isEmpty()) {
                skykingsSeries = Map.of();
                version++;
            }
            return;
        }
        String id = parsed.profileId;
        String mode = null;
        for (ProfileRef r : profiles) if (r.id().equals(id)) mode = r.gameMode();
        String gameMode = mode;
        skykingsNote = "loading";
        ProfileApi.skykingsUser(parsed.uuid, key.trim()).whenComplete((answer, failure) -> {
            if (!id.equals(profileId)) return;
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                skykingsNote = cause.getMessage() == null ? "SkyKings couldn't be reached." : cause.getMessage();
                net.alpaka.addons.AlpakaAddons.LOGGER.warn("Profile Dashboard: SkyKings history for {} failed: {}", playerName, cause.toString());
                return;
            }
            try {
                Map<String, List<ProfileHistory.Point>> series = skykingsSeries(answer, parsed.profileName, gameMode);
                calibrateSkyKingsNetworth(series, parsed.networth, System.currentTimeMillis());
                skykingsSeries = series;
                skykingsNote = series.isEmpty() ? "SkyKings has no history of this profile." : null;
                version++;
                net.alpaka.addons.AlpakaAddons.LOGGER.info("Profile Dashboard: SkyKings history for {} / {}: {} snapshots",
                        playerName, parsed.profileName, series.getOrDefault(Metrics.SB_LEVEL, List.of()).size());
            } catch (RuntimeException e) {
                // A completion stage swallows what is thrown in it; said here, it shows in the log.
                skykingsNote = "SkyKings' answer couldn't be read.";
                net.alpaka.addons.AlpakaAddons.LOGGER.warn("Profile Dashboard: couldn't read SkyKings' history", e);
            }
        });
    }

    /** The profile type SkyKings files a game mode under. */
    static String skykingsType(String gameMode) {
        if (gameMode == null) return "normal";
        return switch (gameMode) {
            case "island" -> "stranded";
            default -> gameMode;
        };
    }

    /**
     * SkyKings' snapshots of one profile as series by metric id. Its profiles go by name and type,
     * not by id; the type tells apart, say, a normal Kiwi from an Ironman Kiwi. The skill average is
     * left to Elite: SkyKings has no Hunting, and an average of different skills would jump where
     * the two meet.
     */
    static Map<String, List<ProfileHistory.Point>> skykingsSeries(JsonElement answer, String profileName, String gameMode) {
        Map<String, List<ProfileHistory.Point>> out = new HashMap<>();
        if (profileName == null) return out;
        String type = skykingsType(gameMode);
        List<JsonElement> entries = new ArrayList<>();
        for (JsonElement e : Json.arr(answer, "data.historic_data")) entries.add(e);
        entries.sort(Comparator.comparingDouble(e -> Json.num(e, "timestamp", 0)));
        for (JsonElement e : entries) {
            double ts = Json.num(e, "timestamp", 0);
            if (!(ts > 0)) continue;
            long t = ts < 1e12 ? (long) (ts * 1000) : (long) ts;
            JsonElement p = null;
            for (JsonElement candidate : Json.arr(e, "profiles")) {
                if (profileName.equalsIgnoreCase(Json.str(candidate, "profile_name"))
                        && type.equals(Json.str(candidate, "profile_type") == null ? "normal" : Json.str(candidate, "profile_type"))) {
                    p = candidate;
                }
            }
            if (p == null) continue;
            Map<String, Double> m = new HashMap<>();
            double sbXp = Json.num(p, "skyblock_xp");
            if (sbXp > 0) m.put(Metrics.SB_LEVEL, sbXp / 100.0);
            for (var skill : Json.obj(p, "skills").entrySet()) {
                double xp = Json.num(skill.getValue(), "xp");
                if (xp > 0) m.put(Metrics.SKILL + skill.getKey(), xp);
            }
            for (String[] boss : ProfileData.SLAYERS) put(m, Metrics.SLAYER + boss[0], Json.num(p, "slayer_xp." + boss[0]), false);
            put(m, Metrics.SLAYER_TOTAL, Json.num(p, "slayer_xp.total"), false);
            put(m, Metrics.CATACOMBS, Json.num(p, "catacombs_xp"), false);
            for (String c : ProfileData.CLASSES) put(m, Metrics.CLASS + c, Json.num(p, "class_xp." + c), false);
            put(m, Metrics.NETWORTH, Json.num(p, "networth"), true);
            double kuudra = 0;
            for (var k : Json.obj(p, "kuudra").entrySet()) {
                if (!k.getKey().startsWith("highest_wave") && k.getValue().isJsonPrimitive()) kuudra += k.getValue().getAsDouble();
            }
            if (kuudra > 0) m.put(Metrics.KUUDRA, kuudra);
            put(m, Metrics.TROPHY_FISH, Json.num(p, "trophy_fish.total_caught"), true);
            for (var v : m.entrySet()) out.computeIfAbsent(v.getKey(), k -> new ArrayList<>()).add(new ProfileHistory.Point(t, v.getValue()));
        }
        out.computeIfPresent(Metrics.NETWORTH, (k, v) -> withoutDips(v));
        return out;
    }

    /**
     * SkyKings' networth comes close to Elite's but not quite (79B against 84B), so it is scaled the
     * same way as Soopy's: by its newest snapshot against Elite's number now. SkyKings looks once a
     * day, so a snapshot up to three days old still stands for now; prices move less than the two
     * calculations differ. An older one would carry real growth into the factor, and then the
     * networth is left out.
     */
    static void calibrateSkyKingsNetworth(Map<String, List<ProfileHistory.Point>> series, double eliteNow, long now) {
        List<ProfileHistory.Point> networth = series.get(Metrics.NETWORTH);
        if (networth == null || networth.isEmpty()) return;
        ProfileHistory.Point last = networth.get(networth.size() - 1);
        if (!(eliteNow > 0) || !(last.value() > 0) || now - last.timeMs() > 3 * ProfileHistory.DAY) {
            series.remove(Metrics.NETWORTH);
            return;
        }
        double factor = eliteNow / last.value();
        List<ProfileHistory.Point> scaled = new ArrayList<>(networth.size());
        for (ProfileHistory.Point p : networth) scaled.add(new ProfileHistory.Point(p.timeMs(), p.value() * factor));
        series.put(Metrics.NETWORTH, scaled);
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
     * Drops short excursions far from what came before and after. Down: an inventory API switched
     * off for a while makes the networth read a tenth of itself. Up: one item's price spiking for a
     * few days - SkyKings once had 48B, 106B, 165B, 165B, 48B within a week while nothing on the
     * profile changed. A run may be a few points long, since the looks that found nothing new
     * repeat it; it only goes when the value comes back to where it was.
     */
    static List<ProfileHistory.Point> withoutDips(List<ProfileHistory.Point> points) {
        List<ProfileHistory.Point> out = new ArrayList<>(points.size());
        int i = 0;
        while (i < points.size()) {
            if (!out.isEmpty()) {
                double before = out.get(out.size() - 1).value();
                int end = i;
                while (end < points.size() && end - i < 8 && away(points.get(end).value(), before)) end++;
                if (end > i && end < points.size() && back(points.get(end).value(), before)) {
                    i = end;
                    continue;
                }
            }
            out.add(points.get(i));
            i++;
        }
        return out;
    }

    private static boolean away(double value, double before) {
        return value < before * 0.5 || value > before * 1.5;
    }

    private static boolean back(double value, double before) {
        return value >= before * 0.5 && value <= before * 1.25;
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
            merged.addAll(skykingsSeries.getOrDefault(m, List.of()));
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
