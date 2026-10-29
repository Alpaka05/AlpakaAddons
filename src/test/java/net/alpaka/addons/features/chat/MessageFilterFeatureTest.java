package net.alpaka.addons.features.chat;

import net.alpaka.addons.features.chat.MessageFilterFeature.DungeonKind;
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
            "You have 2 items stashed away!                                         | STASH",
            "You have 1,000 materials stashed away!                                 | STASH",
            "(This totals 3 types of materials stashed!)                            | STASH",
            ">>> CLICK HERE to pick them up! <<<                                    | STASH",
            ">>> CLICK HERE to pick it up! <<<                                      | STASH",
            "[Sacks] +11 items. (Last 7s.)                                          | SACKS",
            "[Sacks] -1 item. (Last 5s.)                                            | SACKS",
            "[Sacks] +2,111 items, -4 items. (Last 30s.)                            | SACKS",
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
            "Someone: You have 2 items stashed away!",
            "[MVP+] Someone: [Sacks] +11 items. (Last 7s.)",
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

    @Test
    void stashReminderInOneMessage() {
        assertEquals(Kind.STASH, MessageFilterFeature.classify(
                "You have 2 items stashed away!\n                >>> CLICK HERE to pick them up! <<<"));
        assertNull(MessageFilterFeature.classify("You have 2 items stashed away!\nSomeone: hi"));
    }

    /** Color-stripped dungeon lines (from SkyHanni's DungeonChatFilter) and the group each is in. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Alpakaa has started the dungeon countdown. The dungeon will begin in 1 minute. | PREPARE",
            "Alpakaa is now ready!                                                   | PREPARE",
            "Dungeon starts in 4 seconds.                                            | PREPARE",
            "Dungeon starts in 1 second.                                             | PREPARE",
            "[NPC] Mort: Talk to me to change your class and ready up.               | PREPARE",
            "[NPC] Mort: Good luck.                                                  | START",
            "[Berserk] Melee Damage 48% -> 88%                                       | START",
            "A shiver runs down your spine...                                        | AMBIENCE",
            "Alpakaa has obtained Superboom TNT x2!                                  | PICKUP",
            "You found a Wither Essence! Everyone gains an extra essence!            | PICKUP",
            "Fairy the Fairy: Have a great life!                                     | PICKUP",
            "RIGHT CLICK on a WITHER door to open it. This key can only be used to open 1 door! | REMINDER",
            "DUNGEON BUFF! Alpakaa found a Blessing of Power III! (Hidden)           | BLESSING",
            "A Blessing of Life was picked up!                                       | BLESSING",
            "'     Granted you +20 HP and +5 Defense.'                               | BLESSING",
            "This chest has already been searched!                                   | NOT_POSSIBLE",
            "The Lost Adventurer struck you for 1,234 damage!                        | DAMAGE",
            "Crypt Lurker hit you for 512.3 damage.                                  | DAMAGE",
            "Mute silenced you!                                                      | DAMAGE",
            "Your Implosion hit 3 enemies for 1,234,567 damage.                      | ABILITY",
            "This ability is on cooldown for 2s.                                     | ABILITY",
            "Ragnarok is ready to use! Press DROP to activate it!                    | ABILITY",
            "PUZZLE SOLVED! Alpakaa tied Tic Tac Toe! Good job!                      | PUZZLE",
            "[STATUE] Oruo the Omniscient: One more question!                        | PUZZLE",
            "Alpakaa unlocked Wither Essence x3!                                     | END",
            "+41,234 Catacombs Experience (Team Bonus)                               | END",
    })
    void recognizesDungeonChatter(String text, DungeonKind kind) {
        assertEquals(kind, MessageFilterFeature.classifyDungeon(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Party > [MVP+] Alpakaa: Good luck.",
            "[MVP+] Someone: The Lost Adventurer struck you for 1,234 damage!",
            "Party > Someone: is now ready!",
            "[MVP+] Someone: A shiver runs down your spine...",
            "[BOSS] Maxor: WELL WELL WELL LOOK WHO'S HERE!",
            "RARE DROP! Necron's Handle (+250% ✯ Magic Find)",
    })
    void leavesOtherDungeonLinesAlone(String text) {
        assertNull(MessageFilterFeature.classifyDungeon(text));
    }
}
