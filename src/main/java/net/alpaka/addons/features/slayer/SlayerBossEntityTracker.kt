package net.alpaka.addons.features.slayer

import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

/**
 * Watches the slayer boss's own entity, so a fight can be timed over how long the boss was actually
 * alive rather than over what the sidebar says.
 *
 * This exists because the sidebar is not a clock. It flips to "Slay the boss!" around the spawn
 * being triggered rather than around the boss arriving, and back out of it after the boss is gone,
 * so a sidebar-timed fight carries seconds that were not fight. That padding is not constant either,
 * which is what makes the two numbers impossible to compare.
 *
 * SkyHanni measures the same fight from its damage indicator instead:
 *
 *  * Start - `DamageIndicatorManager.grabData` stamps `foundTime` when `MobEvent.Spawn` first hands
 *    it the boss, and `timeToKill` is `foundTime.passedSince()`.
 *  * End - `DataWatcherApi` turns every `LivingEntity.DATA_HEALTH_ID` sync into an
 *    `EntityHealthUpdateEvent`, and the manager posts the boss's death from `health <= 1`. Not from
 *    the entity leaving the world, which is a good deal later.
 *
 * Everyone quoting a slayer time quotes that number, which is why [SlayerTimer] measures a fight
 * this way rather than off the sidebar. There is no setting for it: the sidebar is kept only as the
 * fallback for a fight where the boss could not be watched, and picking the worse of the two
 * deliberately is not something worth offering.
 *
 * ### Finding the boss
 *
 * Hypixel stacks name tags above a slayer boss as invisible armour stands, and gives them the entity
 * ids straight after the boss's own. SkyHanni's `Mob` reads the same stack: the boss is id `N`, its
 * name tag `N + 1` (`MobUtils.getArmorStand`: "the corresponding ArmorStand for a mob has always the
 * ID + 1"), and the `Spawned by: <player>` line is `hologram2`, two further along again. Two real
 * samples, colour-stripped, from SkyHanni's own captured test data:
 *
 * ```
 * ☠ Revenant Horror IV 1.5M❤
 * Spawned by: Alpaka05
 * ```
 *
 * The owner line is what makes this safe in a crowded slayer area: requiring it means another
 * player's boss standing next to ours is never picked up, and neither are the mobs farmed to summon
 * it - in the Graveyard those are named "Revenant Horror" as well, only with a `[Lv5]` prefix and no
 * owner above them.
 *
 * Nothing here is scanned or probed. These are entities the server sent this client so it can draw
 * them, read only while a slayer quest the sidebar already shows is running.
 */
object SlayerBossEntityTracker {

    /**
     * How far from the player a boss name tag may sit and still be picked up, in blocks.
     *
     * Only has to reach far enough to catch the boss *arriving*, which every slayer does right on
     * top of the player who summoned it - after that the boss is followed by entity id, so a
     * Voidgloom Seraph teleporting across its arena is not this radius's problem.
     *
     * Kept deliberately tight because the search box grows with the cube of it, and because what the
     * box fills up with in a slayer area is floating damage numbers: every one is a named armour
     * stand this search has to read a name off. Halving this from the 24 it started at cuts the
     * volume, and so that reading, to an eighth.
     */
    private const val SEARCH_RADIUS = 12.0

    /**
     * How far the boss's name tag may sit from the owner line that vouches for it, squared.
     *
     * Wide enough for the whole stack: the owner line is not the tag directly above the name, it is
     * `hologram2` - two stands further up again - so the two ends of the stack are several name-tag
     * heights apart. Widening this is safe because the nearest pairing wins rather than the first.
     */
    private const val OWNER_RADIUS_SQ = 16.0

    /**
     * How far a boss name tag may sit from the player to be accepted without an owner line at all.
     *
     * The fallback for the case where the owner stack cannot be paired up. Deliberately far shorter
     * than [SEARCH_RADIUS], and only ever used while the sidebar itself says a boss fight of ours is
     * in progress, so what it accepts is a boss that exists, is ours, and is standing next to us.
     */
    private const val UNOWNED_RADIUS_SQ = 100.0

