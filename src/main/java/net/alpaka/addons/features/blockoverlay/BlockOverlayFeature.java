package net.alpaka.addons.features.blockoverlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class BlockOverlayFeature {
    public static boolean isRenderingBlockOverlay = false;
    public static boolean ignoreDepthActive = false;

    public static void render(PoseStack poseStack, double camX, double camY, double camZ, BlockOutlineRenderState renderState) {
        isRenderingBlockOverlay = true;
        ignoreDepthActive = AlpakaConfig.instance.blockIgnoreDepth;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            BlockPos pos = renderState.pos();
            VoxelShape shape = renderState.shape();
            if (shape == null || shape.isEmpty()) return;

            List<AABB> boxes = shape.toAabbs();
            if (boxes.isEmpty()) return;

            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            PoseStack.Pose pose = poseStack.last();

            float outlineR, outlineG, outlineB, outlineA;
            if (AlpakaConfig.instance.blockChromaEnabled) {
                float[] chroma = getChromaColor(AlpakaConfig.instance.blockChromaSpeed);
                outlineR = chroma[0];
                outlineG = chroma[1];
                outlineB = chroma[2];
                outlineA = ((AlpakaConfig.instance.blockOutlineColor >> 24) & 0xFF) / 255.0f;
            } else {
                outlineR = ((AlpakaConfig.instance.blockOutlineColor >> 16) & 0xFF) / 255.0f;
                outlineG = ((AlpakaConfig.instance.blockOutlineColor >> 8) & 0xFF) / 255.0f;
                outlineB = (AlpakaConfig.instance.blockOutlineColor & 0xFF) / 255.0f;
                outlineA = ((AlpakaConfig.instance.blockOutlineColor >> 24) & 0xFF) / 255.0f;
            }

            float fillR, fillG, fillB, fillA;
            if (AlpakaConfig.instance.blockChromaEnabled) {
                float[] chroma = getChromaColor(AlpakaConfig.instance.blockChromaSpeed);
                fillR = chroma[0];
                fillG = chroma[1];
                fillB = chroma[2];
                fillA = ((AlpakaConfig.instance.blockFillColor >> 24) & 0xFF) / 255.0f;
            } else {
                fillR = ((AlpakaConfig.instance.blockFillColor >> 16) & 0xFF) / 255.0f;
                fillG = ((AlpakaConfig.instance.blockFillColor >> 8) & 0xFF) / 255.0f;
                fillB = (AlpakaConfig.instance.blockFillColor & 0xFF) / 255.0f;
                fillA = ((AlpakaConfig.instance.blockFillColor >> 24) & 0xFF) / 255.0f;
            }

            double relX = (double) pos.getX() - camX;
            double relY = (double) pos.getY() - camY;
            double relZ = (double) pos.getZ() - camZ;

            // 1. Render Fill
            if (AlpakaConfig.instance.blockFillEnabled) {
                RenderType fillRenderType = RenderTypes.debugQuads();
                VertexConsumer fillBuffer = bufferSource.getBuffer(fillRenderType);
                for (AABB box : boxes) {
                    drawFill(fillBuffer, pose, box.inflate(0.001d), relX, relY, relZ, fillR, fillG, fillB, fillA);
                }
                bufferSource.endBatch(fillRenderType);
            }

            // 2. Render Outline as 3D Cuboids
            if (AlpakaConfig.instance.blockOutlineEnabled) {
                RenderType outlineRenderType = RenderTypes.debugQuads();
                VertexConsumer outlineBuffer = bufferSource.getBuffer(outlineRenderType);
                for (AABB box : boxes) {
                    drawOutlineAsQuads(outlineBuffer, pose, box.inflate(0.002d), relX, relY, relZ, outlineR, outlineG, outlineB, outlineA);
                }
                bufferSource.endBatch(outlineRenderType);
            }
        } finally {
            isRenderingBlockOverlay = false;
            ignoreDepthActive = false;
        }
    }

    private static float[] getChromaColor(float speed) {
        double timeSec = (double) System.currentTimeMillis() / 1000.0 * speed;
        float hue = (float) (timeSec % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    private static void drawOutlineAsQuads(VertexConsumer buffer, PoseStack.Pose pose, AABB box, double minX, double minY, double minZ, float r, float g, float b, float a) {
        float x1 = (float) (box.minX + minX);
        float y1 = (float) (box.minY + minY);
        float z1 = (float) (box.minZ + minZ);
        float x2 = (float) (box.maxX + minX);
        float y2 = (float) (box.maxY + minY);
        float z2 = (float) (box.maxZ + minZ);

        float t = AlpakaConfig.instance.blockOutlineThickness * 0.002f;

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

    private static void drawFill(VertexConsumer buffer, PoseStack.Pose pose, AABB box, double minX, double minY, double minZ, float r, float g, float b, float a) {
        float x1 = (float) (box.minX + minX);
        float y1 = (float) (box.minY + minY);
        float z1 = (float) (box.minZ + minZ);
        float x2 = (float) (box.maxX + minX);
        float y2 = (float) (box.maxY + minY);
        float z2 = (float) (box.maxZ + minZ);

        quad(buffer, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(buffer, pose, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(buffer, pose, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(buffer, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(buffer, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        quad(buffer, pose, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a) {
        buffer.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose.pose(), x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(pose.pose(), x4, y4, z4).setColor(r, g, b, a);
    }
}
