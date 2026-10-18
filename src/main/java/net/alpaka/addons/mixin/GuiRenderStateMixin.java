package net.alpaka.addons.mixin;

import net.alpaka.addons.client.gui.AlpakaGuiNode;
import net.alpaka.addons.client.gui.AlpakaGuiUnderlay;
import net.alpaka.addons.compat.GnetumCompat;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The layer under every other GUI element; see {@link AlpakaGuiUnderlay}.
 *
 * The strata are drawn in list order, so the layer is a stratum moved to the front of the list.
 * Vanilla's own {@code nextStratum} makes it (the node class is private), and points the current
 * layer at it; forgetting the last element's bounds makes the next element look for its layer from
 * the top stratum again, as it would have without this one. The blur marker, an index into the
 * list, moves along with the strata it counts.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin implements AlpakaGuiUnderlay {

    @Shadow @Final private List<Object> strata;
    @Shadow private int firstStratumAfterBlur;
    @Shadow private ScreenRectangle lastElementBounds;

    @Shadow public abstract void nextStratum();

    @Unique
    private Object alpaka$underlay;

    @Override
    public void alpaka$addUnderlay(GuiElementRenderState state) {
        if (alpaka$underlay == null) {
            nextStratum();
            alpaka$underlay = strata.removeLast();
            strata.addFirst(alpaka$underlay);
            if (firstStratumAfterBlur != Integer.MAX_VALUE) firstStratumAfterBlur++;
            lastElementBounds = null;
        }
        ((AlpakaGuiNode) alpaka$underlay).alpaka$addElement(state);
    }

    /** An underlay element Gnetum held back during its pass arrives here; see {@link GnetumCompat#deferUnderlay}. */
    @Inject(method = "addGuiElement", at = @At("HEAD"), cancellable = true)
    private void alpaka$takeDeferredUnderlay(GuiElementRenderState state, CallbackInfo ci) {
        if (GnetumCompat.takeUnderlay(state)) {
            alpaka$addUnderlay(state);
            ci.cancel();
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void alpaka$resetUnderlay(CallbackInfo ci) {
        alpaka$underlay = null;
    }
}
