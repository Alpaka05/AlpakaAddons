package net.alpaka.addons.features.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectAnimationTest {

    private static final float EPS = 1.0e-4f;

    /** Every turn is a whole number of full turns, so the hand looks exactly as at rest. */
    private static void assertLooksAtRest(InspectAnimation.Pose pose) {
        assertEquals(0.0f, pose.x(), EPS);
        assertEquals(0.0f, pose.y(), EPS);
        assertEquals(0.0f, pose.z(), EPS);
        assertEquals(0.0f, pose.pitch() % 360.0f, EPS);
        assertEquals(0.0f, pose.yaw() % 360.0f, EPS);
        assertEquals(0.0f, pose.roll() % 360.0f, EPS);
    }

    @Test
    void everyAnimationStartsAndEndsAtRest() {
        for (InspectAnimation.Kind kind : InspectAnimation.Kind.values()) {
            assertLooksAtRest(kind.sample(0.0f));
            assertLooksAtRest(kind.sample(1.0f));
            assertLooksAtRest(kind.sample(1.5f));
        }
    }

    @Test
    void keysAreInOrderFromZeroToOne() {
        for (InspectAnimation.Kind kind : InspectAnimation.Kind.values()) {
            InspectAnimation.Key[] keys = kind.keys;
            assertEquals(0.0f, keys[0].time(), kind.name());
            assertEquals(1.0f, keys[keys.length - 1].time(), kind.name());
            for (int i = 1; i < keys.length; i++) {
                assertTrue(keys[i].time() > keys[i - 1].time(), kind.name() + " key " + i);
            }
        }
    }

    @Test
    void curvePassesThroughEveryKey() {
        for (InspectAnimation.Kind kind : InspectAnimation.Kind.values()) {
            for (InspectAnimation.Key key : kind.keys) {
                InspectAnimation.Pose pose = kind.sample(key.time());
                for (int c = 0; c < 6; c++) {
                    assertEquals(key.pose().get(c), pose.get(c), 1.0e-3f, kind.name() + " at " + key.time());
                }
            }
        }
    }

    /** Between two keys no value leaves the range the two keys span, so no pose bulges past what was written. */
    @Test
    void curveNeverOvershootsItsKeys() {
        for (InspectAnimation.Kind kind : InspectAnimation.Kind.values()) {
            InspectAnimation.Key[] keys = kind.keys;
            for (int i = 0; i + 1 < keys.length; i++) {
                for (int step = 0; step <= 50; step++) {
                    float t = keys[i].time() + (keys[i + 1].time() - keys[i].time()) * step / 50.0f;
                    InspectAnimation.Pose pose = kind.sample(t);
                    for (int c = 0; c < 6; c++) {
                        float lo = Math.min(keys[i].pose().get(c), keys[i + 1].pose().get(c));
                        float hi = Math.max(keys[i].pose().get(c), keys[i + 1].pose().get(c));
                        assertTrue(pose.get(c) >= lo - 1.0e-3f && pose.get(c) <= hi + 1.0e-3f,
                                kind.name() + " channel " + c + " at " + t + ": " + pose.get(c));
                    }
                }
            }
        }
    }

    /** No sudden jump from one frame to the next: at 240 fps, no step bigger than a smooth motion makes. */
    @Test
    void motionIsContinuous() {
        for (InspectAnimation.Kind kind : InspectAnimation.Kind.values()) {
            int frames = Math.round(kind.seconds * 240.0f);
            InspectAnimation.Pose previous = kind.sample(0.0f);
            for (int f = 1; f <= frames; f++) {
                InspectAnimation.Pose pose = kind.sample(f / (float) frames);
                for (int c = 0; c < 3; c++) {
                    assertTrue(Math.abs(pose.get(c) - previous.get(c)) < 0.02f, kind.name() + " moves too fast");
                }
                for (int c = 3; c < 6; c++) {
                    assertTrue(Math.abs(pose.get(c) - previous.get(c)) < 15.0f, kind.name() + " turns too fast");
                }
                previous = pose;
            }
        }
    }

    @Test
    void wrappedTakesTheShortWayBack() {
        InspectAnimation.Pose pose = new InspectAnimation.Pose(0, 0, 0, -350, 380, 720).wrapped();
        assertEquals(10.0f, pose.pitch(), EPS);
        assertEquals(20.0f, pose.yaw(), EPS);
        assertEquals(0.0f, pose.roll(), EPS);
    }

    @Test
    void skyblockTypeOutranksTheVanillaItem() {
        assertEquals(InspectAnimation.Kind.SWORD, ItemInspectFeature.kindOfSkyblockType("LEGENDARY DUNGEON SWORD"));
        assertEquals(InspectAnimation.Kind.SWORD, ItemInspectFeature.kindOfSkyblockType("a MYTHIC SWORD a"));
        assertEquals(InspectAnimation.Kind.SWORD, ItemInspectFeature.kindOfSkyblockType("EPIC LONGSWORD"));
        assertEquals(InspectAnimation.Kind.BOW, ItemInspectFeature.kindOfSkyblockType("LEGENDARY DUNGEON BOW"));
        assertEquals(InspectAnimation.Kind.BOW, ItemInspectFeature.kindOfSkyblockType("EPIC SHORTBOW"));
        assertEquals(InspectAnimation.Kind.TOOL, ItemInspectFeature.kindOfSkyblockType("EPIC DRILL"));
        assertEquals(InspectAnimation.Kind.TOOL, ItemInspectFeature.kindOfSkyblockType("RARE PICKAXE"));
        assertEquals(InspectAnimation.Kind.TOOL, ItemInspectFeature.kindOfSkyblockType("UNCOMMON AXE"));
        assertEquals(InspectAnimation.Kind.POLE, ItemInspectFeature.kindOfSkyblockType("LEGENDARY FISHING ROD"));
        assertNull(ItemInspectFeature.kindOfSkyblockType("LEGENDARY ACCESSORY"));
        assertNull(ItemInspectFeature.kindOfSkyblockType("Right-click to use"));
        assertNull(ItemInspectFeature.kindOfSkyblockType(""));
    }
}
