package net.alpaka.addons.mixin;

import net.alpaka.addons.features.slayer.SlayerHudElement;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the slayer HUD be clicked to hold its session clock.
 *
 * The chat screen is the one place the HUD is drawn while a cursor exists, which is what makes this
 * possible at all - the rest of the time the mouse is captured for looking around. The click is only
 * consumed when it actually lands on the HUD, so chat itself behaves exactly as before everywhere
 * else on screen.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (SlayerHudElement.INSTANCE.handleChatClick(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }
}
