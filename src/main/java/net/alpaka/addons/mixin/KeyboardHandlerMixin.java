package net.alpaka.addons.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.party.PartyInviteFeature;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Key presses the mod answers before the game sees them: the party prompt's Y and N, and Tab
 * cycling the chat tabs while the chat is held open with Peek Chat.
 *
 * Taken at the very front of the keyboard handler, so a consumed press never reaches a key mapping
 * or the open screen - which is what keeps Tab from also bringing up the player list, and Y or N
 * from doing whatever else they may be bound to while the prompt is up. Each hook decides for
 * itself whether the moment is right; a press that neither wants goes through untouched.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void alpaka$interceptKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != InputConstants.PRESS) return;
        if (PartyInviteFeature.onKey(event.key(), event.scancode())) {
            ci.cancel();
            return;
        }
        if (ChatTabsFeature.onPeekKey(event.key(), event.hasShiftDown())) {
            ci.cancel();
        }
    }
}
