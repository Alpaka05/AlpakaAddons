package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.AlpakaStats;
import net.alpaka.addons.features.blaze.FirePitFeature;
import net.alpaka.addons.features.critters.PangolinHighlightFeature;
import net.alpaka.addons.features.inventoryhud.InventoryHudFeature;
import net.alpaka.addons.features.party.PartyInviteFeature;
import net.alpaka.addons.features.slayer.SkyblockProfileTracker;
import net.alpaka.addons.features.slayer.SlayerQuestDetector;
import net.alpaka.addons.features.slayer.SlayerSessionTracker;
import net.alpaka.addons.features.slayer.SlayerTimer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * The one place that reacts to joining, leaving and changing worlds, so state from one server or
 * account never leaks into the next.
 *
 * Nothing listened for these before: leaving mid-boss could record a phantom kill, the Skyblock
 * profile carried over to the next account, a party invite could still be accepted from another
 * server, and each feature guessed at world changes on its own. A feature with state tied to a
 * world or a connection resets it from here.
 */
public final class SessionLifecycle {
    /**
     * Away for at least this long, the slayer session is over. A shorter gap - a kick back to the
     * lobby, a quick reconnect - keeps the session the HUD is showing.
     */
    private static final long SESSION_GAP_MS = 10 * 60_000L;

    private static long disconnectedAtMs = 0L;

    private SessionLifecycle() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> onLevelChange());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onStopping());
    }

    private static void onJoin() {
        if (disconnectedAtMs != 0L && System.currentTimeMillis() - disconnectedAtMs >= SESSION_GAP_MS) {
            SlayerSessionTracker.INSTANCE.reset();
        }
        disconnectedAtMs = 0L;
    }

    private static void onDisconnect() {
        disconnectedAtMs = System.currentTimeMillis();
        SkyblockProfileTracker.clear();
        SlayerQuestDetector.INSTANCE.resetForDisconnect();
        SlayerTimer.INSTANCE.clear();
        PartyInviteFeature.dismiss();
        InventoryHudFeature.reset();
        PangolinHighlightFeature.reset();
        FirePitFeature.reset();
        AlpakaStats.flushNow();
    }

    private static void onLevelChange() {
        SlayerQuestDetector.INSTANCE.resetForLevelChange();
        PangolinHighlightFeature.reset();
        FirePitFeature.reset();
    }

    /** Nothing waiting to be written is lost on exit. */
    private static void onStopping() {
        AlpakaConfig.endDeferredSaves();
        AlpakaStats.flushNow();
    }
}