    /** The line Hypixel puts above a mob that a player summoned. */
    private const val OWNER_MARKER = "Spawned by:"

    /** The heart Hypixel ends a mob name tag's health figure with. */
    private const val HEALTH_MARKER = "❤"

    /**
     * How far below its name tag the boss itself may be, squared.
     *
     * Only a fallback distance: the mob is normally resolved by entity id, and this is used when
     * that fails. A name tag floats a block or two over its mob's head.
     */
    private const val MOB_RADIUS = 4.0
    private const val MOB_RADIUS_SQ = MOB_RADIUS * MOB_RADIUS

    /**
     * The health at or below which the boss counts as dead.
     *
     * One rather than zero, to the letter of what SkyHanni does: `DamageIndicatorManager` posts its
     * `DamageIndicatorDeathEvent` from `event.health <= 1`.
     */
    private const val DEAD_HEALTH = 1.0f

    /**
     * How long after a fight ends a name tag is ignored, in milliseconds.
     *
     * A dead boss's name tag outlives its death by about a second - the death is read off the health
     * sync, the entity is removed later - so without this the corpse of the boss just killed would
     * be picked up as the next one and stamp a spawn time onto a fight that has not started.
     */
    private const val REACQUIRE_COOLDOWN_MS = 3_000L

    /** The slayer whose boss is being watched, or null while no quest is running. */
    private var watching: SlayerType? = null

    /** Entity id of the name tag currently taken to be the boss's, or -1 when none is. */
    private var trackedId = -1

    /** Entity id of the boss itself, resolved from its name tag, or -1 when it is not known. */
    private var trackedMobId = -1

    /**
     * Whether the boss has been seen above [DEAD_HEALTH] since it was picked up.
     *
     * Needed because a freshly spawned entity's health reads 0 until the server's first sync for it
     * arrives, and taking that for a kill would end the fight on the tick it started.
     */
    private var sawAlive = false

    /** Wall clock before which a name tag is not accepted. See [REACQUIRE_COOLDOWN_MS]. */
    private var acceptFromMs = 0L

    /**
     * When the boss entity was first seen, or null while it has not been.
     *
     * Set once per boss and never moved afterwards. That is deliberate: Tarantula Broodfather V
     * splits into a "Conjoined Brood" partway through, which is a different entity with a different
     * name tag, and SkyHanni carries the original spawn time across that same transition rather than
     * restarting the clock on the second half of one fight.
     */
    var spawnedAtMs: Long? = null
        private set

    /**
     * When the boss died, or null while it is still up.
     *
     * Taken from its health reaching [DEAD_HEALTH], which is the moment SkyHanni stops its own
     * clock. The entity vanishing counts too, as a fallback for a boss despawned outright, and is
     * cleared again if a matching boss turns up afterwards - both for the Tarantula split above and
     * because the other way to lose a boss is to have walked out of range of it.
     */
    var diedAtMs: Long? = null
        private set

    /** Whether a boss is being followed right now, for the diagnostics command. */
    val isTracking: Boolean get() = trackedId != -1

    /** Whether the boss behind the name tag was resolved, for the diagnostics command. */
    val hasMob: Boolean get() = trackedMobId != -1

    /**
     * Forgets the current boss, for a kill, a cancelled quest or a world change.
     *
     * Holds the search off for [REACQUIRE_COOLDOWN_MS] so that the boss just killed cannot be picked
     * straight back up as the next one.
     */
    fun reset() {
        watching = null
        trackedId = -1
        trackedMobId = -1
        sawAlive = false
        spawnedAtMs = null
        diedAtMs = null
        acceptFromMs = System.currentTimeMillis() + REACQUIRE_COOLDOWN_MS
    }

