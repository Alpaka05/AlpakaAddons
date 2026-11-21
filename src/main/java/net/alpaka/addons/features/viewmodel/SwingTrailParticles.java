package net.alpaka.addons.features.viewmodel;

import java.util.Random;

/**
 * The swing trail of one hand: small particles shed by the item's tip while it strikes, which
 * hang where they were left, drift a little, shrink and fade.
 *
 * A swing has two halves - the strike, when the item leaves its resting place, and the way back.
 * Only the strike sheds particles: on the way back the item passes through the ones it just left,
 * and a second lot there would turn the trail into a cloud. The strike is told from the way back
 * by distance alone: as long as the tip keeps getting further from where it rests, it is striking.
 * That holds for every swing the viewmodel settings can produce, whatever its drift, arc and speed.
 *
 * How many particles a strike sheds depends on how far the tip travels, not on how many frames it
 * takes, and they are spread along the curve the tip took between two frames rather than the
 * straight line between them - a strike is over in a handful of frames, and the trail would show
 * every one of them as a corner. Each particle starts somewhere on the outer part of the item,
 * most of them near the tip, takes a little of the item's speed along and soon comes to rest.
 *
 * Positions are whatever space the caller draws the item in; this class only measures in it. Sizes
 * and speeds are counted in texels - the size one pixel of the item's texture is drawn at - so
 * the trail grows and shrinks with the item.
 */
final class SwingTrailParticles {

    /** A frame gap longer than this means the item was not drawn for a while; the trail restarts. */
    private static final long GAP_RESET_NANOS = 250_000_000L;

    /** The longest step the particles are moved by in one frame, in seconds. */
    private static final float MAX_STEP = 0.05f;

    /** The strike is over once the tip has come back this share of the way it went out. */
    private static final float RETURN_SHARE = 0.03f;

    /** Particles shed per texel the tip travels, at an amount of 100 %. */
    static final float PER_TEXEL = 3.0f;

    /** Particles start between the tip and this share of the way to the grip end. */
    private static final float BLADE_SHARE = 0.3f;

    /** How much of the item's speed a particle takes along, least and most. */
    private static final float CARRY_MIN = 0.03f;
    private static final float CARRY_MAX = 0.14f;

    /** A particle's own speed in a random direction, its fall and how fast it comes to rest; in texels and seconds. */
    private static final float SCATTER = 3.0f;
    private static final float FALL = 6.0f;
    private static final float DRAG = 5.0f;

    /** The share of its life a particle spends fading out at the end. */
    private static final float FADE_SHARE = 0.25f;

    static final int MAX_PARTICLES = 1024;

    /** Floats per vertex: x, y, z and opacity (0..1). A particle is one quad of four. */
    static final int VERTEX_FLOATS = 4;
    static final int PARTICLE_FLOATS = 4 * VERTEX_FLOATS;

    private final Random random;

    // Particles, in no particular order.
    private int count;
    private final float[] position = new float[MAX_PARTICLES * 3];
    private final float[] velocity = new float[MAX_PARTICLES * 3];
    private final float[] size = new float[MAX_PARTICLES];
    private final long[] born = new long[MAX_PARTICLES];
    private final long[] life = new long[MAX_PARTICLES];

    // The swing.
    private final float[] rest = new float[6];
    private boolean restKnown;
    private boolean swinging;
    private boolean striking;
    /**
     * Set when the swing was lost sight of part-way - the item changed, or was not drawn for a
     * while. What is left of that swing sheds nothing: where it began is not known, so its way back
     * would pass for a strike. The next swing clears it.
     */
    private boolean lostTrack;
    private float lastProgress;
    private float furthest;
    private long lastNanos = Long.MIN_VALUE / 2;

    // The last two frames of the strike under way: tip and grip end, six floats each.
    private final float[] older = new float[6];
    private final float[] previous = new float[6];
    private long olderNanos;
    private long previousNanos;
    private boolean haveOlder;
    /** The part of a particle the tip's travel has paid for but which has not been shed yet. */
    private float owed;

