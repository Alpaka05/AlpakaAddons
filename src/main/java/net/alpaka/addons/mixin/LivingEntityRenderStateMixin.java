package net.alpaka.addons.mixin;

import net.alpaka.addons.features.slayer.BossHighlightRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** See {@link BossHighlightRenderState}. */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements BossHighlightRenderState {

    @Unique
    private int alpaka$highlightTint;

    @Override
    public int alpaka$getHighlightTint() {
        return alpaka$highlightTint;
    }

    @Override
    public void alpaka$setHighlightTint(int tint) {
        this.alpaka$highlightTint = tint;
    }
}
