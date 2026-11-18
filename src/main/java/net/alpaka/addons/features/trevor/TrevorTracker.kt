package net.alpaka.addons.features.trevor

import com.google.gson.GsonBuilder
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AtomicJsonFile
import net.alpaka.addons.features.notification.AlpakaNotifications
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.Util
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.animal.cow.Cow
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.entity.animal.rabbit.Rabbit
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Trevor Helper: follows Trevor the Trapper's tasks on the Farming Islands.
 *
 * Counts the animals hunted, by kind, the pelts they gave and how long each hunt took, and keeps
 * that across sessions in its own file. Times the wait until Trevor hands out the next task, and
 * marks the hunted animal with a waypoint while the player can see it.
 *
 * Hypixel does not say in chat which kind of animal a task is about. The kind is taken from the
 * animal the helper found: one of the six kinds, with the health Hypixel gives the trapper's
 * animals, in the player's line of sight. Only an animal the player can see is ever looked at or
 * marked, never one behind a wall (the same rule as Starred Mobs Highlight).
 */
object TrevorTracker {

    /** The kinds of animal Trevor sends the player after, in the order the HUD lists them. */
    enum class Animal(val title: String) {
        COW("Cow"), PIG("Pig"), SHEEP("Sheep"), CHICKEN("Chicken"), RABBIT("Rabbit"), HORSE("Horse"), UNKNOWN("Unknown");

        companion object {
            fun of(entity: LivingEntity): Animal? = when (entity) {
                is Cow -> COW
                is Pig -> PIG
                is Sheep -> SHEEP
                is Chicken -> CHICKEN
                is Rabbit -> RABBIT
                is Horse -> HORSE
                else -> null
            }
        }
    }

    /** Wait after taking a task before Trevor gives the next one (what SkyHanni uses). */
    const val COOLDOWN_MS = 20_000L

    /** How long a task lasts: Trevor says ten minutes. */
    const val HUNT_MS = 600_000L

    /** How often the visible animals are searched for the target, in ticks. */
    private const val SCAN_TICKS = 5

    /** Farthest an animal is looked for; past this its waypoint would not help anyway. */
    private const val MAX_DISTANCE = 128.0

    private const val BEAM_HEIGHT = 40.0

    /** Saved record. */
    class Stats {
        var kills: MutableMap<String, Int> = HashMap()
        var pelts: Long = 0L
        var huntMsTotal: Long = 0L
        var timedHunts: Int = 0
    }

    @JvmStatic
    var stats = Stats()
        private set

    // The task in progress.
    @JvmStatic var huntActive = false
        private set
    @JvmStatic var rarity: String? = null
        private set
    @JvmStatic var location: String? = null
        private set
    private var acceptedAtMs = 0L
    private var nextTaskAtMs = 0L

    // The animal found for it.
    private var targetId = -1
    @JvmStatic var targetKind: Animal? = null
        private set
    /** Whether the target is in sight this tick; only then is its waypoint drawn. */
    private var targetVisible = false
    private var scanIn = 0

    /** Whether the ready notice is owed once the task is done and the wait is over. */
    private var readyAlertDue = false

    private val GSON = GsonBuilder().setPrettyPrinting().create()

    private fun file(): File = FabricLoader.getInstance().configDir.resolve("alpaka-trevor.json").toFile()

    @JvmStatic
    fun isEnabled(): Boolean = AlpakaConfig.instance.trevorHelperEnabled

    @JvmStatic
    fun register() {
        load()
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (!overlay) onChat(message) }
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    private fun onChat(message: Component) {
        if (!isEnabled()) return
        for (line in SkyblockUtils.cleanColor(message.string).split('\n')) {
            val event = TrevorParse.parse(line) ?: continue
            onEvent(event, System.currentTimeMillis())
        }
    }

