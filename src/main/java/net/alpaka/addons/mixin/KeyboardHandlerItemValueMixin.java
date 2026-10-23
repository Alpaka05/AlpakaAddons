package net.alpaka.addons.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.features.itemvalue.ItemValueFeature;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Item Value key acts once per press. A held key repeats, and a menu takes a repeat as another
 * press, which would open and close the popup in turn; so its repeats are dropped at the front of
 * the keyboard handler while a menu is open.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerItemValueMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void alpaka$itemValueKeyOncePerPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.REPEAT && ItemValueFeature.swallowsRepeat(event)) ci.cancel();
    }
}
