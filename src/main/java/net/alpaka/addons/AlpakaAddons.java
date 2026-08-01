package net.alpaka.addons;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlpakaAddons implements ModInitializer {
    public static final String MOD_ID = "alpaka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AlpakaConfig.load();
        LOGGER.info("Alpaka Addons geladen!");
    }
}