    /**
     * Called once per client tick while a slayer quest is up.
     *
     * Costs two entity lookups by id for as long as the boss is where it was left, which is nearly
     * all of a fight. Only while there is no boss in hand does it fall through to the box search
     * below, and only then while the sidebar says a boss fight is on.
     *
     * @param quest the slayer whose quest is running.
     * @param inBossFight whether the sidebar says the boss itself is up. Gates the search entirely,
     *   and unlocks the ownerless fallback in [findBossNameTag].
     */
    fun tick(quest: SlayerType, inBossFight: Boolean) {
        watching = quest

        val level = Minecraft.getInstance().level ?: return

        if (trackedId != -1) {
            val tag = level.getEntity(trackedId)
            val mob = if (trackedMobId == -1) null else level.getEntity(trackedMobId) as? LivingEntity

            if (mob != null && mob.isAlive) {
                if (mob.health > DEAD_HEALTH) {
                    // The only place a recorded death is taken back. Seeing the boss standing there
                    // above the threshold is proof it is not dead; nothing else is, and an earlier
                    // version clearing it on a merely missing entity is what swallowed every death.
                    sawAlive = true
                    diedAtMs = null
                    return
                }
                // At or below the threshold. Only a kill once the boss has been seen alive; before
                // that it is the entity's health simply not having synced yet.
                if (!sawAlive) return
                if (diedAtMs == null) diedAtMs = System.currentTimeMillis()
                return
            }

            if (trackedMobId == -1 && tag != null && tag.isAlive) {
                // A name tag with nothing resolved behind it yet. Keep trying rather than reading
                // the missing mob as a death - it has not been seen alive, so there is no death.
                retryMobResolution(level)
                return
            }

            // The boss was there and is not any more. Its removal is the death, a little later than
            // the health sync would have been but the same event.
            if (diedAtMs == null) diedAtMs = System.currentTimeMillis()
        }

        // Searching costs a box query plus a name read per named armour stand inside it, and in a
        // slayer area nearly every one of those is a floating damage number. Confining it to the
        // window where a boss can actually exist keeps that off the grind, which is both the longer
        // phase and the one with a whole pack of mobs throwing damage tags.
        //
        // It used to run through the grind as well, to catch a boss that reached the world before
        // the scoreboard caught up. Measurement since says the scoreboard leads: an Inferno
        // Demonlord turned up 0.45s *after* "Slay the boss!" appeared. So that bought nothing and
        // was paid for continuously.
        if (!inBossFight) return

        val found = findBossNameTag(quest) ?: return
        if (found.id == trackedId) return
        if (System.currentTimeMillis() < acceptFromMs) return

        trackedId = found.id
        trackedMobId = resolveMob(found)?.id ?: -1
        sawAlive = false
        diedAtMs = null
        if (spawnedAtMs == null) spawnedAtMs = System.currentTimeMillis()
    }

    /** Tries once more to put a mob behind the tracked name tag. True when one was found. */
    private fun retryMobResolution(level: ClientLevel): Boolean {
        val tag = level.getEntity(trackedId) as? ArmorStand ?: return false
        val mob = resolveMob(tag) ?: return false
        trackedMobId = mob.id
        return true
    }

