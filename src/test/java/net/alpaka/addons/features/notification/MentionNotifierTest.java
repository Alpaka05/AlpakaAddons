package net.alpaka.addons.features.notification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionNotifierTest {
    /** Sender blocks, the part before the first ": ", as players' lines carry them. */
    @ParameterizedTest
    @ValueSource(strings = {
            "[MVP+] Someone",
            "[302] [MVP+] Someone",
            "[302] ⚔ Someone",
            "Someone",
            "Guild > [VIP] Someone [ELITE]",
            "Party > [MVP++] Some_One",
            "Officer > Someone",
            "Co-op > [VIP+] Someone",
            "From [MVP+] Someone",
            "LEMAN > [Discord] VultureAir⇾Jiles777",
    })
    void playersCount(String sender) {
        assertTrue(MentionNotifier.fromPlayer(sender), sender);
    }

    /** NPCs, bosses and other speakers the server voices itself. */
    @ParameterizedTest
    @ValueSource(strings = {
            "[NPC] Elle",
            "[NPC] Bartender Bob",
            "[BOSS] Maxor",
            "✆ Kat",
            "The Watcher",
            "Profile ID",
    })
    void serverSpeakersDoNot(String sender) {
        assertFalse(MentionNotifier.fromPlayer(sender), sender);
    }

    /** Color-stripped chat lines, and whether they mention the player "Sky". */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "[MVP+] Someone: hey Sky come here            | true",
            "[MVP+] Someone: sky?                         | true",
            "Party > Bob: @Sky look at this               | true",
            "Guild > [VIP] Bob [ELITE]: thanks, Sky!      | true",
            "[MVP+] Skyler: hi Sky                        | true",
            "[MVP+] Someone: skyblock is fun              | false",
            "[MVP+] Someone: ask Skyler                   | false",
            "Party > Bob: Sky_ is somebody else           | false",
            "[MVP+] Sky: hi everyone, Sky here            | false",
            "Guild > [VIP] Sky [ELITE]: Sky out           | false",
            "Welcome back Sky!                            | false",
            "[MVP+] Someone:                              | false",
    })
    void mentionsTheNameAsAWordFromSomebodyElse(String line, boolean mentioned) {
        assertEquals(mentioned, MentionNotifier.mentions(line.strip(), "Sky"), line);
    }
}
