package net.alpaka.addons.features.viewmodel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/**
 * How the held item leaves the hand and the next one arrives when the player switches slots.
 *
 * Vanilla lowers the old item out of view and raises the new one along one straight line, driven
 * by the renderer's hand height: it falls by 0.4 a tick until the swap, then climbs back the same
 * way, about 300 ms in all. Every style here keeps that clock and only changes the path, so the
 * item still changes at the same moment as in vanilla and nothing about the timing of a slot
 * switch is different. The path is given as a function of the equip progress (0 at rest, 1 fully
 * away), the same for the item leaving and the one arriving, so a switch that interrupts another
 * continues from wherever the item is.
 *
 * Only real slot switches get a style. The other times vanilla lowers the hand - the dip after an
 * attack while the item recharges, or while the hands are busy - stay as they are, since a spin
 * or a flip after every hit would be a different feature.
 */
public final class EquipAnimation {
    private EquipAnimation() {}

    /** The styles, in the order the menu lists them. */
    public enum Style {
        VANILLA("Vanilla"),
        RISE_SPIN("Rise + Spin"),
        POP("Pop"),
        FLIP("Flip"),
        SPIRAL("Spiral"),
        SWOOSH("Swoosh"),
        DROP_IN("Drop In"),
        SLAM("Slam");

        public final String title;

        Style(String title) {
            this.title = title;
        }
    }

    public static final String[] STYLE_NAMES = java.util.Arrays.stream(Style.values()).map(s -> s.title).toArray(String[]::new);

    /** Where a hand is in a slot switch. */
    private enum Phase {
        /** No switch: whatever vanilla does to the hand is left alone. */
        NONE,
        /** The old item is on its way out. */
        LEAVING,
        /** The new item is on its way in. */
        ARRIVING
    }

    /** By {@link InteractionHand#ordinal()}. */
    private static final Phase[] PHASES = {Phase.NONE, Phase.NONE};

    /** Set by the menu's test button, picked up on the next tick. */
    private static boolean previewRequested;
    /** A test run is lowering the main hand item, which vanilla would otherwise keep raised. */
    private static boolean previewLowering;

    public static boolean active() {
        AlpakaConfig c = AlpakaConfig.instance;
        return c.itemSizeFeatureEnabled && c.itemEquipAnimationEnabled;
    }

    /** At 0% intensity there is nothing to animate, so the item is swapped at once, as without any re-equip. */
    public static boolean instant() {
        return active() && AlpakaConfig.instance.itemEquipAnimationIntensity <= 0.0f;
    }

    /** Plays the animation once on the item in the main hand, only on this screen; the item stays where it is. */
    public static void preview() {
        if (Minecraft.getInstance().player != null) previewRequested = true;
    }

    /**
     * Called at the end of each renderer tick for both hands; returns the hand height to keep.
     *
     * @param swapPending whether the renderer still shows an item the hand no longer holds, which is
     *                    what makes vanilla lower it
     * @param oldHeight   the height at the start of the tick
     * @param height      the height vanilla has just worked out
     */
    public static float afterTick(InteractionHand hand, boolean swapPending, float oldHeight, float height) {
        int i = hand.ordinal();
        if (!active() || instant()) {
            PHASES[i] = Phase.NONE;
            previewRequested = false;
            previewLowering = false;
            return height;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            if (previewRequested) {
                previewRequested = false;
                if (!swapPending) {
                    previewLowering = true;
                    PHASES[i] = Phase.LEAVING;
                }
            }
            if (previewLowering) {
                if (swapPending) {
                    // A real switch takes over from the test run.
                    previewLowering = false;
                } else {
                    // Lowered at vanilla's pace; once down, vanilla raises it again as after a swap.
                    float lowered = oldHeight - 0.4f;
                    if (lowered >= 0.1f) return lowered;
                    previewLowering = false;
                    PHASES[i] = Phase.ARRIVING;
                    return 0.0f;
                }
            }
        }
        if (swapPending) {
            PHASES[i] = Phase.LEAVING;
        } else if (PHASES[i] == Phase.LEAVING) {
            PHASES[i] = Phase.ARRIVING;
        }
        // Over once the hand has been fully up for a whole tick, so the last frames still interpolate.
        if (PHASES[i] == Phase.ARRIVING && height >= 1.0f && oldHeight >= 1.0f) {
            PHASES[i] = Phase.NONE;
        }
        return height;
    }

    /**
     * Takes the place of vanilla's arm transform for a hand in a slot switch: the rest position,
     * then the style's offset for this point of the switch. Returns false to leave it to vanilla.
     */
    public static boolean apply(PoseStack pose, HumanoidArm arm, float equipProgress) {
        if (!active()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        Phase phase = PHASES[hand.ordinal()];
        if (phase == Phase.NONE) return false;

        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * 0.56f, -0.52f, -0.72f);
        float strength = AlpakaConfig.instance.itemEquipAnimationIntensity / 100.0f;
        transform(pose, style(), phase == Phase.ARRIVING, Mth.clamp(equipProgress, 0.0f, 1.0f), strength, side);
        return true;
    }

