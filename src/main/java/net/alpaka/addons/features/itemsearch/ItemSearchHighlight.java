package net.alpaka.addons.features.itemsearch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Where the item picked in Item Search is, once its container is open: its slots outlined in the
 * accent color in any menu, and an island chest outlined in the world, for a minute or until it is
 * opened.
 */
public final class ItemSearchHighlight {
    private ItemSearchHighlight() {}

    private static final long DURATION_MS = 60_000L;

    private static String target = null;
    private static long targetUntil = 0L;
    /** Each stack's group key, worked out once rather than every frame; a stack is replaced, not changed, when a slot changes. */
    private static final Map<ItemStack, String> keys = new WeakHashMap<>();
    private static final Set<BlockPos> chests = new LinkedHashSet<>();
    private static long chestsUntil = 0L;

    /** Marks the item, by {@link ItemSearchResults#groupKey}, in every menu for a while. */
    static void item(String groupKey) {
        target = groupKey;
        keys.clear();
        targetUntil = System.currentTimeMillis() + DURATION_MS;
    }

    /** Outlines the chest (and the other half of a large one) in the world for a while. */
    static void chest(BlockPos pos, BlockPos pos2) {
        chests.clear();
        chests.add(pos);
        if (pos2 != null) chests.add(pos2);
        chestsUntil = System.currentTimeMillis() + DURATION_MS;
    }

    /** A chest was opened: if it is the one outlined, it has been found. */
    static void chestOpened(BlockPos pos) {
        if (chests.contains(pos)) chests.clear();
    }

    /** Draws the outline over every slot holding the marked item. */
    static void drawSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        if (target == null) return;
        if (System.currentTimeMillis() > targetUntil) {
            target = null;
            return;
        }
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        int left = a.alpaka$leftPos();
        int top = a.alpaka$topPos();
        int color = AlpakaStyle.accent();
        int fill = (color & 0x00FFFFFF) | 0x50000000;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !target.equals(keys.computeIfAbsent(stack, ItemSearchResults::groupKey))) continue;
            int sx = left + slot.x;
            int sy = top + slot.y;
            graphics.fill(sx, sy, sx + 16, sy + 16, fill);
            graphics.outline(sx - 1, sy - 1, 18, 18, color);
        }
    }

    /** The outlined chests, as see-through boxes with a brighter edge. */
    static void drawChests(LevelRenderContext context) {
        if (chests.isEmpty()) return;
        if (System.currentTimeMillis() > chestsUntil) {
            chests.clear();
            return;
        }
        var camera = context.levelState().cameraRenderState.pos;
        double camX = camera.x;
        double camY = camera.y;
        double camZ = camera.z;
        List<BlockPos> snapshot = new ArrayList<>(chests);
        int color = AlpakaStyle.accent();
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
            for (BlockPos pos : snapshot) {
                // A chest is a little smaller than its block.
                float x0 = (float) (pos.getX() + 0.05 - camX);
                float y0 = (float) (pos.getY() - camY);
                float z0 = (float) (pos.getZ() + 0.05 - camZ);
                box(buffer, pose, x0, y0, z0, x0 + 0.9f, y0 + 0.9f, z0 + 0.9f, r, g, b, 0.35f);
            }
        });
    }

    private static void box(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0,
                            float x1, float y1, float z1, float r, float g, float b, float a) {
        quad(buffer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(buffer, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(buffer, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(buffer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad(buffer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(buffer, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    /** A quad in both windings, so it shows from inside the box as well. */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
        buffer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
        buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
    }
}
