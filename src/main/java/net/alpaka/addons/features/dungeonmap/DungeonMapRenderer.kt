package net.alpaka.addons.features.dungeonmap

import com.mojang.blaze3d.platform.NativeImage
import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.BlurRectRenderState
import net.alpaka.addons.client.gui.GradientRoundedRectRenderState
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.ChatBlurFeature
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.level.material.MapColor
import org.joml.Matrix3x2f
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.abs

object DungeonMapRenderer {
    private val clearTexture = MapTexture("dungeon_map_clear")
    private val bossTexture = MapTexture("dungeon_map_boss", BossMapProjection.TEXTURE)
    private val previewTexture = MapTexture("dungeon_map_preview")
    private val bossPreviewTexture = MapTexture("dungeon_map_boss_preview")
    private val preview = DungeonMapSnapshot().apply { update(previewPixels(), false) }
    // The editor can be opened from the title screen, where there is no player to take a skin from.
    private val profileSkin: Supplier<PlayerSkin> by lazy {
        Minecraft.getInstance().skinManager.createLookup(Minecraft.getInstance().gameProfile, false)
    }
    private val previewTeammateSkin by lazy { DefaultPlayerSkin.get(UUID(0L, 3L)) }
    private const val DEGREES = (Math.PI / 180).toFloat()

    private fun padding() = AlpakaConfig.instance.dungeonMapPadding.toInt().coerceIn(4, 16)
    private fun panelWidth() = 128 + padding() * 2
    private fun titleHeight() = if (AlpakaConfig.instance.dungeonMapTitle) 16 else 0
    // Reserve both panels even during clear, so entering the boss never shifts the HUD's anchor.
    fun width() = panelWidth() * (if (AlpakaConfig.instance.dungeonMapBossMode == 2) 2 else 1)
    fun height() = 128 + padding() * 2 + titleHeight()

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val cfg = AlpakaConfig.instance
        if (!cfg.dungeonMapEnabled || mc.player == null || mc.level == null || mc.gui.hud.isHidden() ||
            !DungeonState.inDungeon() || !SkyblockUtils.isOnSkyblock()) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return
        if (DungeonState.inBossRoom && !cfg.dungeonMapShowBoss || !DungeonState.inBossRoom && !cfg.dungeonMapShowClear) return
        val box = DungeonMapHudElement.visibleBounds(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        draw(graphics, box.x0, box.y0, false)
    }

    @JvmStatic
    fun draw(graphics: GuiGraphicsExtractor, x: Int, y: Int, editing: Boolean) {
        val cfg = AlpakaConfig.instance
        val boss = DungeonState.inBossRoom || editing && cfg.dungeonMapBossMode == 2
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        val scale = DungeonMapHudElement.scaleValue()
        graphics.pose().scale(scale, scale)
        try {
            if (!boss || cfg.dungeonMapBossMode == 1) {
                panel(graphics, false, editing, scale)
            } else {
                panel(graphics, true, editing, scale)
                if (cfg.dungeonMapBossMode == 2) {
                    graphics.pose().translate(panelWidth().toFloat(), 0f)
                    panel(graphics, false, editing, scale)
                }
            }
        } finally {
            graphics.pose().popMatrix()
        }
    }

