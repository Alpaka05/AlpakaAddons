package net.alpaka.addons.features.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Remembers which entities the local player has recently swung at, so that a mob dying can be
 * attributed to this client rather than to another player standing nearby.
 *
 * Hypixel resolves all combat server-side, so no client-side event ever says "you got the kill" -
 * a mob's death sound arrives the same way whoever landed the hit. The one thing this client does
 * know for certain is which entity <em>it</em> attacked, because that swing is our own input on its
 * way out. Matching that against the entity a death sound came from is enough to tell our kills
 * apart from someone else's, without reading anything the server has not already sent for display.
 */
public final class LocalAttackTracker {

    /** How long after our last swing at an entity its death still counts as ours. */
    private static final long ATTACK_MEMORY_MS = 5_000L;

    /**
     * How far a death sound may sit from an entity and still be taken as that entity's.
     *
     * Entity death sounds are emitted at the entity's own position, so this only has to absorb the
     * drift between the sound being created and us looking the entity up a moment later.
     */
    private static final double SOUND_MATCH_RADIUS = 2.0d;

    private static final Map<Integer, Long> attackedAtMs = new HashMap<>();

    private LocalAttackTracker() {}

    /** Records that the local player just attacked the entity with this id. */
    public static void noteAttack(int entityId) {
        long now = System.currentTimeMillis();
        attackedAtMs.put(entityId, now);

        // Swept here rather than on a timer: this runs only on the player's own attacks, which is
        // both rare enough to be free and the only moment the map can grow.
        Iterator<Map.Entry<Integer, Long>> it = attackedAtMs.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > ATTACK_MEMORY_MS) it.remove();
        }
    }

    /**
     * Whether an entity of the given type near this position is one the local player attacked
     * within the last {@link #ATTACK_MEMORY_MS}.
     *
     * Returns false when the entity has already been removed from the world, which means a kill
     * whose sound arrives after the corpse is gone is credited to nobody. That is the safe way
     * round: the point of this check is to stay quiet for other people's kills.
     */
    public static <T extends Entity> boolean wasAttackedByUsNear(Class<T> type, double x, double y, double z) {
        if (attackedAtMs.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        AABB around = new AABB(
                x - SOUND_MATCH_RADIUS, y - SOUND_MATCH_RADIUS, z - SOUND_MATCH_RADIUS,
                x + SOUND_MATCH_RADIUS, y + SOUND_MATCH_RADIUS, z + SOUND_MATCH_RADIUS);

        long now = System.currentTimeMillis();
        for (T entity : mc.level.getEntitiesOfClass(type, around)) {
            Long attackedAt = attackedAtMs.get(entity.getId());
            if (attackedAt != null && now - attackedAt <= ATTACK_MEMORY_MS) return true;
        }
        return false;
    }
}
