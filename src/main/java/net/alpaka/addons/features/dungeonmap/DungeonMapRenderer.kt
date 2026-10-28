package net.alpaka.addons.features.dungeonmap

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.BlurRectRenderState
import net.alpaka.addons.client.gui.GradientRoundedRectRenderState
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.chat.ChatBlurFeature
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.level.material.MapColor
import org.joml.Matrix3x2f
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.hypot

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
    private const val PADDING = 6
    private const val HEADER = 12
    private const val CORNER_RADIUS = 6f
    /** A teammate whose class the tab list has not shown. */
    private const val UNKNOWN_CLASS = 0xFFB0B4BC.toInt()

    private fun panelWidth() = 128 + PADDING * 2
    private fun headerHeight() = if (AlpakaConfig.instance.dungeonMapCurrentRoom) HEADER else 0
    private fun bothPanels() = AlpakaConfig.instance.let { it.dungeonMapShowBoss && it.dungeonMapBossMode == 2 }
    // Reserve both panels even during clear, so entering the boss never shifts the HUD's anchor.
    fun width() = panelWidth() * (if (bothPanels()) 2 else 1)
    fun height() = 128 + PADDING * 2 + headerHeight()

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val cfg = AlpakaConfig.instance
        if (!cfg.dungeonMapEnabled || mc.player == null || mc.level == null || mc.gui.hud.isHidden() ||
            !DungeonState.inDungeon() || !SkyblockUtils.isOnSkyblock()) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return
        if (DungeonState.inBossRoom && !cfg.dungeonMapShowBoss) return
        val box = DungeonMapHudElement.visibleBounds(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        draw(graphics, box.x0, box.y0, false)
    }

    @JvmStatic
    fun draw(graphics: GuiGraphicsExtractor, x: Int, y: Int, editing: Boolean) {
        val cfg = AlpakaConfig.instance
        val boss = DungeonState.inBossRoom || editing && bothPanels()
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        val scale = DungeonMapHudElement.scaleValue()
        graphics.pose().scale(scale, scale)
        try {
            if (!boss || cfg.dungeonMapBossMode == 1) {
                panel(graphics, false, editing, scale, true)
            } else {
                panel(graphics, true, editing, scale, true)
                if (bothPanels()) {
                    graphics.pose().translate(panelWidth().toFloat(), 0f)
                    panel(graphics, false, editing, scale, false)
                }
            }
        } finally {
            graphics.pose().popMatrix()
        }
    }

    /** One map in its panel. Only the [first] panel names where the player is. */
    private fun panel(g: GuiGraphicsExtractor, boss: Boolean, editing: Boolean, scale: Float, first: Boolean) {
        val cfg = AlpakaConfig.instance
        backdrop(g, scale)
        if (cfg.dungeonMapCurrentRoom && first) header(g, editing, boss)
        val top = PADDING + headerHeight()
        g.enableScissor(PADDING, top, PADDING + 128, top + 128)
        g.pose().pushMatrix()
        g.pose().translate(PADDING.toFloat(), top.toFloat())
        try {
            when {
                boss && editing -> bossPreview(g)
                boss -> {
                    val pixels = BossRoomMap.pixels
                    val projection = BossRoomMap.projection
                    if (pixels == null || projection == null) waiting(g, "Loading Arena…") else bossArena(g, pixels, projection)
                }
                else -> clearMap(g, editing)
            }
        } finally {
            g.pose().popMatrix()
            g.disableScissor()
        }
    }

    /** The room the player is in and its secrets, or the boss's name in the boss room. */
    private fun header(g: GuiGraphicsExtractor, editing: Boolean, boss: Boolean) {
        val inBoss = if (editing) boss else DungeonState.inBossRoom
        val room = if (editing) PREVIEW_ROOMS[1] else DungeonRooms.here
        val name = if (inBoss) bossName(DungeonState.floor) else room?.name ?: return
        val secrets = room?.takeIf { !inBoss && it.secrets > 0 }?.let {
            "${(if (editing) PREVIEW_FOUND[it.name] else DungeonRooms.found(it)) ?: "?"}/${it.secrets}"
        }
        val font = Minecraft.getInstance().font
        val style = textStyle()
        val color = AlpakaConfig.instance.dungeonMapTextColor
        // The count sits at the right edge, the name takes the rest of the line.
        var nameEnd = PADDING + 128
        if (secrets != null) {
            nameEnd -= textWidth(font, secrets, style)
            drawText(g, font, secrets, nameEnd, PADDING + 1, ModernGuiUtils.lerpColor(color, 0xFF000000.toInt(), 0.25f), style)
            nameEnd -= 6
        }
        drawText(g, font, fit(font, name, nameEnd - PADDING, style), PADDING, PADDING + 1, color, style)
    }

    /** [text], cut short with an ellipsis if it is wider than [width]. */
    private fun fit(font: Font, text: String, width: Int, style: Style): String {
        if (textWidth(font, text, style) <= width) return text
        var end = text.length
        while (end > 0 && textWidth(font, text.substring(0, end) + "…", style) > width) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun bossName(floor: String?) = when (floor?.drop(1)) {
        "" -> "The Watcher"
        "1" -> "Bonzo"
        "2" -> "Scarf"
        "3" -> "The Professor"
        "4" -> "Thorn"
        "5" -> "Livid"
        "6" -> "Sadan"
        "7" -> "Necron"
        else -> "Boss Room"
    }

    private fun clearMap(g: GuiGraphicsExtractor, editing: Boolean) {
        val cfg = AlpakaConfig.instance
        val source = if (editing) preview else DungeonMapFeature.snapshot
        if (source.colors == null) return waiting(g, "Waiting For Map…")
        if (editing) {
            val sample = previewLabels()
            previewTexture.updateMap(source, palette(), sample.cover, previewKey)
            previewTexture.draw(g)
            drawRoomLabels(g, sample.labels)
        } else {
            clearTexture.updateMap(source, palette(), DungeonMapFeature.cover, DungeonMapFeature.coverRevision)
            clearTexture.draw(g)
            drawRoomLabels(g, DungeonMapFeature.labels)
        }
        // Saved clear positions are stale in the boss room and must not look live.
        if (!cfg.dungeonMapPlayers || !editing && DungeonState.inBossRoom) return
        if (editing) {
            drawPlayer(g, 83f, 65f, -111f, false, "Teammate", previewTeammateSkin, "Healer")
            drawPlayer(g, 40f, 40f, 180f, true, "You", previewOwnSkin())
            return
        }
        val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val now = DungeonMapFeature.now()
        for (player in DungeonMapFeature.players) {
            val at = DungeonMapFeature.locate(player, partialTick, now) ?: continue
            drawPlayer(g, at.x, at.y, at.yaw, player.self, player.name, player.skin)
        }
    }

    /** The editor's sample arena: a square room with four pillars. */
    private fun bossPreview(g: GuiGraphicsExtractor) {
        val cfg = AlpakaConfig.instance
        val custom = cfg.dungeonMapCustomColors
        bossPreviewTexture.upload(if (custom) bossPreviewAlpaka else bossPreviewBlocks, if (custom) 1 else 2)
        bossPreviewTexture.draw(g)
        if (cfg.dungeonMapPlayers) {
            drawPlayer(g, 80f, 45f, -111f, false, "Teammate", previewTeammateSkin, "Healer")
            drawPlayer(g, 64f, 64f, 180f, true, "You", previewOwnSkin())
        }
    }

    private val bossPreviewAlpaka by lazy { bossPreviewPixels(DungeonMapColors.BOSS_WALL, DungeonMapColors.BOSS_FLOOR) }
    private val bossPreviewBlocks by lazy {
        bossPreviewPixels(MapColor.STONE.calculateARGBColor(MapColor.Brightness.HIGH),
            MapColor.STONE.calculateARGBColor(MapColor.Brightness.NORMAL))
    }

    private fun bossPreviewPixels(wall: Int, floor: Int) = IntArray(128 * 128) { i ->
        val x = i % 128
        val y = i / 128
        when {
            x !in 8..119 || y !in 8..119 -> 0
            x !in 12..115 || y !in 12..115 ||
                x in 30..40 && y in 30..40 || x in 87..97 && y in 30..40 ||
                x in 30..40 && y in 87..97 || x in 87..97 && y in 87..97 -> wall
            else -> floor
        }
    }

    private fun waiting(g: GuiGraphicsExtractor, text: String) {
        val font = Minecraft.getInstance().font
        val style = textStyle()
        drawText(g, font, text, (128 - textWidth(font, text, style)) / 2, 60, AlpakaConfig.instance.dungeonMapTextColor, style)
    }

    private var previewKey = -1
    private var previewResult = RoomLabels.Result(emptyList(), null)

    /** Labels on the editor's sample map, built the same way as in a run, from made-up rooms. */
    private fun previewLabels(): RoomLabels.Result {
        val cfg = AlpakaConfig.instance
        val key = (if (cfg.dungeonMapRoomNames) 2 else 0) + (if (cfg.dungeonMapRoomSecrets) 1 else 0)
        if (key == previewKey) return previewResult
        val inks = preview.inks ?: return previewResult
        previewKey = key
        // The sample map's rooms are 20 px apart from 28 px, starting at 10.
        previewResult = RoomLabels.build(inks, { PREVIEW_ROOMS[it] }, { 20f + 28 * it }, { 20f + 28 * it }, 20,
            { PREVIEW_FOUND[it.name] }, cfg.dungeonMapRoomNames, cfg.dungeonMapRoomSecrets)
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
        val style = textStyle()
        val scale = cfg.dungeonMapRoomLabelScale.coerceIn(0.3f, 1f)
        val line = font.lineHeight * scale
        // A room nobody has finished yet is a step darker than a cleared one.
        val open = ModernGuiUtils.lerpColor(cfg.dungeonMapTextColor, 0xFF000000.toInt(), 0.3f)
        for (label in labels) {
            val color = when (label.state) {
                RoomState.COMPLETE -> DungeonMapColors.COMPLETE
                RoomState.FAILED -> DungeonMapColors.FAILED
                RoomState.CLEARED -> DungeonMapColors.CLEARED
                RoomState.NONE -> open
            }
            var y = label.y - line * label.lines.size / 2f
            for (text in label.lines) {
                g.pose().pushMatrix()
                g.pose().translate(label.x, y)
                g.pose().scale(scale, scale)
                drawText(g, font, text, -textWidth(font, text, style) / 2, 0, color, style)
                g.pose().popMatrix()
                y += line
            }
        }
    }

    /** The menu font with Smooth Font on here and under HUD Style, else Minecraft's own. */
    private fun textStyle(): Style =
        if (AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.dungeonMapSmoothFont)) GuiFont.style() else Style.EMPTY

    private fun textWidth(font: Font, text: String, style: Style) =
        if (style.isEmpty) font.width(text) else font.width(FormattedText.of(text, style))

    private fun drawText(g: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int, style: Style) {
        if (style.isEmpty) g.text(font, text, x, y, color, true)
        else g.text(font, Component.literal(text).withStyle(style), x, y, color, true)
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
     * A player's head or arrow, framed in their class's color. [yaw] is the game's: 0 faces south, which is down on the map, while
     * both are drawn facing up.
     */
    private fun drawPlayer(g: GuiGraphicsExtractor, x: Float, y: Float, yaw: Float, self: Boolean, name: String?,
                           skin: PlayerSkin?, dungeonClass: String? = DungeonMapFeature.classOf(name)) {
        val cfg = AlpakaConfig.instance
        // Teammates in their class's color, the same as in the Leap Menu.
        val color = if (self) cfg.dungeonMapSelfColor else DungeonParty.classColor(dungeonClass) ?: UNKNOWN_CLASS
        val facing = (yaw + 180f) * DEGREES
        val scale = cfg.dungeonMapMarkerScale.coerceIn(0.5f, 2.5f)
        val face = if (cfg.dungeonMapPlayerHeads) skin else null
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        g.pose().scale(scale, scale)
        if (face != null) {
            val rotate = cfg.dungeonMapRotateHeads
            if (rotate) g.pose().rotate(facing)
            g.fill(-5, -5, 5, 5, color)
            PlayerFaceExtractor.extractRenderState(g, face, -4, -4, 8)
            // The arrow shows where the player looks; around an upright head it circles the head.
            if (!rotate) g.pose().rotate(facing)
            arrow(g, 0f, -8f, 5f, color)
        } else {
            g.pose().rotate(facing)
            arrow(g, 0f, 0f, 9f, color)
        }
        g.pose().popMatrix()
        if (cfg.dungeonMapNames && !name.isNullOrEmpty()) {
            val font = Minecraft.getInstance().font
            val style = textStyle()
            // Placed through the pose rather than whole pixels, so the name moves with its marker.
            g.pose().pushMatrix()
            g.pose().translate(x, y + (if (face != null) 6f else 5f) * scale + 1f)
            drawText(g, font, name, -textWidth(font, name, style) / 2, 0, cfg.dungeonMapTextColor, style)
            g.pose().popMatrix()
        }
    }

    /** An arrow pointing up, [size] wide and centred on [x], [y], with a dark edge to stand out from any room. */
    private fun arrow(g: GuiGraphicsExtractor, x: Float, y: Float, size: Float, color: Int) {
        val textures = ArrowTextures.ids
        g.pose().pushMatrix()
        g.pose().translate(x - size / 2f, y - size / 2f)
        g.pose().scale(size / ArrowTextures.SIZE, size / ArrowTextures.SIZE)
        val side = ArrowTextures.SIZE
        g.blit(RenderPipelines.GUI_TEXTURED, textures.first, 0, 0, 0f, 0f, side, side, side, side, 0xC0000000.toInt())
        g.blit(RenderPipelines.GUI_TEXTURED, textures.second, 0, 0, 0f, 0f, side, side, side, side, color)
        g.pose().popMatrix()
    }

    private fun backdrop(g: GuiGraphicsExtractor, scale: Float) {
        val cfg = AlpakaConfig.instance
        val w = panelWidth()
        val h = height()
        val tint = cfg.dungeonMapBackgroundTint
        val sink = g as? AlpakaGuiElementSink
        if (sink == null) {
            if (cfg.dungeonMapBackground) g.fill(0, 0, w, h, tint)
            if (cfg.dungeonMapFrame) g.outline(0, 0, w, h, cfg.dungeonMapFrameStart)
            return
        }
        val toScreen = Minecraft.getInstance().window.guiScale.toFloat() * scale
        val pose = Matrix3x2f(g.pose())
        val radius = Math.round(CORNER_RADIUS * toScreen)
        val scissor = sink.`alpaka$currentScissor`()
        if (cfg.dungeonMapBackground) {
            if (cfg.blurOn(cfg.dungeonMapBlur)) {
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
                maxOf(1, Math.round(toScreen)), start, mid, end, mid, toScreen, scissor))
        }
    }

    private val ALPAKA_PALETTE = DungeonMapColors.BY_INK.copyOf().also { it[0] = 1 }
    private val HYPIXEL_PALETTE = IntArray(DungeonMapColors.BY_INK.size)

    /** The colors a map texture is drawn in; a 0 in the first slot keeps the server's colors. */
    private fun palette(): IntArray = if (AlpakaConfig.instance.dungeonMapCustomColors) ALPAKA_PALETTE else HYPIXEL_PALETTE

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
            val (current, image) = image() ?: return
            val custom = palette[0] != 0
            for (pixel in colors.indices) {
                var i = pixel
                if (cover != null && cover[pixel] >= 0) i = cover[pixel]
                val ink = inks[i]
                val color = if (custom && ink != DungeonMapInk.ORIGINAL) palette[ink.ordinal]
                    else MapColor.getColorFromPackedId(colors[i].toInt() and 255)
                image.setPixel(pixel % size, pixel / size, color)
            }
            current.upload()
            revision = snapshot.revision
            lastPalette = palette
            lastCover = coverRevision
        }

        fun upload(pixels: IntArray, version: Int) {
            if (revision == version) return
            val (current, image) = image() ?: return
            for (i in pixels.indices) image.setPixel(i % size, i / size, pixels[i])
            current.upload()
            revision = version
        }

        private fun image(): Pair<DynamicTexture, NativeImage>? {
            val current = texture ?: DynamicTexture({ id.toString() }, NativeImage(size, size, false)).also {
                texture = it
                Minecraft.getInstance().textureManager.register(id, it)
            }
            return current to (current.pixels ?: return null)
        }

        fun draw(g: GuiGraphicsExtractor) {
            g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, size, size, size, size)
        }
    }

    /**
     * A navigation arrow drawn once into two textures, the arrow and its edge, both white so a
     * color tints them. They are sampled linearly, so an arrow drawn smaller than the texture keeps
     * smooth edges at any angle.
     */
    private object ArrowTextures {
        const val SIZE = 32
        private val FILL = Identifier.fromNamespaceAndPath("alpaka", "dungeon_map_arrow")
        private val EDGE = Identifier.fromNamespaceAndPath("alpaka", "dungeon_map_arrow_edge")
        // Tip, right corner, notch, left corner, in texture pixels.
        private val XS = floatArrayOf(16f, 28f, 16f, 4f)
        private val YS = floatArrayOf(3.5f, 28.5f, 21.5f, 28.5f)

        /** The edge, then the arrow. */
        val ids: Pair<Identifier, Identifier> by lazy {
            val manager = Minecraft.getInstance().textureManager
            manager.register(EDGE, SmoothTexture("Alpaka map arrow edge", image(2.5f)))
            manager.register(FILL, SmoothTexture("Alpaka map arrow", image(0f)))
            EDGE to FILL
        }

        /** The arrow grown by [grow] pixels, each pixel's alpha its coverage from 4x4 samples. */
        private fun image(grow: Float): NativeImage {
            val image = NativeImage(SIZE, SIZE, false)
            for (py in 0 until SIZE) for (px in 0 until SIZE) {
                var hits = 0
                for (sy in 0 until 4) for (sx in 0 until 4) {
                    val x = px + (sx + 0.5f) / 4f
                    val y = py + (sy + 0.5f) / 4f
                    if (inside(x, y) || grow > 0f && distance(x, y) <= grow) hits++
                }
                image.setPixel(px, py, (hits * 255 / 16 shl 24) or 0xFFFFFF)
            }
            return image
        }

        private fun inside(x: Float, y: Float): Boolean {
            var inside = false
            var j = XS.size - 1
            for (i in XS.indices) {
                if ((YS[i] > y) != (YS[j] > y) && x < (XS[j] - XS[i]) * (y - YS[i]) / (YS[j] - YS[i]) + XS[i]) inside = !inside
                j = i
            }
            return inside
        }

        private fun distance(x: Float, y: Float): Float {
            var best = Float.MAX_VALUE
            var j = XS.size - 1
            for (i in XS.indices) {
                val dx = XS[i] - XS[j]
                val dy = YS[i] - YS[j]
                val t = (((x - XS[j]) * dx + (y - YS[j]) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
                best = minOf(best, hypot(x - XS[j] - t * dx, y - YS[j] - t * dy))
                j = i
            }
            return best
        }
    }

    private class SmoothTexture(label: String, image: NativeImage) : DynamicTexture({ label }, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }

    private fun previewPixels(): ByteArray {
        val data = ByteArray(128 * 128)
        val colors = intArrayOf(30, 63, 66, 85, 63, 82, 63, 74, 62, 63, 63, 18, 85, 85, 63, 85)
        for (row in 0..3) for (col in 0..3) {
            val x = 10 + col * 28
            val y = 10 + row * 28
            for (dy in 0..19) for (dx in 0..19) data[(y + dy) * 128 + x + dx] = colors[row * 4 + col].toByte()
            // The door into the unexplored corner room is a wither door.
            val door = if (row == 0 && col == 2) 119 else 63
            if (col < 3) for (dx in 20..27) for (dy in 8..11) data[(y + dy) * 128 + x + dx] = door.toByte()
            if (row < 3) for (dy in 20..27) for (dx in 8..11) data[(y + dy) * 128 + x + dx] = 63
            if (row == 1) {
                for (i in 0..5) data[(y + 12 - i) * 128 + x + 8 + i] = 30
                data[(y + 11) * 128 + x + 7] = 30
            }
        }
        // Hypixel's question mark in that room, drawn in the wither door's color.
        val mark = arrayOf(".##.", "#..#", "...#", "..#.", "..#.", "....", "..#.")
        for ((dy, row) in mark.withIndex()) for ((dx, c) in row.withIndex()) {
            if (c == '#') data[(16 + dy) * 128 + 102 + dx] = 119
        }
        return data
    }
}
