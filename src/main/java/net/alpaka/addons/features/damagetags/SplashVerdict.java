package net.alpaka.addons.features.damagetags;

import net.minecraft.network.chat.Component;

/**
 * The damage splash verdict an armor stand carries: the name it was read from and the splash it
 * showed, so the per-frame entity hooks read each name once instead of once a frame. Added to
 * ArmorStand by ArmorStandSplashMixin; see {@link DamageTagFeature}.
 */
public interface SplashVerdict {
    /** The name the verdict was read from, or null while none was. */
    Component alpaka$splashName();

    DamageTagFeature.Splash alpaka$splash();

    void alpaka$setSplash(Component name, DamageTagFeature.Splash splash);
}
