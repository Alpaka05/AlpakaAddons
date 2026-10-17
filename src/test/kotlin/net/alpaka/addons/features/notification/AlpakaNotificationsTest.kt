package net.alpaka.addons.features.notification

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class AlpakaNotificationsTest {
    /**
     * A burst of notices with nothing drawing them, which is what the HUD being hidden looks like.
     * A fifth notice while four were up used to loop forever inside enqueue.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun burstWithoutRenderingStaysBounded() {
        repeat(20) { AlpakaNotifications.send("Notice $it", "", 0, 5_000L) }
        assertTrue(AlpakaNotifications.liveCount() <= 4, "more than four notices on screen")
        assertTrue(AlpakaNotifications.keptCount() <= 8, "the list grew without bound")
    }
}