    internal fun onEvent(event: TrevorParse.Event, now: Long) {
        when (event) {
            is TrevorParse.Accepted -> {
                huntActive = true
                rarity = event.rarity
                location = event.location
                acceptedAtMs = now
                nextTaskAtMs = now + COOLDOWN_MS
                clearTarget()
                readyAlertDue = false
            }
            is TrevorParse.Killed -> {
                val kind = targetKind ?: Animal.UNKNOWN
                stats.kills.merge(kind.name, 1, Int::plus)
                stats.pelts += event.pelts
                if (huntActive && now - acceptedAtMs in 1..HUNT_MS) {
                    stats.huntMsTotal += now - acceptedAtMs
                    stats.timedHunts++
                }
                endHunt()
                save()
            }
            TrevorParse.OutOfTime -> endHunt()
        }
    }

    private fun endHunt() {
        huntActive = false
        rarity = null
        location = null
        clearTarget()
        readyAlertDue = true
    }

    private fun clearTarget() {
        targetId = -1
        targetKind = null
        targetVisible = false
    }

    /** Time until Trevor gives the next task; 0 when he would now. */
    @JvmStatic
    fun nextTaskInMs(now: Long): Long = (nextTaskAtMs - now).coerceAtLeast(0L)

    /** Time left on the task in progress. */
    @JvmStatic
    fun huntLeftMs(now: Long): Long = (acceptedAtMs + HUNT_MS - now).coerceAtLeast(0L)

    /** Distance to the target while it is in sight, else null. */
    @JvmStatic
    fun targetDistance(): Double? {
        if (!targetVisible) return null
        val mc = Minecraft.getInstance()
        val target = mc.level?.getEntity(targetId) ?: return null
        val player = mc.player ?: return null
        return sqrt(player.distanceToSqr(target))
    }

    @JvmStatic
    fun averageHuntMs(): Long? = if (stats.timedHunts > 0) stats.huntMsTotal / stats.timedHunts else null

    @JvmStatic
    fun totalKills(): Int = stats.kills.values.sum()

    @JvmStatic
    fun kills(kind: Animal): Int = stats.kills[kind.name] ?: 0

    private fun tick(mc: Minecraft) {
        val level = mc.level
        val player = mc.player
        if (!isEnabled() || level == null || player == null) {
            targetVisible = false
            return
        }
        val now = System.currentTimeMillis()
        // A task whose ten minutes are over without a word from Hypixel (disconnected meanwhile).
        if (huntActive && now - acceptedAtMs > HUNT_MS) endHunt()

        if (readyAlertDue && !huntActive && now >= nextTaskAtMs) {
            readyAlertDue = false
            if (AlpakaConfig.instance.trevorReadyAlert && onFarmingIslands()) {
                AlpakaNotifications.send("Trevor Ready", "You can take the next task.")
            }
        }

        if (!huntActive) {
            targetVisible = false
            return
        }

        val current = level.getEntity(targetId) as? LivingEntity
        if (current != null && current.isAlive && !current.isRemoved) {
            targetVisible = player.hasLineOfSight(current)
        } else {
            targetVisible = false
            if (--scanIn <= 0) {
                scanIn = SCAN_TICKS
                scan(mc, player)
            }
        }
    }

