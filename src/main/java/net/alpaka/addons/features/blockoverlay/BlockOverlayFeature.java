package net.alpaka.addons.features.blockoverlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class BlockOverlayFeature {

    private static BlockPos lastTargetPos = null;
    private static long targetStartTime = 0L;
    private static long lastRenderTime = 0L;

    public static boolean isPlantBlock(net.minecraft.world.level.block.state.BlockState blockState) {
        if (blockState == null) return false;
        net.minecraft.world.level.block.Block block = blockState.getBlock();
        if (block instanceof net.minecraft.world.level.block.BushBlock ||
            block instanceof net.minecraft.world.level.block.LeavesBlock ||
            block instanceof net.minecraft.world.level.block.FlowerBlock ||
            block instanceof net.minecraft.world.level.block.TallGrassBlock ||
            block instanceof net.minecraft.world.level.block.DoublePlantBlock ||
            block instanceof net.minecraft.world.level.block.CropBlock ||
            block instanceof net.minecraft.world.level.block.SaplingBlock ||
            block instanceof net.minecraft.world.level.block.SugarCaneBlock ||
            block instanceof net.minecraft.world.level.block.CactusBlock ||
            block instanceof net.minecraft.world.level.block.MushroomBlock ||
            block instanceof net.minecraft.world.level.block.NetherWartBlock) {
            return true;
        }
        // No SAPLINGS tag any more since 26.2; the SaplingBlock check above covers them.
        return blockState.is(net.minecraft.tags.BlockTags.FLOWERS) ||
               blockState.is(net.minecraft.tags.BlockTags.CROPS) ||
               blockState.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /**
     * Submits the overlay for the targeted block in place of vanilla's outline.
     *
     * Called from the hook on LevelRenderer.submitBlockOutline, so this runs in the level's submit
     * phase: nothing is drawn here. The quads go to the collector as custom geometry and the feature
     * renderer draws them later in the frame, in the pass the vanilla outline would have used. The
     * pose is copied when the geometry is submitted, which is why it can be popped straight after,
     * exactly as vanilla does with its own outline.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, BlockOutlineRenderState renderState) {
        BlockPos pos = renderState.pos();

        // No plant test here: LevelRendererMixin already made it before calling in, and doing it
        // again meant a second block-state lookup plus four tag lookups every frame.

        VoxelShape shape = renderState.shape();
        if (shape == null || shape.isEmpty()) return;

        List<AABB> boxes = boxesOf(shape);
        if (boxes.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastRenderTime > 120L || lastTargetPos == null || !lastTargetPos.equals(pos)) {
            lastTargetPos = pos;
            targetStartTime = now;
        }
        lastRenderTime = now;

        float fadeInFactor = 1.0f;
        if (AlpakaConfig.instance.blockFadeInEnabled) {
            long elapsed = now - targetStartTime;
            int duration = Math.max(10, AlpakaConfig.instance.blockFadeInDurationMs);
            float progress = Math.min(1.0f, (float) elapsed / (float) duration);
            // Quadratic ease-in curve for a dramatic, sleek fade-in effect
            fadeInFactor = progress * progress;
        }

        // Resolved once and shared by both passes. It used to be worked out separately for the
        // outline and the fill, which ran the HSB conversion twice a frame and allocated a
        // three-float array each time for the same colour.
        boolean chromaEnabled = AlpakaConfig.instance.blockChromaEnabled;
        int chromaRgb = chromaEnabled ? chromaRgb(AlpakaConfig.instance.blockChromaSpeed) : 0;

        final float outlineR, outlineG, outlineB, outlineA;
        if (chromaEnabled) {
            outlineR = ((chromaRgb >> 16) & 0xFF) / 255.0f;
            outlineG = ((chromaRgb >> 8) & 0xFF) / 255.0f;
            outlineB = (chromaRgb & 0xFF) / 255.0f;
        } else {
            outlineR = ((AlpakaConfig.instance.blockOutlineColor >> 16) & 0xFF) / 255.0f;
            outlineG = ((AlpakaConfig.instance.blockOutlineColor >> 8) & 0xFF) / 255.0f;
            outlineB = (AlpakaConfig.instance.blockOutlineColor & 0xFF) / 255.0f;
        }
        outlineA = (((AlpakaConfig.instance.blockOutlineColor >> 24) & 0xFF) / 255.0f) * fadeInFactor;

        final float fillR, fillG, fillB, fillA;
        if (chromaEnabled) {
            fillR = ((chromaRgb >> 16) & 0xFF) / 255.0f;
            fillG = ((chromaRgb >> 8) & 0xFF) / 255.0f;
            fillB = (chromaRgb & 0xFF) / 255.0f;
        } else {
            fillR = ((AlpakaConfig.instance.blockFillColor >> 16) & 0xFF) / 255.0f;
            fillG = ((AlpakaConfig.instance.blockFillColor >> 8) & 0xFF) / 255.0f;
            fillB = (AlpakaConfig.instance.blockFillColor & 0xFF) / 255.0f;
        }
        fillA = (((AlpakaConfig.instance.blockFillColor >> 24) & 0xFF) / 255.0f) * fadeInFactor;

        final boolean drawFill = AlpakaConfig.instance.blockFillEnabled;
        final boolean drawOutline = AlpakaConfig.instance.blockOutlineEnabled;
        if (!drawFill && !drawOutline) return;

        // Read now rather than inside the callback: the callback runs later in the frame, and the
        // values used there should be the ones the render type below was chosen with.
        final float edgeThickness = AlpakaConfig.instance.blockOutlineThickness * 0.002f;
        RenderType renderType = BlockOverlayRenderTypes.current(AlpakaConfig.instance.blockIgnoreDepth);

        // Same camera-relative translation vanilla applies to its own outline, so the shape's boxes
        // can be used in block-local coordinates.
        Vec3 cam = camera.pos;
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            // 1. Fill
            if (drawFill) {
                for (AABB box : boxes) {
                    drawFill(buffer, pose, box.inflate(0.001d), fillR, fillG, fillB, fillA);
                }
            }
            // 2. Outline as 3D cuboids along each edge
            if (drawOutline) {
                for (AABB box : boxes) {
                    drawOutlineAsQuads(buffer, pose, box.inflate(0.002d), edgeThickness, outlineR, outlineG, outlineB, outlineA);
                }
            }
        });
        poseStack.popPose();
    }

    /** The current chroma colour as packed RGB. Returned packed so nothing has to be allocated. */
    private static int chromaRgb(float speed) {
        double timeSec = (double) System.currentTimeMillis() / 1000.0 * speed;
        float hue = (float) (timeSec % 1.0);
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }

    private static VoxelShape cachedShape = null;
    private static List<AABB> cachedBoxes = java.util.List.of();

    /**
     * The shape's boxes, reusing the last result while the shape is unchanged.
     *
     * {@code toAabbs()} builds a fresh list on every call, and the targeted block usually stays the
     * same for many frames in a row - and block states share their shape instances, so even looking
     * at a different block of the same kind hits this.
     */
    private static List<AABB> boxesOf(VoxelShape shape) {
        if (shape != cachedShape) {
            cachedShape = shape;
            cachedBoxes = shape.toAabbs();
        }
        return cachedBoxes;
    }

    private static void drawOutlineAsQuads(VertexConsumer buffer, PoseStack.Pose pose, AABB box, float t, float r, float g, float b, float a) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        // 4 bottom horizontal edges along X
        drawEdge(buffer, pose, x1, y1, z1, x2, y1, z1, t, r, g, b, a);
        drawEdge(buffer, pose, x1, y1, z2, x2, y1, z2, t, r, g, b, a);
        // 4 top horizontal edges along X
        drawEdge(buffer, pose, x1, y2, z1, x2, y2, z1, t, r, g, b, a);
        drawEdge(buffer, pose, x1, y2, z2, x2, y2, z2, t, r, g, b, a);

        // 4 bottom horizontal edges along Z
        drawEdge(buffer, pose, x1, y1, z1, x1, y1, z2, t, r, g, b, a);
        drawEdge(buffer, pose, x2, y1, z1, x2, y1, z2, t, r, g, b, a);
        // 4 top horizontal edges along Z
        drawEdge(buffer, pose, x1, y2, z1, x1, y2, z2, t, r, g, b, a);
        drawEdge(buffer, pose, x2, y2, z1, x2, y2, z2, t, r, g, b, a);

        // 4 vertical edges along Y
        drawEdge(buffer, pose, x1, y1, z1, x1, y2, z1, t, r, g, b, a);
        drawEdge(buffer, pose, x2, y1, z1, x2, y2, z1, t, r, g, b, a);
        drawEdge(buffer, pose, x1, y1, z2, x1, y2, z2, t, r, g, b, a);
        drawEdge(buffer, pose, x2, y1, z2, x2, y2, z2, t, r, g, b, a);
    }

    private static void drawEdge(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float t, float r, float g, float b, float a) {
        float minX, minY, minZ, maxX, maxY, maxZ;
        if (x1 != x2) { // along X
            minX = Math.min(x1, x2) - t;
            maxX = Math.max(x1, x2) + t;
            minY = y1 - t;
            maxY = y1 + t;
            minZ = z1 - t;
            maxZ = z1 + t;
        } else if (y1 != y2) { // along Y
            minX = x1 - t;
            maxX = x1 + t;
            minY = Math.min(y1, y2) - t;
            maxY = Math.max(y1, y2) + t;
            minZ = z1 - t;
            maxZ = z1 + t;
        } else { // along Z
            minX = x1 - t;
            maxX = x1 + t;
            minY = y1 - t;
            maxY = y1 + t;
            minZ = Math.min(z1, z2) - t;
            maxZ = Math.max(z1, z2) + t;
        }

        // Draw the 6 faces of the cuboid edge
        quad(buffer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(buffer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        quad(buffer, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        quad(buffer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(buffer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        quad(buffer, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
    }

    private static void drawFill(VertexConsumer buffer, PoseStack.Pose pose, AABB box, float r, float g, float b, float a) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        quad(buffer, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(buffer, pose, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(buffer, pose, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(buffer, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(buffer, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        quad(buffer, pose, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a) {
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }
}
