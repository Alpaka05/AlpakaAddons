package net.alpaka.addons.features.slayer;

/**
 * Carries {@link SlayerBossHighlight}'s overlay on a living entity's render state, from extraction,
 * where the entity is known, to submission, where the model is drawn and only the state is.
 * Implemented by LivingEntityRenderStateMixin.
 */
public interface BossHighlightRenderState {

    /** The ARGB tint to multiply the model by, or 0 for none. */
    int alpaka$getHighlightTint();

    /** How far to lift the model towards white, 0 to 1. */
    float alpaka$getHighlightWhite();

    void alpaka$setHighlight(int tint, float white);
}
