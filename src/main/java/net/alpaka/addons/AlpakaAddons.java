package net.alpaka.addons;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.AlpakaStats;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlpakaAddons implements ModInitializer {
    public static final String MOD_ID = "alpaka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AlpakaConfig.load();
        // Loaded separately because it is keyed by account and Skyblock profile rather than by
        // instance; see AlpakaStats for why the two are not one file.
        AlpakaStats.load();
        LOGGER.info("Alpaka Addons geladen!");
    }
}
