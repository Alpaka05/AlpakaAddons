package net.alpaka.addons.features.dungeonmap

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.AlpakaPerf
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos

/**
 * Wither and blood doors during the clear: red while nobody has the key, green once the team has
 * one. A door is drawn until its blocks are gone.
 *
 * Which doors are wither or blood doors comes from the Magical Map, which draws them black and
 * red between two rooms; whether the team holds a key comes from the chat. Both boxes are
 * depth-tested like any block, so a door is only marked where it can be seen.
 */
object DoorHighlight {
    /** A door between two cells, by its block in the middle of the doorway at floor height. */
    class Door(val x: Int, val z: Int, val blood: Boolean)

    // "[MVP+] Name has obtained Wither Key!", "A Wither Key was picked up!", "Name opened a WITHER door!"
    private val KEY = Regex("""^(?:(?:\[[^\]]+] )?\w{1,16} has obtained|A) (Wither|Blood) Key(?: was picked up)?!?$""")
    private val WITHER_OPENED = Regex("""^(?:\[[^\]]+] )?\w{1,16} opened a WITHER door!$""")
    private const val BLOOD_OPENED = "The BLOOD DOOR has been opened!"
    private const val FLOOR = 69

    var doors: List<Door> = emptyList()
        private set
    var witherKeys = 0
        private set
    var bloodKey = false
        private set
    private var mapRevision = -1
    /** Doors seen open, so a map that still draws one does not bring it back. */
    private val opened = HashSet<Long>()
    private var ticks = 0

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay && AlpakaConfig.instance.dungeonDoorHighlight && DungeonState.inDungeon() && !DungeonState.inBossRoom) {
                onChat(SkyblockUtils.cleanColor(message.string))
            }
        }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            val t = AlpakaPerf.begin(AlpakaPerf.Section.LEVEL_OVERLAYS)
            try {
                submit(context)
            } finally {
                AlpakaPerf.end(AlpakaPerf.Section.LEVEL_OVERLAYS, t)
            }
        }
    }

    internal fun onChat(text: String) {
        val line = text.trim()
        val key = KEY.matchEntire(line)
        when {
            key != null -> if (key.groupValues[1] == "Wither") witherKeys++ else bloodKey = true
            WITHER_OPENED.matches(line) -> witherKeys = (witherKeys - 1).coerceAtLeast(0)
            line == BLOOD_OPENED -> bloodKey = false
        }
    }

    private fun tick(mc: Minecraft) {
        val active = AlpakaConfig.instance.dungeonDoorHighlight && DungeonState.inDungeon() &&
            !DungeonState.inBossRoom && SkyblockUtils.isOnSkyblock()
        if (!active || mc.level == null) {
            if (doors.isNotEmpty()) doors = emptyList()
            mapRevision = -1
            return
        }
        // The doors change with the map; their blocks are checked twice a second.
        val snapshot = DungeonMapFeature.snapshot
        if (snapshot.revision != mapRevision) {
            mapRevision = snapshot.revision
            doors = find(snapshot.colors, DungeonMapFeature.calibration).filter { BlockPos.asLong(it.x, 0, it.z) !in opened }
        }
        if (++ticks % 10 != 0 || doors.isEmpty()) return
        val level = mc.level ?: return
        val pos = BlockPos.MutableBlockPos()
        doors = doors.filter { door ->
            // An unloaded door is kept for later; an opened one is air.
            val open = level.chunkSource.hasChunk(door.x shr 4, door.z shr 4) &&
                level.getBlockState(pos.set(door.x, FLOOR + 1, door.z)).isAir
            if (open) opened += BlockPos.asLong(door.x, 0, door.z)
            !open
        }
    }

    /**
     * The wither and blood doors on the map: the middle of the gap between two neighbouring cells,
     * if Hypixel drew it black (119) or blood red (18) there.
     */
    internal fun find(colors: ByteArray?, calibration: DungeonMapCalibration): List<Door> {
        if (colors == null || !calibration.ready) return emptyList()
        val result = ArrayList<Door>()
        fun check(px: Float, py: Float, worldX: Int, worldZ: Int) {
            val x = px.toInt()
            val y = py.toInt()
            if (x !in 0 until DungeonMapPixels.SIDE || y !in 0 until DungeonMapPixels.SIDE) return
            when (colors[y * DungeonMapPixels.SIDE + x].toInt() and 255) {
                119 -> result += Door(worldX, worldZ, false)
                18 -> result += Door(worldX, worldZ, true)
            }
        }
        for (z in 0 until 6) for (x in 0 until 6) {
            val worldX = DungeonRooms.cellCenter(x)
            val worldZ = DungeonRooms.cellCenter(z)
            if (x < 5) check((calibration.cellX(x) + calibration.cellX(x + 1)) / 2f, calibration.cellY(z), worldX + 16, worldZ)
            if (z < 5) check(calibration.cellX(x), (calibration.cellY(z) + calibration.cellY(z + 1)) / 2f, worldX, worldZ + 16)
        }
        return result
    }

    private fun submit(context: LevelRenderContext) {
        val shown = doors
        if (shown.isEmpty()) return
        val cfg = AlpakaConfig.instance
        val camera = context.levelState().cameraRenderState.pos
        val camX = camera.x
        val camY = camera.y
        val camZ = camera.z
        val colors = shown.map { if (if (it.blood) bloodKey else witherKeys > 0) cfg.dungeonDoorOpenableColor else cfg.dungeonDoorLockedColor }
        val poseStack = context.poseStack()
        val out = context.submitNodeCollector()
        out.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox()) { pose, buffer ->
            shown.forEachIndexed { i, door -> box(door, camX, camY, camZ) { x0, y0, z0, x1, y1, z1 -> fill(buffer, pose, colors[i], x0, y0, z0, x1, y1, z1) } }
        }
        out.submitCustomGeometry(poseStack, RenderTypes.lines()) { pose, buffer ->
            shown.forEachIndexed { i, door ->
                val opaque = colors[i] or (0xFF shl 24)
                box(door, camX, camY, camZ) { x0, y0, z0, x1, y1, z1 -> edges(buffer, pose, opaque, x0, y0, z0, x1, y1, z1) }
            }
        }
    }

    /**
     * The door's 3x4x3 blocks, just outside their faces, relative to the camera. Taken relative in
     * double precision first: far from the origin a float cannot hold a block position finely.
     */
    private inline fun box(door: Door, camX: Double, camY: Double, camZ: Double,
                           draw: (Float, Float, Float, Float, Float, Float) -> Unit) {
        val grow = 0.002
        draw((door.x - 1 - grow - camX).toFloat(), (FLOOR - grow - camY).toFloat(), (door.z - 1 - grow - camZ).toFloat(),
            (door.x + 2 + grow - camX).toFloat(), (FLOOR + 4 + grow - camY).toFloat(), (door.z + 2 + grow - camZ).toFloat())
    }

    private fun fill(buffer: VertexConsumer, pose: PoseStack.Pose, argb: Int,
                     x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        // The debug box type does not cull faces, so one winding per side is enough.
        quad(buffer, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)
        quad(buffer, pose, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0)
        quad(buffer, pose, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0)
        quad(buffer, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)
        quad(buffer, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0)
        quad(buffer, pose, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1)
    }

    private fun quad(buffer: VertexConsumer, pose: PoseStack.Pose, argb: Int,
                     ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
                     cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float) {
        buffer.addVertex(pose, ax, ay, az).setColor(argb)
        buffer.addVertex(pose, bx, by, bz).setColor(argb)
        buffer.addVertex(pose, cx, cy, cz).setColor(argb)
        buffer.addVertex(pose, dx, dy, dz).setColor(argb)
    }

    private fun edges(buffer: VertexConsumer, pose: PoseStack.Pose, argb: Int,
                      x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        for (y in floatArrayOf(y0, y1)) {
            line(buffer, pose, argb, x0, y, z0, x1, y, z0)
            line(buffer, pose, argb, x0, y, z1, x1, y, z1)
            line(buffer, pose, argb, x0, y, z0, x0, y, z1)
            line(buffer, pose, argb, x1, y, z0, x1, y, z1)
        }
        for (x in floatArrayOf(x0, x1)) for (z in floatArrayOf(z0, z1)) line(buffer, pose, argb, x, y0, z, x, y1, z)
    }

    /** One line; the line shader needs its direction as the normal and a width on every vertex. */
    private fun line(buffer: VertexConsumer, pose: PoseStack.Pose, argb: Int,
                     x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        var nx = x1 - x0
        var ny = y1 - y0
        var nz = z1 - z0
        val length = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
        nx /= length
        ny /= length
        nz /= length
        buffer.addVertex(pose, x0, y0, z0).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(2f)
        buffer.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(2f)
    }

    @JvmStatic
    fun reset() {
        doors = emptyList()
        witherKeys = 0
        bloodKey = false
        mapRevision = -1
        opened.clear()
    }
}