    private fun panel(g: GuiGraphicsExtractor, boss: Boolean, editing: Boolean, scale: Float) {
        val cfg = AlpakaConfig.instance
        val mc = Minecraft.getInstance()
        backdrop(g, scale)
        val pad = padding()
        if (cfg.dungeonMapTitle) {
            val title = when {
                boss -> "Boss Room"
                DungeonState.inBossRoom || editing && cfg.dungeonMapBossMode == 2 -> "Clear Map (Saved)"
                else -> "Dungeon Map"
            }
            g.text(mc.font, title + (DungeonState.floor?.let { " • $it" } ?: ""), pad, pad + 2, cfg.dungeonMapTextColor, true)
        }
        val top = pad + titleHeight()
        g.enableScissor(pad, top, pad + 128, top + 128)
        g.pose().pushMatrix()
        g.pose().translate(pad.toFloat(), top.toFloat())
        try {
            if (boss && editing) {
                val pixels = IntArray(128 * 128) { i ->
                    val x = i % 128
                    val y = i / 128
                    when {
                        x !in 8..119 || y !in 8..119 -> 0
                        x !in 12..115 || y !in 12..115 ||
                            x in 30..40 && y in 30..40 || x in 87..97 && y in 30..40 ||
                            x in 30..40 && y in 87..97 || x in 87..97 && y in 87..97 -> cfg.dungeonMapBossWallColor
                        else -> cfg.dungeonMapBossFloorColor
                    }
                }
                bossPreviewTexture.upload(pixels, pixels.contentHashCode())
                bossPreviewTexture.draw(g)
                if (cfg.dungeonMapPlayers) {
                    drawPlayer(g, 80f, 45f, -111f, false, "Teammate", previewTeammateSkin)
                    drawPlayer(g, 64f, 64f, 180f, true, "You", previewOwnSkin())
                }
            } else if (boss) {
                val pixels = BossRoomMap.pixels
                val projection = BossRoomMap.projection
                if (pixels == null || projection == null) waiting(g, "Loading Arena…") else bossArena(g, pixels, projection)
            } else {
                val source = if (editing) preview else DungeonMapFeature.snapshot
                if (source.colors == null) waiting(g, "Waiting For Map…") else {
                    val texture = if (editing) previewTexture else clearTexture
                    if (editing) {
                        val sample = previewLabels()
                        texture.updateMap(source, palette(), sample.cover, previewKey.hashCode())
                        texture.draw(g)
                        drawRoomLabels(g, sample.labels)
                    } else {
                        texture.updateMap(source, palette(), DungeonMapFeature.cover, DungeonMapFeature.coverRevision)
                        texture.draw(g)
                        drawRoomLabels(g, DungeonMapFeature.labels)
                    }
                    // Saved clear positions are stale in the boss room and must not look live.
                    if (cfg.dungeonMapPlayers && (editing || !DungeonState.inBossRoom)) {
                        if (editing) {
                            drawPlayer(g, 83f, 65f, -111f, false, "Teammate", previewTeammateSkin)
                            drawPlayer(g, 40f, 40f, 180f, true, "You", previewOwnSkin())
                        } else {
                            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
                            val now = DungeonMapFeature.now()
                            for (player in DungeonMapFeature.players) {
                                val at = DungeonMapFeature.locate(player, partialTick, now) ?: continue
                                drawPlayer(g, at.x, at.y, at.yaw, player.self, player.name, player.skin)
                            }
                        }
                    }
                }
            }
        } finally {
            g.pose().popMatrix()
            g.disableScissor()
        }
    }

