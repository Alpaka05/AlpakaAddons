package net.alpaka.addons.features.profile;

import com.google.gson.JsonObject;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Takes a snapshot of the player's own profile now and then while they play SkyBlock, so the
 * dashboard's graphs fill in without it being opened: a few minutes after joining, then at the
 * interval set in the config. One request for the account and one for the profile each time.
 */
public final class ProfileTracker {
    private ProfileTracker() {}

    /** The first snapshot of a session waits this long, past the joining and the island hopping. */
    private static final long FIRST_DELAY_MS = 3 * 60_000L;

    /**
     * The snapshots also go to Soopy, at most this often: its history is what the dashboard shows on
     * any computer and after a reinstall, where the local file is missing. It recorded the months a
     * Soopy mod was installed and nothing since, which is where the old graphs' gaps come from.
     */
    private static final long SOOPY_EVERY_MS = 3 * 60 * 60_000L;

    private static long nextAt = 0L;
    private static long soopyAt = 0L;
    private static boolean busy = false;
    private static int ticks = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks % 100 != 0) return;
            tick(client);
        });
    }

    private static void tick(Minecraft client) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.allowApiCalls || !config.profileDashboardEnabled || !config.profileDashboardTracking
                || client.player == null || !SkyblockUtils.isOnSkyblock()) {
            // Leaving SkyBlock starts the wait over, so a quick visit to the lobby doesn't count.
            nextAt = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (nextAt == 0L) {
            nextAt = now + FIRST_DELAY_MS;
            return;
        }
        if (now < nextAt || busy) return;
        nextAt = now + Math.max(15, config.profileDashboardTrackingMinutes) * 60_000L;
        snapshot(client.getUser().getProfileId().toString().replace("-", ""), client.getUser().getName());
    }

    private static void snapshot(String uuid, String name) {
        busy = true;
        ProfileApi.account(uuid).thenCompose(account -> {
            String profileId = null;
            for (var p : Json.arr(account, "profiles")) {
                if (Json.bool(p, "selected")) profileId = Json.str(p, "profileId");
            }
            if (profileId == null) throw new ProfileApi.NotFound("no selected profile");
            return ProfileApi.member(uuid, profileId, true);
        }).whenComplete((member, failure) -> {
            busy = false;
            if (failure == null && member instanceof JsonObject object) {
                ProfileData data = new ProfileData(object, name);
                ProfileHistory.record(data);
                long now = System.currentTimeMillis();
                if (now - soopyAt >= SOOPY_EVERY_MS && data.profileId != null) {
                    soopyAt = now;
                    ProfileApi.soopyHistory(uuid, data.profileId, 0).exceptionally(e -> null);
                }
            }
        });
    }
}
