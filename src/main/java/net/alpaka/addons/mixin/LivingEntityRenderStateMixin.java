package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.PestRenderState;
import net.alpaka.addons.features.slayer.BossHighlightRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** See {@link BossHighlightRenderState} and {@link PestRenderState}. */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements BossHighlightRenderState, PestRenderState {

    @Unique
    private int alpaka$highlightTint;

    @Unique
    private float alpaka$highlightWhite;

    @Unique
    private boolean alpaka$pestHead;

    @Override
    public int alpaka$getHighlightTint() {
        return alpaka$highlightTint;
    }

    @Override
    public float alpaka$getHighlightWhite() {
        return alpaka$highlightWhite;
    }

    @Override
    public void alpaka$setHighlight(int tint, float white) {
        this.alpaka$highlightTint = tint;
        this.alpaka$highlightWhite = white;
    }

    @Override
    public boolean alpaka$isPestHead() {
        return alpaka$pestHead;
    }

    @Override
    public void alpaka$setPestHead(boolean pestHead) {
        this.alpaka$pestHead = pestHead;
    }
}
