package net.alpaka.addons.features.mobdeath;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Makes dying mobs disappear at once instead of playing vanilla's death animation.
 *
 * Vanilla keeps a dead mob around for twenty ticks, tipping it over and sinking it into the ground
 * while its death timer runs, until the server finally removes it. This hides the body for that
 * whole stretch: the entity is skipped in {@code EntityRenderer.shouldRender}, so neither the model
 * nor its name tag is drawn from the first frame its health is gone. Purely visual - the entity,
 * the drops and the sound are untouched, and the server is not involved.
 *
 * Players are left alone: their own death is a screen, not an animation, and other players dying
 * is rare enough that hiding them would only confuse.
 */
public final class HideMobDeathsFeature {

    private HideMobDeathsFeature() {
    }

    public static boolean shouldHideEntity(Entity entity) {
        if (!AlpakaConfig.instance.hideMobDeathsEnabled) return false;
        if (!(entity instanceof LivingEntity living) || entity instanceof Player) return false;
        // deathTime starts counting the tick after the health reached zero; isDeadOrDying covers the
        // frames in between so the mob never shows up dead for even one frame.
        return living.deathTime > 0 || living.isDeadOrDying();
    }
}
