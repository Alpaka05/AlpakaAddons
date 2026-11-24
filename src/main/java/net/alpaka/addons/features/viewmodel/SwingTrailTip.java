package net.alpaka.addons.features.viewmodel;

/**
 * Where an item's tip and its grip end are, in the item model's own coordinates - what the swing
 * trail hangs on. Found from the model's geometry alone, before the item is posed, so the trail
 * stays on the tip however the viewmodel settings turn, move or scale the item.
 *
 * Item textures are drawn with the grip at the bottom left and the business end at the top right,
 * swords, tools and rods alike, and the handheld models keep that layout. So the tip is the corner
 * of the model that reaches furthest towards the top right, and the grip end is the corner
 * furthest from the tip.
 *
 * An ordinary item is a flat sprite pushed out one pixel thick: two full square faces, front and
 * back, and a rim of small faces around every opaque pixel. The square faces say nothing about
 * the shape - their corner is the corner of the texture, where most tools have no pixel - so for
 * such a model only the rim counts, and the tip lands on the outermost pixel that is really drawn.
 * A model built from boxes (a block, or a resource pack's 3D sword) has no such rim, and all of
 * its corners count.
 */
final class SwingTrailTip {

    /** Position of the tip and of the grip end, in model coordinates (0..1 across a plain item). */
    final float tipX, tipY, tipZ, gripX, gripY, gripZ;

    private SwingTrailTip(float tipX, float tipY, float tipZ, float gripX, float gripY, float gripZ) {
        this.tipX = tipX;
        this.tipY = tipY;
        this.tipZ = tipZ;
        this.gripX = gripX;
        this.gripY = gripY;
        this.gripZ = gripZ;
    }

    /** The slab an extruded sprite fills: one pixel thick around the middle of the model. */
    private static final float SPRITE_MIN_Z = 7.5f / 16.0f;
    private static final float SPRITE_MAX_Z = 8.5f / 16.0f;

    /** Corners this close to the furthest one share the tip, which then sits in their middle. */
    private static final float TIE = 1.0e-3f;

    /**
     * @param corners the model's quad corners, three floats each
     * @param rim     per corner, whether its quad faces sideways or up or down - neither front nor back
     * @param count   number of corners
     * @return the tip and grip end, or null for a model without any extent
     */
    static SwingTrailTip locate(float[] corners, boolean[] rim, int count) {
        if (count == 0) {
            return null;
        }
        boolean sprite = false;
        for (int i = 0; i < count; i++) {
            float z = corners[i * 3 + 2];
            if (z < SPRITE_MIN_Z - TIE || z > SPRITE_MAX_Z + TIE) {
                sprite = false;
                break;
            }
            sprite |= rim[i];
        }

        // The tip: furthest towards the top right.
        float best = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            if (sprite && !rim[i]) {
                continue;
            }
            best = Math.max(best, corners[i * 3] + corners[i * 3 + 1]);
        }
        float tipX = 0.0f, tipY = 0.0f, tipZ = 0.0f;
        int shared = 0;
        for (int i = 0; i < count; i++) {
            if (sprite && !rim[i]) {
                continue;
            }
            if (corners[i * 3] + corners[i * 3 + 1] >= best - TIE) {
                tipX += corners[i * 3];
                tipY += corners[i * 3 + 1];
                tipZ += corners[i * 3 + 2];
                shared++;
            }
        }
        tipX /= shared;
        tipY /= shared;
        tipZ /= shared;

        // The grip end: furthest from the tip.
        float far = 0.0f;
        for (int i = 0; i < count; i++) {
            if (sprite && !rim[i]) {
                continue;
            }
            far = Math.max(far, distanceSq(corners, i, tipX, tipY, tipZ));
        }
        if (far < TIE * TIE) {
            return null;
        }
        float gripX = 0.0f, gripY = 0.0f, gripZ = 0.0f;
        shared = 0;
        float reach = (float) Math.sqrt(far);
        for (int i = 0; i < count; i++) {
            if (sprite && !rim[i]) {
                continue;
            }
            if (Math.sqrt(distanceSq(corners, i, tipX, tipY, tipZ)) >= reach - TIE) {
                gripX += corners[i * 3];
                gripY += corners[i * 3 + 1];
                gripZ += corners[i * 3 + 2];
                shared++;
            }
        }
        return new SwingTrailTip(tipX, tipY, tipZ, gripX / shared, gripY / shared, gripZ / shared);
    }

    private static float distanceSq(float[] corners, int i, float x, float y, float z) {
        float dx = corners[i * 3] - x;
        float dy = corners[i * 3 + 1] - y;
        float dz = corners[i * 3 + 2] - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
