package net.alpaka.addons.features.viewmodel;

/**
 * The swing trail of one hand: a sweep drawn in the air behind the item's tip, in the manner of
 * Minecraft's sweep attack particle - a solid crescent of pixels, pointed at both ends, with a
 * shaded row along its inner edge. Nothing of it is see-through.
 *
 * A swing has two halves - the strike, when the item leaves its resting place, and the way back.
 * Only the strike draws a sweep: on the way back the item passes through the one it just drew.
 * The strike is told from the way back by distance alone: as long as the tip keeps getting
 * further from where it rests, it is striking. That holds for every swing the viewmodel settings
 * can produce, whatever its drift, arc and speed.
 *
 * While the strike lasts the sweep grows with it, its front point on the tip. When the strike is
 * over the sweep stays where it is and is used up from the end it began at, getting thinner as it
 * gets shorter, the way the particle's frames do. A swing chained onto the last one draws a sweep
 * of its own while the old one is used up, so a few are kept.
 *
 * The outer edge of the sweep is the way the tip went; from there it is built towards the side
 * the item is on, in pixels the size of the item's own that face the viewer like a particle's.
 * The few frames a strike takes are joined by a curve, not by straight lines, or the sweep would
 * show every frame as a corner.
 *
 * Positions are in whatever space the caller follows the item in; this class only measures in it
 * and is told where the viewer is.
 */
final class SwingTrailRibbon {

    /** A frame gap longer than this means the item was not drawn for a while; the trail restarts. */
    private static final long GAP_RESET_NANOS = 250_000_000L;

    /** The strike is over once the tip has come back this share of the way it went out. */
    private static final float RETURN_SHARE = 0.03f;

    /** Sweeps kept per hand: the one being drawn and the ones still being used up. */
    static final int MAX_STROKES = 3;

    /** Frames kept of one strike. */
    static final int MAX_SAMPLES = 48;

    /** A frame is only kept if the tip moved at least this many texels since the last one kept. */
    private static final float SPACING = 0.75f;

    /** Pieces the curve between two kept frames is cut into. */
    static final int PIECES = 6;

    /** A sweep shorter than this many pixels is not drawn: the item barely moved. */
    private static final int MIN_COLUMNS = 2;

    /** Most pixels a sweep is long; a longer one is drawn in longer pixels. */
    static final int MAX_COLUMNS = 96;

    /** Most pixels a sweep is thick. */
    static final int MAX_ROWS = 16;

    /** At a width of 100 %, the sweep is this share of the item's length thick at its middle. */
    static final float BLADE_SHARE = 0.2f;

    /** A sweep is at least this many times as long as it is thick, so a short one is thin. */
    static final int ASPECT = 5;

    /** How blunt the crescent is: 1 thins evenly from the middle, less stays thick for longer. */
    private static final float BLUNT = 0.75f;

    /** How bright the row along the inner edge is, as in the particle's texture. */
    static final float EDGE_SHADE = 232.0f / 255.0f;

    /** Floats per vertex: x, y, z and brightness (0..1). The sweep is written as quads of four. */
    static final int VERTEX_FLOATS = 4;
    private static final int POINTS = (MAX_SAMPLES - 1) * PIECES + 1;
    /** Most floats {@link #build} can write: two quads for every pixel of length of every sweep. */
    static final int MAX_FLOATS = MAX_STROKES * MAX_COLUMNS * 2 * 4 * VERTEX_FLOATS;

    /** One sweep: the frames of its strike, oldest first, tip and grip end in six floats each. */
    private static final class Stroke {
        final float[] points = new float[MAX_SAMPLES * 6];
        final long[] times = new long[MAX_SAMPLES];
        int count;
        /** Whether the strike is still under way and the sweep hangs on the tip. */
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
    /** The sweep being drawn, or null between strikes. */
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

    // Scratch for build(): the points of one sweep along its curve.
    private final float[] fine = new float[POINTS * 6];
    private final long[] fineTimes = new long[POINTS];
    private final float[] lengths = new float[POINTS];
    /** The places a pixel apart along one sweep, tip and grip end, and one pixel across it at each. */
    private final float[] places = new float[(MAX_COLUMNS + 1) * 6];
    private final float[] across = new float[(MAX_COLUMNS + 1) * 3];

    SwingTrailRibbon() {
        for (int i = 0; i < MAX_STROKES; i++) {
            strokes[i] = new Stroke();
        }
    }

    /** Drops every sweep and what is known of the swing, as after the item was not drawn. */
    void clear() {
        for (Stroke stroke : strokes) {
            stroke.count = 0;
            stroke.attached = false;
        }
        active = null;
        restKnown = false;
        lostTrack = true;
    }

    /** Ends the strike under way and leaves its sweep to be used up, as after a change of item. */
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
     * @param lifeNanos how long the sweep stays after it was drawn
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

    /** Drops the sweeps whose time is up. */
    private void expire(long now, long lifeNanos) {
        for (Stroke stroke : strokes) {
            if (stroke != active && stroke.count > 0 && now - stroke.times[stroke.count - 1] >= lifeNanos) {
                stroke.count = 0;
            }
        }
    }

