package net.alpaka.addons.features.viewmodel;

/**
 * The inspect animations. Each kind of held item has a few variants, and a press of the key plays
 * one of them; each variant is a list of key poses the item moves through on a smooth curve.
 *
 * A pose turns the item about its own middle, where it is held, rather than bringing it into
 * view: pitch, yaw and roll turn it about the camera's axes (roll spins it in the screen's plane,
 * pitch flips it end over end), and spin turns it about its own length, the blade of a sword. The
 * offset is only for small hops. The values are written for the right hand; the left hand gets them
 * mirrored. Turns past 360° are allowed and intended: a key at 360° looks like rest but makes the
 * curve spin through a full turn on the way there, and the last key of a spin stays at 360° so the
 * item does not unwind. A key a little past a turn's end (370° before 360°) makes the item swing
 * past and settle, as if caught.
 *
 * Pure math with no game classes, so the tests can play it through.
 */
public final class InspectAnimation {
    private InspectAnimation() {}

    static final int CHANNELS = 7;

    /** Offset in blocks (x right, y up, z towards the camera) and turns in degrees. */
    public record Pose(float x, float y, float z, float pitch, float yaw, float roll, float spin) {
        public static final Pose REST = new Pose(0, 0, 0, 0, 0, 0, 0);

        float get(int channel) {
            return switch (channel) {
                case 0 -> x;
                case 1 -> y;
                case 2 -> z;
                case 3 -> pitch;
                case 4 -> yaw;
                case 5 -> roll;
                default -> spin;
            };
        }

        /** The same pose with every turn brought into -180..180, for blending back to rest the short way. */
        public Pose wrapped() {
            return new Pose(x, y, z, wrap(pitch), wrap(yaw), wrap(roll), wrap(spin));
        }

        /** The pose a fraction of the way from this one to another, on a straight line. */
        public Pose lerp(Pose to, float t) {
            return new Pose(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t,
                    pitch + (to.pitch - pitch) * t, yaw + (to.yaw - yaw) * t,
                    roll + (to.roll - roll) * t, spin + (to.spin - spin) * t);
        }

        private static float wrap(float degrees) {
            float d = degrees % 360.0f;
            if (d > 180.0f) d -= 360.0f;
            if (d < -180.0f) d += 360.0f;
            return d;
        }
    }

    /** One key: when in the animation (0..1) and the pose the item has then. */
    record Key(float time, Pose pose) {}

    /** A key that only turns the item. */
    private static Key key(float time, float pitch, float yaw, float roll, float spin) {
        return new Key(time, new Pose(0, 0, 0, pitch, yaw, roll, spin));
    }

    /** A key that also lifts (or lowers) the item a little. */
    private static Key hop(float time, float y, float pitch, float yaw, float roll, float spin) {
        return new Key(time, new Pose(0, y, 0, pitch, yaw, roll, spin));
    }

    private static Key rest(float time) {
        return new Key(time, Pose.REST);
    }

    /** One way of showing an item off: its name, its length at speed 1 and its keys. */
    public record Variant(String name, float seconds, Key... keys) {
        /** The item's pose at a point of the animation, 0 the start and 1 the end. */
        public Pose sample(float t) {
            return InspectAnimation.sample(keys, t);
        }
    }

    // The variants, after the inspects of shooters: quick turns between short holds.

    /** Tipped forward and flipped end over end once, with a little hop, then caught. */
    static final Variant FLIP = new Variant("Flip", 1.4f,
            rest(0.0f),
            hop(0.18f, -0.02f, -70, 0, 0, 0),
            hop(0.32f, 0.01f, -110, 0, 0, 0),
            hop(0.60f, 0.07f, -290, 0, 0, 0),
            hop(0.78f, -0.01f, -372, 0, 0, 0),
            key(1.0f, -360, 0, 0, 0));

    /** Tilted to the side to show its flat, then spun once about its own length on the way back. */
    static final Variant BLADE_SPIN = new Variant("Blade Spin", 1.5f,
            rest(0.0f),
            key(0.22f, 0, 0, -55, 0),
            key(0.42f, 0, 0, -60, 0),
            key(0.78f, 0, 0, -4, 365),
            key(1.0f, 0, 0, 0, 360));

    /** Spun once all the way round in the screen's plane, like a propeller. */
    static final Variant PROPELLER = new Variant("Propeller", 1.1f,
            rest(0.0f),
            key(0.14f, 0, 0, 25, 0),
            key(0.66f, 0, 0, 330, 20),
            key(0.84f, 0, 0, 368, 0),
            key(1.0f, 0, 0, 360, 0));

