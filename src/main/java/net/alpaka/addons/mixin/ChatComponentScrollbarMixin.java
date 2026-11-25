package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.chat.AlpakaChatScroll;
import net.alpaka.addons.features.chat.ChatScrollbar;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla's chat scrollbar, picked out by the order of its fills: faded while the chat opens, and
 * left out where Better Chat draws its own (in ChatComponentMixin).
 * Optional hooks ({@code alpaka.cosmetic.mixins.json}): a chat mod that changes those fills must
 * not keep the game from starting; the worst a missing one does is show both scrollbars.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentScrollbarMixin {
    private static final String EXTRACT_PRIVATE =
        "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";

    /** The scrollbar, drawn at the full height, fades in while the chat unrolls instead of standing there at once. */
    @ModifyArg(
        method = EXTRACT_PRIVATE,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 2),
        index = 4
    )
    private int alpaka$fadeScrollbar(int color) {
        return ((AlpakaChatScroll) (Object) this).alpaka$fadeWhileOpening(color);
    }

    @ModifyArg(
        method = EXTRACT_PRIVATE,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 3),
        index = 4
    )
    private int alpaka$fadeScrollbarHighlight(int color) {
        return ((AlpakaChatScroll) (Object) this).alpaka$fadeWhileOpening(color);
    }

    /** Vanilla's scrollbar - its bar and its lighter edge, the method's last two fills - left out for ours. */
    @WrapOperation(
        method = EXTRACT_PRIVATE,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 2)
    )
    private void alpaka$skipVanillaScrollbar(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!ChatScrollbar.isEnabled()) original.call(access, x0, y0, x1, y1, color);
    }

    @WrapOperation(
        method = EXTRACT_PRIVATE,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 3)
    )
    private void alpaka$skipVanillaScrollbarEdge(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!ChatScrollbar.isEnabled()) original.call(access, x0, y0, x1, y1, color);
    }
}
