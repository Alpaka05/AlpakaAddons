package net.alpaka.addons.features.dungeonmap

import java.util.Locale
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.gui.GuiGraphicsExtractor

object DungeonMapHudElement : HudElement {
    override val id = "dungeon_map"
    override val name = "Dungeon Map"
    override val settingsOptionId = "dungeon_map_enabled"
    override val isFeatureEnabled get() = AlpakaConfig.instance.dungeonMapEnabled
    override var anchorX: Int
        get() = AlpakaConfig.instance.dungeonMapX
        set(value) { AlpakaConfig.instance.dungeonMapX = value }
    override var anchorY: Int
        get() = AlpakaConfig.instance.dungeonMapY
        set(value) { AlpakaConfig.instance.dungeonMapY = value }

    override fun bounds(): HudBounds {
        val width = Math.round(DungeonMapRenderer.width() * scaleValue())
        val height = Math.round(DungeonMapRenderer.height() * scaleValue())
        return HudBounds(anchorX, anchorY, anchorX + width, anchorY + height)
    }

    override fun adjustScale(notches: Double) {
        AlpakaConfig.instance.dungeonMapScale = (scaleValue() + notches.toFloat() * 0.1f).coerceIn(0.5f, 3f)
    }

    override fun reset() {
        anchorX = net.alpaka.addons.client.hud.HudDefaults.DUNGEON_MAP_X
        anchorY = net.alpaka.addons.client.hud.HudDefaults.DUNGEON_MAP_Y
        AlpakaConfig.instance.dungeonMapScale = 1f
    }

    override fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) =
        DungeonMapRenderer.draw(graphics, x, y, true)

    override fun scaleValue() = AlpakaConfig.instance.dungeonMapScale.coerceIn(0.5f, 3f)
    override fun scaleLabel(): String = String.format(Locale.ROOT, "%.2fx", scaleValue())
}