    /** Looks among the animals in sight for the hunted one; the nearest that fits. */
    private fun scan(mc: Minecraft, player: Player) {
        val level = mc.level ?: return
        var best: LivingEntity? = null
        var bestDistance = MAX_DISTANCE * MAX_DISTANCE
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || Animal.of(entity) == null) continue
            if (!entity.isAlive || entity.isInvisible) continue
            if (!TrevorParse.isAnimalHealth(entity.maxHealth)) continue
            val distance = player.distanceToSqr(entity)
            if (distance >= bestDistance) continue
            if (!player.hasLineOfSight(entity)) continue
            best = entity
            bestDistance = distance
        }
        val found = best ?: return
        targetId = found.id
        targetKind = Animal.of(found)
        targetVisible = true
    }

    private var islandsCheckedAtMs = 0L
    private var cachedOnIslands = false

    /** Zones of the Farming Islands, as the sidebar names them; the tab list's Area is checked first. */
    private val ISLAND_ZONES = arrayOf(
        "Mushroom Desert", "Trapper's Den", "Desert Settlement", "Oasis", "Mushroom Gorge",
        "Glowing Mushroom Cave", "Overgrown Mushroom Cave", "Desert Mountain", "Jake's House",
        "Treasure Hunter Camp", "The Barn", "Windmill",
    )

    /** Whether the player is on The Farming Islands, read at most once a second. */
    @JvmStatic
    fun onFarmingIslands(): Boolean {
        val now = System.currentTimeMillis()
        if (now - islandsCheckedAtMs < 1_000L) return cachedOnIslands
        islandsCheckedAtMs = now
        cachedOnIslands = SkyblockUtils.isOnSkyblock() && (
            SkyblockUtils.getTabListLines().any { it.trim().startsWith("Area: The Farming Islands") } ||
                SkyblockUtils.getSidebarLines().any { line -> line.contains("⏣") && ISLAND_ZONES.any { line.contains(it) } })
        return cachedOnIslands
    }

    /** The pelts the sidebar shows, when it shows them. */
    @JvmStatic
    fun sidebarPelts(): Int? = TrevorParse.sidebarPelts(SkyblockUtils.getSidebarLines())

    @JvmStatic
    fun resetStats() {
        stats = Stats()
        save()
    }

    @JvmStatic
    fun reset() {
        clearTarget()
    }

    // ---------------------------------------------------------------- waypoint

    @JvmStatic
    fun hasGizmos(): Boolean = isEnabled() && AlpakaConfig.instance.trevorWaypoint && huntActive && targetVisible

    /** A box around the animal, a beam above it and its kind and distance, while it is in sight. */
    @JvmStatic
    fun collectGizmos() {
        val mc = Minecraft.getInstance()
        val target = mc.level?.getEntity(targetId) ?: return
        val camera = mc.gameRenderer.mainCamera()
        if (!camera.isInitialized) return
        val color = AlpakaConfig.instance.trevorWaypointColor or 0xFF000000.toInt()
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val pos = target.getPosition(partialTick)
        val box = target.boundingBox.move(pos.x - target.x, pos.y - target.y, pos.z - target.z)
        val fill = ARGB.color((ARGB.alpha(color) * 0.25f).roundToInt(), color)
        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 2.0f, fill)).setAlwaysOnTop()
        val top = Vec3(pos.x, box.maxY, pos.z)
        Gizmos.line(top, top.add(0.0, BEAM_HEIGHT, 0.0), ARGB.color(0x80, color), 6.0f).setAlwaysOnTop()

        val distance = sqrt(camera.position().distanceToSqr(pos))
        val label = (targetKind?.title ?: "Animal") + String.format(Locale.ROOT, " %.0fm", distance)
        val scale = TextGizmo.Style.DEFAULT_SCALE * (distance / 8.0).coerceIn(1.0, 25.0).toFloat()
        Gizmos.billboardText(label, top.add(0.0, 0.8, 0.0), TextGizmo.Style.forColorAndCentered(color).withScale(scale))
            .setAlwaysOnTop()
    }

    // ---------------------------------------------------------------- saving

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val loaded = GSON.fromJson(AtomicJsonFile.read(f), Stats::class.java) ?: return
            @Suppress("SENSELESS_COMPARISON")
            if (loaded.kills == null) loaded.kills = HashMap()
            stats = loaded
        } catch (e: Exception) {
            AlpakaAddons.LOGGER.warn("Could not read the Trevor tracker", e)
        }
    }

    /** Off in tests, which have no game folder to write into. */
    internal var persist = true

    private fun save() {
        if (!persist) return
        val json = GSON.toJson(stats)
        Util.ioPool().execute {
            try {
                AtomicJsonFile.write(file(), json)
            } catch (e: Exception) {
                AlpakaAddons.LOGGER.warn("Could not save the Trevor tracker", e)
            }
        }
    }

    /** For tests: forget everything without touching the file. */
    internal fun clearForTest() {
        stats = Stats()
        huntActive = false
        rarity = null
        location = null
        acceptedAtMs = 0L
        nextTaskAtMs = 0L
        clearTarget()
        readyAlertDue = false
    }

    internal fun setTargetForTest(kind: Animal?) {
        targetKind = kind
    }
}
