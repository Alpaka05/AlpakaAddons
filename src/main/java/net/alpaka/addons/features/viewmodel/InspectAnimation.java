package net.alpaka.addons.features.viewmodel;

/**
 * The inspect animations: one per kind of held item, each a list of key poses that the hand moves
 * through on a smooth curve.
 *
 * A pose is an offset and a rotation of the hand in camera space, taken about the item's middle, so
 * the item is turned where it is held instead of being brought into view; the animations only use
 * the offset for a small toss. The
 * values are written for the right hand; the left hand gets them mirrored. Turns past 360° are
 * allowed and intended: a key at 360° looks like rest but makes the curve spin through a full turn
 * on the way there, and the last key of a spin stays at 360° so the hand does not unwind.
 *
 * Pure math with no game classes, so the tests can play it through.
 */
public final class InspectAnimation {
    private InspectAnimation() {}

    /** Offset of the hand in blocks (x right, y up, z towards the camera) and its turn in degrees. */
    public record Pose(float x, float y, float z, float pitch, float yaw, float roll) {
        public static final Pose REST = new Pose(0, 0, 0, 0, 0, 0);

        float get(int channel) {
            return switch (channel) {
                case 0 -> x;
                case 1 -> y;
                case 2 -> z;
                case 3 -> pitch;
                case 4 -> yaw;
                default -> roll;
            };
        }

        /** The same pose with every turn brought into -180..180, for blending back to rest the short way. */
        public Pose wrapped() {
            return new Pose(x, y, z, wrap(pitch), wrap(yaw), wrap(roll));
        }

        /** The pose a fraction of the way from this one to another, on a straight line. */
        public Pose lerp(Pose to, float t) {
            return new Pose(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t,
                    pitch + (to.pitch - pitch) * t, yaw + (to.yaw - yaw) * t, roll + (to.roll - roll) * t);
        }

        private static float wrap(float degrees) {
            float d = degrees % 360.0f;
            if (d > 180.0f) d -= 360.0f;
            if (d < -180.0f) d += 360.0f;
            return d;
        }
    }

    /** One key: when in the animation (0..1) and the pose the hand has then. */
    record Key(float time, Pose pose) {}

    private static Key key(float time, float x, float y, float z, float pitch, float yaw, float roll) {
        return new Key(time, new Pose(x, y, z, pitch, yaw, roll));
    }

    private static Key rest(float time) {
        return new Key(time, Pose.REST);
    }

    /** The point an item turns about, from where the hand holds it: about the middle of a held item. */
    private static final float[] ITEM_MIDDLE = {-0.08f, 0.16f, -0.04f};
    private static final float[] GRIP = {0.0f, 0.0f, 0.0f};

