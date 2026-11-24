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
 * way, about 300 ms in all. That is too quick to tell one path from another, so the Speed setting
 * scales that 0.4 step (see {@link #stepScale}): at the default 50% a switch takes about twice as
 * long. The item is still swapped where vanilla swaps it, once the hand is all the way down, so
 * the styles only change the path. The path is given as a function of the equip progress (0 at rest, 1 fully
 * away), the same for the item leaving and the one arriving, so a switch that interrupts another
 * continues from wherever the item is.
 *
 * Only real slot switches get a style. The other times vanilla lowers the hand - the dip after an
 * attack while the item recharges, or while the hands are busy - stay as they are, since a spin
 * or a flip after every hit would be a different feature.
 *
 * Disable Re-equip Animation keeps the hand up through all of those, and through an item that
 * changes in the same slot. A slot switch (or the test button) is the one thing it leaves to this
 * animation while the animation is on (see {@link #ownsHand}), so both can be used together: the
 * styled switch, and no dip after hits or when SkyBlock updates the item.
 */
public final class EquipAnimation {
    private EquipAnimation() {}

    /** The styles, in the order the menu lists them. */
    public enum Style {
        VANILLA("Vanilla"),
        RISE_SPIN("Rise + Spin"),
        POP("Pop"),
        SWOOSH("Swoosh");

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
    /** The hotbar slot at the last renderer tick, to tell a slot switch from an item that changed in place. */
    private static int lastSelectedSlot = -1;
    /** The main hand is in a switch the player made by changing slots. */
    private static boolean slotSwitch;

    public static boolean active() {
        AlpakaConfig c = AlpakaConfig.instance;
        return c.itemSizeFeatureEnabled && c.itemEquipAnimationEnabled;
    }

    /** At 0% intensity there is nothing to animate, so the item is swapped at once, as without any re-equip. */
    public static boolean instant() {
        return active() && AlpakaConfig.instance.itemEquipAnimationIntensity <= 0.0f;
    }

    /** The Speed setting as a factor of vanilla's pace, never so low that a switch drags on. */
    static float speed() {
        return Mth.clamp(AlpakaConfig.instance.itemEquipAnimationSpeed / 100.0f, 0.2f, 2.0f);
    }

    /**
     * How much of vanilla's 0.4 step a hand may move this tick. Only a hand in a slot switch (or the
     * test run) is slowed down; the dip after an attack keeps vanilla's pace.
     *
     * @param swapPending whether the renderer still shows an item the hand no longer holds
     */
    public static float stepScale(InteractionHand hand, boolean swapPending) {
        if (!active() || instant()) return 1.0f;
        boolean switching = swapPending || PHASES[hand.ordinal()] != Phase.NONE
                || (hand == InteractionHand.MAIN_HAND && (previewLowering || previewRequested));
        return switching ? speed() : 1.0f;
    }

    /** Called at the start of each renderer tick, before vanilla decides whether to swap the item at once. */
    public static void beforeTick(int selectedSlot) {
        if (lastSelectedSlot >= 0 && selectedSlot != lastSelectedSlot && active() && !instant()) slotSwitch = true;
        lastSelectedSlot = selectedSlot;
    }

    /**
     * Whether this animation plays on the hand right now, so Disable Re-equip Animation has to leave
     * it alone: only the main hand, in a slot switch or a test run. Everything else (an item updated
     * in the same slot, the dip after an attack, the off hand) is still skipped by that option.
     */
    public static boolean ownsHand(InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !active() || instant()) return false;
        return slotSwitch || previewRequested || previewLowering || PHASES[hand.ordinal()] != Phase.NONE;
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
            slotSwitch = false;
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
                    float lowered = oldHeight - 0.4f * speed();
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
        // A switch to an identical item is swapped at once by vanilla and never starts a phase.
        if (hand == InteractionHand.MAIN_HAND && PHASES[i] == Phase.NONE && !previewLowering) slotSwitch = false;
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
            case SWOOSH -> {
                // Sweeps out to the side, tipping over until it lies flat, and the next one sweeps in
                // from low at the side and swings upright, like a sword being drawn. The sideways part
                // runs ahead of the drop (e against e squared), so the path is an arc: the item leaves
                // sideways first and then dips out of view, and the next one rises first and then
                // swings in.
                pose.translate(0.55f * side * k * e, -0.35f * k * e * e, 0.0f);
                aroundItem(pose, side, () -> {
                    pose.mulPose(Axis.ZP.rotationDegrees(-side * 80.0f * k * e));
                    pose.mulPose(Axis.YP.rotationDegrees(side * 20.0f * k * e));
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