    /**
     * The clear map at [size] GUI pixels for the Leap Menu, with every teammate's head framed in
     * [colorOf] their class. Returns the teammate under the mouse, whose head is drawn larger, as is
     * [highlight]'s. [preview] draws the editor's sample map instead, for the screenshot tour.
     */
    @JvmStatic
    fun drawLeapMap(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, mouseX: Int, mouseY: Int, highlight: String?,
                    preview: Boolean, colorOf: (String) -> Int): String? {
        val source = if (preview) this.preview else DungeonMapFeature.snapshot
        if (source.colors == null) return null
        val mc = Minecraft.getInstance()
        val scale = size / 128f
        val texture = if (preview) previewTexture else clearTexture
        g.pose().pushMatrix()
        g.pose().translate(x.toFloat(), y.toFloat())
        g.pose().scale(scale, scale)
        // The same texture and room labels as the HUD, which isn't drawn while a screen is open.
        if (preview) {
            texture.updateMap(source, palette(), null, -1)
            texture.draw(g)
        } else {
            texture.updateMap(source, palette(), DungeonMapFeature.cover, DungeonMapFeature.coverRevision)
            texture.draw(g)
            drawRoomLabels(g, DungeonMapFeature.labels)
        }
        g.pose().popMatrix()

        class Spot(val x: Float, val y: Float, val name: String?, val self: Boolean, val skin: PlayerSkin?)
        val spots = ArrayList<Spot>()
        if (preview) {
            spots += Spot(83f, 65f, "Teammate", false, previewTeammateSkin)
            spots += Spot(33f, 95f, "Healer", false, DefaultPlayerSkin.get(UUID(0L, 5L)))
            spots += Spot(40f, 40f, mc.player?.name?.string, true, previewOwnSkin())
        } else {
            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
            val now = DungeonMapFeature.now()
            for (player in DungeonMapFeature.players) {
                val at = DungeonMapFeature.locate(player, partialTick, now) ?: continue
                spots += Spot(at.x, at.y, player.name, player.self, player.skin)
            }
        }
        // The closest teammate within reach of the mouse, in map pixels.
        val mapMouseX = (mouseX - x) / scale
        val mapMouseY = (mouseY - y) / scale
        var hovered: String? = null
        var best = LEAP_REACH
        for (spot in spots) {
            if (spot.self || spot.name == null) continue
            val distance = kotlin.math.hypot(spot.x - mapMouseX, spot.y - mapMouseY)
            if (distance < best) {
                best = distance
                hovered = spot.name
            }
        }
        g.enableScissor(x, y, x + size, y + size)
        try {
            for (spot in spots) {
                val big = !spot.self && spot.name != null &&
                    (spot.name.equals(hovered, true) || spot.name.equals(highlight, true))
                val head = if (big) 14 else 10
                val cx = Math.round(x + spot.x * scale)
                val cy = Math.round(y + spot.y * scale)
                val color = if (spot.self || spot.name == null) 0xFFFFFFFF.toInt() else colorOf(spot.name)
                val half = head / 2
                g.fill(cx - half - 1, cy - half - 1, cx + half + 1, cy + half + 1, color)
                if (spot.skin != null) PlayerFaceExtractor.extractRenderState(g, spot.skin, cx - half, cy - half, head)
                else g.fill(cx - half, cy - half, cx + half, cy + half, 0xFF202020.toInt())
                if (!spot.self && spot.name != null) {
                    val name = spot.name
                    g.text(mc.font, name, cx - mc.font.width(name) / 2, cy + half + 2, if (big) color else 0xFFE8EEEE.toInt(), true)
                }
            }
        } finally {
            g.disableScissor()
        }
        return hovered
    }

    private const val LEAP_REACH = 9f

    private fun waiting(g: GuiGraphicsExtractor, text: String) {
        val mc = Minecraft.getInstance()
        g.text(mc.font, text, (128 - mc.font.width(text)) / 2, 60, AlpakaConfig.instance.dungeonMapTextColor, true)
    }

    private var previewKey = ""
    private var previewResult = RoomLabels.Result(emptyList(), null)

    /** Labels on the editor's sample map, built the same way as in a run, from made-up rooms. */
    private fun previewLabels(): RoomLabels.Result {
        val cfg = AlpakaConfig.instance
        val key = "${cfg.dungeonMapRoomNames}/${cfg.dungeonMapRoomSecrets}/${cfg.dungeonMapHideCheckmarks}"
        if (key == previewKey) return previewResult
        previewKey = key
        val inks = preview.inks ?: return previewResult
        // The sample map's rooms are 20 px apart from 28 px, starting at 10.
        previewResult = RoomLabels.build(inks, { PREVIEW_ROOMS[it] }, { 20f + 28 * it }, { 20f + 28 * it }, 20,
            { PREVIEW_FOUND[it.name] }, cfg.dungeonMapRoomNames, cfg.dungeonMapRoomSecrets, cfg.dungeonMapHideCheckmarks)
        return previewResult
    }

