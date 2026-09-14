package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.tooltip.ScrollableTooltipsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {

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
