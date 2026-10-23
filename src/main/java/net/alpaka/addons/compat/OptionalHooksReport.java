package net.alpaka.addons.compat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Has {@link OptionalHooksPlugin} look at the classes its hooks went into last, which no later class
 * shows to be finished: once the game is up, and on joining a world, by when the renderers loaded
 * during the first resource reload are done as well. Both run outside any class transformation.
 * Its own entrypoint so the diagnosis stays apart from the features.
 */
public final class OptionalHooksReport implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> OptionalHooksPlugin.checkRemaining());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> OptionalHooksPlugin.checkRemaining());
    }
}