    private val PREVIEW_ROOMS = mapOf(
        1 to RoomInfo("Atlas", "NORMAL", 6), 2 to RoomInfo("Water Board", "PUZZLE", 0),
        6 to RoomInfo("Balcony", "NORMAL", 4), 8 to RoomInfo("Archway", "NORMAL", 3),
        9 to RoomInfo("Shadow Assassin", "CHAMPION", 0), 12 to RoomInfo("Old Trap", "TRAP", 4),
        13 to RoomInfo("Bridges", "NORMAL", 6), 20 to RoomInfo("Beams", "NORMAL", 2),
    )
    private val PREVIEW_FOUND = mapOf("Atlas" to 2, "Bridges" to 5, "Beams" to 0)

    /** Each word on a line of its own, centred on the room and colored by its checkmark. */
    private fun drawRoomLabels(g: GuiGraphicsExtractor, labels: List<RoomLabel>) {
        if (labels.isEmpty()) return
        val cfg = AlpakaConfig.instance
        val font = Minecraft.getInstance().font
        val scale = cfg.dungeonMapRoomLabelScale.coerceIn(0.3f, 1f)
        val line = font.lineHeight * scale
        // A room nobody has finished yet is a step darker than a cleared one.
        val open = ModernGuiUtils.lerpColor(cfg.dungeonMapTextColor, 0xFF000000.toInt(), 0.3f)
        for (label in labels) {
            val color = when (label.state) {
                RoomState.COMPLETE -> cfg.dungeonMapCompleteColor
                RoomState.FAILED -> cfg.dungeonMapFailedColor
                RoomState.CLEARED -> cfg.dungeonMapClearedColor
                RoomState.NONE -> open
            }
            var y = label.y - line * label.lines.size / 2f
            for (text in label.lines) {
                g.pose().pushMatrix()
                g.pose().translate(label.x, y)
                g.pose().scale(scale, scale)
                g.pose().translate(-font.width(text) / 2f, 0f)
                g.text(font, text, 0, 0, color, true)
                g.pose().popMatrix()
                y += line
            }
        }
    }

    private fun previewOwnSkin(): PlayerSkin = Minecraft.getInstance().player?.skin ?: profileSkin.get()

    /**
     * The sampled terrain and the players on it. With smooth movement on, the view stays centred on
     * the player's position of this frame and slides over terrain sampled every half second;
     * otherwise it shows the terrain the way it was sampled.
     */
    private fun bossArena(g: GuiGraphicsExtractor, pixels: IntArray, projection: BossMapProjection) {
        val cfg = AlpakaConfig.instance
        val mc = Minecraft.getInstance()
        val local = mc.player ?: return
        val smooth = cfg.dungeonMapSmoothMarkers
        val partialTick = if (smooth) mc.deltaTracker.getGameTimeDeltaPartialTick(false) else 1f
        // Kept within the margin, so even a teleport shows sampled terrain until the next sampling.
        val reach = projection.span * BossMapProjection.MARGIN / BossMapProjection.STEPS.toDouble()
        val view = if (smooth) local.getPosition(partialTick).let {
            BossMapProjection(it.x.coerceIn(projection.centerX - reach, projection.centerX + reach),
                it.z.coerceIn(projection.centerZ - reach, projection.centerZ + reach), projection.span)
        } else projection
        val edge = -2f * BossMapProjection.MARGIN
        g.pose().pushMatrix()
        g.pose().translate(view.mapX(projection.centerX) - 64f + edge, view.mapY(projection.centerZ) - 64f + edge)
        bossTexture.upload(pixels, BossRoomMap.revision)
        bossTexture.draw(g)
        g.pose().popMatrix()
        if (!cfg.dungeonMapPlayers) return
        for (player in mc.level?.players() ?: return) {
            if (player !== local && (player.isInvisible || player.isSpectator ||
                    mc.connection?.getPlayerInfo(player.uuid) == null || abs(player.y - local.y) > 8)) continue
            val at = if (smooth) player.getPosition(partialTick) else player.position()
            // Players outside the view are left to the scissor, which also cuts heads and names off cleanly at the edge.
            drawPlayer(g, view.mapX(at.x), view.mapY(at.z), player.getViewYRot(partialTick), player === local,
                player.name.string, player.skin)
        }
    }

