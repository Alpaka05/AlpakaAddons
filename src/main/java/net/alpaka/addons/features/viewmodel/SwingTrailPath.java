package net.alpaka.addons.features.viewmodel;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

/**
 * The swing trail of one hand: where the item's tip has been during each strike, and the ribbon
 * drawn from that.
 *
 * A swing has two halves - the strike, when the item leaves its resting place, and the way back.
 * Only the strike leaves a trail, as in a comic: a trail on the way back would retrace the same
 * arc and smear the stroke into a blob. The strike is told from the way back by distance alone:
 * as long as the tip keeps getting further from where it rests, it is striking. That holds for
 * every swing the viewmodel settings can produce, whatever its drift, arc and speed.
 *
 * Every frame of a strike adds a cross-section: the tip and the grip end of the item, where they
 * were on screen. The ribbon runs along the tips and reaches from there towards the grip ends, so
 * its leading edge lies on the item itself. It starts as a point where the item rested, widens
 * towards the item, and once the strike is over its leading end closes to a point as well, which
 * leaves a crescent hanging in the air. All of it fades with age.
 *
 * Positions are whatever space the caller draws the item in; this class only measures in it.
 */
final class SwingTrailPath {

    /** A frame gap longer than this means the item was not drawn for a while; the trail restarts. */
    private static final long GAP_RESET_NANOS = 250_000_000L;

    /** The strike is over once the tip has come back this share of the way it went out. */
    private static final float RETURN_SHARE = 0.03f;

    /** Pieces each recorded frame step is drawn in, rounded with a spline; smooths low frame rates. */
    private static final int SMOOTHING = 4;

    /** The ribbon is fully opaque from the tip to this share of its width, then fades to nothing. */
    private static final float CORE_SHARE = 0.3f;

    /** How much of a finished stroke's leading end narrows to a point, and how fast it gets there. */
    private static final float HEAD_SHARE = 0.55f;
    private static final float HEAD_CLOSE_SHARE = 0.3f;

    /** Floats per ribbon vertex: x, y, z and opacity (0..1). */
    static final int VERTEX_FLOATS = 4;

    /** One strike: its cross-sections in time order, six floats each (tip xyz, grip end xyz). */
    private static final class Stroke {
        long[] nanos = new long[16];
        float[] points = new float[16 * 6];
        int size;
        /** When the strike ended, or -1 while it is still under way. */
        long endNanos = -1L;

        void add(long time, float[] point) {
            if (size == nanos.length) {
                nanos = Arrays.copyOf(nanos, size * 2);
                points = Arrays.copyOf(points, size * 2 * 6);
            }
            nanos[size] = time;
            System.arraycopy(point, 0, points, size * 6, 6);
            size++;
        }

        long newest() {
            return nanos[size - 1];
        }
    }

    private final ArrayDeque<Stroke> strokes = new ArrayDeque<>();
    private Stroke striking;

    private final float[] rest = new float[6];
    private boolean restKnown;
    private boolean swinging;
    private float lastProgress;
    private float furthest;
    private long lastNanos = Long.MIN_VALUE / 2;

    /** Drops every stroke and what is known of the resting place, as after a change of item. */
    void clear() {
        strokes.clear();
        striking = null;
        restKnown = false;
        swinging = false;
    }

    /**
     * Records one frame.
     *
     * @param now      frame time
     * @param progress the swing progress the hand is drawn at, 0 while it rests
     * @param point    the tip and the grip end of the item this frame, six floats
     * @param keepNanos how long a stroke is kept after its last cross-section
     */
    void update(long now, float progress, float[] point, long keepNanos) {
        if (now - lastNanos > GAP_RESET_NANOS) {
            clear();
        }
        if (progress <= 0.0f) {
            System.arraycopy(point, 0, rest, 0, 6);
            restKnown = true;
            swinging = false;
            finishStroke(lastNanos);
        } else {
            if (!restKnown) {
                // Drawn mid-swing for the first time: nothing better to measure from.
                System.arraycopy(point, 0, rest, 0, 6);
                restKnown = true;
            }
            // A swing that starts while another is playing shows as progress jumping back.
            if (!swinging || progress < lastProgress) {
                finishStroke(lastNanos);
                striking = new Stroke();
                strokes.addLast(striking);
                // Every swing starts and ends at rest, so that is where the item was a frame ago.
                striking.add(now - lastNanos <= GAP_RESET_NANOS ? lastNanos : now, rest);
                furthest = 0.0f;
            }
            swinging = true;
            if (striking != null) {
                float distance = distance(point, rest);
                if (distance >= furthest) {
                    furthest = distance;
                    striking.add(now, point);
                } else if (distance < furthest * (1.0f - RETURN_SHARE)) {
                    finishStroke(striking.newest());
                } else {
                    striking.add(now, point);
                }
            }
        }
        lastProgress = progress;
        lastNanos = now;

        for (Iterator<Stroke> it = strokes.iterator(); it.hasNext(); ) {
            Stroke stroke = it.next();
            if (stroke != striking && now - stroke.newest() > keepNanos) {
                it.remove();
            }
        }
    }

