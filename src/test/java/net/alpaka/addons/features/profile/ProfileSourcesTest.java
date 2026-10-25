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
    }
}