    /**
     * The mob a boss name tag belongs to, or null when it cannot be pinned down.
     *
     * Hypixel gives a mob's name tag the entity id straight after the mob's own, the assumption
     * SkyHanni's `MobUtils.getArmorStand` is built on. Falling back to the nearest mob underneath
     * covers the exceptions that comment admits to.
     */
    private fun resolveMob(tag: ArmorStand): LivingEntity? {
        val level = Minecraft.getInstance().level ?: return null

        val byId = level.getEntity(tag.id - 1)
        if (byId is LivingEntity && byId !is ArmorStand) return byId

        var best: LivingEntity? = null
        var bestDistance = MOB_RADIUS_SQ
        for (candidate in level.getEntitiesOfClass(
            LivingEntity::class.java,
            tag.boundingBox.inflate(MOB_RADIUS),
        ) { it !is ArmorStand && it !is Player }) {
            val distance = candidate.distanceToSqr(tag)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    /**
     * The name tag of this player's own boss of the given slayer, or null if it is not in the world.
     *
     * Both kinds of tag have to be in hand before either can be judged, and they come back in no
     * particular order, so the list is walked once to sort them and the pairing is done afterwards.
     */
    private fun findBossNameTag(type: SlayerType): ArmorStand? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val playerName = player.gameProfile?.name ?: return null

        val stands = level.getEntitiesOfClass(
            ArmorStand::class.java,
            player.boundingBox.inflate(SEARCH_RADIUS),
        ) { it.hasCustomName() }
        if (stands.isEmpty()) return null

        val owned = ArrayList<ArmorStand>(2)
        val candidates = ArrayList<ArmorStand>(2)

        for (stand in stands) {
            val raw = stand.customName?.string ?: continue

            // Sorted on the raw name, before anything is stripped. Nearly every named armour stand
            // in a slayer area is a floating damage number, and stripping each of those per tick
            // just to find out it is a damage number would be the whole cost of this search. Both
            // markers survive Hypixel's habit of dropping formatting codes mid-word this way.
            if (SkyblockUtils.containsIgnoringFormatting(raw, OWNER_MARKER)) {
                val name = SkyblockUtils.cleanColor(raw)
                val ownerAt = name.indexOf(OWNER_MARKER)
                if (ownerAt >= 0 && namesPlayer(name.substring(ownerAt + OWNER_MARKER.length), playerName)) {
                    owned.add(stand)
                }
            } else if (SkyblockUtils.containsIgnoringFormatting(raw, HEALTH_MARKER)) {
                if (isBossNameTag(SkyblockUtils.cleanColor(raw), type)) candidates.add(stand)
            }
        }
        if (candidates.isEmpty()) return null

        if (owned.isNotEmpty()) {
            // The nearest pairing, not the first that is close enough. A trash mob pressed against
            // the boss can have its own tag inside the radius; the boss's tag is always the closer.
            var best: ArmorStand? = null
            var bestDistance = OWNER_RADIUS_SQ
            for (candidate in candidates) {
                for (owner in owned) {
                    val distance = owner.distanceToSqr(candidate)
                    if (distance <= bestDistance) {
                        bestDistance = distance
                        best = candidate
                    }
                }
            }
            if (best != null) return best
        }

        // No owner line could be paired up. The caller only searches while the sidebar says a boss
        // fight of ours is under way, so a boss of ours certainly exists - and then the nearest one
        // standing next to us is it. Without this the whole feature would go quiet on any layout
        // change to Hypixel's name tag stack, which is exactly what it must not do silently.
        var nearest: ArmorStand? = null
        var nearestDistance = UNOWNED_RADIUS_SQ
        for (candidate in candidates) {
            val distance = player.distanceToSqr(candidate)
            if (distance <= nearestDistance) {
                nearestDistance = distance
                nearest = candidate
            }
        }
        return nearest
    }

    /**
     * Whether the text after "Spawned by:" names this player.
     *
     * Compared token by token rather than by [String.contains] so that a rank prefix in front of the
     * name is tolerated while another player whose name merely contains ours is not.
     */
    private fun namesPlayer(remainder: String, playerName: String): Boolean {
        for (token in remainder.trim().split(' ')) {
            if (token == playerName) return true
        }
        return false
    }

    /**
     * Whether a colour-stripped mob name tag is this slayer's boss.
     *
     * Only reached for names the caller has already found a health figure in, which is what
     * separates a mob's name tag from the holograms Hypixel scatters around an area. What is left to
     * rule out is the mobs farmed to summon the boss, which in the Graveyard are named "Revenant
     * Horror" as well: those carry a level where the boss carries a marker and a roman tier -
     * `[Lv5] Revenant Horror 500❤` against `☠ Revenant Horror IV 1.5M❤`.
     */
    private fun isBossNameTag(name: String, type: SlayerType): Boolean {
        if (name.startsWith("[")) return false
        for (bossName in type.bossNames) {
            if (name.contains(bossName)) return true
        }
        return false
    }
}
