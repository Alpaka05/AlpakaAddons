package net.alpaka.addons.features.garden;

/**
 * Marks a living entity's render state as a pest's head stand, from extraction, where the entity
 * is known, to submission, where only the state is. Implemented by LivingEntityRenderStateMixin.
 */
public interface PestRenderState {

    /** Whether the stand's own body is left out, so only the pest's head carries the outline. */
    boolean alpaka$isPestHead();

    void alpaka$setPestHead(boolean pestHead);
}
