package net.alpaka.addons.features.playerscale;

/**
 * The per-axis scale a player render state is to be drawn at.
 *
 * Implemented onto {@code AvatarRenderState} by {@code AvatarRenderStateMixin}. It exists because the
 * decision and the drawing happen in different phases: whether a player is scaled is settled while
 * the world extracts its state, where the entity and the world-versus-GUI distinction are at hand,
 * but the scale is applied while the state is submitted, where neither is. The state is the one
 * object that travels between the two.
 */
public interface PlayerScaleRenderState {

    float alpaka$getScaleX();

    float alpaka$getScaleY();

    float alpaka$getScaleZ();

    void alpaka$setScale(float x, float y, float z);
}
