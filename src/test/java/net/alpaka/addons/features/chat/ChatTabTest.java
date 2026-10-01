package net.alpaka.addons.features.chat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTabTest {
    /** Colour-stripped Hypixel lines and the tab each belongs on. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Party > [MVP+] Alpakaa: hi                         | PARTY",
            "Party Finder > Someone joined the dungeon group!   | PARTY",
            "Guild > [VIP] Someone [Officer]: gg                 | GUILD",
            "Officer > Someone: meeting                          | GUILD",
            "Co-op > [MVP+] Friend: selling now                  | COOP",
            "From [MVP+] Friend: hey                             | PRIVATE",
            "To [VIP] Friend: hey                                | PRIVATE",
            "From stash: 3 items                                 | ALL",
            "[285] ⚔ [MVP+] Someone: join the party pls          | ALL",
            "[MVP+] Someone: my guild is recruiting              | ALL",
            "Someone: lf party for t5                            | ALL",
            "[MVP+] Someone joined the party.                    | PARTY",
            "[VIP] Someone has left the party.                   | PARTY",
            "[MVP+] Leader invited [VIP] Friend to the party! They have 60 seconds to accept. | PARTY",
            "[MVP+] Leader has invited you to join their party!  | PARTY",
            "The party was disbanded because all invites expired and the party was empty. | PARTY",
            "You left the party.                                 | PARTY",
            "Party Members (3)                                   | PARTY",
            "Someone joined the guild!                           | GUILD",
            "[MVP+] Someone was promoted from Member to Officer  | GUILD",
            "The guild has completed Tier 3 of this week's Guild Quest! | GUILD",
            "RARE DROP! (Enchanted Book) +250% ✯ Magic Find      | ALL",
            "[NPC] Maddox: Your quest is ready                   | ALL",
    })
    void sortsLinesOntoTheirTab(String text, ChatTab tab) {
        assertEquals(tab, ChatTab.classify(text.strip()));
    }
}
