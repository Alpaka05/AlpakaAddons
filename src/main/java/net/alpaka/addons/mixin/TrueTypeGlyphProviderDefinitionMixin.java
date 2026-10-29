package net.alpaka.addons.mixin;

import net.alpaka.addons.client.gui.SmoothFontFaces;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the faces of the mod's own fonts as they open; see {@link SmoothFontFaces}. */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class TrueTypeGlyphProviderDefinitionMixin {
    @Shadow @Final private Identifier location;

    @Inject(method = "load", at = @At("HEAD"))
    private void alpaka$markOurFace(CallbackInfoReturnable<?> cir) {
        SmoothFontFaces.beginLoading("alpaka".equals(this.location.getNamespace()));
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void alpaka$unmarkOurFace(CallbackInfoReturnable<?> cir) {
        SmoothFontFaces.endLoading();
    }
}
