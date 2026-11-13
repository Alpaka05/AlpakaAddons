package net.alpaka.addons.features.profile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileHistoryTest {

    private static ProfileHistory.Snapshot at(long t, double v) {
        return new ProfileHistory.Snapshot(t, Map.of("nw", v));
    }

    @Test
    void closeSnapshotsMergeAndLateOnesSortIn() {
        List<ProfileHistory.Snapshot> points = new ArrayList<>();
        long h = ProfileHistory.HOUR;
        ProfileHistory.add(points, at(0, 1), ProfileHistory.MERGE_MS);
        ProfileHistory.add(points, at(h, 2), ProfileHistory.MERGE_MS);
        ProfileHistory.add(points, at(h + 60_000, 3), ProfileHistory.MERGE_MS);
        assertEquals(2, points.size());
        assertEquals(3.0, points.get(1).m().get("nw"));
        ProfileHistory.add(points, at(h / 2, 9), ProfileHistory.MERGE_MS);
        assertEquals(3, points.size());
        assertEquals(h / 2, points.get(1).t());
    }

    @Test
    void oldHistoryIsThinnedButRecentIsKept() {
        long now = 400 * ProfileHistory.DAY;
        List<ProfileHistory.Snapshot> points = new ArrayList<>();
        // A snapshot every hour for 100 days.
        for (long t = now - 100 * ProfileHistory.DAY; t <= now; t += ProfileHistory.HOUR) points.add(at(t, t));
        int before = points.size();
        ProfileHistory.thin(points, now);
        assertTrue(points.size() < before / 4, "thinned to " + points.size());
        // The last two days stay as they were: 48 hourly snapshots and the one at now.
        long recent = points.stream().filter(p -> now - p.t() < 2 * ProfileHistory.DAY).count();
        assertEquals(48, recent);
        // Still in order, and the newest kept.
        for (int i = 1; i < points.size(); i++) assertTrue(points.get(i).t() > points.get(i - 1).t());
        assertEquals(now, points.get(points.size() - 1).t());
        // Thinning twice changes nothing more.
        int once = points.size();
        ProfileHistory.thin(points, now);
        assertEquals(once, points.size());
    }

    @Test
    void theProfileLookedAtLastIsRemembered() {
        ProfileHistory.Entry peach = new ProfileHistory.Entry();
        peach.player = "Alpakaa";
        peach.points.add(new ProfileHistory.Snapshot(1_000, java.util.Map.of()));
        peach.points.add(new ProfileHistory.Snapshot(9_000, java.util.Map.of()));
        ProfileHistory.Entry kiwi = new ProfileHistory.Entry();
        kiwi.player = "Alpakaa";
        kiwi.points.add(new ProfileHistory.Snapshot(5_000, java.util.Map.of()));
        ProfileHistory.Entry other = new ProfileHistory.Entry();
        other.player = "Someone";
        other.points.add(new ProfileHistory.Snapshot(99_000, java.util.Map.of()));
        java.util.Map<String, ProfileHistory.Entry> entries = java.util.Map.of(
                "8f16:b135", peach, "8f16:93ea", kiwi, "0123:cafe", other, "8f16:empty", new ProfileHistory.Entry());
        // By UUID or by name, whichever was typed; the one with the newest look wins.
        assertEquals("b135", ProfileHistory.lastProfile(entries, "8F16"));
        assertEquals("b135", ProfileHistory.lastProfile(entries, "alpakaa"));
        assertEquals("cafe", ProfileHistory.lastProfile(entries, "Someone"));
        assertEquals(null, ProfileHistory.lastProfile(entries, "Nobody"));
    }
}
