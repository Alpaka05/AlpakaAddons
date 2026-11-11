package net.alpaka.addons.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's protected {@code isInGround()}, synced from the server, so an arrow that sticks in a
 * block can be told from one in flight; see Hide Visual Clutter's Attached Arrows.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowInGroundAccessor {

    @Invoker("isInGround")
    boolean alpaka$isInGround();
}
