package net.alpaka.addons.features.chat;

import net.alpaka.addons.features.chat.MessageFilterFeature.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFilterFeatureTest {
    /** Color-stripped Hypixel lines (from SkyHanni's ChatFilter tests) and the kind each is. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Welcome to Hypixel SkyBlock!                                           | WELCOME",
            "You earned 2 GEXP from playing SkyBlock!                               | GUILD_EXP",
            "You earned 2 GEXP + 210 Event EXP from playing SkyBlock!               | GUILD_EXP",
            "You earned 10 Event EXP from playing SkyBlock!                         | GUILD_EXP",
            "+175 Kill Combo                                                        | KILL_COMBO",
            "+5 Kill Combo +3% ✯ Magic Find                                         | KILL_COMBO",
            "Your Kill Combo has expired! You reached a 23 Kill Combo!              | KILL_COMBO",
            "You are playing on profile: Kiwi                                       | PROFILE_JOIN",
            "You are playing on profile: Kiwi (Co-op)                               | PROFILE_JOIN",
            "Profile ID: 691d6a3b-23ea-4541-80b5-771facc73b16                       | PROFILE_JOIN",
            "You haven't claimed your Summer Rewards yet!                           | REWARD_BUNDLE",
            "Talk to the Summer Sloth in the Hub!                                   | REWARD_BUNDLE_HINT",
    })
    void recognizesSpam(String text, Kind kind) {
        assertEquals(kind, MessageFilterFeature.classify(text.strip()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[MVP+] Someone: Welcome to Hypixel SkyBlock!",
            "Guild > [VIP] Someone: +175 Kill Combo",
            "Someone: You are playing on profile: Kiwi",
            "RARE DROP! (Enchanted Book) +250% ✯ Magic Find",
            "Party > [MVP+] Alpakaa: hi",
    })
    void leavesOtherLinesAlone(String text) {
        assertNull(MessageFilterFeature.classify(text));
    }

    @Test
    void blankLinesAreEmpty() {
        assertEquals(Kind.EMPTY, MessageFilterFeature.classify(""));
        assertEquals(Kind.EMPTY, MessageFilterFeature.classify("   "));
    }

    @Test
    void aSwitchedOffKindStaysVisible() {
        assertTrue(MessageFilterFeature.isHidden(Kind.KILL_COMBO, true, Long.MAX_VALUE));
        assertFalse(MessageFilterFeature.isHidden(Kind.KILL_COMBO, false, Long.MAX_VALUE));
    }

    @Test
    void rewardHintOnlyRightAfterTheReminder() {
        assertTrue(MessageFilterFeature.isHidden(Kind.REWARD_BUNDLE_HINT, true, 50));
        assertFalse(MessageFilterFeature.isHidden(Kind.REWARD_BUNDLE_HINT, true, MessageFilterFeature.HINT_WINDOW_MS + 1));
        assertFalse(MessageFilterFeature.isHidden(Kind.REWARD_BUNDLE_HINT, false, 50));
    }
}
