package net.alpaka.addons.features.viewmodel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipAnimationTest {

    private static Matrix4f pose(EquipAnimation.Style style, boolean arriving, float a, float k, int side) {
        PoseStack pose = new PoseStack();
        EquipAnimation.transform(pose, style, arriving, a, k, side);
        return new Matrix4f(pose.last().pose());
    }

    private static boolean isIdentity(Matrix4f m) {
        return m.equals(new Matrix4f(), 1.0e-4f);
    }

    /** At rest every style must leave the item exactly where vanilla holds it, or it would jump when the switch ends. */
    @Test
    void everyStyleEndsAtRest() {
        for (EquipAnimation.Style style : EquipAnimation.Style.values()) {
            for (int side : new int[]{1, -1}) {
                assertTrue(isIdentity(pose(style, true, 0.0f, 1.0f, side)), style + " arriving, side " + side);
                assertTrue(isIdentity(pose(style, false, 0.0f, 1.0f, side)), style + " leaving, side " + side);
                assertTrue(isIdentity(pose(style, true, 0.0f, 2.0f, side)), style + " at 200%, side " + side);
            }
        }
    }

    /** No intensity, no motion, wherever in the switch. */
    @Test
    void zeroIntensityDoesNotMove() {
        for (EquipAnimation.Style style : EquipAnimation.Style.values()) {
            for (float a : new float[]{0.25f, 0.5f, 1.0f}) {
                assertTrue(isIdentity(pose(style, true, a, 0.0f, 1)), style + " at " + a);
                assertTrue(isIdentity(pose(style, false, a, 0.0f, 1)), style + " at " + a);
            }
        }
    }

    /** Fully away, the leaving and the arriving item are in the same place, so the swap itself does not jump. */
    @Test
    void leavingAndArrivingMeetWhenAway() {
        for (EquipAnimation.Style style : EquipAnimation.Style.values()) {
            Matrix4f leaving = pose(style, false, 1.0f, 1.0f, 1);
            Matrix4f arriving = pose(style, true, 1.0f, 1.0f, 1);
            assertTrue(leaving.equals(arriving, 1.0e-3f), style.toString());
            assertFalse(isIdentity(leaving), style + " should move the item");
        }
    }

    @Test
    void popOvershootsBeforeSettling() {
        assertTrue(Math.abs(EquipAnimation.backOut(0.0f)) < 1.0e-5f);
        assertTrue(Math.abs(EquipAnimation.backOut(1.0f) - 1.0f) < 1.0e-5f);
        assertTrue(EquipAnimation.backOut(0.8f) > 1.0f);
    }

    /**
     * With Disable Re-equip Animation on as well, the equip animation takes only a slot switch on
     * the main hand, until it is over; an item changing in the same slot stays the option's.
     */
    @Test
    void takesOnlySlotSwitchesFromDisableReequip() {
        AlpakaConfig saved = AlpakaConfig.instance;
        try {
            AlpakaConfig c = new AlpakaConfig();
            c.itemSizeFeatureEnabled = true;
            c.itemNoEquipEnabled = true;
            c.itemEquipAnimationEnabled = true;
            AlpakaConfig.instance = c;

            EquipAnimation.beforeTick(0);
            EquipAnimation.afterTick(InteractionHand.MAIN_HAND, false, 1.0f, 1.0f);
            EquipAnimation.beforeTick(0);
            assertFalse(EquipAnimation.ownsHand(InteractionHand.MAIN_HAND), "same slot: item updated in place");

            EquipAnimation.beforeTick(1);
            assertTrue(EquipAnimation.ownsHand(InteractionHand.MAIN_HAND), "slot switch");
            assertFalse(EquipAnimation.ownsHand(InteractionHand.OFF_HAND), "off hand is never taken");
            EquipAnimation.afterTick(InteractionHand.MAIN_HAND, true, 1.0f, 0.6f);
            EquipAnimation.beforeTick(1);
            EquipAnimation.afterTick(InteractionHand.MAIN_HAND, false, 0.0f, 0.4f);
            assertTrue(EquipAnimation.ownsHand(InteractionHand.MAIN_HAND), "still arriving");
            EquipAnimation.afterTick(InteractionHand.MAIN_HAND, false, 1.0f, 1.0f);
            assertFalse(EquipAnimation.ownsHand(InteractionHand.MAIN_HAND), "switch over");

            c.itemEquipAnimationEnabled = false;
            EquipAnimation.beforeTick(2);
            assertFalse(EquipAnimation.ownsHand(InteractionHand.MAIN_HAND), "animation off");
        } finally {
            AlpakaConfig.instance = saved;
        }
    }
}