    /** Turned about its own length to show its edge, held, and turned back past rest. */
    static final Variant EDGE_CHECK = new Variant("Edge Check", 1.3f,
            rest(0.0f),
            key(0.22f, 0, 0, 0, 90),
            key(0.50f, -6, 0, 4, 96),
            key(0.76f, 0, 0, 0, -18),
            rest(1.0f));

    /** A key with the item drawn a little up and closer, for looking at it more closely. */
    private static Key near(float time, float pitch, float yaw, float roll, float spin) {
        return new Key(time, new Pose(0, 0.03f, 0.06f, pitch, yaw, roll, spin));
    }

    /**
     * A closer look: drawn a little nearer and held at one view after another, slower than the
     * other variants. First its face, tipped towards you, then its other face, then from above
     * along its length.
     */
    static final Variant CLOSE_LOOK = new Variant("Close Look", 3.4f,
            rest(0.0f),
            near(0.12f, 20, 0, 0, 80),
            near(0.28f, 24, 0, 5, 95),
            near(0.42f, 10, 0, 0, -80),
            near(0.56f, 14, 0, -5, -95),
            near(0.70f, -35, 0, 0, 0),
            near(0.84f, -30, 0, 10, 10),
            rest(1.0f));

    /** Spun twice in the screen's plane, for long items held like a baton. */
    static final Variant TWIRL = new Variant("Twirl", 1.6f,
            rest(0.0f),
            key(0.12f, 0, 0, 25, 0),
            key(0.70f, 0, 0, 690, 0),
            key(0.86f, 0, 0, 728, 0),
            key(1.0f, 0, 0, 720, 0));

    /** Turned once about the upright axis, like on a turntable, slightly tipped towards you. */
    static final Variant TURNTABLE = new Variant("Turntable", 1.6f,
            rest(0.0f),
            key(0.15f, 12, 20, 0, 0),
            key(0.80f, 12, 350, 0, 0),
            key(1.0f, 0, 360, 0, 0));

    /** Tossed up a little and turned over once in the air. */
    static final Variant TUMBLE = new Variant("Tumble", 1.3f,
            rest(0.0f),
            hop(0.18f, -0.02f, 15, 0, 0, 0),
            hop(0.50f, 0.08f, -180, 40, 0, 0),
            hop(0.78f, -0.01f, -368, 0, 0, 0),
            key(1.0f, -360, 0, 0, 0));

    /** Tipped towards you and turned a little each way. */
    static final Variant SNIFF = new Variant("Sniff", 1.5f,
            rest(0.0f),
            key(0.22f, 25, -20, 0, 0),
            key(0.45f, 25, 20, 0, 0),
            key(0.68f, 20, -12, 0, 0),
            rest(1.0f));

    /** The empty hand turned to look at its back and then the fist. */
    static final Variant HAND_CHECK = new Variant("Hand Check", 1.6f,
            rest(0.0f),
            key(0.25f, 0, -25, 30, 0),
            key(0.60f, -15, 20, -15, 0),
            rest(1.0f));

    /** The empty hand rolled over at the wrist and back. */
    static final Variant WRIST_ROLL = new Variant("Wrist Roll", 1.3f,
            rest(0.0f),
            key(0.30f, 0, 0, 55, 0),
            key(0.55f, 10, 0, 50, 0),
            rest(1.0f));

    /**
     * Where a held item's middle is and which way its length runs, in the frame the item is drawn
     * in (after applyItemArmTransform and the viewmodel's changes), for the right hand. Flat items and tools all use the item/generated
     * first-person transform: moved by (1.13, 3.2, 1.13)/16, turned by (0, -90, 25). The middle is
     * where that move ends, and the length is the texture's diagonal (1, 1, 0) turned the same way.
     */
    private static final float[] HELD_MIDDLE = {1.13f / 16.0f, 3.2f / 16.0f, 1.13f / 16.0f};
    private static final float[] HELD_LENGTH = {0.0f, 0.9397f, 0.3420f};
    /** Blocks sit centred on the hand (their first-person transform does not move them) and stand upright. */
    private static final float[] BLOCK_MIDDLE = {0.0f, 0.0f, 0.0f};
    private static final float[] UPRIGHT = {0.0f, 1.0f, 0.0f};