    public static Style style() {
        Style[] styles = Style.values();
        return styles[Mth.clamp(AlpakaConfig.instance.itemEquipAnimationStyle, 0, styles.length - 1)];
    }

    /**
     * The offset of one style at equip progress {@code a}, in the camera-aligned space vanilla's
     * arm transform works in (x right, y up, z towards the viewer), mirrored for the left arm.
     */
    static void transform(PoseStack pose, Style style, boolean arriving, float a, float k, int side) {
        // Eased at both ends: the item sets off slowly and settles into the hand instead of stopping dead.
        float e = a * a * (3.0f - 2.0f * a);
        switch (style) {
            case VANILLA -> pose.translate(0.0f, -0.6f * k * a, 0.0f);
            case RISE_SPIN -> {
                // Drops away turning about its own upright axis, and the next one rises turning back.
                pose.translate(-0.12f * side * k * e, -0.6f * k * e, 0.0f);
                aroundItem(pose, side, () -> {
                    pose.mulPose(Axis.YP.rotationDegrees(side * 360.0f * k * e));
                    pose.mulPose(Axis.ZP.rotationDegrees(side * 25.0f * k * e));
                });
            }
            case POP -> {
                // Shrinks to nothing where it is, and the next one grows from a point with a little overshoot.
                float scale = arriving ? 1.0f - k * (1.0f - backOut(1.0f - a)) : 1.0f - k * a * a;
                float s = Math.max(scale, 0.001f);
                pose.translate(0.0f, -0.08f * k * e, 0.0f);
                aroundItem(pose, side, () -> pose.scale(s, s, s));
            }
            case FLIP -> {
                // Tips over towards the viewer as it drops, and the next one flips back up.
                pose.translate(0.0f, -0.6f * k * e, 0.0f);
                aroundItem(pose, side, () -> {
                    pose.mulPose(Axis.XP.rotationDegrees(180.0f * k * e));
                    pose.mulPose(Axis.ZP.rotationDegrees(-side * 30.0f * k * e));
                });
            }
            case SPIRAL -> {
                // Spins flat on the screen while it sinks towards the middle, and spins back up.
                pose.translate(-0.2f * side * k * e, -0.6f * k * e, 0.0f);
                aroundItem(pose, side, () -> pose.mulPose(Axis.ZP.rotationDegrees(side * 360.0f * k * e)));
            }
            case SWOOSH -> {
                // Tips outward and slides off the side of the screen; the next one swings in from there.
                pose.translate(0.9f * side * k * e, -0.2f * k * e, 0.0f);
                aroundItem(pose, side, () -> pose.mulPose(Axis.ZP.rotationDegrees(-side * 85.0f * k * e)));
            }
            case DROP_IN -> {
                // Lifts up and back, tilting outward; the next one drops down into the hand from there.
                pose.translate(0.05f * side * k * e, 0.55f * k * e, -0.35f * k * e);
                aroundItem(pose, side, () -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(-side * 45.0f * k * e));
                    pose.mulPose(Axis.XP.rotationDegrees(-15.0f * k * e));
                });
            }
            case SLAM -> {
                // Raised high and far, upright; the next one comes down fast and lands with a small knock.
                float d = e;
                float knock = 0.0f;
                if (arriving) {
                    float t = 1.0f - a;
                    if (t < 0.75f) {
                        float u = t / 0.75f;
                        d = 1.0f - u * u;
                    } else {
                        d = 0.0f;
                        knock = Mth.sin((float) Math.PI * (t - 0.75f) / 0.25f);
                    }
                }
                float dd = d;
                float kk = knock;
                pose.translate(-0.15f * side * k * dd, 0.45f * k * dd - 0.07f * k * kk, -0.6f * k * dd);
                aroundItem(pose, side, () -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(side * 35.0f * k * dd));
                    pose.mulPose(Axis.XP.rotationDegrees(-25.0f * k * dd + 8.0f * k * kk));
                });
            }
        }
    }

    /**
     * Runs a rotation or scale about the middle of a held item rather than the grip, where the
     * first-person display transform of a handheld item puts it.
     */
    private static void aroundItem(PoseStack pose, int side, Runnable change) {
        float px = side * 0.07f;
        float py = 0.2f;
        float pz = 0.07f;
        pose.translate(px, py, pz);
        change.run();
        pose.translate(-px, -py, -pz);
    }

    /** 0 to 1, overshooting a little past 1 before settling. */
    static float backOut(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }
}
