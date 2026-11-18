package net.alpaka.addons.features.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameSuggestionsTest {
    private static final UUID PLAYER = UUID.fromString("8f16f5a6-95d3-4683-92ae-d51c1a5bb037");
    /** Hypixel's NPCs and tab list lines: accounts of version 2. */
    private static final UUID NPC = UUID.fromString("8f16f5a6-95d3-2683-92ae-d51c1a5bb037");

    @Test
    void playersOfTheLobbyButNotItsNpcsOrTabLines() {
        List<PlayerNameSuggestions.Seen> lobby = List.of(
                new PlayerNameSuggestions.Seen("its_Eleminator", PLAYER),
                new PlayerNameSuggestions.Seen("Jerry", NPC),
                new PlayerNameSuggestions.Seen("!A-a", PLAYER),
                new PlayerNameSuggestions.Seen("ITS_ELEMINATOR", PLAYER),
                new PlayerNameSuggestions.Seen(null, PLAYER));
        assertEquals(List.of("Alpakaa", "its_Eleminator", "Friend1", "Looked"),
                PlayerNameSuggestions.candidates(lobby, List.of("Friend1", "alpakaa"), List.of("Looked", "x"), "Alpakaa"));
    }

    @Test
    void friendsGuildPartyAndMessagesAreReadFromTheChat() {
        assertEquals(List.of("Alpakaa"), PlayerNameSuggestions.fromChat("Friend > Alpakaa joined."));
        assertEquals(List.of("Some_One"), PlayerNameSuggestions.fromChat("Guild > [MVP+] Some_One left."));
        assertEquals(List.of("Pen"), PlayerNameSuggestions.fromChat("From [VIP] Pen: hi there"));
        assertEquals(List.of("Pen"), PlayerNameSuggestions.fromChat("To Pen: hello"));
        assertEquals(List.of("Kiwi"), PlayerNameSuggestions.fromChat("[MVP++] Kiwi joined the party."));
        assertEquals(List.of("Kiwi"), PlayerNameSuggestions.fromChat("[MVP++] Kiwi has invited you to join their party!"));
        assertEquals(List.of("Alpakaa", "Some_One"), PlayerNameSuggestions.fromChat("Party Members: [MVP+] Alpakaa ● Some_One ●"));
        // Anything else names nobody.
        assertEquals(List.of(), PlayerNameSuggestions.fromChat("You are playing on profile: Peach"));
        assertEquals(List.of(), PlayerNameSuggestions.fromChat("Friend > some text that isn't a join"));
    }

    @Test
    void theLastLookedUpComeFirst() {
        ProfileHistory.Entry old = new ProfileHistory.Entry();
        old.player = "Old";
        old.points.add(new ProfileHistory.Snapshot(1_000, java.util.Map.of()));
        ProfileHistory.Entry recent = new ProfileHistory.Entry();
        recent.player = "Recent";
        recent.points.add(new ProfileHistory.Snapshot(9_000, java.util.Map.of()));
        ProfileHistory.Entry otherProfile = new ProfileHistory.Entry();
        otherProfile.player = "old";
        otherProfile.points.add(new ProfileHistory.Snapshot(5_000, java.util.Map.of()));
        assertEquals(List.of("Recent", "old"), ProfileHistory.players(java.util.Map.of("a:1", old, "b:1", recent, "a:2", otherProfile, "c:1", new ProfileHistory.Entry())));
    }
}
