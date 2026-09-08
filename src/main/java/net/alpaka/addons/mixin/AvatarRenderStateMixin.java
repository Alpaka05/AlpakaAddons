package net.alpaka.addons.mixin;

import net.alpaka.addons.features.playerscale.PlayerScaleRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the player scale on the render state, from extraction to submission. See
 * {@link PlayerScaleRenderState} for why the state is the carrier.
 */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements PlayerScaleRenderState {

    @Unique
    private float alpaka$scaleX = 1.0f;

    @Unique
    private float alpaka$scaleY = 1.0f;

    @Unique
    private float alpaka$scaleZ = 1.0f;

    @Override
    public float alpaka$getScaleX() {
        return alpaka$scaleX;
    }

    @Override
    public float alpaka$getScaleY() {
        return alpaka$scaleY;
    }

    @Override
    public float alpaka$getScaleZ() {
        return alpaka$scaleZ;
    }

    @Override
    public void alpaka$setScale(float x, float y, float z) {
        this.alpaka$scaleX = x;
        this.alpaka$scaleY = y;
        this.alpaka$scaleZ = z;
    }
}
