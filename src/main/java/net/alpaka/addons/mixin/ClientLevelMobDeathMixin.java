package net.alpaka.addons.mixin;

import net.alpaka.addons.features.mobdeath.HideMobDeathsFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers where dying mobs were removed, for death smoke that arrives late; see {@link HideMobDeathsFeature}. */
@Mixin(ClientLevel.class)
public class ClientLevelMobDeathMixin {

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void alpaka$rememberRemovedBody(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = ((ClientLevel) (Object) this).getEntity(id);
        if (entity != null) {
            HideMobDeathsFeature.onEntityRemoved(entity);
        }
    }
}
