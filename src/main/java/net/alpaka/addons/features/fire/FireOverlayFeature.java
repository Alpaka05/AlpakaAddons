package net.alpaka.addons.features.fire;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Hides or softens the fire Minecraft draws for burning things.
 *
 * Two separate effects: the flames wrapped around a burning entity in the world (mobs, other
 * players, and yourself in third person), and the two fire sprites covering the bottom of your
 * screen while you burn in first person. Both are purely cosmetic - the burning, its damage and the
 * fire sound are untouched.
 *
 * The entity flames are dropped on the render state, the same flag vanilla rebuilds every frame
 * from {@code isOnFire()}, so nothing about the entity itself changes.
 *
 * The first-person fire can also be lowered and stilled. Vanilla animates it through the block
 * atlas, the same slot every fire block in the world reads, so freezing that slot would still the
 * world's fire too. Instead the still fire is drawn from the fire texture file itself, which holds
 * every frame stacked top to bottom, showing only the first.
 */
public final class FireOverlayFeature {

    private FireOverlayFeature() {
    }

    public static void applyEntity(EntityRenderState state) {
        if (AlpakaConfig.instance.hideEntityFire) {
            state.displayFireAnimation = false;
        }
    }

    /** Whether the first-person fire is drawn at all: at zero opacity it is skipped outright. */
    public static boolean showFirstPersonFire() {
        return AlpakaConfig.instance.firstPersonFireOpacity > 0.0f;
    }

    /** Scales the alpha of vanilla's first-person fire color by the opacity setting. */
    public static int firstPersonFireColor(int argb) {
        float opacity = Math.clamp(AlpakaConfig.instance.firstPersonFireOpacity, 0.0f, 1.0f);
        if (opacity >= 1.0f) return argb;
        int alpha = Math.round((argb >>> 24) * opacity);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * How far vanilla's fire reaches above the bottom of the screen, in the units of the translate
     * the quads are placed with (measured: the flames end about at the screen's middle). The height
     * setting moves the quads down by a share of this.
     */
    private static final float VANILLA_FIRE_REACH = 0.35f;

    /** Moves the fire quads down so they reach the set share of vanilla's height. */
    public static float firstPersonFireY(float vanillaY) {
        float height = Math.clamp(AlpakaConfig.instance.firstPersonFireHeight, 0.0f, 1.0f);
        return vanillaY - (1.0f - height) * VANILLA_FIRE_REACH;
    }

    public static boolean staticFirstPersonFire() {
        return AlpakaConfig.instance.firstPersonFireStatic;
    }

    /** The texture file behind an atlas sprite: block/fire_1 is textures/block/fire_1.png. */
    public static Identifier spriteTexture(TextureAtlasSprite sprite) {
        Identifier name = sprite.contents().name();
        return name.withPath("textures/" + name.getPath() + ".png");
    }

    /**
     * The bottom texture coordinate of the texture file's first frame. Animation frames are as
     * tall as the texture is wide, so a 16x512 strip holds 32 of them.
     */
    public static float firstFrameV(TextureAtlasSprite sprite) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(spriteTexture(sprite));
        int width = texture.getTexture().getWidth(0);
        int height = texture.getTexture().getHeight(0);
        return width <= 0 || height <= width ? 1.0f : (float) width / height;
    }
}
