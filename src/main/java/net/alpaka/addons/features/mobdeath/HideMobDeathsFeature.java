package net.alpaka.addons.features.mobdeath;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.LivingEntityDeadAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * Makes dying mobs disappear at once instead of playing vanilla's death animation.
 *
 * Vanilla keeps a dead mob around for twenty ticks, tipping it over and sinking it into the ground
 * while its death timer runs, until the server finally removes it. This hides the body for that
 * whole stretch: the entity is skipped in {@code EntityRenderer.shouldRender}, so neither the model
 * nor its name tag is drawn from the first frame its health is gone. Purely visual - the entity,
 * the drops and the sound are untouched, and the server is not involved.
 *
 * A mob counts as dying once any of three things says so: its health is zero, its death timer runs,
 * or vanilla's {@code dead} flag is set. The flag is the one that sticks: the death event sets it and
 * nothing clears it, while Hypixel may send the health back up after the death event, which used to
 * bring the body back standing (and stopped its death timer before it began).
 *
 * The SkyBlock name above a mob is an armor stand of its own. It goes with the body when it sits
 * right after the mob in entity ids (how Hypixel spawns them) or when it reads zero health.
 *
 * With Hide Death Smoke on as well, the puff of smoke when the server finally removes the body goes
 * too. It can arrive two ways: as the entity event vanilla turns into {@code makePoofParticles}
 * ({@code LivingEntityMixin}), or as plain poof particles sent by the server
 * ({@code ClientPacketListenerMobDeathMixin}), which may come just after the body is gone; so the
 * spots of recently removed dying mobs are remembered for a moment.
 *
 * Real players are left alone: their own death is a screen, not an animation, and other players
 * dying is rare enough that hiding them would only confuse. Hypixel's player-shaped mobs (most
 * dungeon mobs, for one) are not real players: their UUIDs are version 2, a real player's version 4.
 */
public final class HideMobDeathsFeature {

    /** "0❤" or "0/1.2M❤" at the start of a word: a SkyBlock nametag of a mob with no health left. */
    private static final Pattern ZERO_HEALTH = Pattern.compile("(?:^|\\s)0(?:/[\\d.,]+[kKmMbB]?)?❤");
    /** How long a removed dying mob's spot still swallows poof particles, in milliseconds. */
    private static final long REMOVED_SMOKE_WINDOW_MS = 1000;
    /** How far around a mob's box poof particles count as its death smoke. */
    private static final double SMOKE_MARGIN = 1.0;

    private record RemovedBody(AABB box, long removedAt) {}

    /** Recently removed dying mobs, oldest first. Only touched on the client thread. */
    private static final Deque<RemovedBody> REMOVED = new ArrayDeque<>();

    private HideMobDeathsFeature() {
    }

    public static boolean shouldHideEntity(Entity entity) {
        if (!AlpakaConfig.instance.hideMobDeathsEnabled) return false;
        if (entity instanceof ArmorStand stand) return isDeadMobsName(stand);
        return entity instanceof LivingEntity living && isDyingMob(living);
    }

    public static boolean shouldHideSmoke(LivingEntity entity) {
        if (!smokeEnabled() || !entity.level().isClientSide()) return false;
        return isDyingMob(entity);
    }

    /** A poof particle packet from the server that lands on a dying or just removed mob. */
    public static boolean shouldHideSmokePacket(ClientboundLevelParticlesPacket packet, ClientLevel level) {
        if (!smokeEnabled() || packet.getParticle() != ParticleTypes.POOF) return false;
        double x = packet.getX(), y = packet.getY(), z = packet.getZ();

        long now = System.currentTimeMillis();
        while (!REMOVED.isEmpty() && now - REMOVED.peekFirst().removedAt() > REMOVED_SMOKE_WINDOW_MS) {
            REMOVED.pollFirst();
        }
        for (RemovedBody body : REMOVED) {
            if (body.box().contains(x, y, z)) return true;
        }

        AABB around = new AABB(x, y, z, x, y, z).inflate(SMOKE_MARGIN);
        return !level.getEntitiesOfClass(LivingEntity.class, around,
                living -> isDyingMob(living) && living.getBoundingBox().inflate(SMOKE_MARGIN).contains(x, y, z)).isEmpty();
    }

    /** The client is about to drop this entity; keep its spot if it died, for smoke arriving late. */
    public static void onEntityRemoved(Entity entity) {
        if (!smokeEnabled() || !(entity instanceof LivingEntity living) || !isDyingMob(living)) return;
        REMOVED.addLast(new RemovedBody(living.getBoundingBox().inflate(SMOKE_MARGIN), System.currentTimeMillis()));
        // A big AoE kill removes a lot at once; nothing older than the window is needed anyway.
        while (REMOVED.size() > 64) REMOVED.pollFirst();
    }

    private static boolean smokeEnabled() {
        return AlpakaConfig.instance.hideMobDeathsEnabled && AlpakaConfig.instance.hideMobDeathSmoke;
    }

    private static boolean isDyingMob(LivingEntity living) {
        if (living instanceof Player player && player.getUUID().version() != 2) return false;
        // deathTime starts counting the tick after the health reached zero; isDeadOrDying covers the
        // frames in between so the mob never shows up dead for even one frame.
        return living.deathTime > 0 || living.isDeadOrDying() || ((LivingEntityDeadAccessor) living).alpaka$isDead();
    }

    /** The SkyBlock nametag of a dying mob: the stand spawned right after it, or one reading 0 health. */
    private static boolean isDeadMobsName(ArmorStand stand) {
        if (!stand.hasCustomName() || !SkyblockUtils.isOnSkyblock()) return false;
        Entity owner = stand.level().getEntity(stand.getId() - 1);
        if (owner instanceof LivingEntity living && !(owner instanceof ArmorStand) && isDyingMob(living)) return true;
        Component name = stand.getCustomName();
        return name != null && readsZeroHealth(name.getString());
    }

    /** Whether a SkyBlock mob nametag (color codes already stripped) shows no health left. */
    static boolean readsZeroHealth(String name) {
        return ZERO_HEALTH.matcher(name).find();
    }
}
