package net.alpaka.addons.mixin;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the mod's own shaders from crashing the game under "Improved Transparency".
 *
 * With that option on, 26.3 draws everything that blends in the world through a set of three
 * pipelines per render type, and throws on a render type that has none. The aura's shader has no
 * such set (the see-through block overlay has, built from vanilla's), so its geometry is left out
 * of those passes instead: the aura is not drawn while the option is on, and the game goes on.
 * Only the mod's own render types; another mod's missing set still shows as the error it is.
 */
@Mixin(PreparedRenderType.class)
public class PreparedRenderTypeMixin {
    @Shadow @Final private String name;
    @Shadow @Final private OitPipelineSet oitPipelineSet;

    @Inject(method = "drawFromBufferOit", at = @At("HEAD"), cancellable = true)
    private void alpaka$skipWithoutOitPipelines(StagedVertexBuffer.ExecuteInfo info, OitStage stage, RenderPass renderPass, CallbackInfo ci) {
        if (this.oitPipelineSet == null && this.name.startsWith("alpaka_")) ci.cancel();
    }
}
