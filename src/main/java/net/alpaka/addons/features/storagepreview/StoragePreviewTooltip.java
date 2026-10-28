package net.alpaka.addons.features.storagepreview;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A remembered backpack or ender chest page, as a tooltip image: its slots, nine to a row. */
public record StoragePreviewTooltip(List<ItemStack> items) implements TooltipComponent {
}
