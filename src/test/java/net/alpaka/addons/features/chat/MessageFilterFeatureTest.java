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
            "Autopet equipped your [Lvl 100] Mosquito! VIEW RULE                    | AUTOPET",
            "Autopet equipped your [Lvl 200] [122✦] Golden Dragon! VIEW RULE (2)    | AUTOPET",
            "A FIRE SALE A                                                          | FIRE_SALE",
            "♨ Fire Sales for Dark Lord Skin are starting soon!                     | FIRE_SALE",
            "♨ A Fire Sale for Dark Lord Skin is starting soon!                     | FIRE_SALE",
            "♨ Fire Sale for Dark Lord Skin ended!                                  | FIRE_SALE",
            "♨ Dark Lord Skin for a limited time (2,500 left)!                      | FIRE_SALE",
            "♨ Visit the Community Shop in the next 2 days to grab yours! [WARP]    | FIRE_SALE",
            "♨ And 3 more!                                                          | FIRE_SALE",
            "♨ Selling multiple items for a limited time!                           | FIRE_SALE",
            "[WATCHDOG ANNOUNCEMENT]                                                | WATCHDOG",
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
            "[MVP+] Someone: Autopet equipped your [Lvl 100] Mosquito! VIEW RULE",
            "Guild > Someone: ♨ And 3 more!",
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

    @Test
    void fireSaleAnnouncementInOneMessage() {
        assertEquals(Kind.FIRE_SALE, MessageFilterFeature.classify("A FIRE SALE A\n♨ Selling multiple items for a limited time!"));
    }

    @Test
    void watchdogTakesItsLinesUpToTheBlankOne() {
        MessageFilterFeature.resetWatchdog();
        assertEquals(Kind.WATCHDOG, MessageFilterFeature.next("[WATCHDOG ANNOUNCEMENT]"));
        assertEquals(Kind.WATCHDOG, MessageFilterFeature.next("Watchdog has banned 5,123 players in the last 7 days."));
        assertEquals(Kind.WATCHDOG, MessageFilterFeature.next("Staff have banned an additional 1,234 in the last 7 days."));
        assertEquals(Kind.WATCHDOG, MessageFilterFeature.next("Blacklisted modifications are a bannable offense!"));
        assertEquals(Kind.WATCHDOG, MessageFilterFeature.next(""));
        assertNull(MessageFilterFeature.next("Party > [MVP+] Alpakaa: hi"));
    }

    @Test
    void watchdogNeverSwallowsMoreThanItsLimit() {
        MessageFilterFeature.resetWatchdog();
        MessageFilterFeature.next("[WATCHDOG ANNOUNCEMENT]");
        for (int i = 0; i < MessageFilterFeature.WATCHDOG_MAX_LINES; i++) {
            assertEquals(Kind.WATCHDOG, MessageFilterFeature.next("line " + i));
        }
        assertNull(MessageFilterFeature.next("Party > [MVP+] Alpakaa: hi"));
    }

    @Test
    void watchdogInOneMessage() {
        MessageFilterFeature.resetWatchdog();
        assertEquals(Kind.WATCHDOG, MessageFilterFeature.next("[WATCHDOG ANNOUNCEMENT]\nWatchdog has banned 5,123 players in the last 7 days."));
        assertNull(MessageFilterFeature.next("Party > [MVP+] Alpakaa: hi"));
    }
}
