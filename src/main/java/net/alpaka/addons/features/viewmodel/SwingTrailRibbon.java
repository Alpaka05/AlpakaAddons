package net.alpaka.addons.features.viewmodel;

/**
 * The swing trail of one hand: a slash drawn in the air behind the item's tip, the way a comic
 * draws a sword stroke - a band that is as wide as the outer part of the blade where the blade is
 * now and runs out to a point where the stroke began.
 *
 * A swing has two halves - the strike, when the item leaves its resting place, and the way back.
 * Only the strike draws a slash: on the way back the item passes through the one it just drew.
 * The strike is told from the way back by distance alone: as long as the tip keeps getting
 * further from where it rests, it is striking. That holds for every swing the viewmodel settings
 * can produce, whatever its drift, arc and speed.
 *
 * While the strike lasts the slash hangs on the blade. When it is over the slash stays where it
 * is, closes at the front too - a crescent now - and is used up from its tail as its time runs
 * out. A swing chained onto the last one draws a slash of its own while the old one fades, so a
 * few are kept.
 *
 * The outer edge of the slash is the way the tip went; the inner edge lies on the blade, some of
 * the way to the grip end. The few frames a strike takes are joined by a curve, not by straight
 * lines, or the slash would show every frame as a corner.
 *
 * Positions are whatever space the caller draws the item in; this class only measures in it.
 */
final class SwingTrailRibbon {

    /** A frame gap longer than this means the item was not drawn for a while; the trail restarts. */
    private static final long GAP_RESET_NANOS = 250_000_000L;

    /** The strike is over once the tip has come back this share of the way it went out. */
    private static final float RETURN_SHARE = 0.03f;

    /** Slashes kept per hand: the one being drawn and the ones still fading. */
    static final int MAX_STROKES = 3;

    /** Frames kept of one strike. */
    static final int MAX_SAMPLES = 48;

    /** A frame is only kept if the tip moved at least this many texels since the last one kept. */
    private static final float SPACING = 0.75f;

    /** Pieces the curve between two kept frames is cut into. */
    static final int PIECES = 6;

    /** A slash shorter than this many texels is not drawn: the item barely moved. */
    private static final float MIN_TEXELS = 2.0f;

    /** At a width of 100 %, the slash reaches this share of the way from the tip to the grip end. */
    static final float BLADE_SHARE = 0.36f;

    /** The outer share of the slash's width that is drawn solid; the rest fades out towards the grip. */
    private static final float CORE_SHARE = 0.55f;

    /** How fast the slash narrows towards its tail: 1 is a straight taper, more a thinner tail. */
    private static final float TAPER = 1.8f;

    /** The share of the slash's length its front closes over once the strike has ended. */
    private static final float FRONT_SHARE = 0.4f;

    /** Floats per vertex: x, y, z and opacity (0..1). The slash is written as quads of four. */
    static final int VERTEX_FLOATS = 4;
    private static final int POINTS = (MAX_SAMPLES - 1) * PIECES + 1;
    /** Most floats {@link #build} can write: two quads between every two points of every slash. */
    static final int MAX_FLOATS = MAX_STROKES * (POINTS - 1) * 2 * 4 * VERTEX_FLOATS;

    /** One slash: the frames of its strike, oldest first, tip and grip end in six floats each. */
    private static final class Stroke {
        final float[] points = new float[MAX_SAMPLES * 6];
        final long[] times = new long[MAX_SAMPLES];
        int count;
        /** Whether the strike is still under way and the slash hangs on the blade. */
        boolean attached;
        long releasedAt;

        void add(float[] point, long time, float spacing) {
            // A frame that hardly moved on from the one before is replaced, not kept.
            if (count >= 2 && distance(points, (count - 2) * 6, points, (count - 1) * 6) < spacing) {
                count--;
            }
            if (count == MAX_SAMPLES) {
                System.arraycopy(points, 6, points, 0, (count - 1) * 6);
                System.arraycopy(times, 1, times, 0, count - 1);
                count--;
            }
            System.arraycopy(point, 0, points, count * 6, 6);
            times[count] = time;
            count++;
        }
    }

    private final Stroke[] strokes = new Stroke[MAX_STROKES];
    /** The slash being drawn, or null between strikes. */
    private Stroke active;

    // The swing.
    private final float[] rest = new float[6];
    private boolean restKnown;
    private boolean swinging;
    /**
     * Set when the swing was lost sight of part-way - the item changed, or was not drawn for a
     * while. What is left of that swing draws nothing: where it began is not known, so its way back
     * would pass for a strike. The next swing clears it.
     */
    private boolean lostTrack;
    private float lastProgress;
    private float furthest;
    private long lastNanos = Long.MIN_VALUE / 2;

    // Scratch for build(): the points of one slash along its curve.
    private final float[] fine = new float[POINTS * 6];
    private final long[] fineTimes = new long[POINTS];
    private final float[] lengths = new float[POINTS];

