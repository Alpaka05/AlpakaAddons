package net.alpaka.addons.features.dungeonmap

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapId
import kotlin.math.abs

/**
 * One player on the clear map: the marker the map last sent for them, if any, and their entity
 * while they are in render distance. [name] is null for a marker no teammate could be matched to.
 */
class ClearPlayer(
    val key: String,
    val name: String?,
    val self: Boolean,
    val marker: MapMarker?,
    val entity: AbstractClientPlayer?,
    val skin: PlayerSkin?,
)

object DungeonMapFeature {
    val snapshot = DungeonMapSnapshot()
    val calibration = DungeonMapCalibration()
    /** Everyone on the clear map as of the last tick, the player last so they are drawn on top. */
    var players: List<ClearPlayer> = emptyList()
        private set
    /** Room names and secret counts to draw, rebuilt when the map or a room changes. */
    var labels: List<RoomLabel> = emptyList()
        private set
    /** Checkmark pixels the labels replace (see [RoomLabels.Result.cover]), with a revision for the texture. */
    var cover: IntArray? = null
        private set
    var coverRevision = 0
        private set
    private var labelKey = ""
    private val tracks = HashMap<String, MarkerTrack>()
    private var party: List<PartyMember> = emptyList()
    private var mapId: MapId? = null
    private var ticks = 0
    private var stillTicks = 0
    private var lastX = Double.NaN
    private var lastZ = Double.NaN

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
        // "3/5 Secrets" on the action bar, also when another mod hides that part of it.
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (overlay) onActionBar(message) }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (overlay) onActionBar(message) }
    }

    private fun onActionBar(message: Component) {
        if (!AlpakaConfig.instance.dungeonMapEnabled || !DungeonState.inDungeon() || DungeonState.inBossRoom) return
        DungeonRooms.onActionBar(SkyblockUtils.cleanColor(message.string))
    }

    private fun tick(client: Minecraft) {
        ticks++
        if (client.level == null || !DungeonState.inDungeon() || !SkyblockUtils.isOnSkyblock()) {
            if (mapId != null || snapshot.colors != null || BossRoomMap.pixels != null || players.isNotEmpty()) reset()
            return
        }
        val cfg = AlpakaConfig.instance
        if (!cfg.dungeonMapEnabled) return
        if (DungeonState.inBossRoom) {
            players = emptyList()
            // The saved map's labels still follow the options.
            updateLabels()
            // Every half second, and sooner for a fast or teleporting player, so the view that follows
            // them stays on sampled terrain.
            val due = ticks % 10 == 0 || ticks % 2 == 0 && cfg.dungeonMapSmoothMarkers && BossRoomMap.drifted(client)
            if (cfg.dungeonMapShowBoss && cfg.dungeonMapBossMode != 1 && due) BossRoomMap.update(client)
            return
        }
        val player = client.player ?: return
        if (mapId == null || ticks % 20 == 0) findMap(player)
        val id = mapId ?: return
        val data = client.level?.getMapData(id) ?: return
        if (snapshot.update(data.colors, false) && !calibration.hasRooms) {
            val inks = snapshot.inks
            if (inks == null || !calibration.measureRooms(inks)) calibration.assumeFloor(DungeonState.floor)
        }
        // The tab list only changes when someone dies or is revived; four reads a second are plenty.
        if (ticks % 5 == 0) party = DungeonParty.read(client)
        // Rooms are recognized from loaded chunks, so the scan retries until each one has arrived.
        if (ticks % 20 == 0) client.level?.let(DungeonRooms::scan)
        DungeonRooms.track(player)
        updateLabels()
        // Read every tick: markers that wait for the next read arrive late and step unevenly.
        val decorations = data.decorations.toList()
        watchOwnMarker(player, decorations)
        players = resolve(client, player, decorations)
        if (ticks % 20 == 0) {
            val now = now()
            tracks.values.removeIf { now - it.touched > 2000 }
        }
    }

    private fun updateLabels() {
        val cfg = AlpakaConfig.instance
        val inks = snapshot.inks
        if (!cfg.dungeonMapRoomNames && !cfg.dungeonMapRoomSecrets || inks == null || !calibration.ready) {
            labels = emptyList()
            setCover(null)
            return
        }
        // Again when the map, a room or an option changes, and every second for the calibration.
        val key = "${snapshot.revision}/${DungeonRooms.revision}/${cfg.dungeonMapRoomNames}/" +
            "${cfg.dungeonMapRoomSecrets}/${cfg.dungeonMapHideCheckmarks}"
        if (key == labelKey && ticks % 20 != 0) return
        labelKey = key
        val result = RoomLabels.build(inks, DungeonRooms::room, calibration::cellX, calibration::cellY,
            calibration.drawnRoomSize(), DungeonRooms::found, cfg.dungeonMapRoomNames, cfg.dungeonMapRoomSecrets,
            cfg.dungeonMapHideCheckmarks)
        labels = result.labels
        for (label in result.labels) if (label.state == RoomState.COMPLETE) DungeonRooms.complete(label.room)
        setCover(result.cover)
    }

    private fun setCover(value: IntArray?) {
        if (value.contentEquals(cover)) return
        cover = value
        coverRevision++
    }

    private fun findMap(player: LocalPlayer) {
        // Slot nine is Hypixel's Magical Map. Scan named maps too, in case it has been moved.
        for (slot in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(slot)
            if (!stack.`is`(Items.FILLED_MAP)) continue
            val name = SkyblockUtils.cleanColor(stack.hoverName.string)
            if (slot != 8 && !name.contains("Magical Map", ignoreCase = true)) continue
            mapId = stack.get(DataComponents.MAP_ID) ?: continue
            return
        }
    }

    /** Hypixel marks the player green (a frame marker) and everyone else blue. */
    private fun isOwn(decoration: MapDecoration) = decoration.type().`is`(MapDecorationTypes.FRAME)

    /**
     * Shows the calibration the player's own marker, but only after the player has stood still for
     * half a second: a moving player is ahead of a marker that was sent a moment ago.
     */
    private fun watchOwnMarker(player: LocalPlayer, decorations: List<MapDecoration>) {
        stillTicks = if (abs(player.x - lastX) < 0.01 && abs(player.z - lastZ) < 0.01) stillTicks + 1 else 0
        lastX = player.x
        lastZ = player.z
        if (stillTicks < 10) return
        val own = decorations.firstOrNull(::isOwn) ?: return
        // A marker pinned to the edge of the map says nothing about where the player is.
        if (own.x() !in -126..126 || own.y() !in -126..126) return
        val marker = MapMarker.of(own)
        calibration.observe(player.x, player.z, marker.x, marker.y)
    }

    private fun resolve(client: Minecraft, self: LocalPlayer, decorations: List<MapDecoration>): List<ClearPlayer> {
        val ownName = self.gameProfile.name()
        val ownMarker = decorations.firstOrNull(::isOwn)?.let { MapMarker.of(it) }
        val others = decorations.filterNot(::isOwn).map { MapMarker.of(it) }
        val teammates = party.filter { it.alive && !it.name.equals(ownName, ignoreCase = true) }.map { it.name }
        // Teammates in render distance, by name. Real players have version 4 UUIDs, NPCs do not.
        val entities = LinkedHashMap<String, AbstractClientPlayer>()
        for (player in client.level?.players().orEmpty()) {
            // A ghost still listed as alive while the tab list catches up is left to its marker.
            if (player === self || player.uuid.version() != 4 || player.isSpectator || player.isInvisible) continue
            val name = player.gameProfile.name()
            teammates.firstOrNull { it.equals(name, ignoreCase = true) }?.let { entities[it] = player }
        }
        val live = if (calibration.ready) entities.mapValues { (_, player) ->
            MapMarker(calibration.mapX(player.x), calibration.mapY(player.z), player.yRot)
        } else emptyMap()
        val owners = MarkerAssignment.assign(others, teammates, live)
        val result = ArrayList<ClearPlayer>(others.size + 2)
        others.forEachIndexed { index, marker ->
            val name = owners[index]
            val entity = name?.let { entities[it] }
            result += ClearPlayer(name?.lowercase() ?: "#$index", name, false, marker, entity, skinOf(client, name, entity))
        }
        // A teammate in range whose marker is missing can still be drawn from where they stand.
        for ((name, entity) in entities) {
            if (name !in owners.values) result += ClearPlayer(name.lowercase(), name, false, null, entity, entity.skin)
        }
        result += ClearPlayer(SELF, ownName, true, ownMarker, self, self.skin)
        return result
    }

    private fun skinOf(client: Minecraft, name: String?, entity: AbstractClientPlayer?): PlayerSkin? =
        entity?.skin ?: name?.let { client.connection?.getPlayerInfo(it)?.skin }

    /**
     * Where to draw [player] this frame, or null if there is nothing to place them by. In render
     * distance, and once the map is calibrated, that is their live position; otherwise their marker.
     */
    fun locate(player: ClearPlayer, partialTick: Float, now: Long): MarkerTrack? {
        val smooth = AlpakaConfig.instance.dungeonMapSmoothMarkers
        val entity = player.entity?.takeUnless { it.isRemoved }
        if (smooth && entity != null && calibration.ready) {
            val at = entity.getPosition(partialTick)
            return track(player.key).apply {
                follow(calibration.mapX(at.x), calibration.mapY(at.z), entity.getViewYRot(partialTick), now)
            }
        }
        val marker = player.marker ?: return null
        return track(player.key).apply { if (smooth) glide(marker, now) else jump(marker, now) }
    }

    private fun track(key: String) = tracks.getOrPut(key) { MarkerTrack() }

    @JvmStatic
    fun now() = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun reset() {
        mapId = null
        players = emptyList()
        labels = emptyList()
        setCover(null)
        labelKey = ""
        DungeonRooms.reset()
        party = emptyList()
        tracks.clear()
        stillTicks = 0
        lastX = Double.NaN
        lastZ = Double.NaN
        snapshot.reset()
        calibration.reset()
        BossRoomMap.reset()
    }

    private const val SELF = "\u0000self"
}
