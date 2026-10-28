package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.storagepreview.StoragePreviewFeature;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Gives a backpack or ender chest icon in the Storage menu the remembered content as its tooltip
 * image. Only how the tooltip looks; see {@link StoragePreviewFeature}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenStoragePreviewMixin {

    @WrapOperation(
            method = "extractTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getTooltipImage()Ljava/util/Optional;")
    )
    private Optional<TooltipComponent> alpaka$storagePreview(ItemStack stack, Operation<Optional<TooltipComponent>> original) {
        return StoragePreviewFeature.tooltipImage((AbstractContainerScreen<?>) (Object) this, stack, original.call(stack));
    }
}
