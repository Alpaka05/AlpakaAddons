package net.alpaka.addons.features.cosmetics;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/**
 * Draws the rare drop animation's item in 3D, turning, onto the screen: a picture-in-picture
 * renderer, as vanilla draws the player in the inventory. The GUI's own item drawing is flat and
 * fixed, so the item is drawn here into a texture of its own each frame, at the screen's full
 * resolution, and that texture goes on the screen.
 */
public final class RareDropItemRenderer extends PictureInPictureRenderer<RareDropItemRenderer.State> {

    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * The item to draw, turned by {@code spin} around the upright, tipped back by {@code tilt} and
     * leant sideways by {@code roll}, all in radians, and moved {@code offsetX} GUI pixels right and
     * {@code offsetY} down from the middle of its box; {@code scale} is how big one block is, in GUI
     * pixels.
     */
    public record State(ItemStackRenderState item, float spin, float tilt, float roll, float offsetX, float offsetY,
                        int x0, int y0, int x1, int y1, float scale,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State(ItemStackRenderState item, float spin, float tilt, float roll, float offsetX, float offsetY,
                     int x0, int y0, int x1, int y1, float scale) {
            this(item, spin, tilt, roll, offsetX, offsetY, x0, y0, x1, y1, scale, null,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
        }
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        // Moved inside the picture rather than by moving the picture, whose box can only sit on whole
        // GUI pixels: the flight and the slow bob then glide instead of stepping a pixel at a time.
        poseStack.translate(state.offsetX() / state.scale(), state.offsetY() / state.scale(), 0.0f);
        // The texture's y runs down, as the screen's does; the item's model has it up.
        poseStack.scale(1.0f, -1.0f, -1.0f);
        // The item frame's view (FIXED) faces the item away from whoever looks at it, as the frame
        // turns it round again: half a turn more shows its front, a head's face and a flat item the
        // way round it is in the inventory.
        poseStack.rotate(new Quaternionf().rotateZ(state.roll()).rotateX(state.tilt()).rotateY(state.spin() + (float) Math.PI));
        Minecraft.getInstance().gameRenderer.lighting().setupFor(
                state.item().usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        state.item().submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    /** The item turns around the middle of its picture; vanilla's renderers stand theirs on the bottom edge. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    @Override
    protected String getTextureLabel() {
        return "alpaka rare drop item";
    }
}
