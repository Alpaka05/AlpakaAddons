package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.features.tooltip.ScrollableTooltipsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements AlpakaGuiElementSink {

    @Shadow @Final private GuiRenderState guiRenderState;

    @Shadow public abstract Matrix3x2fStack pose();

    /**
     * A mirror of the extractor's scissor stack. The real one is a package-private nested class
     * that this mixin cannot name, so the same rectangles are tracked here: each enableScissor
     * pushes the new area intersected with the one in force, exactly as vanilla's stack does.
     */
    @Unique
    private final Deque<ScreenRectangle> alpaka$scissors = new ArrayDeque<>();

    @Inject(method = "enableScissor", at = @At("TAIL"))
    private void alpaka$trackEnableScissor(int x0, int y0, int x1, int y1, CallbackInfo ci) {
        ScreenRectangle area = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(pose());
        ScreenRectangle parent = alpaka$scissors.peekLast();
        if (parent != null) {
            ScreenRectangle clipped = parent.intersection(area);
            area = clipped != null ? clipped : ScreenRectangle.empty();
        }
        alpaka$scissors.addLast(area);
    }

    @Inject(method = "disableScissor", at = @At("TAIL"))
    private void alpaka$trackDisableScissor(CallbackInfo ci) {
        alpaka$scissors.pollLast();
    }

    @Override
    public void alpaka$submitElement(GuiElementRenderState state) {
        guiRenderState.addGuiElement(state);
    }

    @Override
    public ScreenRectangle alpaka$currentScissor() {
        return alpaka$scissors.peekLast();
    }

    /**
     * Every tooltip is placed through this one call, with the screen size and the tooltip's own
     * size at hand - which is all the scrollable tooltips need to tell an oversized one apart and
     * shift it.
     */
    @WrapOperation(
            method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;")
    )
    private Vector2ic alpaka$scrollTallTooltip(ClientTooltipPositioner positioner, int guiWidth, int guiHeight,
                                               int x, int y, int width, int height, Operation<Vector2ic> original) {
        Vector2ic vanilla = original.call(positioner, guiWidth, guiHeight, x, y, width, height);
        return ScrollableTooltipsFeature.position(vanilla, guiHeight, width, height);
    }
}
