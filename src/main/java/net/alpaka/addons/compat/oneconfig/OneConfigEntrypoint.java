package net.alpaka.addons.compat.oneconfig;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.oneconfig.api.config.v1.ConfigEntrypoint;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;

/**
 * Puts the mod's settings into OneConfig's menu, for players who run the mod in OneClient.
 *
 * OneConfig asks for this class through the {@code oneconfig:config} entrypoint, so without
 * OneConfig it is never loaded and nothing here has to check for it. With a tree registered,
 * OneConfig lists the settings itself instead of the Mod Menu card that only opened the mod's own
 * menu; that menu stays reachable from OneConfig's page.
 *
 * Should the tree fail to build - OneConfig changing under it, say - the error is logged and
 * OneConfig falls back to that card, so the settings are never out of reach.
 */
public final class OneConfigEntrypoint implements ConfigEntrypoint, OneConfigTree.Host {

    /** How long after the last change from OneConfig the config file is written, in ticks. */
    private static final int SAVE_DELAY_TICKS = 10;

    /** How often the rows that depend on a value set elsewhere are looked at again, in ticks. */
    private static final int REFRESH_TICKS = 10;

    /** Ticks left until the held-back save is let through; 0 while none is held back. */
    private int saveCountdown = 0;
    private boolean tickHooked = false;
    private Runnable refresh;
    private int refreshCountdown = REFRESH_TICKS;

    @Override
    public void registerConfigs(Context context) {
        try {
            ConfigManager.active().register(OneConfigTree.build(ConfigTree.groups(), this));
            context.originalScreen(() -> onClientThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(new AlpakaConfigScreen(mc.gui.screen()));
            }));
        } catch (Throwable t) {
            AlpakaAddons.LOGGER.error("Could not put the settings into OneConfig's menu; they stay in the mod's own", t);
        }
    }

    /**
     * Most setters save, and OneConfig calls one for each step of a slider drag and each typed
     * letter. The writes are held back while changes keep coming and done once, shortly after the
     * last one, or at once when OneConfig asks for a save.
     */
    @Override
    public void change(Runnable setter) {
        onClientThread(() -> {
            AlpakaConfig.beginDeferredSaves();
            saveCountdown = SAVE_DELAY_TICKS;
            hookTick();
            setter.run();
            // Some setters leave the saving to the picker they were written for; held back like the rest.
            AlpakaConfig.save();
        });
    }

    private void hookTick() {
        if (tickHooked) return;
        tickHooked = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        if (saveCountdown > 0 && --saveCountdown == 0) AlpakaConfig.endDeferredSaves();
        if (refresh != null && --refreshCountdown <= 0) {
            refreshCountdown = REFRESH_TICKS;
            refresh.run();
        }
    }

    @Override
    public void keepFresh(Runnable task) {
        refresh = task;
        onClientThread(this::hookTick);
    }

    @Override
    public OneConfigTree.Bound key(ConfigOption option) {
        KeyMapping mapping = option.getKeyMapping();
        if (mapping == null || mapping.isUnbound()) return null;
        return bound(InputConstants.getKey(mapping.saveString()));
    }

    @Override
    public OneConfigTree.Bound defaultKey(ConfigOption option) {
        KeyMapping mapping = option.getKeyMapping();
        return mapping == null ? null : bound(mapping.getDefaultKey());
    }

    private static OneConfigTree.Bound bound(InputConstants.Key key) {
        if (key.equals(InputConstants.UNKNOWN)) return null;
        return switch (key.getType()) {
            case KEYSYM -> new OneConfigTree.Bound(key.getValue(), false);
            case MOUSE -> new OneConfigTree.Bound(key.getValue(), true);
            // A key known by its position only, which OneConfig's field has no name for.
            case SCANCODE -> null;
        };
    }

    @Override
    public void bind(ConfigOption option, OneConfigTree.Bound key) {
        KeyMapping mapping = option.getKeyMapping();
        if (mapping == null) return;
        AlpakaKeyBinds.set(mapping, key == null ? InputConstants.UNKNOWN
                : (key.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM).getOrCreate(key.code()));
    }

    @Override
    public void act(ConfigOption option) {
        onClientThread(() -> option.triggerAction(Minecraft.getInstance().gui.screen()));
    }

    @Override
    public void openInOwnMenu(ConfigOption option) {
        onClientThread(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screen own = AlpakaConfigScreen.openOnOption(mc.gui.screen(), option.getId());
            mc.gui.setScreen(own != null ? own : new AlpakaConfigScreen(mc.gui.screen()));
        });
    }

    @Override
    public void save() {
        // Every change from OneConfig came through change(), so a held-back save is all there can be.
        onClientThread(() -> {
            saveCountdown = 0;
            AlpakaConfig.endDeferredSaves();
        });
    }

    /** The options, the config and the screens belong to the client thread, wherever OneConfig calls from. */
    private static void onClientThread(Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) action.run();
        else mc.execute(action);
    }
}
