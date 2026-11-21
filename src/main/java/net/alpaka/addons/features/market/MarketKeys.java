package net.alpaka.addons.features.market;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.minecraft.client.KeyMapping;

/**
 * The keys that move around a market screen, one set for each market, bound in the settings under
 * its Custom screen, all unbound until the player picks a key. They only act inside the market
 * screen; see MarketScreen.hotkeyTarget. The number keys, "/" and Ctrl+F stay fixed.
 */
public final class MarketKeys {
    private MarketKeys() {}

    /** One market's keys. Create Auction and Reset Settings are null for the Bazaar, which has no such buttons. */
    public record Set(KeyMapping previousPage, KeyMapping nextPage, KeyMapping back, KeyMapping search,
                      KeyMapping createAuction, KeyMapping resetSettings) {
    }

    public static Set AUCTION_HOUSE;
    public static Set BAZAAR;

    public static void register() {
        AUCTION_HOUSE = new Set(
                key("auction_house", "key.alpaka.ah_previous_page", InputConstants.UNKNOWN.getValue()),
                key("auction_house", "key.alpaka.ah_next_page", InputConstants.UNKNOWN.getValue()),
                key("auction_house", "key.alpaka.ah_back", InputConstants.UNKNOWN.getValue()),
                key("auction_house", "key.alpaka.ah_search", InputConstants.UNKNOWN.getValue()),
                key("auction_house", "key.alpaka.ah_create_auction", InputConstants.UNKNOWN.getValue()),
                key("auction_house", "key.alpaka.ah_reset_settings", InputConstants.UNKNOWN.getValue()));
        BAZAAR = new Set(
                key("bazaar", "key.alpaka.bz_previous_page", InputConstants.UNKNOWN.getValue()),
                key("bazaar", "key.alpaka.bz_next_page", InputConstants.UNKNOWN.getValue()),
                key("bazaar", "key.alpaka.bz_back", InputConstants.UNKNOWN.getValue()),
                key("bazaar", "key.alpaka.bz_search", InputConstants.UNKNOWN.getValue()),
                null, null);
    }

    public static Set of(MarketKind kind) {
        return kind == MarketKind.AUCTION_HOUSE ? AUCTION_HOUSE : BAZAAR;
    }

    // The two markets are never open at once, so each set only clashes within itself.
    private static KeyMapping key(String market, String name, int key) {
        return AlpakaKeyBinds.registerMenuKey(
                new KeyMapping(name, InputConstants.Type.KEYBOARD, key, AlpakaKeyCategory.CATEGORY), market);
    }
}
