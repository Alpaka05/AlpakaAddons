package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void hideBlazeNameTags(T entity, double distance, CallbackInfoReturnable<Boolean> info) {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return;

        if (entity instanceof Blaze) {
            info.setReturnValue(false);
            return;
        }

        if (entity.hasCustomName()) {
            Component customName = entity.getCustomName();
            if (customName != null) {
                String clean = SlayerDropTracker.cleanColor(customName.getString());
                if (clean.contains("Smoldering Blaze") || clean.contains("Blaze")) {
                    info.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void filterBlazeEntities(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> info) {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return;

        if (entity instanceof SmallFireball) {
            info.setReturnValue(false);
            return;
        }

        if (entity.hasCustomName()) {
            Component customName = entity.getCustomName();
            if (customName != null) {
                String clean = SlayerDropTracker.cleanColor(customName.getString());
                if (clean.contains("Smoldering Blaze")) {
                    info.setReturnValue(false);
                }
            }
        }
    }
}
