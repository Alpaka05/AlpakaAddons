package net.alpaka.addons.mixin;

import net.alpaka.addons.features.damagetags.DamageTagFeature;
import net.alpaka.addons.features.damagetags.SplashVerdict;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every armor stand a place for its damage splash verdict; see {@link SplashVerdict}. */
@Mixin(ArmorStand.class)
public abstract class ArmorStandSplashMixin implements SplashVerdict {
    @Unique private Component alpaka$splashName;
    @Unique private DamageTagFeature.Splash alpaka$splash;

    @Override
    public Component alpaka$splashName() {
        return this.alpaka$splashName;
    }

    @Override
    public DamageTagFeature.Splash alpaka$splash() {
        return this.alpaka$splash;
    }

    @Override
    public void alpaka$setSplash(Component name, DamageTagFeature.Splash splash) {
        this.alpaka$splashName = name;
        this.alpaka$splash = splash;
    }
}
