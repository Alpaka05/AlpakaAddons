package net.alpaka.addons.features.profile;

import net.alpaka.addons.config.AlpakaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With Network Features off, the dashboard must not go online at all: the README promises that no
 * request is made. Every request of the dashboard goes through ProfileApi or AlpakaApi, so each
 * kind of request there has to fail at once, before anything is sent or cached, and before the
 * game is asked to log in anywhere.
 */
class ProfileApiNetworkOffTest {
    private AlpakaConfig saved;

    @BeforeEach
    void networkOff() {
        saved = AlpakaConfig.instance;
        AlpakaConfig.instance = new AlpakaConfig();
        AlpakaConfig.instance.allowApiCalls = false;
    }

    @AfterEach
    void restore() {
        AlpakaConfig.instance = saved;
    }

    private static void failsAtOnce(CompletableFuture<?> future) {
        // Done already: nothing was queued or sent.
        assertTrue(future.isDone());
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(ProfileApi.NetworkOff.class, e.getCause());
    }

    @Test
    void noRequestGoesOut() {
        String uuid = "8f16f5a695d3468392aed51c1a5bb037";
        failsAtOnce(AlpakaApi.dashboard("Alpakaa", null, null, false));
        failsAtOnce(AlpakaApi.dashboard(uuid, "p1", null, true));
        failsAtOnce(ProfileApi.soopyHistory(uuid, "p1", 0));
        failsAtOnce(ProfileApi.skykingsUser(uuid, "<token>"));
        failsAtOnce(ProfileApi.bingo());
        failsAtOnce(ProfileApi.collections());
        failsAtOnce(ProfileApi.neuItem("https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/X.json"));
    }

    @Test
    void aSessionOpenedWhileOffSendsNothing() {
        ProfileSession session = new ProfileSession("Alpakaa", false);
        assertTrue(session.state == ProfileSession.State.FAILED, "state " + session.state);
    }
}
