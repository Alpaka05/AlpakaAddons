package net.alpaka.addons;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.features.wheel.CommandWheelFeature;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlpakaAddons implements ModInitializer {
    public static final String MOD_ID = "alpaka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AlpakaConfig.load();
        CustomSoundFeature.register();
        CommandWheelFeature.register();
        SlayerDropTracker.registerEvents();
        LOGGER.info("Alpaka Addons geladen!");
    }
}
