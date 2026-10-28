package net.alpaka.addons.features.dungeonmap

import net.minecraft.world.level.saveddata.maps.MapDecoration
import kotlin.math.hypot

/** A marker as the map sent it: map pixels, and the facing in degrees (0 is south, like a yaw). */
data class MapMarker(val x: Float, val y: Float, val yaw: Float) {
    companion object {
        fun of(decoration: MapDecoration) = MapMarker(decoration.x() / 2f + 64f, decoration.y() / 2f + 64f,
            (decoration.rot().toInt() and 15) * 22.5f)
    }
}

object MarkerAssignment {
    /** How far a marker may trail its player's live position and still count as theirs. */
    const val CLAIM = 6f

    /**
     * Which teammate each of the other players' [markers] belongs to, by index.
     *
     * Hypixel sends them in tab order and leaves out the dead, so [teammates] (alive, without the
     * player, in tab order) are paired with them one by one. Teammates in render distance, whose map
     * position [live] is known, first claim the marker next to them, and only the rest are paired by
     * order. That keeps names right even if the order is off, as long as the far ones are in order.
     */
    fun assign(markers: List<MapMarker>, teammates: List<String>, live: Map<String, MapMarker>): Map<Int, String> {
        if (live.isNotEmpty()) {
            val pairs = ArrayList<Triple<Float, String, Int>>()
            for ((name, at) in live) markers.forEachIndexed { index, marker ->
                val distance = hypot(marker.x - at.x, marker.y - at.y)
                if (distance <= CLAIM) pairs += Triple(distance, name, index)
            }
            pairs.sortBy { it.first }
            val owners = HashMap<Int, String>()
            for ((_, name, index) in pairs) if (index !in owners && name !in owners.values) owners[index] = name
            // A teammate in range without a marker beside it means the positions can't be trusted.
            if (owners.size == live.size) {
                val rest = teammates.filter { it !in owners.values }.iterator()
                for (index in markers.indices) if (index !in owners && rest.hasNext()) owners[index] = rest.next()
                return owners
            }
        }
        val owners = HashMap<Int, String>()
        for (index in markers.indices) if (index < teammates.size) owners[index] = teammates[index]
        return owners
    }
}

/**
 * Moves one marker smoothly.
 *
 * Map markers arrive a few times a second, in half-pixel and 22.5° steps. Each new one is approached
 * over the time the last one took to arrive, so a walking player glides at an even pace, about one
 * update behind. Live positions are followed as they are, with a short blend whenever a player
 * switches between the two (walking out of or into render distance).
 */
class MarkerTrack {
    var x = 0f
        private set
    var y = 0f
        private set
    var yaw = 0f
        private set
    /** When the track was last drawn, so tracks of players gone from the map can be dropped. */
    var touched = 0L
        private set

    private var started = false
    private var live = false
    private var fromX = 0f
    private var fromY = 0f
    private var fromYaw = 0f
    private var toX = 0f
    private var toY = 0f
    private var toYaw = 0f
    private var since = 0L
    private var duration = 1L
    private var lastChange = 0L
    /** How far the drawn facing was from the live one when a blend began. */
    private var yawOffset = 0f

    fun glide(marker: MapMarker, now: Long) {
        touched = now
        val changed = marker.x != toX || marker.y != toY || marker.yaw != toYaw
        if (!started) return jump(marker, now)
        if (changed || live) {
            fromX = x
            fromY = y
            fromYaw = yaw
            toX = marker.x
            toY = marker.y
            toYaw = marker.yaw
            duration = if (live) HANDOVER else (now - lastChange).coerceIn(MIN_STEP, MAX_STEP)
            since = now
            if (changed) lastChange = now
            live = false
            // A leap or teleport is drawn as one, not as a dash across the map.
            if (hypot(toX - fromX, toY - fromY) > SNAP) duration = 0
        }
        val t = if (duration <= 0) 1f else ((now - since).toFloat() / duration).coerceIn(0f, 1f)
        x = fromX + (toX - fromX) * t
        y = fromY + (toY - fromY) * t
        yaw = fromYaw + wrapDegrees(toYaw - fromYaw) * t
    }

    fun follow(liveX: Float, liveY: Float, liveYaw: Float, now: Long) {
        touched = now
        if (!started || !live) {
            fromX = if (started) x else liveX
            fromY = if (started) y else liveY
            // Kept as an offset that fades out, so a player turning during the blend never makes
            // the difference wrap around and the head spin the other way.
            yawOffset = if (started) wrapDegrees(yaw - liveYaw) else 0f
            since = if (hypot(liveX - fromX, liveY - fromY) > SNAP) now - HANDOVER else now
            started = true
            live = true
        }
        val t = ((now - since).toFloat() / HANDOVER).coerceIn(0f, 1f)
        val eased = t * t * (3 - 2 * t)
        x = fromX + (liveX - fromX) * eased
        y = fromY + (liveY - fromY) * eased
        yaw = liveYaw + yawOffset * (1 - eased)
    }

    /** Puts the marker where the map says, without easing. */
    fun jump(marker: MapMarker, now: Long) {
        touched = now
        if (marker.x != toX || marker.y != toY || marker.yaw != toYaw) lastChange = now
        x = marker.x
        y = marker.y
        yaw = marker.yaw
        toX = x
        toY = y
        toYaw = yaw
        started = true
        live = false
        since = now
        duration = 0
    }

    private companion object {
        const val MIN_STEP = 50L
        const val MAX_STEP = 300L
        const val HANDOVER = 150L
        /** Map pixels; a room and its gap are 20 to 22. */
        const val SNAP = 16f

        fun wrapDegrees(degrees: Float): Float {
            val wrapped = degrees % 360f
            return when {
                wrapped >= 180f -> wrapped - 360f
                wrapped < -180f -> wrapped + 360f
                else -> wrapped
            }
        }
    }
}
