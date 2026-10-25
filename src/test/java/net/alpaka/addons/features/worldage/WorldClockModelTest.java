package net.alpaka.addons.features.worldage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldClockModelTest {

    /** One time update as recorded: game time, overworld clock ticks, clock rate. */
    private record Update(long gameTime, long clockTicks, float rate) {}

    private static WorldClockModel replay(Update... updates) {
        WorldClockModel model = new WorldClockModel();
        for (Update update : updates) {
            model.onTimeUpdate(update.gameTime(), update.clockTicks(), update.rate());
        }
        return model;
    }

    /** Crimson Isle, server mini194AA, the first packets after joining (recorded 2026-10-18). */
    private static final Update[] CRIMSON_ISLE = {
            new Update(952522848L, 681594005L, 1.0f),
            new Update(952522862L, 681594019L, 1.0f),
            new Update(952522863L, 681618000L, 1.0f),
            new Update(952522882L, 681618019L, 1.0f),
            new Update(952522883L, 681642000L, 1.0f),
            new Update(952522902L, 681642019L, 1.0f),
            new Update(952522903L, 681666000L, 1.0f),
            new Update(952522922L, 681666019L, 1.0f),
            new Update(952522923L, 681690000L, 1.0f),
    };

    @Test
    void crimsonIsleIsPinnedAndShowsItsAgeInsteadOfCountingUp() {
        WorldClockModel model = replay(CRIMSON_ISLE);
        assertEquals(WorldClockModel.Mode.PINNED, model.mode());
        // 681690000 / 24000 = 28403 updates of 20 ticks = 568060 ticks = day 23.
        assertEquals(23L, model.day(681690000L));
    }

    @Test
    void crimsonIsleDayStaysPutWhileTheClockKeepsJumping() {
        WorldClockModel model = replay(CRIMSON_ISLE);
        long before = model.day(0L);
        // Another ten seconds of jumps: ten more days on the clock, under a day of real age.
        for (int i = 1; i <= 10; i++) {
            model.onTimeUpdate(952522923L + 20L * i, 681690000L + 24000L * i, 1.0f);
        }
        assertEquals(before, model.day(0L));
    }

    @Test
    void frozenHubClockShowsTheClocksDay() {
        // Hub, server mini62CP: fixed at 42000, rate 0. The world age is the map's, so the clock's
        // day is shown, as NoFrills and Skytils do.
        WorldClockModel model = replay(
                new Update(1330211943L, 42000L, 0.0f),
                new Update(1330211944L, 42000L, 0.0f),
                new Update(1330211963L, 42000L, 0.0f));
        assertEquals(WorldClockModel.Mode.FROZEN, model.mode());
        assertEquals(1L, model.day(42000L));
    }

    @Test
    void frozenLobbyClockShowsTheClocksDay() {
        // Lobby dynamiclobby5G: fixed at 18000, rate 0.
        WorldClockModel model = replay(
                new Update(34781616L, 18000L, 0.0f),
                new Update(34781636L, 18000L, 0.0f));
        assertEquals(0L, model.day(18000L));
    }

    @Test
    void frozenPrivateIslandClockReadsTheWorldAge() {
        // Private Island, server mini105N: clock fixed at 0, rate 0, world age counting from its load.
        WorldClockModel model = replay(
                new Update(341L, 0L, 0.0f),
                new Update(358L, 0L, 0.0f),
                new Update(378L, 0L, 0.0f));
        assertEquals(WorldClockModel.Mode.FROZEN, model.mode());
        assertEquals(0L, model.day(0L));

        // The same island three days later.
        model.onTimeUpdate(3L * 24000L + 378L, 0L, 0.0f);
        assertEquals(3L, model.day(0L));
    }

    @Test
    void runningClockGivesItsDay() {
        long start = 3L * 24000L + 5000L;
        WorldClockModel model = replay(
                new Update(1000L, start, 1.0f),
                new Update(1020L, start + 20L, 1.0f),
                new Update(1040L, start + 40L, 1.0f));
        assertEquals(WorldClockModel.Mode.NORMAL, model.mode());
        assertEquals(3L, model.day(start + 45L));
        // The live clock carries the day over midnight between updates.
        assertEquals(4L, model.day(4L * 24000L + 10L));
    }

    @Test
    void lagDoesNotLookLikeAJump() {
        long start = 10L * 24000L;
        WorldClockModel model = replay(
                new Update(1000L, start, 1.0f),
                new Update(1100L, start + 100L, 1.0f),
                new Update(1100L, start + 100L, 1.0f),
                new Update(1700L, start + 700L, 1.0f));
        assertEquals(WorldClockModel.Mode.NORMAL, model.mode());
        assertEquals(10L, model.day(start + 700L));
    }

    @Test
    void pinnedClockShowsNoDayBeforeItsFirstJump() {
        // The second Crimson Isle update came 14 ticks after the first, before the first jump; read
        // as a running clock it showed the clock's raw count, day 28399, for a second.
        WorldClockModel model = replay(CRIMSON_ISLE[0], CRIMSON_ISLE[1]);
        assertEquals(WorldClockModel.Mode.WAITING, model.mode());
        assertEquals(WorldClockModel.UNKNOWN_DAY, model.day(CRIMSON_ISLE[1].clockTicks()));
    }

    @Test
    void firstUpdateAloneIsUnknown() {
        // One value cannot tell a running clock from a pinned one.
        WorldClockModel model = replay(CRIMSON_ISLE[0]);
        assertEquals(WorldClockModel.UNKNOWN_DAY, model.day(CRIMSON_ISLE[0].clockTicks()));
    }

    @Test
    void resetForgetsThePreviousWorld() {
        WorldClockModel model = replay(CRIMSON_ISLE);
        model.reset();
        assertEquals(WorldClockModel.Mode.WAITING, model.mode());
        model.onTimeUpdate(500L, 2L * 24000L, 1.0f);
        model.onTimeUpdate(520L, 2L * 24000L + 20L, 1.0f);
        model.onTimeUpdate(540L, 2L * 24000L + 40L, 1.0f);
        assertEquals(2L, model.day(2L * 24000L + 40L));
    }

    @Test
    void clockThatStartsRunningAgainAfterAFreezeIsRead() {
        WorldClockModel model = replay(
                new Update(100L, 42000L, 0.0f),
                new Update(120L, 42000L, 1.0f),
                new Update(140L, 42020L, 1.0f));
        assertEquals(WorldClockModel.Mode.NORMAL, model.mode());
        assertEquals(1L, model.day(42020L));
    }

    @Test
    void oneOffJumpOnlyPinsBriefly() {
        // A /time set two days ahead on a normal world.
        long start = 5L * 24000L;
        WorldClockModel model = replay(
                new Update(1000L, start, 1.0f),
                new Update(1020L, start + 20L, 1.0f),
                new Update(1040L, start + 2L * 24000L, 1.0f));
        assertEquals(WorldClockModel.Mode.PINNED, model.mode());
        long clock = start + 2L * 24000L;
        for (long gameTime = 1060L; gameTime <= 1160L; gameTime += 20L) {
            clock += 20L;
            model.onTimeUpdate(gameTime, clock, 1.0f);
        }
        assertEquals(WorldClockModel.Mode.NORMAL, model.mode());
        assertEquals(7L, model.day(clock));
    }
}
