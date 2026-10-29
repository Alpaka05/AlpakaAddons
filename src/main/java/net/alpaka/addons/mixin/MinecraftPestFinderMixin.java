package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.PestFinder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells Pest Finder when the attack key starts an attack, which is when a vacuum's pest tracker goes
 * off. Only watches: the attack itself is left exactly as it was.
 */
@Mixin(Minecraft.class)
public class MinecraftPestFinderMixin {

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void alpaka$pestFinderClick(CallbackInfoReturnable<Boolean> cir) {
        PestFinder.onAttack();
    }
}
