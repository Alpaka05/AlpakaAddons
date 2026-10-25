package net.alpaka.addons.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla's protected {@code dead} flag. The death event sets it on the client and nothing clears it,
 * so it still says "dead" after the server sends the health back up; see HideMobDeathsFeature.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDeadAccessor {

    @Accessor("dead")
    boolean alpaka$isDead();
}