    /**
     * A player's head or arrow. [yaw] is the game's: 0 faces south, which is down on the map, while
     * both are drawn facing up.
     */
    private fun drawPlayer(g: GuiGraphicsExtractor, x: Float, y: Float, yaw: Float, self: Boolean, name: String?,
                           skin: PlayerSkin?) {
        val cfg = AlpakaConfig.instance
        val color = if (self) cfg.dungeonMapSelfColor else cfg.dungeonMapPlayerColor
        val facing = (yaw + 180f) * DEGREES
        val scale = cfg.dungeonMapMarkerScale.coerceIn(0.5f, 2.5f)
        val face = if (cfg.dungeonMapPlayerHeads) skin else null
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        g.pose().scale(scale, scale)
        if (face != null) {
            val border = cfg.dungeonMapHeadBorder
            if (cfg.dungeonMapRotateHeads) g.pose().rotate(facing)
            if (border) g.fill(-5, -5, 5, 5, color)
            PlayerFaceExtractor.extractRenderState(g, face, -4, -4, 8)
            // A notch shows where the player looks; on an upright head it circles the head.
            if (!cfg.dungeonMapRotateHeads) g.pose().rotate(facing)
            val top = if (border) -7 else -6
            g.fill(-1, top, 1, top + 2, color)
        } else {
            g.pose().rotate(facing)
            g.fill(-1, -4, 1, -2, 0xDD000000.toInt())
            g.fill(-3, -2, 3, 3, 0xDD000000.toInt())
            g.fill(-1, -3, 1, -1, color)
            g.fill(-2, -1, 2, 2, color)
        }
        g.pose().popMatrix()
        if (cfg.dungeonMapNames && !name.isNullOrEmpty()) {
            val font = Minecraft.getInstance().font
            // Placed through the pose rather than whole pixels, so the name moves with its marker.
            g.pose().pushMatrix()
            g.pose().translate(x, y + (if (face != null) 6f else 4f) * scale + 1f)
            g.text(font, name, -font.width(name) / 2, 0, cfg.dungeonMapTextColor, true)
            g.pose().popMatrix()
        }
    }

    private fun backdrop(g: GuiGraphicsExtractor, scale: Float) {
        val cfg = AlpakaConfig.instance
        val w = panelWidth()
        val h = height()
        val opacity = cfg.dungeonMapBackgroundOpacity.coerceIn(0f, 100f) / 100f
        val tint = (Math.round((cfg.dungeonMapBackgroundColor ushr 24) * opacity) shl 24) or
            (cfg.dungeonMapBackgroundColor and 0xFFFFFF)
        val sink = g as? AlpakaGuiElementSink
        if (sink == null) {
            if (cfg.dungeonMapBackground) g.fill(0, 0, w, h, tint)
            if (cfg.dungeonMapFrame) g.outline(0, 0, w, h, cfg.dungeonMapFrameStart)
            return
        }
        val toScreen = Minecraft.getInstance().window.guiScale.toFloat() * scale
        val pose = Matrix3x2f(g.pose())
        val radius = Math.round(cfg.dungeonMapCornerRadius.coerceIn(0f, 16f) * toScreen)
        val scissor = sink.`alpaka$currentScissor`()
        if (cfg.dungeonMapBackground) {
            if (cfg.dungeonMapBlur) {
                val panel = BlurRectRenderState(pose, 0, 0, w, h, radius, tint, toScreen, scissor)
                ChatBlurFeature.requestForHud(panel.bounds())
                sink.`alpaka$submitElement`(panel)
            } else sink.`alpaka$submitElement`(GradientRoundedRectRenderState(
                pose, 0, 0, w, h, radius, 0, tint, tint, tint, tint, toScreen, scissor))
        }
        if (cfg.dungeonMapFrame) {
            val start = cfg.dungeonMapFrameStart
            val end = cfg.dungeonMapFrameEnd
            val mid = ModernGuiUtils.lerpColor(start, end, 0.5f)
            sink.`alpaka$submitElement`(GradientRoundedRectRenderState(pose, 0, 0, w, h, radius,
                maxOf(1, Math.round(cfg.dungeonMapFrameWidth.coerceIn(1f, 4f) * toScreen)),
                start, mid, end, mid, toScreen, scissor))
        }
    }

