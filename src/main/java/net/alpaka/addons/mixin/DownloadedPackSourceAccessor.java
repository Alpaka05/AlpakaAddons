package net.alpaka.addons.mixin;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pack manager behind the pack source, for Cache Resource Pack's startup restore. */
@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {
    @Accessor("manager")
    ServerPackManager alpaka$manager();
}