    private void finishStroke(long time) {
        if (striking != null) {
            striking.endNanos = time;
            striking = null;
        }
    }

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Whether there is nothing to draw, so the caller can skip {@link #build}. */
    boolean isEmpty() {
        return strokes.isEmpty();
    }

    /**
     * Builds the ribbon as quads: four vertices each, {@link #VERTEX_FLOATS} floats per vertex.
     *
     * @param now         frame time
     * @param lengthNanos how long a part of the trail stays before it has faded out
     * @param width       how far the ribbon reaches from the tip towards the grip end, 0..1
     * @param out         receives the vertices; a larger array is returned when it is too small
     * @param count       receives the number of floats written, in {@code count[0]}
     * @return {@code out}, or its larger replacement
     */
    float[] build(long now, long lengthNanos, float width, float[] out, int[] count) {
        int written = 0;
        float[] cross = new float[10];
        float[] previous = new float[10];
        for (Stroke stroke : strokes) {
            int n = stroke.size;
            if (n < 2 || now - stroke.newest() >= lengthNanos) {
                continue;
            }
            float[] p = stroke.points;
            float[] along = new float[n];
            for (int i = 1; i < n; i++) {
                float dx = p[i * 6] - p[(i - 1) * 6];
                float dy = p[i * 6 + 1] - p[(i - 1) * 6 + 1];
                float dz = p[i * 6 + 2] - p[(i - 1) * 6 + 2];
                along[i] = along[i - 1] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            float total = along[n - 1];
            if (total < 1.0e-5f) {
                continue;
            }
            // 0 while the item still carries the leading end, then growing to HEAD_SHARE.
            float head = stroke.endNanos < 0L ? 0.0f
                    : HEAD_SHARE * Math.min(1.0f, (now - stroke.endNanos) / (HEAD_CLOSE_SHARE * lengthNanos));

            boolean havePrevious = false;
            for (int i = 0; i < n - 1; i++) {
                for (int j = i == 0 ? 0 : 1; j <= SMOOTHING; j++) {
                    float u = j / (float) SMOOTHING;
                    float place = (along[i] + (along[i + 1] - along[i]) * u) / total;
                    float age = (now - (stroke.nanos[i] + (long) ((stroke.nanos[i + 1] - stroke.nanos[i]) * (double) u)))
                            / (float) lengthNanos;
                    crossSection(p, n, i, u, place, age, head, width, cross);
                    if (havePrevious && (previous[9] > 0.0f || cross[9] > 0.0f)) {
                        if (written + 2 * 4 * VERTEX_FLOATS > out.length) {
                            out = Arrays.copyOf(out, Math.max(out.length * 2, written + 2 * 4 * VERTEX_FLOATS));
                        }
                        // Tip edge to core, fully opaque; core to inner edge, fading out.
                        written = vertex(out, written, previous, 0, previous[9]);
                        written = vertex(out, written, cross, 0, cross[9]);
                        written = vertex(out, written, cross, 3, cross[9]);
                        written = vertex(out, written, previous, 3, previous[9]);
                        written = vertex(out, written, previous, 3, previous[9]);
                        written = vertex(out, written, cross, 3, cross[9]);
                        written = vertex(out, written, cross, 6, 0.0f);
                        written = vertex(out, written, previous, 6, 0.0f);
                    }
                    float[] swap = previous;
                    previous = cross;
                    cross = swap;
                    havePrevious = true;
                }
            }
        }
        count[0] = written;
        return out;
    }

    private static int vertex(float[] out, int at, float[] cross, int offset, float alpha) {
        out[at] = cross[offset];
        out[at + 1] = cross[offset + 1];
        out[at + 2] = cross[offset + 2];
        out[at + 3] = alpha;
        return at + VERTEX_FLOATS;
    }

    /**
     * One cross-section of the ribbon: the tip edge, the core line and the inner edge (three floats
     * each) and the opacity, into {@code out}.
     *
     * @param place where along the stroke this is, 0 at its start and 1 at its leading end
     * @param age   how old this part is, as a share of the trail length
     * @param head  how much of the leading end is narrowing to a point
     */
    private static void crossSection(float[] p, int n, int i, float u, float place, float age, float head,
                                     float width, float[] out) {
        int before = Math.max(i - 1, 0);
        int after = Math.min(i + 2, n - 1);
        float fresh = Math.max(0.0f, 1.0f - age);
        // Pointed where the strike began, full where the item is.
        float reach = width * (float) Math.pow(place, 0.7) * (0.6f + 0.4f * fresh);
        if (head > 0.0f) {
            reach *= Math.min(1.0f, (1.0f - place) / head);
        }
        for (int axis = 0; axis < 3; axis++) {
            float tip = spline(p[before * 6 + axis], p[i * 6 + axis], p[(i + 1) * 6 + axis], p[after * 6 + axis], u);
            float grip = spline(p[before * 6 + 3 + axis], p[i * 6 + 3 + axis], p[(i + 1) * 6 + 3 + axis],
                    p[after * 6 + 3 + axis], u);
            out[axis] = tip;
            out[3 + axis] = tip + (grip - tip) * reach * CORE_SHARE;
            out[6 + axis] = tip + (grip - tip) * reach;
        }
        out[9] = fresh * (float) Math.sqrt(fresh);
    }

    /** Catmull-Rom through b and c, with a and d as their neighbors. */
    private static float spline(float a, float b, float c, float d, float u) {
        float u2 = u * u;
        float u3 = u2 * u;
        return 0.5f * (2.0f * b + (c - a) * u + (2.0f * a - 5.0f * b + 4.0f * c - d) * u2
                + (3.0f * b - a - 3.0f * c + d) * u3);
    }
}