    SwingTrailRibbon() {
        for (int i = 0; i < MAX_STROKES; i++) {
            strokes[i] = new Stroke();
        }
    }

    /** Drops every slash and what is known of the swing, as after the item was not drawn. */
    void clear() {
        for (Stroke stroke : strokes) {
            stroke.count = 0;
            stroke.attached = false;
        }
        active = null;
        restKnown = false;
        lostTrack = true;
    }

    /** Ends the strike under way and leaves its slash to fade, as after a change of item. */
    void interrupt(long now) {
        release(now);
        restKnown = false;
        lostTrack = true;
    }

    /** Whether there is nothing to draw. */
    boolean isEmpty() {
        for (Stroke stroke : strokes) {
            if (stroke.count >= 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Records one frame.
     *
     * @param now       frame time
     * @param progress  the swing progress the hand is drawn at, 0 while it rests
     * @param point     the tip and the grip end of the item this frame, six floats
     * @param texel     the size one pixel of the item's texture is drawn at
     * @param lifeNanos how long the slash stays after it was drawn
     */
    void update(long now, float progress, float[] point, float texel, long lifeNanos) {
        if (now - lastNanos > GAP_RESET_NANOS) {
            clear();
        }
        expire(now, lifeNanos);

        if (progress <= 0.0f) {
            System.arraycopy(point, 0, rest, 0, 6);
            restKnown = true;
            swinging = false;
            lostTrack = false;
            release(now);
        } else if (lostTrack && !(swinging && progress < lastProgress)) {
            swinging = true;
        } else {
            lostTrack = false;
            if (!restKnown) {
                // A swing chained onto the one lost track of: it starts where the item rests.
                System.arraycopy(point, 0, rest, 0, 6);
                restKnown = true;
            }
            // A swing that starts while another is playing shows as progress jumping back.
            if (!swinging || progress < lastProgress) {
                release(now);
                active = free();
                active.count = 0;
                active.attached = true;
                furthest = 0.0f;
                // Every swing starts and ends at rest, so that is where the item was a frame ago.
                active.add(rest, now - lastNanos <= GAP_RESET_NANOS ? lastNanos : now, 0.0f);
            }
            swinging = true;
            if (active != null) {
                float distance = distance(point, 0, rest, 0);
                if (distance < furthest * (1.0f - RETURN_SHARE)) {
                    release(now);
                } else {
                    furthest = Math.max(furthest, distance);
                    active.add(point, now, SPACING * texel);
                }
            }
        }
        lastProgress = progress;
        lastNanos = now;
    }

    private void release(long now) {
        if (active != null) {
            active.attached = false;
            active.releasedAt = now;
            active = null;
        }
    }

    /** Drops the slashes whose time is up. */
    private void expire(long now, long lifeNanos) {
        for (Stroke stroke : strokes) {
            if (stroke != active && stroke.count > 0 && now - stroke.times[stroke.count - 1] >= lifeNanos) {
                stroke.count = 0;
            }
        }
    }

    /** A slash to draw the next strike with: an unused one, or else the one that has faded most. */
    private Stroke free() {
        Stroke oldest = null;
        for (Stroke stroke : strokes) {
            if (stroke.count == 0) {
                return stroke;
            }
            if (oldest == null || stroke.releasedAt < oldest.releasedAt) {
                oldest = stroke;
            }
        }
        return oldest;
    }

    /**
     * Writes the slashes as quads, {@link #VERTEX_FLOATS} floats a vertex.
     *
     * @param now       frame time
     * @param texel     the size one pixel of the item's texture is drawn at
     * @param lifeNanos how long a slash stays after it was drawn
     * @param width     how wide the slash is, 1 for the usual width
     * @param out       receives the vertices, at least {@link #MAX_FLOATS} floats
     * @return the number of floats written
     */
    int build(long now, float texel, long lifeNanos, float width, float[] out) {
        int written = 0;
        for (Stroke stroke : strokes) {
            if (stroke.count >= 2) {
                written = build(stroke, now, texel, lifeNanos, width, out, written);
            }
        }
        return written;
    }

    private int build(Stroke stroke, long now, float texel, long lifeNanos, float width, float[] out, int written) {
        // The frames, joined by a curve.
        int segments = stroke.count - 1;
        int points = segments * PIECES + 1;
        for (int segment = 0; segment < segments; segment++) {
            for (int piece = 0; piece < PIECES; piece++) {
                float u = piece / (float) PIECES;
                int at = segment * PIECES + piece;
                curve(stroke, segment, u, fine, at * 6);
                fineTimes[at] = stroke.times[segment] + (long) ((stroke.times[segment + 1] - stroke.times[segment]) * (double) u);
            }
        }
        System.arraycopy(stroke.points, segments * 6, fine, (points - 1) * 6, 6);
        fineTimes[points - 1] = stroke.times[segments];

        // The tail: whatever was drawn longer ago than the slash stays is gone.
        long cutoff = now - lifeNanos;
        int first = 0;
        while (first < points - 1 && fineTimes[first + 1] <= cutoff) {
            first++;
        }
        if (fineTimes[points - 1] <= cutoff) {
            return written;
        }
        if (fineTimes[first] < cutoff) {
            float u = (float) ((cutoff - fineTimes[first]) / (double) (fineTimes[first + 1] - fineTimes[first]));
            for (int k = 0; k < 6; k++) {
                fine[first * 6 + k] += (fine[(first + 1) * 6 + k] - fine[first * 6 + k]) * u;
            }
            fineTimes[first] = cutoff;
        }

        // How far along the slash each point is, from the front.
        lengths[points - 1] = 0.0f;
        for (int i = points - 2; i >= first; i--) {
            lengths[i] = lengths[i + 1] + distance(fine, i * 6, fine, (i + 1) * 6);
        }
        float total = lengths[first];
        if (texel <= 0.0f || total < MIN_TEXELS * texel) {
            return written;
        }

        // Once the strike is over the slash fades, and its front closes to a point as well.
        float released = stroke.attached ? 0.0f : Math.min(1.0f, (now - stroke.releasedAt) / (float) lifeNanos);
        float fade = 1.0f - released * released;
        float front = FRONT_SHARE * Math.min(1.0f, released * 4.0f);

        float reach = BLADE_SHARE * width;
        float px = 0, py = 0, pz = 0, pcx = 0, pcy = 0, pcz = 0, pix = 0, piy = 0, piz = 0, pa = 0;
        for (int i = first; i < points; i++) {
            float along = lengths[i] / total;
            float wide = (float) Math.pow(1.0f - along, TAPER);
            if (front > 0.0f && along < front) {
                float t = along / front;
                wide *= t * (2.0f - t);
            }
            float alpha = fade * (1.0f - along);
            int p = i * 6;
            float x = fine[p], y = fine[p + 1], z = fine[p + 2];
            float dx = (fine[p + 3] - x) * reach * wide;
            float dy = (fine[p + 4] - y) * reach * wide;
            float dz = (fine[p + 5] - z) * reach * wide;
            float cx = x + dx * CORE_SHARE, cy = y + dy * CORE_SHARE, cz = z + dz * CORE_SHARE;
            float ix = x + dx, iy = y + dy, iz = z + dz;
            if (i > first) {
                // The solid outer band.
                written = vertex(out, written, px, py, pz, pa);
                written = vertex(out, written, x, y, z, alpha);
                written = vertex(out, written, cx, cy, cz, alpha);
                written = vertex(out, written, pcx, pcy, pcz, pa);
                // The inner band, fading out towards the grip.
                written = vertex(out, written, pcx, pcy, pcz, pa);
                written = vertex(out, written, cx, cy, cz, alpha);
                written = vertex(out, written, ix, iy, iz, 0.0f);
                written = vertex(out, written, pix, piy, piz, 0.0f);
            }
            px = x;
            py = y;
            pz = z;
            pcx = cx;
            pcy = cy;
            pcz = cz;
            pix = ix;
            piy = iy;
            piz = iz;
            pa = alpha;
        }
        return written;
    }

    /**
     * Where the tip and the grip end were {@code u} of the way from one kept frame to the next, on
     * the curve through the frames around them (Catmull-Rom).
     */
    private static void curve(Stroke stroke, int segment, float u, float[] out, int at) {
        int p1 = segment * 6;
        int p2 = p1 + 6;
        int p0 = segment > 0 ? p1 - 6 : p1;
        int p3 = segment + 2 < stroke.count ? p2 + 6 : p2;
        float u2 = u * u, u3 = u2 * u;
        float w0 = 0.5f * (-u3 + 2.0f * u2 - u);
        float w1 = 0.5f * (3.0f * u3 - 5.0f * u2 + 2.0f);
        float w2 = 0.5f * (-3.0f * u3 + 4.0f * u2 + u);
        float w3 = 0.5f * (u3 - u2);
        float[] points = stroke.points;
        for (int k = 0; k < 6; k++) {
            out[at + k] = points[p0 + k] * w0 + points[p1 + k] * w1 + points[p2 + k] * w2 + points[p3 + k] * w3;
        }
    }

    private static float distance(float[] a, int ai, float[] b, int bi) {
        float dx = a[ai] - b[bi];
        float dy = a[ai + 1] - b[bi + 1];
        float dz = a[ai + 2] - b[bi + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int vertex(float[] out, int at, float x, float y, float z, float alpha) {
        out[at] = x;
        out[at + 1] = y;
        out[at + 2] = z;
        out[at + 3] = alpha;
        return at + VERTEX_FLOATS;
    }
}
