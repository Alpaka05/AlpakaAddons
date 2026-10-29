package net.alpaka.addons.features.market;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.gui.screens.Screen;

/** Where the market screens are switched on, and which market the player is in at the moment. */
public final class Markets {
    private Markets() {}

    private static String tradeItem;
    private static String tradeAction;
    private static MarketKind tradeKind;
    private static long tradeAtMs;

    static void rememberTrade(MarketKind kind, net.minecraft.world.item.ItemStack item, String action) {
        tradeKind = kind;
        tradeItem = SkyblockUtils.cleanColor(item.getHoverName().getString())
                + (item.getCount() > 1 ? " ×" + item.getCount() : "");
        tradeAction = action;
        tradeAtMs = System.currentTimeMillis();
    }

    /** A short-lived heading for the sign reached from the clicked product action. */
    static String tradeContext(MarketKind kind) {
        if (kind == null || kind != tradeKind || System.currentTimeMillis() - tradeAtMs > 30_000L) return null;
        return tradeItem + " · " + tradeAction;
    }

    /** Whether the custom screen replaces this market's menus right now. */
    public static boolean skinned(MarketKind kind) {
        if (kind == null || !SkyblockUtils.isOnSkyblock()) return false;
        AlpakaConfig config = AlpakaConfig.instance;
        return kind == MarketKind.AUCTION_HOUSE ? config.customAuctionHouseEnabled : config.customBazaarEnabled;
    }

    /** Whether the keys press this market's buttons: Enable Hotkeys under its Custom screen. */
    public static boolean hotkeys(MarketKind kind) {
        AlpakaConfig config = AlpakaConfig.instance;
        return kind == MarketKind.AUCTION_HOUSE ? config.auctionHouseHotkeysEnabled : config.bazaarHotkeysEnabled;
    }

    /** Whether Hypixel's sign prompts get the input bar. */
    public static boolean signInput() {
        return AlpakaConfig.instance.customSignInputEnabled && (SkyblockUtils.isOnSkyblock() || devAnywhere);
    }

    /**
     * For the screenshot tour only: lets a sign opened in its singleplayer world take the same way
     * in as one of Hypixel's, through the game's own sign opening.
     */
    static boolean devAnywhere = false;

    /**
     * The market the player is in, judged by the screen that is open: a market menu, or the input
     * bar opened from one. Null anywhere else.
     */
    public static MarketKind context(Screen screen) {
        if (screen instanceof MarketScreen market) return market.kind;
        if (screen instanceof MarketInputScreen input) return input.context();
        return null;
    }

    /** How long after a market menu closed its market still counts as where the player is. */
    private static final long RECENT_MS = 3000L;

    private static MarketKind lastKind = null;
    private static long lastLeftAtMs = 0L;

    /** Notes that a market's menu closed, for whatever opens right after it. */
    static void left(MarketKind kind) {
        lastKind = kind;
        lastLeftAtMs = System.currentTimeMillis();
    }

    /**
     * Like {@link #context}, but also the market whose menu closed a moment ago: Hypixel may close
     * the chest before it opens a sign or the next page. Only used for how a screen looks - its
     * heading, whether it pops open - never for deciding which menus get replaced.
     */
    public static MarketKind recentContext(Screen screen) {
        MarketKind now = context(screen);
        if (now != null) return now;
        return System.currentTimeMillis() - lastLeftAtMs < RECENT_MS ? lastKind : null;
    }

    /** A menu opening this soon after a market menu closed is a step inside that market. */
    private static final long NAVIGATION_MS = 600L;

    /**
     * The market a menu that is opening now was reached from: the open screen's, or one that closed
     * just before - no player opens another menu by hand that fast. Decides whether a generic title
     * such as "Confirm Purchase" counts as part of the market.
     */
    public static MarketKind navigationContext(Screen screen) {
        MarketKind now = context(screen);
        if (now != null) return now;
        return System.currentTimeMillis() - lastLeftAtMs < NAVIGATION_MS ? lastKind : null;
    }

    /** The title of the market menu opened last, skinned or not. */
    private static String lastMenuTitle = null;

    /** Notes a market menu opening, for a sign it asks next: "Create BIN Auction" says what its price is for. */
    static void menuOpened(String title) {
        lastMenuTitle = title;
    }

    static String lastMenuTitle() {
        return lastMenuTitle;
    }

    // ---- Recent searches ------------------------------------------------------------------------

    private static final int MAX_RECENT = 6;
    private static final java.util.Map<MarketKind, java.util.LinkedList<String>> RECENT_SEARCHES =
            new java.util.EnumMap<>(MarketKind.class);

    /** Remembers a search for this session, newest first, so the input bar can offer it again. */
    static void rememberSearch(MarketKind kind, String query) {
        if (kind == null || query == null || query.isBlank()) return;
        java.util.LinkedList<String> list = RECENT_SEARCHES.computeIfAbsent(kind, k -> new java.util.LinkedList<>());
        String q = query.trim();
        list.removeIf(s -> s.equalsIgnoreCase(q));
        list.addFirst(q);
        while (list.size() > MAX_RECENT) list.removeLast();
    }

    static java.util.List<String> recentSearches(MarketKind kind) {
        java.util.LinkedList<String> list = kind == null ? null : RECENT_SEARCHES.get(kind);
        return list == null ? java.util.List.of() : java.util.List.copyOf(list);
    }
}
