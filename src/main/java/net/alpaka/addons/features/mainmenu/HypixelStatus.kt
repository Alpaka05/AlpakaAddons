package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.server.network.EventLoopGroupHolder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hypixel's live player count for the main menu, fetched the way the server list does it: an
 * ordinary status ping to mc.hypixel.net, nothing more.
 *
 * Lives for the whole game rather than in the menu. The menu used to own the pinger and ping from
 * init(), on the render thread: the DNS lookup and the connect froze the game for a round trip on
 * every open, every return from a sub-screen, every resize event and once a minute. Now the ping
 * runs on its own thread, at most once a minute after a success and with a growing pause after a
 * failure, and the menu only asks for a refresh and reads the result.
 *
 * Nothing is pinged while "Allow Network Features" is off.
 */
object HypixelStatus {
    enum class State { OFF, CONNECTING, ONLINE, OFFLINE }

    /** A count younger than this is not refreshed. */
    private const val FRESH_MS = 60_000L
    /** An attempt still unanswered after this long counts as failed. */
    private const val TIMEOUT_MS = 8_000L
    /** Pause before the next attempt after one, two, three or more failures in a row. */
    private val BACKOFF_MS = longArrayOf(15_000L, 30_000L, 60_000L)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Alpaka-HypixelPing").apply { isDaemon = true }
    }
    private val pinger = ServerStatusPinger()
    private val data = ServerData("Hypixel Network", "mc.hypixel.net", ServerData.Type.OTHER)
    private val inFlight = AtomicBoolean(false)

    // Only touched on the render thread; worker results arrive through Minecraft.execute.
    private var attemptStartedMs = 0L
    private var lastSuccessMs = 0L
    private var failures = 0
    private var nextAttemptMs = 0L

    /** The last player count that arrived, kept while a refresh runs so the line does not flicker. */
    var online: Int? = null
        private set
    /** Round trip of the last ping in milliseconds, or 0 when not known. */
    var pingMs: Long = 0L
        private set

    val state: State
        get() = when {
            !AlpakaConfig.instance.allowApiCalls -> State.OFF
            online != null -> State.ONLINE
            failures > 0 -> State.OFFLINE
            else -> State.CONNECTING
        }

    /** Starts a ping unless one is running, the last count is still fresh, or a failure pause runs. */
    fun requestRefresh() {
        if (!AlpakaConfig.instance.allowApiCalls) return
        val now = System.currentTimeMillis()
        if (lastSuccessMs != 0L && now - lastSuccessMs < FRESH_MS) return
        if (now < nextAttemptMs) return
        if (!inFlight.compareAndSet(false, true)) return

        attemptStartedMs = now
        val mc = Minecraft.getInstance()
        val transport = EventLoopGroupHolder.remote(mc.options.useNativeTransport())
        executor.execute {
            try {
                // The first callback fires when the status with the player count has arrived, the
                // second when the round trip is known. A failed attempt never calls back.
                pinger.pingServer(
                    data,
                    { mc.execute { onStatus() } },
                    { mc.execute { pingMs = data.ping } },
                    transport,
                )
            } catch (_: Throwable) {
                mc.execute { onFailure() }
            }
        }
    }

    /** Called every tick while the menu is open. */
    fun tick() {
        pinger.tick()
        if (inFlight.get() && System.currentTimeMillis() - attemptStartedMs > TIMEOUT_MS) {
            pinger.removeAll()
            onFailure()
        }
        requestRefresh()
    }

    /** The menu closed: drop any open connection. The next open refreshes if the count is stale. */
    fun onMenuClosed() {
        pinger.removeAll()
        inFlight.set(false)
    }

    private fun onStatus() {
        inFlight.set(false)
        val players = data.players ?: return onFailure()
        online = players.online()
        lastSuccessMs = System.currentTimeMillis()
        failures = 0
        nextAttemptMs = 0L
    }

    private fun onFailure() {
        inFlight.set(false)
        failures++
        // A count that has gone stale is no longer shown as current.
        if (lastSuccessMs == 0L || System.currentTimeMillis() - lastSuccessMs > FRESH_MS * 3) online = null
        nextAttemptMs = System.currentTimeMillis() + BACKOFF_MS[(failures - 1).coerceAtMost(BACKOFF_MS.size - 1)]
    }
}