    /** A sweep to draw the next strike with: an unused one, or else the one released longest ago. */
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
     * Writes the sweeps as quads, {@link #VERTEX_FLOATS} floats a vertex.
     *
     * @param now       frame time
     * @param texel     the size one pixel of the item's texture is drawn at
     * @param lifeNanos how long a sweep stays after it was drawn
     * @param width     how thick the sweep is, 1 for the usual thickness
     * @param eyeX      where the viewer is
     * @param out       receives the vertices, at least {@link #MAX_FLOATS} floats
     * @return the number of floats written
     */
    int build(long now, float texel, long lifeNanos, float width, float eyeX, float eyeY, float eyeZ, float[] out) {
        int written = 0;
        for (Stroke stroke : strokes) {
            if (stroke.count >= 2) {
                written = build(stroke, now, texel, lifeNanos, width, eyeX, eyeY, eyeZ, out, written);
            }
        }
        return written;
    }

    private int build(Stroke stroke, long now, float texel, long lifeNanos, float width,
                      float eyeX, float eyeY, float eyeZ, float[] out, int written) {
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

        // The tail: whatever was drawn longer ago than the sweep stays is gone.
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

        // How far along the sweep each point is, from the front.
        lengths[points - 1] = 0.0f;
        for (int i = points - 2; i >= first; i--) {
            lengths[i] = lengths[i + 1] + distance(fine, i * 6, fine, (i + 1) * 6);
        }
        float total = lengths[first];
        if (texel <= 0.0f) {
            return written;
        }

        // Once the strike is over the sweep is used up from where it began, slowly at first.
        float released = stroke.attached ? 0.0f : Math.min(1.0f, (now - stroke.releasedAt) / (float) lifeNanos);
        float visible = total * (1.0f - released * released);
        int columns = Math.min(MAX_COLUMNS, Math.round(visible / texel));
        if (columns < MIN_COLUMNS) {
            return written;
        }
        float step = visible / columns;

        // How thick it is at its middle, in pixels.
        int head = (points - 1) * 6;
        float item = distance(fine, head, fine, head + 3);
        int thick = Math.max(1, Math.min(MAX_ROWS, Math.round(BLADE_SHARE * width * item / texel)));
        thick = Math.max(1, Math.min(thick, columns / ASPECT));

        // The places a pixel apart on the way the tip went, from the front, and the way to the
        // grip end from each.
        int at = points - 1;
        for (int column = 0; column <= columns; column++) {
            float length = column * step;
            while (at > first && lengths[at - 1] < length) {
                at--;
            }
            float u = 0.0f;
            if (at > first && lengths[at - 1] > lengths[at]) {
                u = Math.min(1.0f, (length - lengths[at]) / (lengths[at - 1] - lengths[at]));
            }
            int a = at * 6;
            int b = (at > first ? at - 1 : at) * 6;
            for (int k = 0; k < 6; k++) {
                places[column * 6 + k] = fine[a + k] + (fine[b + k] - fine[a + k]) * u;
            }
        }

        // The pixels face the viewer, as a particle does: each column stands at a right angle to
        // the tip's way and to the line of sight. Along the item instead, they would be sheared
        // to slivers wherever the item moves along its own length. Of the two sides that leaves,
        // the sweep is on the one the item is on, taken over the whole of it so it cannot twist.
        float side = 0.0f;
        for (int column = 0; column <= columns; column++) {
            int p = column * 6;
            int before = Math.max(0, column - 1) * 6;
            int after = Math.min(columns, column + 1) * 6;
            float tx = places[after] - places[before];
            float ty = places[after + 1] - places[before + 1];
            float tz = places[after + 2] - places[before + 2];
            // The line of sight to this place.
            float x = places[p] - eyeX, y = places[p + 1] - eyeY, z = places[p + 2] - eyeZ;
            float nx = ty * z - tz * y;
            float ny = tz * x - tx * z;
            float nz = tx * y - ty * x;
            float size = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float way = (float) Math.sqrt((tx * tx + ty * ty + tz * tz) * (x * x + y * y + z * z));
            int n = column * 3;
            if (size > 0.05f * way) {
                across[n] = nx / size * texel;
                across[n + 1] = ny / size * texel;
                across[n + 2] = nz / size * texel;
            } else if (column > 0) {
                // The tip came straight at the viewer here: as the column before.
                across[n] = across[n - 3];
                across[n + 1] = across[n - 2];
                across[n + 2] = across[n - 1];
            } else {
                across[n] = 0.0f;
                across[n + 1] = 0.0f;
                across[n + 2] = 0.0f;
            }
            side += across[n] * (places[p + 3] - places[p]) + across[n + 1] * (places[p + 4] - places[p + 1])
                    + across[n + 2] * (places[p + 5] - places[p + 2]);
        }
        float sign = side < 0.0f ? -1.0f : 1.0f;

        // One column of pixels after the other.
        for (int column = 1; column <= columns; column++) {
            float along = (column - 0.5f) / columns;
            int rows = Math.max(1, Math.round(thick * (float) Math.pow(Math.sin(Math.PI * along), BLUNT)));
            // All of it is one brightness but the row along the inner edge.
            int body = rows > 1 ? rows - 1 : rows;
            written = quad(out, written, column, sign, 0, body, 1.0f);
            if (rows > body) {
                written = quad(out, written, column, sign, body, rows, EDGE_SHADE);
            }
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

    /** One run of pixels of a column, from row {@code from} to row {@code to}. */
    private int quad(float[] out, int at, int column, float sign, int from, int to, float shade) {
        at = corner(out, at, column - 1, sign * from, shade);
        at = corner(out, at, column, sign * from, shade);
        at = corner(out, at, column, sign * to, shade);
        at = corner(out, at, column - 1, sign * to, shade);
        return at;
    }

    private int corner(float[] out, int at, int place, float rows, float shade) {
        int p = place * 6;
        int n = place * 3;
        return vertex(out, at, places[p] + across[n] * rows, places[p + 1] + across[n + 1] * rows, places[p + 2] + across[n + 2] * rows, shade);
    }

    private static int vertex(float[] out, int at, float x, float y, float z, float shade) {
        out[at] = x;
        out[at + 1] = y;
        out[at + 2] = z;
        out[at + 3] = shade;
        return at + VERTEX_FLOATS;
    }
}
