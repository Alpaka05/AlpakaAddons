package net.alpaka.addons.mixin;

import net.alpaka.addons.features.chat.ChatScrollbar;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mouse for Better Chat's scrollbar; see {@link ChatScrollbar}. The chat screen does not
 * handle drags or releases itself, so those two are added here, falling through to the screen's
 * own handling whenever the bar is not being dragged.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenScrollbarMixin extends Screen {

    protected ChatScreenScrollbarMixin(Component title) {
        super(title);
    }

    /** After the command suggestions have had the click, so an open popup keeps it. */
    @Inject(
            method = "mouseClicked",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonEvent;button()I", ordinal = 0),
            cancellable = true
    )
    private void alpaka$grabScrollbar(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 0 && ChatScrollbar.press(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 0 && ChatScrollbar.drag(event.y())) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && ChatScrollbar.release()) return true;
        return super.mouseReleased(event);
    }
}