    /** The kinds of held item, each with its variants. */
    public enum Kind {
        SWORD(HELD_MIDDLE, HELD_LENGTH, FLIP, BLADE_SPIN, PROPELLER, EDGE_CHECK, CLOSE_LOOK),
        TOOL(HELD_MIDDLE, HELD_LENGTH, FLIP, BLADE_SPIN, PROPELLER, CLOSE_LOOK),
        BOW(HELD_MIDDLE, HELD_LENGTH, EDGE_CHECK, PROPELLER, CLOSE_LOOK),
        POLE(HELD_MIDDLE, HELD_LENGTH, TWIRL, BLADE_SPIN, FLIP, CLOSE_LOOK),
        BLOCK(BLOCK_MIDDLE, UPRIGHT, TURNTABLE, TUMBLE, PROPELLER, CLOSE_LOOK),
        FOOD(HELD_MIDDLE, HELD_LENGTH, SNIFF, EDGE_CHECK, BLADE_SPIN, CLOSE_LOOK),
        ITEM(HELD_MIDDLE, HELD_LENGTH, BLADE_SPIN, FLIP, PROPELLER, EDGE_CHECK, CLOSE_LOOK),
        HAND(new float[3], UPRIGHT, HAND_CHECK, WRIST_ROLL);

        /** The point the item turns about, from where the hand is, in blocks (right hand). */
        public final float pivotX;
        public final float pivotY;
        public final float pivotZ;
        /** The direction of the item's length, which spin turns about (right hand, unit length). */
        public final float axisX;
        public final float axisY;
        public final float axisZ;
        public final Variant[] variants;

        Kind(float[] pivot, float[] axis, Variant... variants) {
            this.pivotX = pivot[0];
            this.pivotY = pivot[1];
            this.pivotZ = pivot[2];
            this.axisX = axis[0];
            this.axisY = axis[1];
            this.axisZ = axis[2];
            this.variants = variants;
        }
    }

    /**
     * The pose at time t on the curve through the keys. Between two keys the curve is a cubic
     * Hermite segment. The slope at a key is the one through its neighbours, which keeps a spin
     * moving through its keys at an even pace; where the item turns around (a peak or a held value)
     * the slope is zero instead, so it comes to a stop there and the curve never overshoots the pose
     * it was given. The first and last keys are at rest with no speed, so the animation starts and
     * ends softly.
     */
    static Pose sample(Key[] keys, float t) {
        if (t <= keys[0].time) return keys[0].pose;
        int last = keys.length - 1;
        if (t >= keys[last].time) return keys[last].pose;
        int i = 0;
        while (t > keys[i + 1].time) i++;
        Key a = keys[i];
        Key b = keys[i + 1];
        float h = b.time - a.time;
        float s = (t - a.time) / h;
        float s2 = s * s;
        float s3 = s2 * s;
        float h00 = 2 * s3 - 3 * s2 + 1;
        float h10 = s3 - 2 * s2 + s;
        float h01 = -2 * s3 + 3 * s2;
        float h11 = s3 - s2;
        float[] v = new float[CHANNELS];
        for (int c = 0; c < CHANNELS; c++) {
            float p0 = a.pose.get(c);
            float p1 = b.pose.get(c);
            float m0 = slope(keys, i, c);
            float m1 = slope(keys, i + 1, c);
            v[c] = h00 * p0 + h10 * h * m0 + h01 * p1 + h11 * h * m1;
        }
        return new Pose(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
    }

    private static float slope(Key[] keys, int i, int channel) {
        if (i == 0 || i == keys.length - 1) return 0.0f;
        float before = keys[i].pose.get(channel) - keys[i - 1].pose.get(channel);
        float after = keys[i + 1].pose.get(channel) - keys[i].pose.get(channel);
        if (before == 0.0f || after == 0.0f || (before > 0) != (after > 0)) return 0.0f;
        float m = (keys[i + 1].pose.get(channel) - keys[i - 1].pose.get(channel))
                / (keys[i + 1].time - keys[i - 1].time);
        // Capped at three times the steepness of either side (Fritsch-Carlson), past which a
        // segment would bulge beyond its own end values.
        float limit = 3.0f * Math.min(Math.abs(before) / (keys[i].time - keys[i - 1].time),
                Math.abs(after) / (keys[i + 1].time - keys[i].time));
        return Math.copySign(Math.min(Math.abs(m), limit), m);
    }
}
