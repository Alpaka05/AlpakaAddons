package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileSourcesTest {

    @Test
    void soopyHistoryBecomesSeriesOldestFirst() {
        // Newest first, as Soopy sends it; cut down from a real page.
        JsonElement page = JsonParser.parseString("""
                {"success":true,"data":[
                 {"type":"NoChange","last_save":3000.0},
                 {"type":"Data","last_save":2000.0,"sbLvl":400.06,"networth":41349745764.2,
                  "slayer":{"totalExp":5115344.0,"zombie":{"xp":1712650.0}},
                  "dungeons":{"catacombs_xp":30741859.6,"class_levels":{"mage":{"xp":3553419.5,"level":30.4}}},
                  "skills":{"combat":0.0,"farming":157127123.2}},
                 {"type":"Data","last_save":1000.0,"sbLvl":399.0,"networth":40000000000.0,
                  "slayer":{"totalExp":5000000.0},"dungeons":{"catacombs_xp":30000000.0},"skills":{"combat":0.0}}]}""");
        Map<String, List<ProfileHistory.Point>> series = ProfileSession.soopySeries(List.of(page));
        List<ProfileHistory.Point> networth = series.get(Metrics.NETWORTH);
        // Two snapshots, and the "no change" repeating the second.
        assertEquals(3, networth.size());
        assertEquals(1000L, networth.get(0).timeMs());
        assertEquals(41349745764.2, networth.get(2).value());
        assertEquals(1712650.0, series.get(Metrics.SLAYER + "zombie").get(0).value());
        assertEquals(3553419.5, series.get(Metrics.CLASS + "mage").get(0).value());
        assertEquals(2, series.get(Metrics.SKILL + "farming").size());
        // Zero XP was the skills API being off.
        assertNull(series.get(Metrics.SKILL + "combat"));
    }

    @Test
    void bingoProfilesOnlyWhileBingoRuns() {
        JsonElement account = JsonParser.parseString("""
                {"id":"u","name":"P","profiles":[
                 {"profileId":"a","profileName":"Apple","selected":false},
                 {"profileId":"b","profileName":"Tomato","gameMode":"bingo","selected":true},
                 {"profileId":"c","profileName":"Zucchini","gameMode":"ironman","selected":false}]}""");
        JsonElement event = JsonParser.parseString("{\"start\":1000,\"end\":2000}");
        List<ProfileSession.ProfileRef> after = ProfileSession.profiles(account, event, 5000);
        assertEquals(List.of("Apple", "Zucchini"), after.stream().map(ProfileSession.ProfileRef::name).toList());
        List<ProfileSession.ProfileRef> during = ProfileSession.profiles(account, event, 1500);
        // The selected one first.
        assertEquals("Tomato", during.get(0).name());
        assertEquals(3, during.size());
        // Without an answer about the event, old Bingo profiles stay hidden.
        assertEquals(2, ProfileSession.profiles(account, null, 1500).size());
    }

    @Test
    void readsTheHeadTextureFromANeuItem() {
        String body = "{\"nbttag\": \"{SkullOwner:{Properties:{textures:[0:{Name:\\\"textures\\\",Value:\\\"ewogIHRleHR1cmVz=\\\"}]}}}\"}";
        assertEquals("ewogIHRleHR1cmVz=", PetIcons.extract(body));
        assertNull(PetIcons.extract("{}"));
    }

    @Test
    void soopyNetworthIsScaledToElitesOrLeftOut() {
        List<ProfileHistory.Point> soopy = List.of(new ProfileHistory.Point(1_000, 100), new ProfileHistory.Point(9_000_000, 180));
        Map<String, List<ProfileHistory.Point>> series = new java.util.HashMap<>(Map.of(Metrics.NETWORTH, soopy));
        // Soopy's snapshot from this look says 180 where Elite says 260: everything scales by 260/180.
        ProfileSession.calibrateNetworth(series, 260, 9_000_000);
        assertEquals(260.0, series.get(Metrics.NETWORTH).get(1).value(), 1e-9);
        assertEquals(100 * 260 / 180.0, series.get(Metrics.NETWORTH).get(0).value(), 1e-9);
        // Without a snapshot of Soopy's from this look, nothing to compare with.
        Map<String, List<ProfileHistory.Point>> stale = new java.util.HashMap<>(Map.of(Metrics.NETWORTH, soopy));
        ProfileSession.calibrateNetworth(stale, 260, 90_000_000);
        assertNull(stale.get(Metrics.NETWORTH));
    }

    @Test
    void aMomentaryDipIsDropped() {
        List<ProfileHistory.Point> points = List.of(new ProfileHistory.Point(1, 177), new ProfileHistory.Point(2, 20.8),
                new ProfileHistory.Point(3, 172), new ProfileHistory.Point(4, 10));
        // The last one has no later neighbour to say it was a dip, so it stays.
        assertEquals(List.of(177.0, 172.0, 10.0), ProfileSession.withoutDips(points).stream().map(ProfileHistory.Point::value).toList());
        // A dip repeated by the looks after it is still a dip.
        List<ProfileHistory.Point> repeated = List.of(new ProfileHistory.Point(1, 177), new ProfileHistory.Point(2, 20.8),
                new ProfileHistory.Point(3, 20.8), new ProfileHistory.Point(4, 180));
        assertEquals(List.of(177.0, 180.0), ProfileSession.withoutDips(repeated).stream().map(ProfileHistory.Point::value).toList());
        // And so is a price spike: the real one from SkyKings.
        List<ProfileHistory.Point> spike = List.of(new ProfileHistory.Point(1, 48.7), new ProfileHistory.Point(2, 106.5),
                new ProfileHistory.Point(3, 165.3), new ProfileHistory.Point(4, 165.3), new ProfileHistory.Point(5, 48.7),
                new ProfileHistory.Point(6, 49.1));
        assertEquals(List.of(48.7, 48.7, 49.1), ProfileSession.withoutDips(spike).stream().map(ProfileHistory.Point::value).toList());
        // Growth that stays is growth.
        List<ProfileHistory.Point> growth = List.of(new ProfileHistory.Point(1, 10), new ProfileHistory.Point(2, 20),
                new ProfileHistory.Point(3, 21));
        assertEquals(3, ProfileSession.withoutDips(growth).size());
    }

    @Test
    void aStretchWithoutDataIsAGap() {
        long day = ProfileHistory.DAY;
        List<ProfileHistory.Point> points = List.of(new ProfileHistory.Point(0, 1), new ProfileHistory.Point(day, 2),
                new ProfileHistory.Point(100 * day, 3));
        long gap = ProfileChart.gapMs(100 * day);
        assertEquals(7 * day, gap);
        assertNull(ProfileChart.holeAt(points, day / 2, gap));
        assertEquals(List.of(day, 100 * day), java.util.Arrays.stream(ProfileChart.holeAt(points, 50 * day, gap)).boxed().toList());
        // Over years, a quiet fortnight is not worth marking.
        assertEquals(1000 * day / 40, ProfileChart.gapMs(1000 * day));
    }

    @Test
    void skyKingsHistoryIsReadPerProfileNameAndType() {
        // Cut down from a real /leaderboard/user answer: profiles by name and type, seconds.
        JsonElement answer = JsonParser.parseString("""
                {"success":true,"data":{"historic_data":[
                 {"timestamp":2000,"networth":0,"profiles":[
                  {"profile_name":"Peach","profile_type":"normal","skyblock_xp":62152,"networth":0,
                   "skills":{"farming":{"xp":572935534.3,"level":60}},"slayer_xp":{"zombie":2429815,"total":17638995},
                   "catacombs_xp":3130200448.9,"class_xp":{"mage":754507586.3},
                   "kuudra":{"none":16,"highest_wave_none":9,"hot":18},"trophy_fish":{"total_caught":40310}},
                  {"profile_name":"Kiwi","profile_type":"ironman","skyblock_xp":100}]},
                 {"timestamp":1000,"networth":76040000000,"profiles":[
                  {"profile_name":"Peach","profile_type":"normal","skyblock_xp":62100,"networth":76040000000}]}]}}""");
        Map<String, List<ProfileHistory.Point>> series = ProfileSession.skykingsSeries(answer, "Peach", null);
        assertEquals(List.of(621.0, 621.52), series.get(Metrics.SB_LEVEL).stream().map(ProfileHistory.Point::value).toList());
        assertEquals(1_000_000L, series.get(Metrics.SB_LEVEL).get(0).timeMs());
        assertEquals(572935534.3, series.get(Metrics.SKILL + "farming").get(0).value());
        assertEquals(34.0, series.get(Metrics.KUUDRA).get(0).value());
        assertEquals(754507586.3, series.get(Metrics.CLASS + "mage").get(0).value());
        // A networth of 0 is "not worked out this time", not a profile worth nothing.
        assertEquals(1, series.get(Metrics.NETWORTH).size());
        // The Ironman Kiwi is not the normal one.
        assertNull(ProfileSession.skykingsSeries(answer, "Kiwi", null).get(Metrics.SB_LEVEL));
        assertEquals(1.0, ProfileSession.skykingsSeries(answer, "Kiwi", "ironman").get(Metrics.SB_LEVEL).get(0).value());
    }

    @Test
    void skyKingsNetworthIsScaledWhenRecent() {
        long day = ProfileHistory.DAY;
        Map<String, List<ProfileHistory.Point>> series = new java.util.HashMap<>(Map.of(Metrics.NETWORTH,
                List.of(new ProfileHistory.Point(0, 70), new ProfileHistory.Point(10 * day, 79))));
        ProfileSession.calibrateSkyKingsNetworth(series, 84, 11 * day);
        assertEquals(84.0, series.get(Metrics.NETWORTH).get(1).value(), 1e-9);
        Map<String, List<ProfileHistory.Point>> old = new java.util.HashMap<>(Map.of(Metrics.NETWORTH,
                List.of(new ProfileHistory.Point(0, 70))));
        ProfileSession.calibrateSkyKingsNetworth(old, 84, 10 * day);
        assertNull(old.get(Metrics.NETWORTH));
    }
}
