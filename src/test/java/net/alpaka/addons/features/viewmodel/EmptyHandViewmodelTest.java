package net.alpaka.addons.features.viewmodel;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyHandViewmodelTest {

    /** Vanilla's first-person arm pose (renderPlayerArm at rest) for the right (1) or left (-1) arm. */
    private static Matrix4f vanillaArm(float f) {
        return new Matrix4f()
                .translate(f * 0.64000005f, -0.6f, -0.71999997f)
                .rotateY((float) Math.toRadians(f * 45.0f))
                .translate(f * -1.0f, 3.6f, 3.5f)
                .rotateZ((float) Math.toRadians(f * 120.0f))
                .rotateX((float) Math.toRadians(200.0f))
                .rotateY((float) Math.toRadians(f * -135.0f))
                .translate(f * 5.6f, 0.0f, 0.0f);
    }

    private static Vector3f fist(Matrix4f pose, float side) {
        return pose.transformPosition(side * EmptyHandViewmodel.FIST_X, EmptyHandViewmodel.FIST_Y, 0.0f, new Vector3f());
    }

    /** The fist must stay put however the arm is scaled; that is what keeps it on screen. */
    @Test
    void fistStaysWhereItIs() {
        for (float side : new float[]{1, -1}) {
            Vector3f before = fist(vanillaArm(side), side);
            for (float scale : new float[]{0.3f, 0.5f, 1.0f, 2.0f}) {
                Matrix4f pose = vanillaArm(side);
                EmptyHandViewmodel.apply(pose, side, scale);
                Vector3f after = fist(pose, side);
                assertTrue(before.distance(after) < 1.0e-4f, "side " + side + " scale " + scale + ": " + after);
            }
        }
    }

    /** Scaling never turns the arm: it keeps pointing the way vanilla points it. */
    @Test
    void scaleKeepsTheArmsDirection() {
        for (float side : new float[]{1, -1}) {
            Matrix4f vanilla = vanillaArm(side);
            Matrix4f pose = vanillaArm(side);
            EmptyHandViewmodel.apply(pose, side, 0.4f);
            Vector3f along = vanilla.transformDirection(0, 1, 0, new Vector3f()).normalize();
            Vector3f alongAfter = pose.transformDirection(0, 1, 0, new Vector3f()).normalize();
            assertTrue(along.distance(alongAfter) < 1.0e-5f, "side " + side + ": " + alongAfter);
        }
    }

    /** The fist point really is the hand: in front of the camera, low on its own side of the screen. */
    @Test
    void fistIsInView() {
        for (float side : new float[]{1, -1}) {
            Vector3f fist = fist(vanillaArm(side), side);
            assertTrue(fist.z < -0.5f, "in front: " + fist);
            assertTrue(fist.x * side > 0.0f && fist.y < 0.0f, "lower " + (side > 0 ? "right" : "left") + ": " + fist);
            // Inside a 70° vertical field of view.
            assertTrue(Math.abs(fist.y / fist.z) < Math.tan(Math.toRadians(35)), "on screen: " + fist);
        }
    }

    /** Scale shrinks the arm toward the fist: the shoulder end comes closer by exactly that factor. */
    @Test
    void scaleShrinksTowardTheFist() {
        Matrix4f pose = vanillaArm(1);
        Vector3f fist = fist(pose, 1);
        Vector3f shoulder = pose.transformPosition(-5 / 16f, 2 / 16f, 0, new Vector3f());
        EmptyHandViewmodel.apply(pose, 1, 0.5f);
        Vector3f shoulderAfter = pose.transformPosition(-5 / 16f, 2 / 16f, 0, new Vector3f());
        assertEquals(shoulder.distance(fist) * 0.5f, shoulderAfter.distance(fist), 1.0e-4f);
    }

    /** At scale 1 the arm is exactly vanilla's. */
    @Test
    void defaultsLeaveTheArmAlone() {
        Matrix4f pose = vanillaArm(1);
        EmptyHandViewmodel.apply(pose, 1, 1);
        assertTrue(pose.equals(vanillaArm(1), 1.0e-5f));
    }
}
