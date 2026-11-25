package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.hideeffects.HideEffects;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hide Effects reports the inventory's effect list as absent; the HUD's effect icons still go by
 * vanilla's answer, so they don't pop up in the corner while the inventory is open.
 */
@Mixin(Hud.class)
public class HudHiddenEffectsMixin {

    @WrapOperation(method = "extractEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;showsActiveEffects()Z"))
    private boolean alpaka$vanillaEffectsAnswer(Screen screen, Operation<Boolean> original) {
        return HideEffects.askVanilla(() -> original.call(screen));
    }
}
