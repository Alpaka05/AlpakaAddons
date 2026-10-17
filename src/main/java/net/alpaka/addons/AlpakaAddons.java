package net.alpaka.addons;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's id and shared logger.
 *
 * The mod is client-only, so it has no main entrypoint: the config and the slayer record are loaded
 * at the start of {@link net.alpaka.addons.client.AlpakaClient#onInitializeClient()}.
 */
public final class AlpakaAddons {
    public static final String MOD_ID = "alpaka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private AlpakaAddons() {}
}
