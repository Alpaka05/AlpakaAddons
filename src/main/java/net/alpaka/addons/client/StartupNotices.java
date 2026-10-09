package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.utils.ModVersion;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;

/**
 * Messages that have to wait until the player is in game: a settings file that had to be restored,
 * and, once after an update, what changed.
 *
 * Both are local chat lines. The update note waits for Skyblock, since what it is about is there.
 */
public final class StartupNotices {
    private static final String RELEASES_URL = "https://github.com/Alpaka05/AlpakaAddons/releases/latest";

    private static boolean loadNoticeShown = false;
    private static boolean updateNoticeDone = false;

    private StartupNotices() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        if (loadNoticeShown && updateNoticeDone) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!loadNoticeShown) {
            loadNoticeShown = true;
            String notice = AlpakaConfig.takeLoadNotice();
            if (notice != null) SlayerDropTracker.sendModMessage(notice);
        }

        if (!updateNoticeDone && SkyblockUtils.isOnSkyblock()) {
            updateNoticeDone = true;
            showUpdateNotice();
        }
    }

    /**
     * Once per update: which version is running now and where the release notes are. A fresh
     * install says nothing; its config did not exist before this start.
     */
    private static void showUpdateNotice() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        String current = ModVersion.mod();
        String previous = cfg.lastSeenVersion == null ? "" : cfg.lastSeenVersion;
        if (current.equals(previous)) return;

        boolean updated = AlpakaConfig.existedAtStart();
        cfg.lastSeenVersion = current;
        AlpakaConfig.save();
        if (!updated) return;

        SlayerDropTracker.sendModMessage(Component.literal("§7Updated to §f" + current + "§7. ")
                .append(Component.literal("[Release notes]").withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(RELEASES_URL)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(RELEASES_URL))))));

        // Every config from before 1.2.20 lacks the field, so an empty one means an update from a
        // version that could still post to party and guild chat by itself.
        if (previous.isEmpty()) {
            SlayerDropTracker.sendModMessage("§7The mod no longer sends chat by itself: the automatic §f!since§7 party reply "
                    + "and the guild drop announcement are gone. §fOffer Replies To !since§7 and §fShare Buttons On Top Drops§7 "
                    + "in §f/aa§7 add buttons that fill your chat box instead.");
        }
    }
}
