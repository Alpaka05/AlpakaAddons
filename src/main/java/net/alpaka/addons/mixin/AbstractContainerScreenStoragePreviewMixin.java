package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.storagepreview.StoragePreviewFeature;
import net.alpaka.addons.features.storagepreview.StoragePreviewFreeze;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Gives a backpack or ender chest icon in the Storage menu the remembered content as its tooltip
 * image, and keeps the menu's tooltips out of the way of one pinned with the freeze key. Only how
 * the tooltips look; see {@link StoragePreviewFeature} and {@link StoragePreviewFreeze}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenStoragePreviewMixin {

    /** While a Storage Preview is pinned with the freeze key, the menu's own tooltips stay hidden. */
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideUnderPinnedPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (StoragePreviewFreeze.hidesMenuTooltips((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @WrapOperation(
            method = "extractTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getTooltipImage()Ljava/util/Optional;")
    )
    private Optional<TooltipComponent> alpaka$storagePreview(ItemStack stack, Operation<Optional<TooltipComponent>> original) {
        return StoragePreviewFeature.tooltipImage((AbstractContainerScreen<?>) (Object) this, stack, original.call(stack));
    }
}
