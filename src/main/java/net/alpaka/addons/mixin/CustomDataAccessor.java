package net.alpaka.addons.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tag inside an item's custom data, read without the full copy {@code copyTag()} makes; see {@link net.alpaka.addons.utils.ItemData}. */
@Mixin(CustomData.class)
public interface CustomDataAccessor {
    @Accessor("tag")
    CompoundTag alpaka$tag();
}