    /** The kinds of held item, each with its own animation and length. */
    public enum Kind {
        /**
         * Blade check: the sword turns its flat towards you, flips to the other side,
         * then is twirled once and settles back.
         */
        SWORD(2.6f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.14f, 0.0f, 0.0f, 0.0f, -10, -40, 15),
                key(0.34f, 0.0f, 0.0f, 0.0f, -5, 50, 10),
                key(0.48f, 0.0f, 0.0f, 0.0f, 0, 0, 20),
                key(0.72f, 0.0f, 0.0f, 0.0f, 0, 0, 380),
                key(1.0f, 0, 0, 0, 0, 0, 360)),
        /**
         * Toss and catch: the tool is turned to show its head from both sides, then given a little toss
         * that flips it end over end, and caught.
         */
        TOOL(2.4f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.15f, 0.0f, 0.0f, 0.0f, -10, -40, 0),
                key(0.34f, 0.0f, 0.0f, 0.0f, -5, 35, 15),
                key(0.46f, 0.0f, -0.03f, 0.0f, 10, 10, 10),
                key(0.62f, 0.0f, 0.10f, 0.0f, -190, 0, 5),
                key(0.78f, 0.0f, 0.0f, 0.0f, -360, 0, 0),
                key(1.0f, 0, 0, 0, -360, 0, 0)),
        /** Sight down: the bow is turned to look along it, held, shown from the back, and lowered. */
        BOW(2.4f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.20f, 0.0f, 0.0f, 0.0f, 10, 70, 0),
                key(0.48f, 0.0f, 0.0f, 0.0f, 8, 70, -25),
                key(0.70f, 0.0f, 0.0f, 0.0f, 0, -30, 15),
                rest(1.0f)),
        /** Twirl: the long item is spun twice around its middle like a baton. */
        POLE(2.2f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.14f, 0.0f, 0.0f, 0.0f, 0, -15, 15),
                key(0.70f, 0.0f, 0.0f, 0.0f, 0, -15, 735),
                key(1.0f, 0, 0, 0, 0, 0, 720)),
        /** Turntable: the block or head is turned once all the way round. */
        BLOCK(2.2f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.18f, 0.0f, 0.0f, 0.0f, 15, 0, 0),
                key(0.82f, 0.0f, 0.0f, 0.0f, 15, 360, 0),
                key(1.0f, 0, 0, 0, 0, 360, 0)),
        /** A sniff: the food is tipped towards you and turned a little each way. */
        FOOD(1.8f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.22f, 0.0f, 0.0f, 0.0f, 25, -15, 0),
                key(0.45f, 0.0f, 0.0f, 0.0f, 25, 15, 0),
                key(0.68f, 0.0f, 0.0f, 0.0f, 20, -10, 0),
                rest(1.0f)),
        /** Look over: anything else is turned once to show its back. */
        ITEM(2.2f, ITEM_MIDDLE,
                rest(0.0f),
                key(0.18f, 0.0f, 0.0f, 0.0f, 0, -30, 10),
                key(0.55f, 0.0f, 0.0f, 0.0f, 10, 180, -10),
                key(0.82f, 0.0f, 0.0f, 0.0f, 0, 340, 5),
                key(1.0f, 0, 0, 0, 0, 360, 0)),
        /** Hand check: the empty hand is turned to look at the back of it and then the fist. */
        HAND(1.8f, GRIP,
                rest(0.0f),
                key(0.25f, 0.0f, 0.0f, 0.0f, 0, -25, 30),
                key(0.60f, 0.0f, 0.0f, 0.0f, -15, 20, -15),
                rest(1.0f));

        /** How long the animation runs at speed 1, in seconds. */
        public final float seconds;
        /** The point the hand turns about, from the grip, in blocks (right hand). */
        public final float pivotX;
        public final float pivotY;
        public final float pivotZ;
        final Key[] keys;

        Kind(float seconds, float[] pivot, Key... keys) {
            this.seconds = seconds;
            this.pivotX = pivot[0];
            this.pivotY = pivot[1];
            this.pivotZ = pivot[2];
            this.keys = keys;
        }

        /** The hand's pose at a point of the animation, 0 the start and 1 the end. */
        public Pose sample(float t) {
            return InspectAnimation.sample(keys, t);
        }
    }

    /**
     * The pose at time t on the curve through the keys. Between two keys the curve is a cubic
     * Hermite segment. The slope at a key is the one through its neighbours, which keeps a spin
     * moving through its keys at an even pace; where the hand turns around (a peak or a held value)
     * the slope is zero instead, so the hand comes to a stop there and the curve never overshoots
     * the pose it was given. The first and last keys are at rest with no speed, so the animation
     * starts and ends softly.
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
        float[] v = new float[6];
        for (int c = 0; c < 6; c++) {
            float p0 = a.pose.get(c);
            float p1 = b.pose.get(c);
            float m0 = slope(keys, i, c);
            float m1 = slope(keys, i + 1, c);
            v[c] = h00 * p0 + h10 * h * m0 + h01 * p1 + h11 * h * m1;
        }
        return new Pose(v[0], v[1], v[2], v[3], v[4], v[5]);
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
