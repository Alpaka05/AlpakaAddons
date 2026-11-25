package net.alpaka.addons.features.viewmodel;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The viewmodel's scale on the bare arm. Vanilla draws the first-person arm from the player model's
 * origin, which ends up below the screen (about 0.75 blocks from the fist), so shrinking the arm
 * about that point pulled the hand off screen. Here it is scaled about its fist instead, so the hand
 * stays where it is and only the X/Y/Z offsets move it.
 *
 * The rotation is left out: it is tuned for items, and the arm lies along a different direction, so
 * the same turn points the fist off to the side instead of forward.
 */
public final class EmptyHandViewmodel {
    private EmptyHandViewmodel() {}

    /**
     * The middle of the right fist in the player model's space, in blocks: the arm's pivot
     * (-5, 2, 0) px plus the end of its 12 px box, tilted by the 0.1 rad vanilla gives the
     * first-person arm. The left arm is this mirrored in x.
     */
    static final float FIST_X = -0.43f;
    static final float FIST_Y = 0.74f;

    /** Scales the arm in {@code pose} about its fist; {@code side} is 1 for the right arm, -1 for the left. */
    public static void apply(Matrix4f pose, float side, float scale) {
        Vector3f fist = pose.transformPosition(side * FIST_X, FIST_Y, 0.0f, new Vector3f());
        pose.mulLocal(new Matrix4f()
                .translation(fist)
                .scale(scale)
                .translate(-fist.x, -fist.y, -fist.z));
        // A uniform scale leaves the normals' directions alone.
    }
}