    SwingTrailParticles(Random random) {
        this.random = random;
    }

    /** Drops every particle and what is known of the swing, as after the item was not drawn. */
    void clear() {
        count = 0;
        interrupt();
    }

    /** Ends the strike under way without touching the particles, as after a change of item. */
    void interrupt() {
        restKnown = false;
        striking = false;
        lostTrack = true;
    }

    int count() {
        return count;
    }

    /**
     * Records one frame: moves the particles on and, during a strike, sheds new ones.
     *
     * @param now       frame time
     * @param progress  the swing progress the hand is drawn at, 0 while it rests
     * @param point     the tip and the grip end of the item this frame, six floats
     * @param texel     the size one pixel of the item's texture is drawn at
     * @param lifeNanos how long a particle stays at most; each lives between half of that and all of it
     * @param amount    how many particles are shed, 1 for the usual number
     */
    void update(long now, float progress, float[] point, float texel, long lifeNanos, float amount) {
        if (now - lastNanos > GAP_RESET_NANOS) {
            clear();
        }
        advance(now, Math.min((now - lastNanos) / 1.0e9f, MAX_STEP), texel);

        if (progress <= 0.0f) {
            System.arraycopy(point, 0, rest, 0, 6);
            restKnown = true;
            swinging = false;
            striking = false;
            lostTrack = false;
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
                striking = true;
                furthest = 0.0f;
                owed = 0.0f;
                haveOlder = false;
                // Every swing starts and ends at rest, so that is where the item was a frame ago.
                System.arraycopy(rest, 0, previous, 0, 6);
                previousNanos = now - lastNanos <= GAP_RESET_NANOS ? lastNanos : now;
            }
            swinging = true;
            if (striking) {
                float distance = distance(point, rest);
                if (distance < furthest * (1.0f - RETURN_SHARE)) {
                    striking = false;
                } else {
                    furthest = Math.max(furthest, distance);
                    shed(now, point, texel, lifeNanos, amount);
                    System.arraycopy(previous, 0, older, 0, 6);
                    olderNanos = previousNanos;
                    haveOlder = true;
                    System.arraycopy(point, 0, previous, 0, 6);
                    previousNanos = now;
                }
            }
        }
        lastProgress = progress;
        lastNanos = now;
    }

    /** Moves every particle on by {@code step} seconds and drops the ones whose time is up. */
    private void advance(long now, float step, float texel) {
        float slow = (float) Math.exp(-DRAG * step);
        for (int i = count - 1; i >= 0; i--) {
            if (now - born[i] >= life[i]) {
                remove(i);
                continue;
            }
            move(i, step, slow, texel);
        }
    }

    private void move(int i, float step, float slow, float texel) {
        position[i * 3] += velocity[i * 3] * step;
        position[i * 3 + 1] += velocity[i * 3 + 1] * step;
        position[i * 3 + 2] += velocity[i * 3 + 2] * step;
        velocity[i * 3] *= slow;
        velocity[i * 3 + 1] = velocity[i * 3 + 1] * slow - FALL * texel * step;
        velocity[i * 3 + 2] *= slow;
    }

    private void remove(int i) {
        int last = --count;
        if (i != last) {
            System.arraycopy(position, last * 3, position, i * 3, 3);
            System.arraycopy(velocity, last * 3, velocity, i * 3, 3);
            size[i] = size[last];
            born[i] = born[last];
            life[i] = life[last];
        }
    }

    /** Sheds the particles for the way from the last frame of the strike to this one. */
    private void shed(long now, float[] point, float texel, long lifeNanos, float amount) {
        long span = now - previousNanos;
        float travelled = distance(point, previous);
        if (texel <= 0.0f || travelled <= 0.0f) {
            return;
        }
        owed += travelled / texel * PER_TEXEL * amount;
        float seconds = Math.max(span / 1.0e9f, 1.0e-4f);
        float[] at = new float[6];
        while (owed >= 1.0f) {
            owed -= 1.0f;
            if (count == MAX_PARTICLES) {
                continue;
            }
            float u = random.nextFloat();
            curve(point, now, u, at);
            int i = count++;
            // Most of them near the tip, thinning out down the item.
            float down = random.nextFloat();
            down = down * down * down * BLADE_SHARE;
            float carry = CARRY_MIN + (CARRY_MAX - CARRY_MIN) * random.nextFloat();
            for (int axis = 0; axis < 3; axis++) {
                position[i * 3 + axis] = at[axis] + (at[3 + axis] - at[axis]) * down
                        + (random.nextFloat() - 0.5f) * 0.7f * texel;
                velocity[i * 3 + axis] = (point[axis] - previous[axis]) / seconds * carry
                        + (random.nextFloat() - 0.5f) * 2.0f * SCATTER * texel;
            }
            size[i] = texel * (0.3f + 0.5f * random.nextFloat());
            life[i] = (long) (lifeNanos * (0.5f + 0.5f * random.nextFloat()));
            // Shed on the way, not at the end of it: it has been around since then.
            born[i] = previousNanos + (long) (span * (double) u);
            float age = (now - born[i]) / 1.0e9f;
            move(i, age, (float) Math.exp(-DRAG * age), texel);
        }
    }

    /**
     * Where the tip and the grip end were {@code u} of the way from the last frame to this one, into
     * {@code out}. With a frame before the last to go by, on the curve through all three.
     */
    private void curve(float[] point, long now, float u, float[] out) {
        double t0 = olderNanos, t1 = previousNanos, t2 = now;
        if (!haveOlder || t0 >= t1 || t1 >= t2) {
            for (int k = 0; k < 6; k++) {
                out[k] = previous[k] + (point[k] - previous[k]) * u;
            }
            return;
        }
        double t = t1 + (t2 - t1) * u;
        float w0 = (float) ((t - t1) * (t - t2) / ((t0 - t1) * (t0 - t2)));
        float w1 = (float) ((t - t0) * (t - t2) / ((t1 - t0) * (t1 - t2)));
        float w2 = (float) ((t - t0) * (t - t1) / ((t2 - t0) * (t2 - t1)));
        for (int k = 0; k < 6; k++) {
            out[k] = older[k] * w0 + previous[k] * w1 + point[k] * w2;
        }
    }

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Writes the particles as squares facing the viewer: {@link #PARTICLE_FLOATS} floats each.
     *
     * @param now   frame time
     * @param scale how big the particles are drawn, 1 for their own size
     * @param out   receives the vertices, at least {@code count() * PARTICLE_FLOATS} floats
     * @return the number of floats written
     */
    int build(long now, float scale, float[] out) {
        int written = 0;
        for (int i = 0; i < count; i++) {
            float age = (now - born[i]) / (float) life[i];
            if (age >= 1.0f) {
                continue;
            }
            float alpha = Math.min(1.0f, (1.0f - age) / FADE_SHARE);
            // Shrinks to a quarter of its size over its life, slowly at first.
            float half = 0.5f * size[i] * scale * (1.0f - 0.75f * age * age);
            float x = position[i * 3], y = position[i * 3 + 1], z = position[i * 3 + 2];
            written = vertex(out, written, x - half, y - half, z, alpha);
            written = vertex(out, written, x + half, y - half, z, alpha);
            written = vertex(out, written, x + half, y + half, z, alpha);
            written = vertex(out, written, x - half, y + half, z, alpha);
        }
        return written;
    }

    private static int vertex(float[] out, int at, float x, float y, float z, float alpha) {
        out[at] = x;
        out[at + 1] = y;
        out[at + 2] = z;
        out[at + 3] = alpha;
        return at + VERTEX_FLOATS;
    }
}