    private fun palette(): IntArray {
        val c = AlpakaConfig.instance
        return intArrayOf(if (c.dungeonMapCustomColors) 1 else 0, c.dungeonMapRoomColor,
            c.dungeonMapEntranceColor, c.dungeonMapPuzzleColor, c.dungeonMapTrapColor,
            c.dungeonMapMinibossColor, c.dungeonMapFairyColor, c.dungeonMapBloodColor,
            c.dungeonMapUnknownColor, c.dungeonMapDoorColor, c.dungeonMapClearedColor,
            c.dungeonMapCompleteColor, c.dungeonMapFailedColor)
    }

    private class MapTexture(name: String, private val size: Int = 128) {
        private val id = Identifier.fromNamespaceAndPath("alpaka", name)
        private var texture: DynamicTexture? = null
        private var revision = -1
        private var lastPalette = intArrayOf()
        private var lastCover = 0

        /**
         * [cover] replaces checkmark pixels under labels; [coverRevision] tells when it changed. A map
         * drawn without labels leaves both out.
         */
        fun updateMap(snapshot: DungeonMapSnapshot, palette: IntArray, cover: IntArray? = null, coverRevision: Int = 0) {
            if (revision == snapshot.revision && palette.contentEquals(lastPalette) && coverRevision == lastCover) return
            val colors = snapshot.colors ?: return
            val inks = snapshot.inks ?: return
            val pixels = IntArray(colors.size) { pixel ->
                val i = cover?.get(pixel)?.takeIf { it >= 0 } ?: pixel
                if (palette[0] != 0 && inks[i] != DungeonMapInk.ORIGINAL) palette[inks[i].ordinal]
                else MapColor.getColorFromPackedId(colors[i].toInt() and 255)
            }
            lastPalette = palette
            lastCover = coverRevision
            upload(pixels, snapshot.revision, true)
        }

        fun upload(pixels: IntArray, version: Int, force: Boolean = false) {
            if (!force && revision == version) return
            var current = texture
            if (current == null) {
                current = DynamicTexture({ id.toString() }, NativeImage(size, size, false))
                texture = current
                Minecraft.getInstance().textureManager.register(id, current)
            }
            val image = current.pixels ?: return
            for (i in pixels.indices) image.setPixel(i % size, i / size, pixels[i])
            current.upload()
            revision = version
        }

        fun draw(g: GuiGraphicsExtractor) {
            g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, size, size, size, size)
        }
    }

    private fun previewPixels(): ByteArray {
        val data = ByteArray(128 * 128)
        val colors = intArrayOf(30, 63, 66, 85, 63, 82, 63, 74, 62, 63, 63, 18, 85, 85, 63, 85)
        for (row in 0..3) for (col in 0..3) {
            val x = 10 + col * 28
            val y = 10 + row * 28
            for (dy in 0..19) for (dx in 0..19) data[(y + dy) * 128 + x + dx] = colors[row * 4 + col].toByte()
            if (col < 3) for (dx in 20..27) for (dy in 8..11) data[(y + dy) * 128 + x + dx] = 63
            if (row < 3) for (dy in 20..27) for (dx in 8..11) data[(y + dy) * 128 + x + dx] = 63
            if (row == 1) {
                for (i in 0..5) data[(y + 12 - i) * 128 + x + 8 + i] = 30
                data[(y + 11) * 128 + x + 7] = 30
            }
        }
        return data
    }
}
