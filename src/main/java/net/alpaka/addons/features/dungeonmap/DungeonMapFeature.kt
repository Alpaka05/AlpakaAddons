package net.alpaka.addons.features.dungeonmap

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapId

object DungeonMapFeature {
    val snapshot = DungeonMapSnapshot()
    var markers: List<MapDecoration> = emptyList()
        private set
    private var mapId: MapId? = null
    private var ticks = 0

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: Minecraft) {
        if (++ticks % 2 != 0) return
        if (client.level == null || !DungeonState.inDungeon() || !SkyblockUtils.isOnSkyblock()) {
            if (mapId != null || snapshot.colors != null || BossRoomMap.pixels != null) reset()
            return
        }
        val cfg = AlpakaConfig.instance
        if (!cfg.dungeonMapEnabled) return
        if (DungeonState.inBossRoom) {
            if (cfg.dungeonMapShowBoss && cfg.dungeonMapBossMode != 1 && ticks % 10 == 0) BossRoomMap.update(client)
            return
        }
        val player = client.player ?: return
        // Slot nine is Hypixel's Magical Map. Scan named maps too, in case it has been moved.
        for (slot in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(slot)
            if (!stack.`is`(Items.FILLED_MAP)) continue
            val name = SkyblockUtils.cleanColor(stack.hoverName.string)
            if (slot != 8 && !name.contains("Magical Map", ignoreCase = true)) continue
            mapId = stack.get(DataComponents.MAP_ID) ?: continue
            break
        }
        val id = mapId ?: return
        val data = client.level?.getMapData(id) ?: return
        snapshot.update(data.colors, false)
        markers = data.decorations.toList()
    }

    @JvmStatic
    fun reset() {
        mapId = null
        markers = emptyList()
        snapshot.reset()
        BossRoomMap.reset()
    }
}
