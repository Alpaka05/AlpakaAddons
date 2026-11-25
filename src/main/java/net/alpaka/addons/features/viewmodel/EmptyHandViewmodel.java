package net.alpaka.addons.features.viewmodel;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The viewmodel's rotation and scale on the bare arm. Vanilla draws the first-person arm from the
 * player model's origin, which ends up below the screen (about 0.75 blocks from the fist), so
 * turning or shrinking the arm about that point swung the hand off screen. Here the arm is turned
 * and scaled about its fist instead, on the camera's axes like a held item, so the hand stays where
 * it is and only the X/Y/Z offsets move it.
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

    /**
     * Turns and scales the arm in {@code pose}/{@code normal} about its fist. {@code side} is 1 for
     * the right arm and -1 for the left; yaw and roll are mirrored for the left one as for items.
     */
    public static void apply(Matrix4f pose, Matrix3f normal, float side,
                             float pitchDeg, float yawDeg, float rollDeg, float scale) {
        Vector3f fist = pose.transformPosition(side * FIST_X, FIST_Y, 0.0f, new Vector3f());
        Quaternionf turn = new Quaternionf()
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateY((float) Math.toRadians(side * yawDeg))
                .rotateZ((float) Math.toRadians(side * rollDeg));
        Matrix4f around = new Matrix4f()
                .translation(fist)
                .rotate(turn)
                .scale(scale)
                .translate(-fist.x, -fist.y, -fist.z);
        pose.mulLocal(around);
        // A uniform scale leaves the normals' directions alone; only the turn changes them.
        normal.set(new Matrix3f().set(turn).mul(normal));
    }
}
