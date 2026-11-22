package net.alpaka.addons.features.viewmodel;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.CameraType;

/**
 * First-Person Hand in F5: when the first-person hand is on screen. GameRendererMixin draws it in
 * the back view, and the viewmodel features that only make sense while the hand is visible (Inspect
 * Animation) ask here, so they work in F5 exactly when the hand is there.
 */
public final class HandInThirdPerson {
    private HandInThirdPerson() {}

    /** Whether the switch adds the hand to this camera: the back view only, since in the front view it would float in front of your face. */
    public static boolean addsHand(CameraType camera) {
        return AlpakaConfig.instance.renderHandInThirdPerson && camera == CameraType.THIRD_PERSON_BACK;
    }

    /** Whether the first-person hand is drawn with this camera. */
    public static boolean handShown(CameraType camera) {
        return camera.isFirstPerson() || addsHand(camera);
    }
}
