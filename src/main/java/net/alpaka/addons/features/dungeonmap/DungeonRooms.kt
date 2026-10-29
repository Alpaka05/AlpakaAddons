package net.alpaka.addons.features.dungeonmap

import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.io.InputStreamReader

/** A room as the table knows it: its name, Odin's room type, its secrets, and how many cells it spans. */
data class RoomInfo(val name: String, val type: String, val secrets: Int, val cells: Int = 1) {
    /** Rooms whose name says nothing the map's color does not already say. */
    val plain get() = type == "ENTRANCE" || type == "BLOOD" || type == "FAIRY"
}

/**
 * Which room stands in each 32-block cell of the dungeon, read from the loaded world, and how many
 * of its secrets have been found.
 *
 * A room is recognized by its center column: the blocks from y 140 down to the floor, hashed, give
 * a "core" that the room table maps to the room (a room whose center changes, like a puzzle being
 * solved, has several). The table and the hash are adapted from Odin (BSD-3, see CREDITS.md).
 *
 * Found secrets come off the action bar, which shows "3/5 Secrets" for the room the player stands
 * in, and a green checkmark on the map means all of them. Only the world and messages the client
 * already has are read; nothing is sent.
 */
object DungeonRooms {
    private const val CELLS = 36
    private val ROOM_SECRETS = Regex("(\\d+)/(\\d+) Secrets")

    private val table: Map<Int, RoomInfo> by lazy { loadTable() }
    private val cells = arrayOfNulls<RoomInfo>(CELLS)
    private val roofs = IntArray(CELLS)
    private val found = HashMap<String, Int>()
    private var current: RoomInfo? = null
    private var skipNext = false
    private var lastTotal = -1

    /** The recognized room the player is standing in or over, for the map's header. */
    var here: RoomInfo? = null
        private set

    /** Changes whenever a room is recognized or a count changes, so labels are rebuilt. */
    var revision = 0
        private set

    /** The room in world cell [cell] (x + 6 * z, from the dungeon's north-west corner), once known. */
    fun room(cell: Int): RoomInfo? = cells.getOrNull(cell)

    fun found(room: RoomInfo): Int? = found[room.name]

    /** The room table, by core. Several cores lead to the same room. */
    fun loadTable(): Map<Int, RoomInfo> {
        val stream = DungeonRooms::class.java.classLoader.getResourceAsStream("assets/alpaka/dungeonmap/rooms.json")
            ?: return emptyMap()
        val result = HashMap<Int, RoomInfo>()
        stream.use {
            val rooms = JsonParser.parseReader(InputStreamReader(it, Charsets.UTF_8)).asJsonObject.getAsJsonArray("rooms")
            for (element in rooms) {
                val room = element.asJsonObject
                val info = RoomInfo(room.get("name").asString, room.get("type").asString, room.get("secrets").asInt,
                    room.get("cells").asInt)
                for (core in room.getAsJsonArray("cores")) result[core.asInt] = info
            }
        }
        return result
    }

    /** Reads the center column of every cell not recognized yet whose chunk has arrived. */
    fun scan(level: Level) {
        val pos = BlockPos.MutableBlockPos()
        for (cell in 0 until CELLS) {
            if (cells[cell] != null) continue
            val x = cellCenter(cell % 6)
            val z = cellCenter(cell / 6)
            // The client level claims to have every chunk; its chunk cache knows which arrived.
            if (!level.chunkSource.hasChunk(x shr 4, z shr 4)) continue
            val (core, roof) = core { y ->
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).block).toString()
            }
            recognize(cell, table[core] ?: continue, roof)
        }
    }

    internal fun recognize(cell: Int, room: RoomInfo, roof: Int) {
        cells[cell] = room
        roofs[cell] = roof
        revision++
    }

    /**
     * Odin's core: [block] gives the block id at a height. Air above the roof counts as "0" per
     * layer, then every block down to the floor is written out except chests and oak planks (which
     * differ between copies of a room), and once two layers of bedrock lie below y 69 the rest is
     * padded. Returns the hash and the roof height.
     */
    fun core(block: (Int) -> String): Pair<Int, Int> {
        val column = StringBuilder(1024)
        var roof = 0
        var bedrock = 0
        for (y in 140 downTo 12) {
            val id = block(y)
            val air = id == "minecraft:air" || id == "minecraft:cave_air" || id == "minecraft:void_air"
            if (roof == 0) {
                if (air || id == "minecraft:gold_block") {
                    column.append('0')
                    continue
                }
                roof = y
            }
            if (air && bedrock >= 2 && y < 69) {
                repeat(y - 11) { column.append('0') }
                break
            }
            if (id == "minecraft:bedrock") bedrock++
            else {
                bedrock = 0
                if (id == "minecraft:oak_planks" || id == "minecraft:trapped_chest" || id == "minecraft:chest") continue
            }
            // Block.toString() in 26.2, which is what the cores were hashed from.
            column.append("Block{").append(id).append('}')
        }
        return column.toString().hashCode() to roof
    }

    /** World cell index 0..5 to the block at that cell's center; cell 0 spans -200 to -170. */
    fun cellCenter(index: Int) = -185 + 32 * index

    /** Follows which room the player is in, the way Odin does, so action bar counts go to it. */
    fun track(player: Player) = track(player.blockX, player.blockY, player.blockZ)

    internal fun track(blockX: Int, blockY: Int, blockZ: Int) {
        val x = (blockX + 201) shr 5
        val z = (blockZ + 201) shr 5
        val cell = x + z * 6
        val room = if (x in 0..5 && z in 0..5) cells[cell] else null
        here = room
        if (room == null) return
        // Walking over a room's roof is not being in it.
        if (room === current || blockY > roofs[cell] - 10) return
        current = room
        skipNext = true
    }

    /**
     * Right after entering a room the action bar may still show the last room's count; one with the
     * same total as before is skipped then. A count is only taken if its total matches the room's,
     * and only upwards, since secrets cannot be lost.
     */
    fun onActionBar(text: String) {
        val match = ROOM_SECRETS.find(text) ?: return
        val total = match.groupValues[2].toIntOrNull() ?: return
        val count = match.groupValues[1].toIntOrNull() ?: return
        val sameTotal = total == lastTotal
        lastTotal = total
        if (skipNext && sameTotal) {
            skipNext = false
            return
        }
        skipNext = false
        val room = current ?: return
        if (room.secrets != total || (found[room.name] ?: -1) >= count) return
        found[room.name] = count
        revision++
    }

    /** A green checkmark: every secret of [room] has been found. */
    fun complete(room: RoomInfo) {
        if (room.secrets > 0 && found[room.name] != room.secrets) {
            found[room.name] = room.secrets
            revision++
        }
    }

    fun reset() {
        cells.fill(null)
        roofs.fill(0)
        found.clear()
        current = null
        here = null
        skipNext = false
        lastTotal = -1
        revision++
    }
}
