package net.alpaka.addons.features.quickjoin

import net.alpaka.addons.compliance.Outbound
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.mainmenu.CustomMainMenuScreen
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.Connection
import net.minecraft.util.Util
import net.minecraft.world.scores.DisplaySlot
import java.lang.ref.WeakReference

/**
 * Quick Join: onto Hypixel when the game starts, and on into SkyBlock once Hypixel is joined.
 *
 * /skyblock waits until the lobby's sidebar has been up for [SETTLE_MS]: sent during the login it
 * was dropped, and the sidebar is also what tells a lobby from a SkyBlock island the player was put
 * straight back onto. Each connection gets one try, so going to a lobby later stays where it is.
 */
object QuickJoin {

    /** How long the sidebar has to be up before /skyblock goes out. */
    private const val SETTLE_MS = 1_000L

    /** No lobby sidebar this long after joining (limbo, a queue) and Quick Join leaves it be. */
    private const val GIVE_UP_MS = 20_000L

    /** Whether the first main menu of this launch has been seen, or a server joined before it. */
    private var launchHandled = false

    /** When Hypixel was joined and /skyblock is still to come; 0 when nothing is pending. */
    private var joinedAtMs = 0L
    private var sidebarSinceMs = 0L

    /**
     * The connection Quick Join last acted on. A server switch inside Hypixel can log in again on the
     * same connection; that is not a new join. Weak so a closed connection is not kept alive.
     */
    private var handled: WeakReference<Connection>? = null

    @JvmStatic
    fun register() {
        ClientPlayConnectionEvents.JOIN.register { handler, _, client -> onJoin(handler.connection, client) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> joinedAtMs = 0L }
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    /** Connects to [ip] the way the server list would, with the player's own entry if there is one. */
    @JvmStatic
    fun connect(parent: Screen, mc: Minecraft, ip: String) {
        // The player's own server-list entry when there is one, as the server list itself would
        // join: its resource pack choice stands. A fresh entry is always set to prompt, so Hypixel's
        // pack was asked about again on every join from here.
        val data = savedEntryFor(mc, ip)
            ?: ServerData(if (ip.contains("alpha")) "Hypixel Alpha" else "Hypixel Network", ip, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(ip), data, false, null)
    }

    private fun onJoin(connection: Connection, mc: Minecraft) {
        launchHandled = true
        if (handled?.get() === connection) return
        handled = WeakReference(connection)
        val ip = mc.currentServer?.ip ?: return
        if (!isHypixel(hostOf(ip))) return
        joinedAtMs = Util.getMillis()
        sidebarSinceMs = 0L
    }

    private fun tick(mc: Minecraft) {
        if (!launchHandled) tickLaunch(mc)
        if (joinedAtMs == 0L) return

        val cfg = AlpakaConfig.instance
        val now = Util.getMillis()
        if (!cfg.quickJoinEnabled || !cfg.quickJoinSkyblock || now - joinedAtMs > GIVE_UP_MS) {
            joinedAtMs = 0L
            return
        }
        val level = mc.level ?: return
        if (mc.player == null) return
        if (level.scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == null) {
            sidebarSinceMs = 0L
            return
        }
        if (sidebarSinceMs == 0L) {
            sidebarSinceMs = now
            return
        }
        if (now - sidebarSinceMs < SETTLE_MS) return
        joinedAtMs = 0L
        if (!SkyblockUtils.isOnSkyblock()) Outbound.quickJoinSkyblock()
    }

    /** Once the first main menu is up, connects to Hypixel if Join Hypixel on Launch is on. */
    private fun tickLaunch(mc: Minecraft) {
        val screen = mc.gui.screen()
        if (mc.gui.overlay() != null || mc.level != null) return
        if (screen !is TitleScreen && screen !is CustomMainMenuScreen) return
        launchHandled = true
        val cfg = AlpakaConfig.instance
        // Shift is the way out for a launch that should stay in the menu, for singleplayer say.
        if (!cfg.quickJoinEnabled || !cfg.quickJoinOnLaunch || mc.hasShiftDown()) return
        connect(screen, mc, "mc.hypixel.net")
    }

    /** The first server-list entry for the same Hypixel network as [ip], or null; alpha only matches alpha. */
    private fun savedEntryFor(mc: Minecraft, ip: String): ServerData? {
        val wanted = hostOf(ip)
        val alpha = wanted.startsWith("alpha.")
        val list = ServerList(mc)
        list.load()
        for (i in 0 until list.size()) {
            val entry = list.get(i)
            val host = hostOf(entry.ip)
            val same = if (alpha) host == wanted else isHypixel(host) && !host.startsWith("alpha.")
            if (same) return entry
        }
        return null
    }

    private fun isHypixel(host: String): Boolean = host == "hypixel.net" || host.endsWith(".hypixel.net")

    private fun hostOf(ip: String): String = ip.trim().lowercase().substringBefore(':').trimEnd('.')
}
