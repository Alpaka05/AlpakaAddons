package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatPeekFeature;
import net.alpaka.addons.features.cursor.KeepCursorPositionFeature;
import net.alpaka.addons.features.tooltip.ScrollableTooltipsFeature;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double xoffset, double yoffset, CallbackInfo info) {
        if (ZoomFeature.isZooming()) {
            if (yoffset != 0) {
                ZoomFeature.onMouseScroll(yoffset);
            }
            info.cancel();
            return;
        }
        // Scrolls the held-open chat instead of the hotbar.
        if (ChatPeekFeature.onScroll(yoffset)) {
            info.cancel();
            return;
        }
        // Scrolls an oversized tooltip instead of the screen under it.
        var screen = Minecraft.getInstance().gui.screen();
        if (screen != null && !(screen instanceof ScrollableTooltipsFeature.KeepsWheel) && ScrollableTooltipsFeature.onScroll(yoffset)) {
            info.cancel();
        }
    }

    /** Keep Cursor Position: note where the cursor was before grabbing centres it. */
    @Inject(method = "grabMouse", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MouseHandler;xpos:D", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void alpaka$rememberCursor(CallbackInfo ci) {
        KeepCursorPositionFeature.onGrab(this.xpos, this.ypos, Util.getMillis());
    }

    /**
     * Keep Cursor Position: release at the remembered spot instead of the centre. GLFW only moves a
     * disabled cursor virtually and, on leaving that mode, puts it back where the grab found it
     * (the centre), so the position is set a second time once the cursor is visible again.
     */
    @WrapOperation(method = "releaseMouse", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"))
    private void alpaka$keepCursor(Window window, int mode, double x, double y, Operation<Void> original) {
        double[] kept = AlpakaConfig.instance.keepCursorPositionEnabled
                ? KeepCursorPositionFeature.restoreFor(
                        this.minecraft.gui.screen() instanceof AbstractContainerScreen<?>, Util.getMillis(),
                        window.getScreenWidth(), window.getScreenHeight())
                : null;
        if (kept == null) {
            original.call(window, mode, x, y);
            return;
        }
        this.xpos = kept[0];
        this.ypos = kept[1];
        original.call(window, mode, kept[0], kept[1]);
        original.call(window, mode, kept[0], kept[1]);
    }
}
