package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.itemvalue.ItemValue;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.MarketIcons;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Profile Dashboard, opened with /alpakaprofile: a SkyBlock profile at a glance - networth,
 * skills, dungeons, slayers, pets, collections and the rest - with graphs of how each grew.
 *
 * The numbers come from Hypixel through the AlpakaAddons API ({@link AlpakaApi}), read into what
 * the pages show by {@link HypixelProfile}; the graphs from the history other sites kept
 * ({@link ProfileApi}) and the dashboard's own snapshots ({@link ProfileHistory}). Any player can be looked up from the search
 * field; the player's own profile also remembers the last visit, so the overview can say what
 * changed since.
 *
 * Nothing here talks to the game server: it is a window of the mod's own, drawn in the config
 * menu's design.
 */
public class ProfileDashboardScreen extends Screen {
    /** Compiled once: these run per row, every frame the page is on. */
    private static final java.util.regex.Pattern CAMEL_HUMP = java.util.regex.Pattern.compile("([a-z])([A-Z])");
    private static final java.util.regex.Pattern BACKPACK_KEY = java.util.regex.Pattern.compile("backpack_\\d+");


    // ---- Pages ----------------------------------------------------------------------------------

    /**
     * The sidebar's pages. Where SkyBlock has an item that stands for a page (the Maddox Batphone
     * for slayers, a backpack for inventories), the row wears it, drawn as the NotEnoughUpdates
     * repository has it; until that is in, or with Network Features off, the plain item instead.
     * Pages whose menu in SkyBlock already shows a plain item (Skills, Collections, Bestiary)
     * keep that one.
     */
    enum Page {
        OVERVIEW("Overview", Items.NETHER_STAR, "SKYBLOCK_MENU"),
        SKILLS("Skills", Items.DIAMOND_SWORD, null),
        DUNGEONS("Dungeons", Items.WITHER_SKELETON_SKULL, "MASTER_SKULL_TIER_7"),
        SLAYERS("Slayers", Items.ROTTEN_FLESH, "AATROX_BATPHONE"),
        NETWORTH("Networth", Items.GOLD_INGOT, "PIGGY_BANK"),
        INVENTORY("Inventories", Items.CHEST, "JUMBO_BACKPACK"),
        PETS("Pets", Items.BONE, null),
        COLLECTIONS("Collections", Items.PAINTING, null),
        BESTIARY("Bestiary", Items.WRITABLE_BOOK, null),
        MUSEUM("Museum", Items.ARMOR_STAND, null),
        RIFT("Rift", Items.ENDER_EYE, "RIFT_PRISM"),
        CHOCOLATE("Rabbits", Items.COCOA_BEANS, "RABBIT_HAT");

        final String title;
        final Item icon;
        /** The SkyBlock item's id, or null where the plain item is the right one. */
        final String skyblockIcon;

        Page(String title, Item icon, String skyblockIcon) {
            this.title = title;
            this.icon = icon;
            this.skyblockIcon = skyblockIcon;
        }
    }

    /** What the screen was last left on, so reopening it shows the same page and range. */
    private static Page lastPage = Page.OVERVIEW;
    private static ProfileChart.Range range = ProfileChart.Range.MONTH;
    /**
     * Sessions of this game, by who was asked for, so reopening the dashboard is instant. Each one
     * holds several megabytes (networth, inventories, graphs), so only the few players looked at last
     * are kept, least recently asked for dropped first. Only touched on the client thread; the screen
     * holds its own session, so dropping one from here never takes it from under an open dashboard.
     */
    private static final Map<String, KeptSession> SESSIONS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, KeptSession> eldest) {
            return size() > SESSIONS_KEPT;
        }
    };
    private static final int SESSIONS_KEPT = 5;
    /** A quarter of an hour: the API keeps the own profile ten minutes, so reopening sooner shows nothing older. */
    private static final long SESSION_KEEP_MS = 15 * 60_000L;

    private record KeptSession(ProfileSession session, long at) {}

    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int SIDE_W = 116;
    /** The sidebar folded down to its icons: a 16-pixel item in a row with a little room either side. */
    private static final int SIDE_W_COLLAPSED = 32;
    private static final int SIDE_ROW_H = 20;
    private static final int CHIP_H = 12;
    private static final int[] PALETTE = {0xFF5CE1F0, 0xFFFFB347, 0xFF5BE38A, 0xFFB388FF, 0xFFFF6B9A, 0xFFFFE066,
            0xFF7FA7FF, 0xFFFF8A65, 0xFF4DD0B5, 0xFFC0CA33, 0xFF90A4AE};

    // ---- State ----------------------------------------------------------------------------------

    private ProfileSession session;
    private Page page = lastPage;
    /** The metric each page's graph shows, once the player picked one there. */
    private final Map<Page, String> chosen = new EnumMap<>(Page.class);
    private long openTimeMs = 0L;
    private long lastFrameMs = System.currentTimeMillis();
    private final EditableText search = new EditableText(16);
    /** The click being handled, so a text field's hit can put the caret under it, and the field edited before it. */
    private MouseButtonEvent click = null;
    private EditableText clickFrom = null;
    private boolean searchFocused = false;
    private long caretFrom = 0L;
    /**
     * The players looked up before, listed under the player search while it has the focus; the one
     * picked with the arrow keys (null for none), and where the list is drawn.
     */
    private List<String> recent = List.of();
    /**
     * The query of a player looked up by name, noted as searched for once their profile is there;
     * by query rather than session, so Try Again, which makes a new session, still counts.
     */
    private String searchedPending;
    private String recentPicked;
    private int recentX, recentY, recentW;
    private static final int RECENT_MAX = 8;
    private static final int RECENT_ROW_H = 16;
    private static final int RECENT_LABEL_H = 14;
    /** A popover is open: the profile list, or the sites from the footer when {@link #sourceMenu}. */
    private boolean menuOpen = false;
    private boolean sourceMenu = false;
    private int sourceMenuX, sourceMenuY;
    private float indicatorY = Float.NaN;
    /** How far the sidebar is folded, 0 open to 1 down to its icons; starts where it was left. */
    private float sideFold = AlpakaConfig.instance.profileSidebarCollapsed ? 1.0f : 0.0f;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Runnable> overlays = new ArrayList<>();
    /** Areas, mostly items, greyed out above the content: what the profile doesn't have yet. */
    private final List<int[]> veils = new ArrayList<>();
    /** Corners of museum slots whose item is taken out right now, marked above the items like the veils. */
    private final List<int[]> borrowedDots = new ArrayList<>();
    private static final int VEIL = 0xA0131313;
    private final Scroll scroll = new Scroll();
    private Page scrolledPage = null;
    private String scrolledDetail = null;
    /**
     * The sub-page shown instead of the page's overview, null for none: the skill on the Skills page
     * ("farming", "mining"), the skill's collections on the Collections page, the island on the Bestiary page,
     * the category on the Museum page.
     */
    private String skillDetail = null;
    private String hoveredTip;
    private Object hoveredTipKey;
    private Object lastTipKey;
    private long tipSince;
    private int mx;
    private int my;
    /** The content area, which clips and scrolls; hover and clicks outside it don't count. */
    private int clipX, clipY, clipW, clipH;
    private boolean clipping;
    private ResolvableProfile face;
    private String faceFor;
    /** The player as a figure on the overview, made once per dashboard. */
    private final ProfileFigure figure = new ProfileFigure();
    /** Whether the right mouse button is down, which shows the figure without its armor. */
    private boolean rightHeld;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private static final class Scroll {
        double current;
        double target;
        double max;

        void update(float deltaSec) {
            target = Math.max(0, Math.min(max, target));
            current += (target - current) * Math.min(1.0f, deltaSec * 14.0f);
            if (Math.abs(target - current) < 0.05) current = target;
        }

        void by(double amount) {
            target = Math.max(0, Math.min(max, target + amount));
        }

        int offset() {
            return (int) Math.round(current);
        }
    }

    // ---- Scrollbar ------------------------------------------------------------------------------

    /** The thumb never gets shorter than this, so a page of hundreds of rows still has one to grab. */
    private static final int SCROLL_MIN_THUMB = 16;
    /** Where the track is, in the margin right of the content; {@code scrollbarShown} false while the page fits. */
    private boolean scrollbarShown;
    private int barX, barTop, barH, thumbH;
    /** Dragged by the thumb, and where on it it was grabbed, from its top. */
    private boolean scrollDragging;
    private double scrollGrab;
    /** 0 thin, 1 wide: the bar widens under the mouse and while it is dragged. */
    private float barWide;

    /**
     * The scrollbar along the content's right edge, for pages that run long - a networth list
     * folded out to hundreds of items, a wide search. Thin, and wider under the mouse; the thumb
     * can be dragged, and a press on the track jumps there.
     */
    private void drawScrollbar(GuiGraphicsExtractor graphics, float deltaSec) {
        scrollbarShown = scroll.max > 0;
        if (!scrollbarShown) {
            scrollDragging = false;
            return;
        }
        barX = clipX + clipW - 8;
        barTop = clipY + 4;
        barH = clipH - 8;
        double content = clipH + scroll.max;
        thumbH = Math.min(barH, Math.max(SCROLL_MIN_THUMB, (int) Math.round(barH * (clipH / content))));
        int thumbY = barTop + (int) Math.round((barH - thumbH) * (scroll.current / scroll.max));
        boolean near = !menuOpen && AlpakaStyle.inside(mx, my, barX - 2, barTop, 10, barH);
        barWide = PloppAnimation.interpolate(barWide, near || scrollDragging ? 1.0f : 0.0f, deltaSec, 18.0f);
        int w = Math.round(2 + 2 * barWide);
        int x = barX + 6 - w;
        if (barWide > 0.05f) ModernGuiUtils.drawRoundedRect(graphics, x, barTop, w, barH, w / 2, MarketIcons.fade(AlpakaStyle.TRACK, 0.6f * barWide));
        boolean onThumb = near && my >= thumbY && my < thumbY + thumbH;
        int color = scrollDragging ? AlpakaStyle.TEXT : onThumb ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED;
        ModernGuiUtils.drawRoundedRect(graphics, x, thumbY, w, thumbH, w / 2, color);
    }

    /** A press on the bar: grabs the thumb, or on the track moves the thumb's middle there first. */
    private boolean pressScrollbar(double mouseX, double mouseY) {
        if (!scrollbarShown || !AlpakaStyle.inside(mouseX, mouseY, barX - 2, barTop, 10, barH)) return false;
        int thumbY = barTop + (int) Math.round((barH - thumbH) * (scroll.current / scroll.max));
        if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
            scrollGrab = mouseY - thumbY;
        } else {
            scrollGrab = thumbH / 2.0;
            dragScrollbar(mouseY);
        }
        scrollDragging = true;
        return true;
    }

    private void dragScrollbar(double mouseY) {
        double travel = Math.max(1, barH - thumbH);
        double frac = Math.max(0, Math.min(1, (mouseY - scrollGrab - barTop) / travel));
        // Straight there, without the glide of the wheel: the thumb stays under the mouse.
        scroll.current = scroll.target = frac * scroll.max;
    }

    /** Where Escape goes back to: the config, when it was opened from there. */
    private final Screen parent;

    private ProfileDashboardScreen(ProfileSession session, Screen parent) {
        super(Component.literal("Profile Dashboard"));
        this.session = session;
        this.parent = parent;
    }

    /** Opens the dashboard on a player by name, or on the player's own profile with null. */
    public static void open(String player) {
        open(player, null);
    }

    /** As above, going back to {@code parent} when closed. */
    public static void open(String player, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        ProfileDashboardScreen screen = new ProfileDashboardScreen(session(player), parent);
        // Opening the own profile from its key or the config is no search; /ap with a name is.
        if (player != null && !player.isBlank()) screen.searchedPending = screen.session.query;
        mc.gui.setScreen(screen);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        super.removed();
        // The built items hold the profile's JSON; nothing needs them while no dashboard is open.
        InventoryItems.forget();
        ProfileChart.forgetVisible();
    }

    private static ProfileSession session(String player) {
        Minecraft mc = Minecraft.getInstance();
        String own = mc.getUser().getName();
        boolean isOwn = player == null || player.isBlank() || player.equalsIgnoreCase(own);
        String query = isOwn ? mc.getUser().getProfileId().toString().replace("-", "") : player.trim();
        String key = query.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        // Sessions past their time are never handed out again, so they go now instead of waiting to
        // be pushed out by newer ones.
        SESSIONS.values().removeIf(kept -> now - kept.at() >= SESSION_KEEP_MS);
        KeptSession cached = SESSIONS.get(key);
        if (cached != null && cached.session().state != ProfileSession.State.FAILED) {
            return cached.session();
        }
        ProfileSession fresh = new ProfileSession(query, isOwn);
        SESSIONS.put(key, new KeptSession(fresh, now));
        return fresh;
    }

    @Override
    protected void init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private int panelW() {
        return Math.max(Math.min(320, this.width), Math.min(700, this.width - 16));
    }

    private int panelH() {
        return Math.max(Math.min(160, this.height), Math.min(410, this.height - 16));
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        overlays.clear();
        pinTop = pinBottom = Integer.MIN_VALUE;
        veils.clear();
        borrowedDots.clear();
        hoveredTip = null;
        hoveredTipKey = null;
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;

        session.sync();
        // The name typed may be in any case, or no player at all: what counts is the profile that came.
        if (searchedPending != null && searchedPending.equals(session.query) && session.data != null) {
            ProfileHistory.searched(session.data.uuid);
            searchedPending = null;
        }
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        // With the profile list open, nothing under it lights up.
        mx = mouseX;
        my = mouseY;

        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        AlpakaStyle.panel(graphics, x, y, w, h);
        AlpakaStyle.headerBand(graphics, x, y, w);

        drawHeader(graphics, x, y, w);
        AlpakaStyle.headerLine(graphics, x, y, w);
        // Nothing under the list of recent players lights up while the mouse is on it.
        if (overRecent(mouseX, mouseY)) mx = my = -10_000;
        float foldTarget = AlpakaConfig.instance.profileSidebarCollapsed ? 1.0f : 0.0f;
        sideFold = PloppAnimation.interpolate(sideFold, foldTarget, deltaSec, 16.0f);
        if (Math.abs(sideFold - foldTarget) < 0.002f) sideFold = foldTarget;
        int sideW = Math.round(SIDE_W + (SIDE_W_COLLAPSED - SIDE_W) * sideFold);
        drawSidebar(graphics, x, y + HEADER_H, sideW, h - HEADER_H, deltaSec);
        ModernGuiUtils.drawRect(graphics, x + sideW, y + HEADER_H + 6, 1, h - HEADER_H - 12, AlpakaStyle.LINE);

        clipX = x + sideW + 1;
        clipY = y + HEADER_H;
        clipW = w - sideW - 1;
        clipH = h - HEADER_H - 4;
        // The mark of a search result goes once its page, tab or profile isn't shown any more.
        if (focused() && (session.data != focusData || !viewKey().equals(focusView))) dropMark();
        focusY = Integer.MIN_VALUE;
        // A search is a view of its own, and every new query starts at the top.
        String detail = searching() ? "\u0000search:" + itemSearch.getText() : skillDetail;
        if (scrolledPage != page || !java.util.Objects.equals(scrolledDetail, detail)) {
            scrolledPage = page;
            scrolledDetail = detail;
            scroll.current = scroll.target = 0;
        }
        graphics.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
        clipping = true;
        int top = clipY + PAD - scroll.offset();
        int bottom = drawContent(graphics, clipX + PAD + 2, top, clipW - 2 * PAD - 4, now);
        if (!veils.isEmpty() || !borrowedDots.isEmpty()) {
            // Items are drawn above everything else of their layer, so the veils over them need one of their own.
            graphics.nextStratum();
            for (int[] v : veils) graphics.fill(v[0], v[1], v[0] + v[2], v[1] + v[3], VEIL);
            for (int[] dot : borrowedDots) ModernGuiUtils.drawRoundedRect(graphics, dot[0], dot[1], 4, 4, 2, ink(GOLD));
        }
        clipping = false;
        scroll.max = Math.max(0, bottom + scroll.offset() - clipY - clipH + PAD);
        // A search result's item or pet scrolls into the middle of the view, as far as the page lets
        // it, once the view has settled: a sub-page found while drawing starts at the top next frame.
        if (focusScroll && focusY != Integer.MIN_VALUE && !searching() && scrolledPage == page
                && java.util.Objects.equals(scrolledDetail, skillDetail)) {
            scroll.target = Math.max(0, Math.min(scroll.max, scroll.offset() + focusY + focusH / 2.0 - (clipY + clipH / 2.0)));
            focusScroll = false;
        }
        if (devScrollToEnd) scroll.current = scroll.target = scroll.max;
        scroll.update(deltaSec);
        AlpakaStyle.scrollFades(graphics, clipX, clipY, clipW, clipH, scroll.offset() > 0, scroll.offset() + 1 < scroll.max);
        drawScrollbar(graphics, deltaSec);
        graphics.disableScissor();

        for (Runnable overlay : overlays) overlay.run();
        menuHitsFrom = hits.size();
        if (menuOpen && sourceMenu) drawSourceMenu(graphics);
        else if (menuOpen) drawProfileMenu(graphics, x, y, w);
        mx = mouseX;
        my = mouseY;
        if (!recent.isEmpty()) drawRecent(graphics);

        if (hoveredTipKey != lastTipKey) {
            lastTipKey = hoveredTipKey;
            tipSince = now;
        }
        if (hoveredTip != null && now - tipSince >= 250) {
            AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, hoveredTip);
        }
        if (hoveredItem != null) {
            graphics.setComponentTooltipForNextFrame(this.font, InventoryItems.tooltip(hoveredItem), mouseX, mouseY);
            hoveredItem = null;
        } else if (hoveredLines != null) {
            graphics.setComponentTooltipForNextFrame(this.font, hoveredLines, mouseX, mouseY);
        }
        hoveredLines = null;
        AlpakaStyle.endOpen(graphics, pushed);
    }

    // ---- Header ---------------------------------------------------------------------------------

    private int profileChipX, profileChipW;
    /** Where the open profile list's rows start among the click areas; only they take clicks then. */
    private int menuHitsFrom;

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w) {
        ProfileSession s = session;
        int faceSize = 16;
        int fx = x + PAD + 4;
        int fy = y + (HEADER_H - faceSize) / 2;
        ResolvableProfile profile = face();
        if (profile != null) {
            net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(graphics, profile, fx, fy, faceSize);
        } else {
            ModernGuiUtils.drawRoundedRect(graphics, fx, fy, faceSize, faceSize, 3, AlpakaStyle.ROW_SELECTED);
        }
        int tx = fx + faceSize + 7;
        String name = s.playerName != null ? s.playerName : s.own ? Minecraft.getInstance().getUser().getName() : s.query;
        int ty = y + (HEADER_H - 8) / 2;
        text(graphics, name, tx, ty, AlpakaStyle.TEXT);
        tx += textW(name) + 8;

        // The profile, as a chip that opens the list of the player's profiles.
        ProfileData d = s.data;
        String profileName = d != null && d.profileName != null ? d.profileName : profileNameOf(s);
        if (profileName != null) {
            String label = profileName + modeMark(s);
            int cw = textW(label) + 20;
            boolean hover = AlpakaStyle.inside(mx, my, tx, ty - 3, cw, 14) && !menuOpen;
            boolean chipOpen = menuOpen && !sourceMenu;
            int bg = chipOpen ? MarketIcons.fade(AlpakaStyle.accent(), 0.3f) : hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW;
            ModernGuiUtils.drawRoundedRect(graphics, tx, ty - 3, cw, 14, 7, bg);
            text(graphics, label, tx + 7, ty, chipOpen || hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            AlpakaStyle.chevron(graphics, tx + cw - 7, ty + 4, chipOpen, AlpakaStyle.TEXT_MUTED);
            profileChipX = tx;
            profileChipW = cw;
            if (s.profiles.size() > 1) {
                headerHit(tx, ty - 3, cw, 14, () -> {
                    menuOpen = !menuOpen;
                    sourceMenu = false;
                });
                if (hover) tip("profiles", "Switch to another of " + name + "'s profiles.");
            }
        }

        // Right side: close, refresh, and the player search.
        int size = 16;
        int bx = x + w - PAD - size;
        int by = y + (HEADER_H - size) / 2;
        boolean hoverClose = AlpakaStyle.inside(mx, my, bx, by, size, size);
        if (hoverClose) ModernGuiUtils.drawRoundedRect(graphics, bx, by, size, size, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.ROW_SELECTED);
        MarketIcons.draw(graphics, MarketIcons.Glyph.CLOSE, bx + 4, by + 4, 8, hoverClose ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
        headerHit(bx, by, size, size, this::onClose);

        String refresh = s.refreshing ? "Refreshing…" : "Refresh";
        int rw = textW(refresh) + 14;
        int rx = bx - 6 - rw;
        boolean hoverRefresh = AlpakaStyle.inside(mx, my, rx, by, rw, size);
        AlpakaStyle.button(graphics, this.font, rx, by - 1, rw, AlpakaStyle.BUTTON_H, refresh, hoverRefresh, AlpakaStyle.ButtonKind.PLAIN,
                s.state == ProfileSession.State.READY && !s.refreshing);
        if (s.state == ProfileSession.State.READY && !s.refreshing) {
            headerHit(rx, by, rw, size, s::refresh);
            if (hoverRefresh) {
                ProfileData shown = s.data;
                String fresh = shown != null && shown.lastUpdated > 0 ? "Updated " + Metrics.ago(shown.lastUpdated * 1000L) + ". " : "";
                tip("refresh", fresh + "Asks Hypixel for the profile again, at most once a minute.");
            }
        }

        int sw = Math.min(120, Math.max(70, w / 6));
        int sx = rx - 8 - sw;
        recent = List.of();
        if (sx > tx + 90) {
            boolean hoverSearch = AlpakaStyle.inside(mx, my, sx, by - 1, sw, AlpakaStyle.BUTTON_H);
            AlpakaStyle.textField(graphics, this.font, sx, by - 1, sw, AlpakaStyle.BUTTON_H, search, null, "Look up a player",
                    searchFocused, hoverSearch, caretFrom);
            headerHit(sx, by - 1, sw, AlpakaStyle.BUTTON_H, () -> {
                if (click != null) AlpakaStyle.clickTextField(this.font, search, null, sx, sw, click.x(), clickFrom == search, click.hasShiftDown());
                searchFocused = true;
                caretFrom = System.currentTimeMillis();
            });
            if (hoverSearch && !searchFocused) tip("search", "Type a player's name and press Enter to see their profile. Click in to pick one you looked up before.");
            if (searchFocused) {
                recent = ProfileHistory.recent(ProfileHistory.players(), search.getText(), name, RECENT_MAX);
                if (recentPicked != null && !recent.contains(recentPicked)) recentPicked = null;
                int widest = 0;
                for (String player : recent) widest = Math.max(widest, textW(player));
                recentW = Math.max(sw, widest + 20);
                // Under the field, from its left edge, unless that would run past the window.
                recentX = Math.min(sx, x + w - PAD - recentW);
                recentY = by - 1 + AlpakaStyle.BUTTON_H + 3;
            }
        }
    }

    private int recentH() {
        return RECENT_LABEL_H + recent.size() * RECENT_ROW_H + 6;
    }

    private boolean overRecent(double mouseX, double mouseY) {
        return !recent.isEmpty() && AlpakaStyle.inside(mouseX, mouseY, recentX, recentY, recentW, recentH());
    }

    /** The players looked up before, under the focused player search: a click looks one up again. */
    private void drawRecent(GuiGraphicsExtractor graphics) {
        int px = recentX;
        int py = recentY;
        int mw = recentW;
        graphics.nextStratum();
        AlpakaStyle.popover(graphics, px, py, mw, recentH());
        // A click on the list between its rows keeps the field, rather than reaching what is under it.
        hits.add(new Hit(px, py, mw, recentH(), () -> searchFocused = true));
        text(graphics, "Recent", px + 10, py + 6, AlpakaStyle.TEXT_MUTED);
        int ry = py + RECENT_LABEL_H + 2;
        for (String player : recent) {
            boolean hover = AlpakaStyle.inside(mx, my, px + 4, ry, mw - 8, RECENT_ROW_H);
            boolean lit = hover || player.equals(recentPicked);
            if (lit) ModernGuiUtils.drawRoundedRect(graphics, px + 4, ry, mw - 8, RECENT_ROW_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            text(graphics, player, px + 10, ry + 4, lit ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            hits.add(new Hit(px + 4, ry, mw - 8, RECENT_ROW_H, () -> lookUp(player)));
            ry += RECENT_ROW_H;
        }
    }

    private static String profileNameOf(ProfileSession s) {
        for (ProfileSession.ProfileRef r : s.profiles) if (r.id().equals(s.profileId)) return r.name();
        return null;
    }

    /** The shown profile's game mode, such as "ironman" or "bingo"; null for a normal one. */
    /** Whether the profile on show is a co-op, whose bank is shared and each member's personal bank apart. */
    private static boolean coop(ProfileSession s) {
        for (ProfileSession.ProfileRef r : s.profiles) if (r.id().equals(s.profileId)) return r.members() > 1;
        return false;
    }

    private static String gameMode(ProfileSession s) {
        for (ProfileSession.ProfileRef r : s.profiles) if (r.id().equals(s.profileId)) return r.gameMode();
        return null;
    }

    /** A mark after the profile's name for the special modes. */
    private static String modeMark(ProfileSession s) {
        String mode = gameMode(s);
        if (mode == null) return "";
        return switch (mode) {
            case "ironman" -> " (Ironman)";
            case "bingo" -> " (Bingo)";
            case "island" -> " (Stranded)";
            default -> "";
        };
    }

    private void drawProfileMenu(GuiGraphicsExtractor graphics, int x, int y, int w) {
        List<ProfileSession.ProfileRef> refs = session.profiles;
        int rowH = 16;
        int mw = Math.max(profileChipW, 130);
        int mh = refs.size() * rowH + 8;
        int px = profileChipX;
        int py = y + HEADER_H - 4;
        graphics.nextStratum();
        AlpakaStyle.popover(graphics, px, py, mw, mh);
        int ry = py + 4;
        for (ProfileSession.ProfileRef r : refs) {
            boolean current = r.id().equals(session.profileId);
            boolean hover = AlpakaStyle.inside(mx, my, px + 4, ry, mw - 8, rowH);
            if (hover || current) {
                ModernGuiUtils.drawRoundedRect(graphics, px + 4, ry, mw - 8, rowH, AlpakaStyle.ROW_RADIUS,
                        current ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
            }
            if (current) ModernGuiUtils.drawRoundedRect(graphics, px + 4, ry + 4, 2, rowH - 8, 1, AlpakaStyle.accent());
            String label = (r.name() == null ? "?" : r.name()) + (r.gameMode() == null ? "" : "  " + ProfileData.title(r.gameMode()));
            text(graphics, label, px + 11, ry + 4, current || hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            if (r.selected()) {
                String tag = "Active";
                text(graphics, tag, px + mw - 10 - textW(tag), ry + 4, AlpakaStyle.TEXT_MUTED);
            }
            String id = r.id();
            hits.add(new Hit(px + 4, ry, mw - 8, rowH, () -> {
                menuOpen = false;
                session.switchProfile(id);
            }));
            ry += rowH;
        }
    }

    /** The sites the footer's "Data Sources" offers to open the profile on. */
    private void drawSourceMenu(GuiGraphicsExtractor graphics) {
        String player = session.playerName;
        if (player == null) {
            menuOpen = false;
            return;
        }
        ProfileData d = session.data;
        String profile = d != null && d.profileName != null ? d.profileName : null;
        String[][] sites = {
            {"Elite", ProfileApi.ELITE_SITE + player + (profile != null ? "/" + profile : "")},
            {"SkyCrypt", ProfileApi.SKYCRYPT_SITE + player + (profile != null ? "/" + profile : "")},
            {"SkyKings", ProfileApi.SKYKINGS_SITE + player},
        };
        int rowH = 16;
        int mw = 0;
        for (String[] site : sites) mw = Math.max(mw, textW("Open on " + site[0]));
        mw += 22;
        int mh = sites.length * rowH + 8;
        int px = sourceMenuX;
        int py = Math.max(4, sourceMenuY - mh - 2);
        graphics.nextStratum();
        AlpakaStyle.popover(graphics, px, py, mw, mh);
        int ry = py + 4;
        for (String[] site : sites) {
            boolean hover = AlpakaStyle.inside(mx, my, px + 4, ry, mw - 8, rowH);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, px + 4, ry, mw - 8, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            text(graphics, "Open on " + site[0], px + 11, ry + 4, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            String title = "Open " + site[0];
            String url = site[1];
            hits.add(new Hit(px + 4, ry, mw - 8, rowH, () -> {
                menuOpen = false;
                sourceMenu = false;
                openLink(title, url);
            }));
            ry += rowH;
        }
    }

    /** The player's face: from the skin the API names, or the game's own profile for the player. */
    private ResolvableProfile face() {
        ProfileSession s = session;
        String key = s.skinHash != null ? s.skinHash : s.own ? "own" : null;
        if (key == null) return null;
        if (!key.equals(faceFor)) {
            faceFor = key;
            if (s.skinHash != null) {
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + s.skinHash + "\"}}}";
                face = ItemIcons.profile(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
            } else {
                face = ResolvableProfile.createUnresolved(Minecraft.getInstance().getUser().getProfileId());
            }
        }
        return face;
    }

    // ---- Sidebar --------------------------------------------------------------------------------

    private void drawSidebar(GuiGraphicsExtractor graphics, int x, int y, int sideW, int h, float deltaSec) {
        int rowX = x + 4;
        int rowW = sideW - 8;
        // Fully open, or on its way to or from its icons: names, the search and the footer only
        // show where they fit, so nothing spills over the page while the sidebar slides.
        boolean open = sideW == SIDE_W;
        boolean folded = AlpakaConfig.instance.profileSidebarCollapsed;
        int pages = Page.values().length;

        // On a small window the footer gives way first, then the rows get slimmer. The label stays:
        // the item search opens from it. Whether the footer fits doesn't depend on the fold, so the
        // rows keep their height while the sidebar slides.
        int footerH = 30;
        boolean footer = h >= 12 + pages * 17 + footerH + 12;
        int rowH = Math.max(14, Math.min(SIDE_ROW_H, (h - 10 - 12 - (footer ? footerH : 0)) / pages - 1));
        int rowY = y + 8;
        // While the sidebar slides, the label and the footer fade out over the first half of the way
        // and the globe that stands in for the footer fades in over the second, so nothing pops in
        // or out at either end.
        float wide = Math.max(0.0f, 1.0f - 2.0f * sideFold);
        float slim = Math.max(0.0f, 2.0f * sideFold - 1.0f);
        graphics.enableScissor(x, y, x + sideW, y + h);

        // The arrow that folds the sidebar down to its icons and opens it again: at the end of the
        // label's line, or alone in the middle while folded, gliding between the two.
        int arrow = 12;
        int ax = rowX + Math.round((rowW - arrow) * (1.0f - sideFold / 2.0f));
        int ay = rowY - 3;
        boolean hoverArrow = AlpakaStyle.inside(mx, my, ax, ay, arrow, arrow) && !menuOpen;
        AlpakaStyle.chevronButton(graphics, ax, ay, arrow, !folded, hoverArrow, true);
        hits.add(new Hit(ax, ay, arrow, arrow, () -> {
            AlpakaConfig.instance.profileSidebarCollapsed = !AlpakaConfig.instance.profileSidebarCollapsed;
            AlpakaConfig.save();
            if (AlpakaConfig.instance.profileSidebarCollapsed) {
                // The field has no room in the slim sidebar, so the search goes with it.
                itemSearch.setText("");
                itemSearchOpen = false;
                itemSearchFocused = false;
            }
        }));
        if (hoverArrow) tip("sidefold", folded ? "Show the page names." : "Fold the sidebar down to its icons.");

        if (wide <= 0.0f) {
            // No label or search while folded.
        } else if (open && (itemSearchOpen || !itemSearch.isEmpty())) {
            // The search through every item the profile owns, where the label was.
            int fh = 14;
            int fy = rowY - 3;
            boolean hoverField = AlpakaStyle.inside(mx, my, rowX, fy, rowW, fh) && !menuOpen;
            AlpakaStyle.textField(graphics, this.font, rowX, fy, rowW - arrow - 2, fh, itemSearch, null, "Search Items",
                    itemSearchFocused, hoverField, itemCaretFrom);
            hits.add(new Hit(rowX, fy, rowW - arrow - 2, fh, () -> {
                if (click != null) {
                    AlpakaStyle.clickTextField(this.font, itemSearch, null, rowX, rowW - arrow - 2, click.x(),
                            clickFrom == itemSearch, click.hasShiftDown());
                }
                itemSearchFocused = true;
                itemCaretFrom = System.currentTimeMillis();
            }));
        } else {
            text(graphics, "Profile", x + PAD, rowY, AlpakaStyle.fade(AlpakaStyle.TEXT_MUTED, wide));
            // The magnifier just after the label opens the item search in its place.
            // Ten pixels, centered on the label's capitals rather than its full height with the
            // descenders, which sat it a pixel low: at eight the glass was too small to read as round.
            int gx = x + PAD + textW("Profile") + 4;
            int gy = rowY - 2;
            boolean hoverGlass = open && AlpakaStyle.inside(mx, my, gx - 2, gy - 2, 14, 14) && !menuOpen;
            MarketIcons.draw(graphics, MarketIcons.Glyph.SEARCH, gx, gy, 10,
                    AlpakaStyle.fade(hoverGlass ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED, wide));
            if (open) hits.add(new Hit(gx - 2, gy - 2, 14, 14, () -> {
                itemSearchOpen = true;
                itemSearchFocused = true;
                itemCaretFrom = System.currentTimeMillis();
            }));
            if (hoverGlass) {
                tip("itemsearch", "Search every item the profile owns: inventory, Ender Chest, backpacks, wardrobe, accessories, vault, bags, museum, sacks and pets.");
            }
        }
        rowY += 12;

        float targetY = rowY + page.ordinal() * (rowH + 1);
        if (Float.isNaN(indicatorY)) indicatorY = targetY;
        indicatorY = PloppAnimation.interpolate(indicatorY, targetY, deltaSec, 16.0f);
        int iy = Math.round(indicatorY);
        ModernGuiUtils.drawRoundedRect(graphics, rowX, iy, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_SELECTED);
        ModernGuiUtils.drawRoundedRect(graphics, rowX, iy + 4, 2, rowH - 8, 1, AlpakaStyle.accent());

        for (Page p : Page.values()) {
            boolean hover = AlpakaStyle.inside(mx, my, rowX, rowY, rowW, rowH) && !menuOpen;
            if (hover && p != page) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            // Items only at their full 16 pixels: scaled down, the game's nearest-neighbour sampling
            // drops rows and columns unevenly and they turn blotchy. Rows too slim for that go without.
            // Folded, the icons are all there is, so they stay even in slim rows.
            boolean icons = rowH >= 18 || !open;
            int ix = rowX + Math.min(5, (rowW - 16) / 2);
            if (icons) {
                ItemStack skyblock = p.skyblockIcon == null ? ItemStack.EMPTY : ItemIcons.forItem(p.skyblockIcon, null);
                if (!skyblock.isEmpty()) stackIcon(graphics, skyblock, ix, rowY + (rowH - 16) / 2, 1.0f);
                else icon(graphics, p.icon, ix, rowY + (rowH - 16) / 2, 1.0f);
            }
            int tx = rowX + (icons ? 27 : 10);
            if (tx + textW(p.title) <= rowX + rowW - 2) {
                text(graphics, p.title, tx, rowY + (rowH - 8) / 2, p == page || hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            } else if (hover) {
                tip(p, p.title);
            }
            Page target = p;
            hits.add(new Hit(rowX, rowY, rowW, rowH, () -> {
                page = target;
                lastPage = target;
                skillDetail = null;
                itemSearch.setText("");
                itemSearchOpen = false;
            }));
            rowY += rowH + 1;
        }
        // The "open elsewhere" sign opens the same sites as the footer. On a short window it stands in
        // for the footer for good; otherwise it takes the footer's place as the sidebar folds, in the
        // folded sidebar's middle, so it doesn't travel with the sliding edge. A globe of eleven
        // pixels read as nothing in particular.
        int size = 16;
        float sign = footer ? slim : 1.0f;
        int gx = footer ? x + (SIDE_W_COLLAPSED - size) / 2 : rowX + (rowW - size) / 2;
        int gy = y + h - size - 5;
        if (sign > 0.0f && session.playerName != null && gy >= rowY + 2) {
            boolean live = !footer || sideFold >= 0.5f;
            boolean hoverLink = live && AlpakaStyle.inside(mx, my, gx, gy, size, size) && !menuOpen;
            if (hoverLink) {
                ModernGuiUtils.drawRoundedRect(graphics, gx, gy, size, size, AlpakaStyle.FIELD_RADIUS,
                        AlpakaStyle.fade(AlpakaStyle.ROW_SELECTED, sign));
            }
            MarketIcons.draw(graphics, MarketIcons.Glyph.LINK, gx + 2.5f, gy + 2.5f, size - 5,
                    AlpakaStyle.fade(hoverLink ? ModernGuiUtils.getAccentColor() : AlpakaStyle.TEXT_SOFT, sign));
            if (live) {
                hits.add(new Hit(gx, gy, size, size, () -> openSourceMenu(gx, gy)));
                if (hoverLink) tip("source", sourcesTip());
            }
        }

        if (footer && wide > 0.0f) {
            // At the bottom: how fresh the numbers are, and where they come from.
            boolean live = sideFold < 0.5f;
            int fy = y + h - footerH;
            ProfileData d = session.data;
            if (d != null && d.lastUpdated > 0) {
                String updated = "Updated " + Metrics.ago(d.lastUpdated * 1000L);
                // What is on show stays when asking again failed (or the kept profile, when the new one
                // didn't come); the reason is a hover away.
                String failed = session.refreshing ? null : session.error;
                if (failed != null) updated += " · Couldn't refresh";
                text(graphics, updated, x + PAD, fy, AlpakaStyle.fade(AlpakaStyle.TEXT_MUTED, wide));
                if (live && failed != null && AlpakaStyle.inside(mx, my, x + PAD - 2, fy - 2, textW(updated) + 4, 11)) {
                    tip("refresh-failed", failed);
                }
            }
            String source = "Data Sources";
            int sw = textW(source);
            boolean hoverSource = live && AlpakaStyle.inside(mx, my, x + PAD - 2, fy + 10, sw + 4, 11) && !menuOpen;
            text(graphics, source, x + PAD, fy + 12,
                    AlpakaStyle.fade(hoverSource ? AlpakaStyle.accent() : AlpakaStyle.DIVIDER, wide));
            if (live && session.playerName != null) {
                int sx = x + PAD - 2;
                int sy = fy + 10;
                hits.add(new Hit(sx, sy, sw + 4, 11, () -> openSourceMenu(sx, sy)));
                if (hoverSource) tip("source", sourcesTip());
            }
        }
        graphics.disableScissor();
    }

    /** Opens the list of sites above the given point. */
    private void openSourceMenu(int x, int y) {
        sourceMenuX = x;
        sourceMenuY = y;
        sourceMenu = true;
        menuOpen = true;
    }

    private String sourcesTip() {
        boolean skykings = !AlpakaConfig.instance.skykingsApiKey.isBlank();
        return "Profile data from Hypixel's API, through the AlpakaAddons API (api.alpaka.yt). The networth is worked "
                + "out here, with the Bazaar's prices from Hypixel and the lowest BINs from SkyCofl, as Item Value does it. "
                + "The history comes from Soopy (soopy.dev); pet heads, the Bestiary, the Museum, crop milestones and "
                + "Hoppity's rabbits from the NEU repository; Farming Weight by Elite's formula (elitebot.dev). Soopy works "
                + "networth out differently, so its older networth is scaled to match."
                + (skykings ? " With your key, SkyKings' daily history too" + (session.skykingsNote != null
                        && !session.skykingsNote.equals("loading") ? ": " + session.skykingsNote : ".")
                        : " A SkyKings API key in the config adds SkyKings' daily history.")
                + " Not affiliated with Hypixel. Click to open this profile on Elite, SkyCrypt or SkyKings.";
    }

    // ---- Content --------------------------------------------------------------------------------

    /** Draws the page and returns where it ends. */
    private int drawContent(GuiGraphicsExtractor graphics, int x, int y, int w, long now) {
        ProfileSession s = session;
        if (!AlpakaConfig.instance.allowApiCalls) {
            int h = clipH - 2 * PAD;
            ProfileChart.message(graphics, this.font, "The dashboard loads profiles from Hypixel's API, so it needs Network Features.", x, y, w, h / 2);
            int bw = 150;
            int bx = x + (w - bw) / 2;
            int by = y + h / 2 + 4;
            boolean hover = hover(bx, by, bw, AlpakaStyle.BUTTON_H);
            AlpakaStyle.button(graphics, this.font, bx, by, bw, AlpakaStyle.BUTTON_H, "Turn On Network Features", hover, AlpakaStyle.ButtonKind.PRIMARY);
            hit(bx, by, bw, AlpakaStyle.BUTTON_H, () -> {
                AlpakaConfig.instance.allowApiCalls = true;
                AlpakaConfig.save();
                session = new ProfileSession(s.query, s.own);
            });
            return y + h;
        }
        if (s.data == null) {
            int h = clipH - 2 * PAD;
            if (s.state == ProfileSession.State.FAILED) {
                ProfileChart.message(graphics, this.font, s.error == null ? "Couldn't load the profile." : s.error, x, y, w, h / 2);
                int bw = 90;
                int bx = x + (w - bw) / 2;
                int by = y + h / 2 + 4;
                boolean hover = hover(bx, by, bw, AlpakaStyle.BUTTON_H);
                AlpakaStyle.button(graphics, this.font, bx, by, bw, AlpakaStyle.BUTTON_H, "Try Again", hover, AlpakaStyle.ButtonKind.PLAIN);
                hit(bx, by, bw, AlpakaStyle.BUTTON_H, () -> {
                    String key = s.query.toLowerCase(Locale.ROOT);
                    SESSIONS.remove(key);
                    session = session(s.own ? null : s.query);
                });
            } else {
                drawLoading(graphics, x, y, w, h, now);
            }
            return y + h;
        }
        ProfileData d = s.data;
        if (searching()) return itemResults(graphics, d, x, y, w);
        return switch (page) {
            case OVERVIEW -> overview(graphics, d, x, y, w, now);
            case SKILLS -> skills(graphics, d, x, y, w);
            case DUNGEONS -> dungeons(graphics, d, x, y, w);
            case SLAYERS -> slayers(graphics, d, x, y, w);
            case NETWORTH -> networth(graphics, d, x, y, w);
            case PETS -> pets(graphics, d, x, y, w);
            case COLLECTIONS -> collections(graphics, d, x, y, w);
            case INVENTORY -> inventories(graphics, d, x, y, w);
            case BESTIARY -> bestiary(graphics, d, x, y, w);
            case MUSEUM -> museum(graphics, d, x, y, w);
            case RIFT -> rift(graphics, d, x, y, w);
            case CHOCOLATE -> chocolate(graphics, d, x, y, w);
        };
    }

    /** Cards where the page will be, softly pulsing, while the profile loads. */
    private void drawLoading(GuiGraphicsExtractor graphics, int x, int y, int w, int h, long now) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 280.0);
        int color = ModernGuiUtils.lerpColor(AlpakaStyle.ROW, AlpakaStyle.ROW_HOVER, pulse);
        int cols = 4;
        int tw = (w - (cols - 1) * 6) / cols;
        for (int i = 0; i < cols; i++) ModernGuiUtils.drawRoundedRect(graphics, x + i * (tw + 6), y, tw, 46, AlpakaStyle.CARD_RADIUS, color);
        ModernGuiUtils.drawRoundedRect(graphics, x, y + 52, w, Math.max(40, h - 52 - 30), AlpakaStyle.CARD_RADIUS, color);
        String who = session.playerName != null ? session.playerName : session.own ? "your profile" : session.query;
        String dots = ".".repeat((int) (now / 400 % 4));
        String label = "Loading " + who + dots;
        text(graphics, label, x + (w - textW("Loading " + who + "...")) / 2, y + h - 18, AlpakaStyle.TEXT_MUTED);
    }

    // ---- Overview -------------------------------------------------------------------------------

    private int overview(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w, long now) {
        y = sinceLastVisit(graphics, d, x, y, w);

        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.NETWORTH, "Networth", networthText(d), null));
        tiles.add(new Tile(Metrics.SB_LEVEL, "SkyBlock Level", String.format(Locale.US, "%.2f", d.skyblockLevel), null));
        tiles.add(new Tile(Metrics.SKILL_AVG, "Skill Average", d.skillsApi ? String.format(Locale.US, "%.2f", d.skillAverage) : "API off", null));
        tiles.add(new Tile(Metrics.CATACOMBS, "Catacombs", String.format(Locale.US, "%.2f", Levels.catacombs(d.catacombs.xp())), null));
        tiles.add(new Tile(Metrics.SLAYER_TOTAL, "Slayer XP", Coins.compact(d.slayerXp), null));
        tiles.add(new Tile(Metrics.MAGICAL_POWER, "Magical Power", Coins.full(d.magicalPower), null));
        tiles.add(coinsTile(d));
        tiles.add(d.farmingWeight > 0
                ? new Tile(Metrics.FARMING_WEIGHT, "Farming Weight", Coins.full(Math.round(d.farmingWeight * 10) / 10.0), null)
                : new Tile(Metrics.RUNS, "Dungeon Runs", Coins.full(d.totalRuns), null));
        y = tiles(graphics, tiles, x, y, w, 4);

        // The player beside the graph, as profile viewers show them, the two as tall as each
        // other; on the narrowest the graph alone.
        String metric = metricFor(Page.OVERVIEW);
        int chartH = 150;
        int figureW = Math.max(100, Math.min(160, w / 5));
        if (w - figureW - 6 >= 180) {
            figureCard(graphics, d, x, y + 6, figureW, chartH, now);
            y = chartCard(graphics, metric, metricMaxLevel(d, metric), x + figureW + 6, y + 6, w - figureW - 6, chartH);
        } else {
            y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y + 6, w, 130);
        }

        int colW = (w - 6) / 2;
        int leftEnd = nextUp(graphics, d, x, y + 6, w >= 420 ? colW : w);
        int rightEnd = w >= 420 ? milestones(graphics, d, x + colW + 6, y + 6, colW, now) : milestones(graphics, d, x, leftEnd + 6, w, now);
        return Math.max(leftEnd, rightEnd);
    }

    /**
     * The coins on hand: the purse and the bank side by side, and on hover the exact amounts, the
     * bank split into the co-op's and the player's own when there are both.
     */
    private static Tile coinsTile(ProfileData d) {
        double bank = d.bank + d.personalBank;
        boolean split = d.bank > 0 && d.personalBank > 0;
        String tip = "Purse: " + Coins.full(Math.floor(d.purse)) + " coins\nBank: " + Coins.full(Math.floor(bank)) + " coins"
                + (split ? "\n  Co-op: " + Coins.full(Math.floor(d.bank)) + "\n  Personal: " + Coins.full(Math.floor(d.personalBank)) : "");
        return pair("Purse", Coins.compact(d.purse), "Bank", Coins.compact(bank), null, tip);
    }

    /**
     * The player in the armor they wear, with the pet they have out floating beside them, named in
     * its tooltip. Outside a world, where no figure can be made, the face instead.
     */
    private void figureCard(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w, int h, long now) {
        card(graphics, x, y, w, h);
        ProfileData.Pet pet = null;
        for (ProfileData.Pet p : d.pets) {
            if (p.active()) {
                pet = p;
                break;
            }
        }
        int petSize = w >= 130 ? 32 : 24;
        int petW = pet != null ? petSize + 2 : 0;
        int statusH = onlineStatus(graphics, x, y, w);
        // The figure's box: what is left beside the pet and below the status.
        int bx = x + 6;
        int by = y + 8 + statusH;
        int bw = w - 12 - petW;
        int bh = h - 14 - statusH;
        // A player in armor is a little over two blocks tall, turned towards the mouse, and about
        // one wide, arms included.
        int scale = (int) Math.max(8, Math.min(bh / 2.5f, bw / 1.1f));
        String uuid = session.uuid != null ? session.uuid : d.uuid;
        // Looks at the mouse wherever it is on the screen, the sidebar and header too, as the
        // inventory's player does; only stopping at the content's edge read as a glitch.
        float lookX = mx;
        float lookY = my;
        // Right mouse held on the card takes the armor off, to show the skin under it.
        boolean bare = rightHeld && hover(x, y, w, h);
        boolean drawn = uuid != null && figure.draw(graphics, uuid, bare ? null : session.inventory("armor"), bx, by, bx + bw, by + bh,
                scale, lookX, lookY);
        if (drawn && !bare && hover(bx, by, bw, bh)) {
            // The piece under the mouse shows its tooltip, as in an inventory.
            String slot = ProfileFigure.pieceAt(mx - (bx + bw / 2f), my - (by + bh / 2f), scale);
            JsonElement armor = session.inventory("armor");
            JsonElement item = slot == null || armor == null ? null : Json.obj(armor, "items").get(slot);
            if (InventoryItems.present(item)) hoveredItem = item;
        }
        if (!drawn) {
            ResolvableProfile profile = face();
            int size = Math.min(48, Math.min(bw, bh) - 8);
            if (profile != null && size > 8) {
                net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(graphics, profile,
                        bx + (bw - size) / 2, by + (bh - size) / 2, size);
            }
        }
        if (pet == null) return;
        ItemStack head = PetIcons.icon(pet);
        // Floating up and down, a little over a second each way. Moved by fractions of a pixel, not
        // whole ones: stepping a pixel at a time read as lag.
        float bob = (float) Math.sin(now * (2 * Math.PI / 2600.0)) * petSize * 0.2f;
        int px = x + w - 6 - petSize;
        int py = by + Math.max((int) Math.ceil(petSize * 0.2f), (int) (bh / 2 - scale * 0.85f));
        if (head != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(px, py + bob);
            graphics.pose().scale(petSize / 16f, petSize / 16f);
            graphics.item(head, 0, 0);
            graphics.pose().popMatrix();
        }
        // Over the whole height it floats through, so the tooltip doesn't flicker as it moves.
        int reach = (int) Math.ceil(petSize * 0.2f);
        if (hover(px, py - reach, petSize, petSize + 2 * reach)) {
            String held = pet.heldItem() == null ? "" : "\nHeld Item: " + heldItem(pet.heldItem());
            tip("figurePet", pet.name() + " · Lvl " + pet.level() + held + "\n" + petTip(pet));
        }
    }

    /**
     * Whether the player is online and where, at the top of the figure's card: a green dot and the
     * island, or a grey one and "Offline". Nothing while it is asked for, or when it couldn't be. Returns the
     * height it takes, which is kept either way so the figure doesn't jump when it arrives.
     */
    private int onlineStatus(GuiGraphicsExtractor graphics, int x, int y, int w) {
        int h = 14;
        PlayerStatus status = session.status;
        if (status == null) return h;
        int ty = y + 8;
        // The green dot says online, so the words can be the place alone: the card is narrow.
        String where = status.where();
        String label = AlpakaStyle.ellipsize(this.font, where != null ? where : status.online() ? "Online" : "Offline", w - 16 - 8);
        int lw = 8 + textW(label);
        int lx = x + (w - lw) / 2;
        ModernGuiUtils.drawRoundedRect(graphics, lx, ty + 2, 5, 5, 2, status.online() ? ink(ProfileChart.UP) : AlpakaStyle.TEXT_MUTED);
        text(graphics, label, lx + 8, ty, status.online() ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
        if (hover(x, y + 4, w, h)) {
            tip("onlineStatus", status.online()
                    ? "Online on Hypixel: " + status.fullWhere() + "."
                    : "Offline, or hiding it: players can turn their online status off in Hypixel's API settings.");
        }
        return h;
    }

    /** A strip saying what grew since the previous visit; nothing on the first one. */
    private int sinceLastVisit(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        ProfileHistory.Snapshot before = session.previousVisit;
        if (before == null || !session.own) return y;
        Map<String, Double> nowMetrics = d.metrics();
        List<String> parts = new ArrayList<>();
        for (String key : new String[] {Metrics.NETWORTH, Metrics.SB_LEVEL, Metrics.SKILL_AVG, Metrics.CATACOMBS,
                Metrics.SLAYER_TOTAL, Metrics.MAGICAL_POWER, Metrics.PETS_MAXED, Metrics.RUNS, Metrics.KUUDRA}) {
            Double a = before.m().get(key);
            Double b = nowMetrics.get(key);
            if (a == null || b == null || Math.abs(b - a) < 1e-6) continue;
            String label = switch (key) {
                case Metrics.CATACOMBS -> "Cata XP";
                case Metrics.SB_LEVEL -> "SB Level";
                default -> Metrics.label(key);
            };
            parts.add(Metrics.delta(key, b - a) + " " + label);
        }
        int h = 20;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.12f));
        ModernGuiUtils.drawRoundedRect(graphics, x, y + 5, 2, h - 10, 1, AlpakaStyle.accent());
        String lead = "Since your last visit " + Metrics.ago(before.t()) + ": ";
        String body = parts.isEmpty() ? "nothing changed yet." : String.join("  ·  ", parts);
        text(graphics, lead, x + 8, y + 6, AlpakaStyle.TEXT_SOFT);
        int bx = x + 8 + textW(lead);
        text(graphics, AlpakaStyle.ellipsize(this.font, body, x + w - 8 - bx), bx, y + 6, parts.isEmpty() ? AlpakaStyle.TEXT_MUTED : ink(ProfileChart.UP));
        if (hover(x, y, w, h) && !parts.isEmpty()) tip("since", String.join("\n", parts));
        return y + h + 6;
    }


    /** A level-up still ahead: how far along it is, what is missing, and in how many days at the recent pace. */
    private record Goal(String metric, String name, int level, double progress, double missing, double days) {}

    private Goal goal(String metric, String name, int level, double progress, double missing) {
        double rate = session.ratePerDay(metric, 7);
        double days = rate > 0 ? missing / rate : Double.NaN;
        // Beyond a few years the pace says nothing useful any more.
        if (days > 3 * 365) days = Double.NaN;
        return new Goal(metric, name, level, progress, missing, days);
    }

    /** The level-ups closest at hand: how far along each is and, at the recent pace, when it lands. */
    private int nextUp(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        List<Goal> goals = new ArrayList<>();
        if (d.skillsApi) {
            for (ProfileData.Level s : d.skills) {
                if (s.maxed()) continue;
                goals.add(goal(Metrics.SKILL + s.id(), s.name(), s.level(), s.progress(), s.xpForNext() - s.xpCurrent()));
            }
        }
        if (!d.catacombs.maxed() && d.catacombs.xp() > 0) {
            goals.add(goal(Metrics.CATACOMBS, "Catacombs", d.catacombs.level(), d.catacombs.progress(),
                    d.catacombs.xpForNext() - d.catacombs.xpCurrent()));
        }
        for (ProfileData.Level c : d.classes) {
            if (c.maxed() || c.xp() <= 0) continue;
            goals.add(goal(Metrics.CLASS + c.id(), c.name(), c.level(), c.progress(), c.xpForNext() - c.xpCurrent()));
        }
        for (ProfileData.Slayer s : d.slayers) {
            if (s.maxed() || s.xp() <= 0) continue;
            goals.add(goal(Metrics.SLAYER + s.id(), shortSlayer(s.name()), s.level(), s.progress(), s.xpForNext()));
        }
        goals.sort((a, b) -> {
            boolean ea = !Double.isNaN(a.days());
            boolean eb = !Double.isNaN(b.days());
            if (ea != eb) return ea ? -1 : 1;
            if (ea) return Double.compare(a.days(), b.days());
            return Double.compare(b.progress(), a.progress());
        });

        int shown = Math.min(6, goals.size());
        int rowH = 22;
        int h = 24 + Math.max(1, shown) * rowH + 2;
        card(graphics, x, y, w, h);
        text(graphics, "Next Level-Ups", x + 8, y + 7, AlpakaStyle.TEXT);
        String pace = "at your 7-day pace";
        if (w > 200) text(graphics, pace, x + w - 8 - textW(pace), y + 7, AlpakaStyle.TEXT_MUTED);
        int ry = y + 22;
        if (shown == 0) text(graphics, "Everything is maxed. Impressive.", x + 8, ry + 4, AlpakaStyle.TEXT_MUTED);
        for (int i = 0; i < shown; i++) {
            Goal g = goals.get(i);
            boolean hover = hover(x + 3, ry, w - 6, rowH - 1);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, x + 3, ry, w - 6, rowH - 1, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_HOVER);
            String title = g.name() + " " + (g.level() + 1);
            text(graphics, title, x + 8, ry + 3, AlpakaStyle.TEXT_SOFT);
            String eta = Double.isNaN(g.days()) ? Coins.compact(g.missing()) + " XP to go" : "in " + Metrics.duration(g.days());
            text(graphics, eta, x + w - 8 - textW(eta), ry + 3, Double.isNaN(g.days()) ? AlpakaStyle.TEXT_MUTED : AlpakaStyle.accent());
            bar(graphics, x + 8, ry + 14, w - 16, 3, g.progress(), AlpakaStyle.accent());
            String metric = g.metric();
            hit(x + 3, ry, w - 6, rowH - 1, () -> chosen.put(Page.OVERVIEW, metric));
            if (hover) {
                tip("goal" + i, String.format(Locale.US, "%.1f%% of the way to %s %d, %s XP to go.", g.progress() * 100, g.name(),
                        g.level() + 1, Coins.full(Math.ceil(g.missing()))) + " Click to see its graph.");
            }
            ry += rowH;
        }
        return y + h;
    }

    private static String shortSlayer(String name) {
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    /** One level-up found in the history. */
    private record Milestone(long timeMs, String text, String metric) {}

    /** The level-ups of the last 30 days, newest first, read off the history of every level there is. */
    private List<Milestone> milestones(ProfileData d, long now) {
        // The session hands out the same series list until its data changes, so the level-ups of
        // all time are only worked out again then; the 30 days are still cut every frame. Which
        // metrics there are depends on the profile alone, so that list is made once per profile.
        if (d != milestoneData) {
            milestoneData = d;
            milestoneMetrics = new ArrayList<>();
            milestoneNames = new ArrayList<>();
            milestoneLevels = new ArrayList<>();
            for (ProfileData.Level s : d.skills) {
                int max = s.maxLevel();
                milestoneMetrics.add(Metrics.SKILL + s.id());
                milestoneNames.add(s.name());
                milestoneLevels.add(xp -> (int) Levels.skill(s.id(), xp, max));
            }
            milestoneMetrics.add(Metrics.CATACOMBS);
            milestoneNames.add("Catacombs");
            milestoneLevels.add(xp -> (int) Math.min(50, Levels.catacombs(xp)));
            for (ProfileData.Level c : d.classes) {
                milestoneMetrics.add(Metrics.CLASS + c.id());
                milestoneNames.add(c.name());
                milestoneLevels.add(xp -> (int) Math.min(50, Levels.catacombs(xp)));
            }
            milestoneMetrics.add(Metrics.SB_LEVEL);
            milestoneNames.add("SkyBlock Level");
            milestoneLevels.add(level -> (int) Math.floor(level));
            milestoneSeries = List.of();
        }

        boolean same = milestoneSeries.size() == milestoneMetrics.size();
        for (int i = 0; same && i < milestoneMetrics.size(); i++) {
            same = session.series(milestoneMetrics.get(i)) == milestoneSeries.get(i);
        }
        if (!same) {
            List<List<ProfileHistory.Point>> series = new ArrayList<>(milestoneMetrics.size());
            for (String metric : milestoneMetrics) series.add(session.series(metric));
            List<Milestone> all = new ArrayList<>();
            for (int i = 0; i < milestoneMetrics.size(); i++) {
                levelUps(all, series.get(i), milestoneMetrics.get(i), milestoneNames.get(i), milestoneLevels.get(i));
            }
            all.sort((a, b) -> Long.compare(b.timeMs(), a.timeMs()));
            milestoneSeries = series;
            milestoneAll = all;
        }
        long since = now - 30 * ProfileHistory.DAY;
        List<Milestone> out = new ArrayList<>();
        // Newest first: past the first one that is too old, all the rest are too.
        for (Milestone m : milestoneAll) {
            if (m.timeMs() < since) break;
            out.add(m);
        }
        return out;
    }

    private ProfileData milestoneData;
    private List<String> milestoneMetrics = List.of();
    private List<String> milestoneNames = List.of();
    private List<java.util.function.DoubleToIntFunction> milestoneLevels = List.of();
    private List<List<ProfileHistory.Point>> milestoneSeries = List.of();
    private List<Milestone> milestoneAll = List.of();

    /** Every level-up in one metric's history, at any time. */
    private static void levelUps(List<Milestone> out, List<ProfileHistory.Point> points, String metric, String name,
                                 java.util.function.DoubleToIntFunction level) {
        int previous = Integer.MIN_VALUE;
        for (ProfileHistory.Point p : points) {
            int now = level.applyAsInt(p.value());
            if (previous != Integer.MIN_VALUE && now > previous) {
                String text = now - previous == 1 ? name + " " + now : name + " " + previous + " → " + now;
                out.add(new Milestone(p.timeMs(), text, metric));
            }
            previous = now;
        }
    }

    private int milestones(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w, long now) {
        List<Milestone> list = milestones(d, now);
        int shown = Math.min(7, list.size());
        int rowH = 19;
        int h = 24 + Math.max(1, shown) * rowH + 2;
        card(graphics, x, y, w, h);
        text(graphics, "Recent Level-Ups", x + 8, y + 7, AlpakaStyle.TEXT);
        String span = "last 30 days";
        if (w > 200) text(graphics, span, x + w - 8 - textW(span), y + 7, AlpakaStyle.TEXT_MUTED);
        int ry = y + 22;
        if (shown == 0) {
            List<net.minecraft.network.chat.FormattedText> lines = GuiFont.splitLines(this.font,
                    "None recorded yet. Level-ups show up here as the history grows.", w - 16);
            for (net.minecraft.network.chat.FormattedText line : lines) {
                graphics.text(this.font, net.minecraft.locale.Language.getInstance().getVisualOrder(line), x + 8, ry + 4, AlpakaStyle.TEXT_MUTED, false);
                ry += 10;
            }
            return Math.max(y + h, ry + 6);
        }
        for (int i = 0; i < shown; i++) {
            Milestone m = list.get(i);
            boolean hover = hover(x + 3, ry, w - 6, rowH - 1);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, x + 3, ry, w - 6, rowH - 1, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_HOVER);
            ModernGuiUtils.drawRoundedRect(graphics, x + 9, ry + 7, 4, 4, 2, ink(ProfileChart.UP));
            String when = Metrics.ago(m.timeMs());
            text(graphics, AlpakaStyle.ellipsize(this.font, m.text(), w - 30 - textW(when)), x + 18, ry + 5, AlpakaStyle.TEXT_SOFT);
            text(graphics, when, x + w - 8 - textW(when), ry + 5, AlpakaStyle.TEXT_MUTED);
            String metric = m.metric();
            hit(x + 3, ry, w - 6, rowH - 1, () -> chosen.put(Page.OVERVIEW, metric));
            ry += rowH;
        }
        return y + h;
    }

    // ---- Tiles and graphs -----------------------------------------------------------------------

    /** A number on a card: its metric (for the sparkline and the graph; null for none), name and value. */
    private record Tile(String metric, String label, String value, String note, Tile beside, String tip) {
        Tile(String metric, String label, String value, String note) {
            this(metric, label, value, note, null, null);
        }
    }

    /**
     * Two numbers in one card, side by side, each with its name; the note goes below both and the
     * tip shows on hover. Without a graph: neither number has one of its own here.
     */
    private static Tile pair(String label, String value, String label2, String value2, String note, String tip) {
        return new Tile(null, label, value, note, new Tile(null, label2, value2, null), tip);
    }

    private int tiles(GuiGraphicsExtractor graphics, List<Tile> tiles, int x, int y, int w, int columns) {
        int cols = w < 380 ? Math.min(2, columns) : columns;
        int gap = 6;
        int tw = (w - (cols - 1) * gap) / cols;
        int th = 50;
        String selected = chosen.get(page);
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int tx = x + (i % cols) * (tw + gap);
            int ty = y + (i / cols) * (th + gap);
            // A graph only where there is a history to draw.
            boolean clickable = t.metric() != null && Metrics.hasHistory(t.metric());
            boolean hover = clickable && hover(tx, ty, tw, th);
            boolean isSelected = clickable && t.metric().equals(selected != null ? selected : defaultMetric(page));
            ModernGuiUtils.drawRoundedRect(graphics, tx, ty, tw, th, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (isSelected) ModernGuiUtils.drawRoundedOutline(graphics, tx, ty, tw, th, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
            if (t.beside() != null) {
                // Two numbers, each with its name, side by side in the large cut when they fit:
                // the second halfway, or just after the first when that is wider. Where they don't,
                // the first stays large like every other card's number and the second goes on the
                // small line below it, where the others show their change.
                int inner = tw - 14;
                int w1 = bigWidth(t.value());
                int w2 = bigWidth(t.beside().value());
                if (w1 + 10 + w2 <= inner) {
                    int x2 = tx + 7 + Math.max(inner / 2, w1 + 10);
                    int w2max = tx + tw - 7 - x2;
                    text(graphics, AlpakaStyle.ellipsize(this.font, t.label(), x2 - tx - 7 - 4), tx + 7, ty + 6, AlpakaStyle.TEXT_MUTED);
                    text(graphics, AlpakaStyle.ellipsize(this.font, t.beside().label(), w2max), x2, ty + 6, AlpakaStyle.TEXT_MUTED);
                    value(graphics, t.value(), tx + 7, ty + 18, w1);
                    value(graphics, t.beside().value(), x2, ty + 18, w2max);
                } else {
                    text(graphics, AlpakaStyle.ellipsize(this.font, t.label(), tw - 12), tx + 7, ty + 6, AlpakaStyle.TEXT_MUTED);
                    value(graphics, t.value(), tx + 7, ty + 18, inner);
                    String name = t.beside().label() + " ";
                    text(graphics, name, tx + 7, ty + th - 13, AlpakaStyle.TEXT_MUTED);
                    text(graphics, AlpakaStyle.ellipsize(this.font, t.beside().value(), inner - textW(name)), tx + 7 + textW(name),
                            ty + th - 13, AlpakaStyle.TEXT_SOFT);
                }
            } else {
                text(graphics, AlpakaStyle.ellipsize(this.font, t.label(), tw - 12), tx + 7, ty + 6, AlpakaStyle.TEXT_MUTED);
                value(graphics, t.value(), tx + 7, ty + 18, tw - 14);
            }
            // Keyed by the label: the tile is made anew every frame, the label stays the same string.
            if (!clickable && t.tip() != null && hover(tx, ty, tw, th)) tip(t.label().intern(), t.tip());
            if (clickable && session.historyProblem() != null) {
                // No history to show, so neither a change nor a line; the card still picks the graph,
                // which then says what it needs.
                String metric = t.metric();
                hit(tx, ty, tw, th, () -> chosen.put(page, metric));
                if (t.note() != null) text(graphics, AlpakaStyle.ellipsize(this.font, t.note(), tw - 14), tx + 7, ty + th - 13, AlpakaStyle.TEXT_MUTED);
            } else if (clickable) {
                List<ProfileHistory.Point> series = session.series(t.metric());
                List<ProfileHistory.Point> visible = ProfileChart.visible(series, range, System.currentTimeMillis());
                double change = ProfileChart.change(visible);
                String delta = Double.isNaN(change) ? "" : change == 0 ? "no change" : Metrics.delta(t.metric(), change);
                int color = Double.isNaN(change) || change == 0 ? AlpakaStyle.TEXT_MUTED : change > 0 ? ink(ProfileChart.UP) : ink(ProfileChart.DOWN);
                text(graphics, delta, tx + 7, ty + th - 13, color);
                int sx = tx + Math.max(textW(delta) + 14, tw / 2);
                int sw = tx + tw - 7 - sx;
                if (sw > 16) ProfileChart.sparkline(graphics, series, range, ink(AlpakaStyle.accent()), sx, ty + th - 20, sw, 13);
                String metric = t.metric();
                hit(tx, ty, tw, th, () -> chosen.put(page, metric));
                if (hover) {
                    tip(t.label().intern(), (t.note() != null ? t.note() + " " : "") + (Double.isNaN(change) ? "No history in this range yet."
                            : Metrics.delta(metric, change) + " over the last " + rangeWords() + ".") + " Click for its graph.");
                }
            } else if (t.note() != null) {
                text(graphics, AlpakaStyle.ellipsize(this.font, t.note(), tw - 14), tx + 7, ty + th - 13, AlpakaStyle.TEXT_MUTED);
            }
        }
        int rows = (tiles.size() + cols - 1) / cols;
        return y + rows * th + (rows - 1) * gap;
    }

    /** The graph of one metric in a card, with its name, latest value and the range to pick. */
    private int chartCard(GuiGraphicsExtractor graphics, String metric, int maxLevel, int x, int y, int w, int h) {
        card(graphics, x, y, w, h);
        List<ProfileHistory.Point> series = session.series(metric);
        String title = Metrics.label(metric);
        text(graphics, title, x + 8, y + 7, AlpakaStyle.TEXT);
        if (!series.isEmpty()) {
            double last = series.get(series.size() - 1).value();
            String value = Metrics.format(metric, last);
            String level = Metrics.levelNote(metric, last, maxLevel);
            if (level != null) value += "  ·  " + level;
            text(graphics, value, x + 8 + textW(title) + 8, y + 7, AlpakaStyle.accent());
        }
        // Without SkyKings' history no graph at all: a few scattered points would only mislead.
        String problem = session.historyProblem();
        if (problem != null) {
            ProfileChart.message(graphics, this.font, problem, x, y + 22, w, h - 26);
            return y + h;
        }
        int chipsRight = x + w - 6;
        ProfileChart.Range[] ranges = ProfileChart.Range.values();
        for (int i = ranges.length - 1; i >= 0; i--) {
            ProfileChart.Range r = ranges[i];
            int cw = textW(r.chip) + 10;
            int cx = chipsRight - cw;
            boolean selected = r == range;
            boolean hover = hover(cx, y + 5, cw, CHIP_H);
            int bg = selected ? MarketIcons.fade(AlpakaStyle.accent(), hover ? 0.35f : 0.24f) : hover ? AlpakaStyle.ROW_SELECTED : 0;
            if (bg != 0) ModernGuiUtils.drawRoundedRect(graphics, cx, y + 5, cw, CHIP_H, CHIP_H / 2, bg);
            text(graphics, r.chip, cx + 5, y + 7, selected ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
            hit(cx, y + 5, cw, CHIP_H, () -> range = r);
            chipsRight = cx - 2;
        }
        ProfileChart.draw(graphics, this.font, series, metric, maxLevel, range, ink(AlpakaStyle.accent()),
                x + 4, y + 22, w - 8, h - 26, clippedMouseX(), clippedMouseY(), overlays::add, session.own);
        return y + h;
    }

    private String defaultMetric(Page p) {
        ProfileData d = session.data;
        return switch (p) {
            case OVERVIEW -> firstWithHistory(Metrics.NETWORTH, Metrics.SKILL_AVG, Metrics.CATACOMBS, Metrics.SB_LEVEL);
            case NETWORTH -> Metrics.NETWORTH;
            case SKILLS -> d == null ? Metrics.SKILL_AVG : fastestSkill(d);
            case DUNGEONS -> Metrics.CATACOMBS;
            case SLAYERS -> Metrics.SLAYER_TOTAL;
            case PETS -> Metrics.PETS_MAXED;
            case COLLECTIONS -> Metrics.MINIONS;
            case BESTIARY -> Metrics.BESTIARY;
            // No graph on these pages.
            case MUSEUM, RIFT, CHOCOLATE -> null;
            case INVENTORY -> Metrics.NETWORTH;
        };
    }

    /**
     * The first of these with something to draw in the range, or the first of all: on a first look
     * the networth has no history yet, but another of them may.
     */
    private String firstWithHistory(String... metrics) {
        long now = System.currentTimeMillis();
        for (String m : metrics) {
            if (ProfileChart.visible(session.series(m), range, now).size() >= 2) return m;
        }
        return metrics[0];
    }

    private String metricFor(Page p) {
        String chosenMetric = chosen.get(p);
        return chosenMetric != null ? chosenMetric : defaultMetric(p);
    }

    private static int metricMaxLevel(ProfileData d, String metric) {
        if (metric.startsWith(Metrics.SKILL)) {
            for (ProfileData.Level s : d.skills) if (metric.equals(Metrics.SKILL + s.id())) return s.maxLevel();
        }
        return 0;
    }

    /**
     * The skill that grew most over the last week: by levels among the ones still levelling, else
     * by XP among the maxed ones, else the first skill.
     */
    private String fastestSkill(ProfileData d) {
        String best = null;
        double bestRate = 0;
        for (ProfileData.Level s : d.skills) {
            if (s.maxed() || s.xpForNext() <= 0) continue;
            double rate = session.ratePerDay(Metrics.SKILL + s.id(), 7);
            if (rate > 0 && rate / s.xpForNext() > bestRate) {
                bestRate = rate / s.xpForNext();
                best = Metrics.SKILL + s.id();
            }
        }
        if (best != null) return best;
        for (ProfileData.Level s : d.skills) {
            double rate = session.ratePerDay(Metrics.SKILL + s.id(), 7);
            if (rate > bestRate) {
                bestRate = rate;
                best = Metrics.SKILL + s.id();
            }
        }
        if (best != null) return best;
        return d.skills.isEmpty() ? Metrics.SKILL_AVG : Metrics.SKILL + d.skills.get(0).id();
    }

    // ---- Skills ---------------------------------------------------------------------------------

    private static final Map<String, Item> SKILL_ICONS = new LinkedHashMap<>();
    static {
        SKILL_ICONS.put("farming", Items.GOLDEN_HOE);
        SKILL_ICONS.put("mining", Items.STONE_PICKAXE);
        SKILL_ICONS.put("combat", Items.STONE_SWORD);
        SKILL_ICONS.put("foraging", Items.JUNGLE_SAPLING);
        SKILL_ICONS.put("fishing", Items.FISHING_ROD);
        SKILL_ICONS.put("enchanting", Items.ENCHANTING_TABLE);
        SKILL_ICONS.put("alchemy", Items.BREWING_STAND);
        SKILL_ICONS.put("carpentry", Items.CRAFTING_TABLE);
        SKILL_ICONS.put("runecrafting", Items.MAGMA_CREAM);
        SKILL_ICONS.put("social", Items.EMERALD);
        SKILL_ICONS.put("taming", Items.GHAST_SPAWN_EGG);
        SKILL_ICONS.put("hunting", Items.LEAD);
        SKILL_ICONS.put("healer", Items.SPLASH_POTION);
        SKILL_ICONS.put("mage", Items.BLAZE_ROD);
        SKILL_ICONS.put("berserk", Items.IRON_SWORD);
        SKILL_ICONS.put("archer", Items.BOW);
        SKILL_ICONS.put("tank", Items.LEATHER_CHESTPLATE);
        SKILL_ICONS.put("zombie", Items.ROTTEN_FLESH);
        SKILL_ICONS.put("spider", Items.COBWEB);
        SKILL_ICONS.put("wolf", Items.MUTTON);
        SKILL_ICONS.put("enderman", Items.ENDER_PEARL);
        SKILL_ICONS.put("blaze", Items.BLAZE_POWDER);
        SKILL_ICONS.put("vampire", Items.REDSTONE);
    }

    private int skills(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        if ("farming".equals(skillDetail)) return farming(graphics, d, x, y, w);
        if ("mining".equals(skillDetail)) return mining(graphics, d, x, y, w);
        if ("foraging".equals(skillDetail)) return foraging(graphics, d, x, y, w);
        if ("fishing".equals(skillDetail)) return fishing(graphics, d, x, y, w);
        if ("combat".equals(skillDetail)) return combat(graphics, d, x, y, w);
        if (!d.skillsApi) return apiOff(graphics, "Skills", x, y, w);
        int maxed = 0;
        for (ProfileData.Level s : d.skills) if (s.maxed()) maxed++;
        String fastest = fastestSkill(d);
        double fastestRate = session.ratePerDay(fastest, 7);
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.SKILL_AVG, "Skill Average", String.format(Locale.US, "%.2f", d.skillAverage), null));
        tiles.add(new Tile(null, "Total Skill XP", Coins.compact(d.skillTotalXp), "across all skills"));
        tiles.add(new Tile(null, "Skills Maxed", maxed + " / " + d.skills.size(), null));
        tiles.add(new Tile(null, "Fastest This Week", fastestRate > 0 ? Metrics.label(fastest).replace(" XP", "") : "—",
                fastestRate > 0 ? Coins.compact(fastestRate) + " XP a day" : "needs a week of history"));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;

        y = levelGrid(graphics, d.skills, Metrics.SKILL, x, y, w, Double.NaN);
        String metric = metricFor(Page.SKILLS);
        return chartCard(graphics, metric, metricMaxLevel(d, metric), x, y + 6, w, 130);
    }

    /** Levels as rows of two: icon, name, level, the bar to the next one and the recent pace. */
    /** {@code average}, unless NaN, is a last card after the levels: the class average, as a level of its own. */
    private int levelGrid(GuiGraphicsExtractor graphics, List<ProfileData.Level> levels, String prefix, int x, int y, int w, double average) {
        int cols = w >= 380 ? 2 : 1;
        int gap = 6;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 38;
        String selected = metricFor(page);
        for (int i = 0; i < levels.size(); i++) {
            ProfileData.Level l = levels.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + 4);
            String metric = prefix + l.id();
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (metric.equals(selected)) ModernGuiUtils.drawRoundedOutline(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
            Item icon = SKILL_ICONS.get(l.id());
            // At twice its size: a whole multiple keeps every texel on whole screen pixels.
            if (icon != null) icon(graphics, icon, cx + 4, cy + 3, 2.0f);
            int tx = cx + 42;
            int tw = cw - 42 - 8;
            boolean isSelectedClass = prefix.equals(Metrics.CLASS) && l.id().equals(session.data.selectedClass);
            // Farming, Mining, Combat, Foraging and Fishing open a page of their own, which the arrow promises.
            boolean opens = prefix.equals(Metrics.SKILL) && SKILL_PAGES.contains(l.id());
            String name = l.name();
            text(graphics, name, tx, cy + 7, isSelectedClass ? ink(ProfileChart.UP) : AlpakaStyle.TEXT_SOFT);
            if (opens) text(graphics, "›", tx + textW(name) + 4, cy + 7, hover ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
            // Every bar means the way to the next level. A maxed one fills in chroma rather than the
            // accent, which may be gold too; the XP past the cap counts on as an overflow level, its
            // whole number beside a MAX badge at the card's edge.
            Levels.Overflow over = l.overflow();
            if (over != null) {
                maxBadge(graphics, String.valueOf(over.level()), tx + tw, cy + 7);
                maxedBar(graphics, tx, cy + 19, tw, 3, over.progress());
                text(graphics, Math.round(Math.floor(over.progress() * 100)) + "% to " + (over.level() + 1) + " · "
                                + Coins.compact(over.xpCurrent()) + " / " + Coins.compact(over.xpForNext()),
                        tx, cy + 25, AlpakaStyle.TEXT_MUTED);
            } else {
                if (l.maxed()) {
                    maxBadge(graphics, String.valueOf(l.level()), tx + tw, cy + 7);
                    maxedBar(graphics, tx, cy + 19, tw, 3, 1);
                } else {
                    String level = l.level() + " / " + l.maxLevel();
                    text(graphics, level, tx + tw - textW(level), cy + 7, AlpakaStyle.TEXT);
                    bar(graphics, tx, cy + 19, tw, 3, l.progress(), AlpakaStyle.accent());
                }
                String xp = l.maxed() ? Coins.compact(l.xp()) + " XP" : Coins.compact(l.xpCurrent()) + " / " + Coins.compact(l.xpForNext());
                text(graphics, xp, tx, cy + 25, AlpakaStyle.TEXT_MUTED);
            }
            double rate = session.ratePerDay(metric, 7);
            if (rate > 0) {
                String pace = "+" + Coins.compact(rate) + "/day";
                text(graphics, pace, tx + tw - textW(pace), cy + 25, ink(ProfileChart.UP));
            }
            String id = l.id();
            hit(cx, cy, cw, ch, () -> {
                chosen.put(page, metric);
                if (opens) skillDetail = id;
            });
            if (hover) {
                String tipText = l.name() + ": " + Coins.full(Math.floor(l.xp())) + " XP in total.";
                if (!l.maxed()) tipText += " " + Coins.full(Math.ceil(l.xpForNext() - l.xpCurrent())) + " XP to level " + (l.level() + 1) + ".";
                if (over != null) tipText += " Maxed at " + l.maxLevel() + "; the XP past it makes overflow level "
                        + String.format(Locale.US, "%.2f", over.exact()) + ", " + Coins.full(Math.ceil(over.xpForNext() - over.xpCurrent()))
                        + " XP to " + (over.level() + 1) + ".";
                if (isSelectedClass) tipText += " Your selected class.";
                tip(metric, tipText + (opens ? " Click for the " + l.name() + " page." : " Click for its graph."));
            }
        }
        int count = levels.size();
        if (!Double.isNaN(average)) {
            averageCard(graphics, levels, average, x + (count % cols) * (cw + gap), y + (count / cols) * (ch + 4), cw, ch);
            count++;
        }
        int rows = (count + cols - 1) / cols;
        return y + rows * (ch + 4) - 4;
    }

    /**
     * The class average as a card like the classes': once every class is maxed, a MAX badge and the
     * way to the next whole average in chroma, as an overflow level; before, the way to 50.
     */
    private void averageCard(GuiGraphicsExtractor graphics, List<ProfileData.Level> classes, double average, int cx, int cy, int cw, int ch) {
        boolean hover = hover(cx, cy, cw, ch);
        ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
        icon(graphics, Items.NETHER_STAR, cx + 4, cy + 3, 2.0f);
        int tx = cx + 42;
        int tw = cw - 42 - 8;
        int all = ProfileData.CLASSES.length;
        boolean maxed = classes.size() == all && classes.stream().allMatch(ProfileData.Level::maxed);
        text(graphics, "Class Average", tx, cy + 7, AlpakaStyle.TEXT_SOFT);
        String level = String.format(Locale.US, "%.2f", average);
        if (maxed) {
            int whole = (int) Math.floor(average);
            double progress = average - whole;
            maxBadge(graphics, String.valueOf(whole), tx + tw, cy + 7);
            maxedBar(graphics, tx, cy + 19, tw, 3, progress);
            text(graphics, Math.round(Math.floor(progress * 100)) + "% to " + (whole + 1) + " · all " + all + " classes",
                    tx, cy + 25, AlpakaStyle.TEXT_MUTED);
        } else {
            String shown = level + " / 50";
            text(graphics, shown, tx + tw - textW(shown), cy + 7, AlpakaStyle.TEXT);
            bar(graphics, tx, cy + 19, tw, 3, average / 50, AlpakaStyle.accent());
            text(graphics, "Over all " + all + " classes", tx, cy + 25, AlpakaStyle.TEXT_MUTED);
        }
        if (hover) tip("classAverage", "The five classes' levels averaged, overflow past 50 included; one never played counts as 0.");
    }

    // ---- Farming and Mining ---------------------------------------------------------------------

    /** The skills that open a page of their own from the Skills page. */
    private static final List<String> SKILL_PAGES = List.of("farming", "mining", "combat", "foraging", "fishing");

    /** The Garden's crops: their key in the garden data, their name, and how they look. */
    private static final String[][] CROPS = {
            {"wheat", "Wheat"}, {"carrot", "Carrot"}, {"potato", "Potato"}, {"pumpkin", "Pumpkin"},
            {"melon", "Melon"}, {"mushroom", "Mushroom"}, {"cocoaBeans", "Cocoa Beans"}, {"cactus", "Cactus"},
            {"sugarCane", "Sugar Cane"}, {"netherWart", "Nether Wart"}, {"sunflower", "Sunflower"},
            {"moonflower", "Moonflower"}, {"wildRose", "Wild Rose"}};
    private static final Map<String, Item> CROP_ICONS = new HashMap<>();
    static {
        CROP_ICONS.put("wheat", Items.WHEAT);
        CROP_ICONS.put("carrot", Items.CARROT);
        CROP_ICONS.put("potato", Items.POTATO);
        CROP_ICONS.put("pumpkin", Items.PUMPKIN);
        CROP_ICONS.put("melon", Items.MELON_SLICE);
        CROP_ICONS.put("mushroom", Items.RED_MUSHROOM);
        CROP_ICONS.put("cocoaBeans", Items.COCOA_BEANS);
        CROP_ICONS.put("cactus", Items.CACTUS);
        CROP_ICONS.put("sugarCane", Items.SUGAR_CANE);
        CROP_ICONS.put("netherWart", Items.NETHER_WART);
        CROP_ICONS.put("sunflower", Items.SUNFLOWER);
        CROP_ICONS.put("moonflower", Items.BLUE_ORCHID);
        CROP_ICONS.put("wildRose", Items.ROSE_BUSH);
    }

    /** The way back to all skills, and the page's name beside it. */
    private int skillHeader(GuiGraphicsExtractor graphics, String title, int x, int y) {
        String back = "‹ All Skills";
        int cw = textW(back) + 14;
        boolean hover = hover(x, y, cw, 15);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, cw, 15, 7, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
        text(graphics, back, x + 7, y + 4, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
        hit(x, y, cw, 15, () -> skillDetail = null);
        text(graphics, title, x + cw + 8, y + 4, AlpakaStyle.TEXT);
        return y + 15 + 8;
    }

    private static ProfileData.Level skill(ProfileData d, String id) {
        for (ProfileData.Level l : d.skills) if (l.id().equals(id)) return l;
        return null;
    }

    private static Tile skillTile(ProfileData d, String id, String name) {
        ProfileData.Level l = skill(d, id);
        Levels.Overflow over = l == null ? null : l.overflow();
        String value = l == null ? "—" : String.valueOf(over != null ? over.level() : l.level());
        String note = l == null ? null : over != null ? "Maxed · " + (int) Math.floor(over.progress() * 100) + "% to " + (over.level() + 1)
                : l.maxed() ? "Maxed" : Math.round(l.progress() * 100) + "% to " + (l.level() + 1);
        return new Tile(Metrics.SKILL + id, name, value, note);
    }

    private int farming(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        y = skillHeader(graphics, "Farming", x, y);
        JsonElement r = d.raw;
        List<Tile> tiles = new ArrayList<>();
        tiles.add(skillTile(d, "farming", "Farming"));
        tiles.add(new Tile(Metrics.FARMING_WEIGHT, "Farming Weight", Coins.full(Math.round(d.farmingWeight * 10) / 10.0), null));
        double progress = Json.num(r, "garden.gardenProgress", 0);
        tiles.add(new Tile(null, "Garden Level", Coins.full(Json.num(r, "garden.gardenLevel", 0)),
                progress > 0 && progress < 1 ? Math.round(progress * 100) + "% to the next" : null));
        tiles.add(new Tile(null, "Visitors Served", Coins.full(Json.num(r, "garden.completedVisitors", 0)),
                Coins.full(Json.num(r, "garden.uniqueVisitors", 0)) + " different ones"));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        String metric = metricFor(Page.SKILLS);
        y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y, w, 120) + 6;
        if (!Json.obj(r, "garden.crops").isEmpty()) y = crops(graphics, d, x, y, w) + 6;
        return cards(graphics, farmingCards(d), x, y, w);
    }

    /** Every crop: its milestone, how much was farmed, its share of the weight and the best contest. */
    private int crops(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement r = d.raw;
        int cols = w >= 540 ? 3 : w >= 340 ? 2 : 1;
        int gap = 6;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 42;
        for (int i = 0; i < CROPS.length; i++) {
            String key = CROPS[i][0];
            String name = CROPS[i][1];
            String plain = name.replace(" ", "");
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + 4);
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            icon(graphics, CROP_ICONS.get(key), cx + 4, cy + 5, 2.0f);
            int tx = cx + 42;
            int tw = cw - 42 - 8;
            double amount = Json.num(r, "garden.crops." + key, 0);
            int milestone = GardenTables.milestone(name, amount);
            String level = milestone < 0 ? "Milestone …" : "Milestone " + milestone;
            text(graphics, AlpakaStyle.ellipsize(this.font, name, tw - textW(level) - 6), tx, cy + 7, AlpakaStyle.TEXT_SOFT);
            text(graphics, level, tx + tw - textW(level), cy + 7, AlpakaStyle.TEXT);
            text(graphics, Coins.compact(amount), tx, cy + 19, AlpakaStyle.TEXT);
            double weight = Json.num(r, "farmingWeight.cropWeight." + name, 0);
            String wt = String.format(Locale.US, "%.1f weight", weight);
            text(graphics, wt, tx + tw - textW(wt), cy + 19, AlpakaStyle.TEXT_MUTED);
            double best = Json.num(r, "jacob.stats.personalBests." + plain, 0);
            if (best > 0) text(graphics, "Best contest " + Coins.compact(best), tx, cy + 30, AlpakaStyle.TEXT_MUTED);
            if (hover) {
                StringBuilder tipText = new StringBuilder(name + ": " + Coins.full(amount) + " farmed.");
                int upgrade = (int) Json.num(r, "garden.cropUpgrades." + key, 0);
                tipText.append(" Crop Upgrade ").append(upgrade).append(" / 9.");
                double contests = Json.num(r, "jacob.stats.crops." + plain + ".participations", 0);
                if (contests > 0) {
                    tipText.append(" ").append(Coins.full(contests)).append(contests == 1 ? " contest" : " contests");
                    List<String> medals = new ArrayList<>();
                    for (String medal : new String[] {"diamond", "platinum", "gold", "silver", "bronze"}) {
                        int n = (int) Json.num(r, "jacob.stats.crops." + plain + ".medals." + medal, 0);
                        if (n > 0) medals.add(n + " " + medal);
                    }
                    tipText.append(medals.isEmpty() ? "." : ": " + String.join(", ", medals) + ".");
                }
                if (best > 0) tipText.append(" Best contest: ").append(Coins.full(best)).append(".");
                tip(CROPS[i], tipText.toString());
            }
        }
        int rows = (CROPS.length + cols - 1) / cols;
        return y + rows * (ch + 4) - 4;
    }

    private static List<Card> farmingCards(ProfileData d) {
        JsonElement r = d.raw;
        List<Card> cards = new ArrayList<>();

        List<String[]> jacob = new ArrayList<>();
        count(jacob, "Contests", Json.num(r, "jacob.participations", 0));
        for (String medal : new String[] {"diamond", "platinum", "gold", "silver", "bronze"}) {
            double earned = Json.num(r, "jacob.earnedMedals." + medal, 0);
            if (earned > 0) jacob.add(row(ProfileData.title(medal) + " Medals", Coins.full(earned)));
        }
        count(jacob, "Crops With Gold", Json.num(r, "jacob.uniqueGoldMedals", 0));
        jacob.add(row("Double Drops", (int) Json.num(r, "jacob.perks.doubleDrops", 0) + " / 15"));
        jacob.add(row("Farming Level Cap", "+" + (int) Json.num(r, "jacob.perks.levelCap", 0) + " / 10"));
        add(cards, "Jacob's Contests", jacob);

        List<String[]> garden = new ArrayList<>();
        int plots = Json.arr(r, "garden.plots").size();
        if (plots > 0) garden.add(row("Plots Unlocked", plots + " / 24"));
        count(garden, "Garden XP", Json.num(r, "garden.experience", 0));
        count(garden, "Copper", Json.num(r, "unparsed.copper", 0));
        count(garden, "Visitors Served", Json.num(r, "garden.completedVisitors", 0));
        count(garden, "Different Visitors", Json.num(r, "garden.uniqueVisitors", 0));
        add(cards, "Garden", garden);

        List<String[]> composter = new ArrayList<>();
        for (String[] up : new String[][] {{"speed", "Speed"}, {"multi_drop", "Multi Drop"}, {"fuel_cap", "Fuel Cap"},
                {"organic_matter_cap", "Organic Matter Cap"}, {"cost_reduction", "Cost Reduction"}}) {
            JsonElement level = Json.at(r, "garden.composter.upgrades." + up[0]);
            if (level != null) composter.add(row(up[1], (int) Json.num(r, "garden.composter.upgrades." + up[0], 0) + " / 25"));
        }
        add(cards, "Composter", composter);

        List<String[]> pests = new ArrayList<>();
        double total = Json.sum(r, "farmingWeight.pests");
        count(pests, "Total", total);
        for (var e : Json.obj(r, "farmingWeight.pests").entrySet()) {
            double n = Json.num(e.getValue());
            if (n > 0) {
                pests.add(row(ProfileData.title(CAMEL_HUMP.matcher(e.getKey()).replaceAll("$1_$2")), Coins.full(n)));
            }
        }
        add(cards, "Pests Killed", pests);

        List<String[]> bonus = new ArrayList<>();
        for (var e : Json.obj(r, "farmingWeight.bonusWeight").entrySet()) {
            double n = Json.num(e.getValue());
            if (!Double.isNaN(n)) bonus.add(row(e.getKey(), String.format(Locale.US, "%.2f", n)));
        }
        add(cards, "Bonus Weight", bonus);
        return cards;
    }

    private int mining(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        y = skillHeader(graphics, "Mining", x, y);
        JsonElement r = d.raw;
        List<Tile> tiles = new ArrayList<>();
        tiles.add(skillTile(d, "mining", "Mining"));
        // A profile that never opened the Heart of the Mountain has no tier to show.
        if (Json.num(r, "stats.mining.experience", 0) > 0) {
            tiles.add(new Tile(null, "Heart of the Mountain", "Tier " + (int) Json.num(r, "stats.mining.hotmLevel", 0), null));
        } else {
            tiles.add(new Tile(null, "Peak of the Mountain", (int) Json.num(r, "stats.mining.peakOfTheMountainLevel", 0) + " / 10", null));
        }
        tiles.add(new Tile(null, "Nucleus Runs", Coins.full(Json.num(r, "stats.mining.crystalNucleusRuns", 0)), null));
        tiles.add(new Tile(null, "Commissions", "Milestone " + (int) Json.num(r, "stats.mining.commissionMilestone", 0), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        String metric = metricFor(Page.SKILLS);
        y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y, w, 120) + 6;
        String tokens = Json.num(r, "stats.mining.tokensSpent", 0) > 0 ? Coins.full(Json.num(r, "stats.mining.tokensSpent", 0)) + " tokens spent" : null;
        y = skillTree(graphics, "Heart of the Mountain", SkillTrees.hotm(), Json.obj(r, "stats.mining.nodes"),
                Json.obj(r, "stats.mining.nodeToggles"), "core_of_the_mountain", (int) Json.num(r, "stats.mining.hotmLevel", 0),
                tokens, x, y, w) + 6;
        return cards(graphics, miningCards(d), x, y, w);
    }

    private static List<Card> miningCards(ProfileData d) {
        JsonElement r = d.raw;
        List<Card> cards = new ArrayList<>();

        List<String[]> powder = new ArrayList<>();
        for (String type : new String[] {"mithril", "gemstone", "glacite"}) {
            double available = Json.num(r, "stats.mining.powder." + type + ".available");
            if (!Double.isNaN(available)) powder.add(row(ProfileData.title(type), Coins.full(available)));
        }
        add(cards, "Powder", powder);

        List<String[]> crystals = new ArrayList<>();
        for (var e : Json.obj(r, "stats.mining.crystals").entrySet()) {
            double placed = Json.num(e.getValue(), "totalPlaced", 0);
            double found = Json.num(e.getValue(), "totalFound", 0);
            String value = placed > 0 ? Coins.full(placed) + " placed" : found > 0 ? Coins.full(found) + " found" : "—";
            crystals.add(row(ProfileData.title(e.getKey()), value));
        }
        add(cards, "Crystals", crystals);

        List<String[]> glacite = new ArrayList<>();
        count(glacite, "Mineshafts Entered", Json.num(r, "stats.mining.glacite.mineshaftsEntered", 0));
        count(glacite, "Fossils Donated", Json.num(r, "stats.mining.glacite.fossilsDonatedCount", 0));
        count(glacite, "Fossil Dust", Json.num(r, "stats.mining.glacite.fossilDust", 0));
        for (var e : Json.obj(r, "stats.mining.glacite.corpsesLooted").entrySet()) {
            double n = Json.num(e.getValue());
            if (!Double.isNaN(n)) count(glacite, ProfileData.title(e.getKey()) + " Corpses", n);
        }
        add(cards, "Glacite Tunnels", glacite);

        List<String[]> more = new ArrayList<>();
        double lastHollows = Json.num(r, "stats.mining.crystalHollowsLastAccess", 0);
        if (lastHollows > 0) more.add(row("Crystal Hollows", Metrics.ago((long) lastHollows)));
        String rock = Json.str(r, "stats.mining.rockMilestone.currentRarity");
        if (rock != null) more.add(row("Rock Pet Milestone", ProfileData.title(rock)));
        count(more, "Tokens Spent", Json.num(r, "stats.mining.tokensSpent", 0));
        add(cards, "More", more);
        return cards;
    }

    // ---- Combat ----------------------------------------------------------------------------------

    /** The Dojo's tests by Hypixel's id, with the names the Dojo gives them. */
    private static final String[][] DOJO_TESTS = {
            {"mob_kb", "Force"}, {"wall_jump", "Stamina"}, {"archer", "Mastery"}, {"sword_swap", "Discipline"},
            {"snake", "Swiftness"}, {"lock_head", "Control"}, {"fireball", "Tenacity"}};
    /** Kuudra's tiers by Hypixel's id, lowest first. */
    private static final String[][] KUUDRA_TIERS = {
            {"none", "Basic"}, {"hot", "Hot"}, {"burning", "Burning"}, {"fiery", "Fiery"}, {"infernal", "Infernal"}};

    /** The Dojo belt these points earn. */
    static String dojoBelt(double points) {
        if (points >= 10_000) return "Black Belt";
        if (points >= 7_000) return "Brown Belt";
        if (points >= 6_000) return "Blue Belt";
        if (points >= 4_000) return "Green Belt";
        if (points >= 2_000) return "Yellow Belt";
        return "White Belt";
    }

    private int combat(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        y = skillHeader(graphics, "Combat", x, y);
        JsonElement r = d.raw;
        List<Tile> tiles = new ArrayList<>();
        tiles.add(skillTile(d, "combat", "Combat"));
        String highest = Json.str(r, "stats.crimsonIsle.highestKuudraTier");
        String highestName = null;
        for (String[] t : KUUDRA_TIERS) if (t[0].equals(highest)) highestName = t[1];
        tiles.add(new Tile(null, "Kuudra Runs", Coins.full(Json.num(r, "stats.crimsonIsle.totalKuudraRuns", 0)),
                highestName == null ? null : "Up to " + highestName));
        String faction = Json.str(r, "stats.crimsonIsle.selectedFaction");
        double reputation = "barbarians".equals(faction) ? Json.num(r, "stats.crimsonIsle.barbariansReputation", 0)
                : Json.num(r, "stats.crimsonIsle.magesReputation", 0);
        tiles.add(new Tile(null, "Reputation", faction == null ? "—" : Coins.full(reputation),
                faction == null ? null : ProfileData.title(faction)));
        double dojo = Json.num(r, "stats.crimsonIsle.dojoPointsTotal", 0);
        tiles.add(new Tile(null, "Dojo Points", Coins.full(dojo), dojoBelt(dojo)));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        String metric = metricFor(Page.SKILLS);
        y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y, w, 120) + 6;
        return cards(graphics, combatCards(d), x, y, w);
    }

    private static List<Card> combatCards(ProfileData d) {
        JsonElement r = d.raw;
        List<Card> cards = new ArrayList<>();

        List<String[]> kuudra = new ArrayList<>();
        for (String[] t : KUUDRA_TIERS) {
            double runs = Json.num(r, "stats.crimsonIsle.kuudraCompletions." + t[0], 0);
            double wave = Json.num(r, "stats.crimsonIsle.kuudraHighestWaves." + t[0], 0);
            if (runs > 0 || wave > 0) kuudra.add(row(t[1], Coins.full(runs) + (wave > 0 ? " · wave " + (int) wave : "")));
        }
        add(cards, "Kuudra", kuudra);

        List<String[]> dojo = new ArrayList<>();
        for (String[] t : DOJO_TESTS) {
            JsonElement test = Json.obj(r, "stats.crimsonIsle.dojoTests." + t[0]);
            double points = Json.num(test, "points", 0);
            if (points <= 0) continue;
            String rank = Json.str(test, "rank");
            double time = Json.num(test, "time", 0);
            dojo.add(row(t[1], (rank == null ? "" : rank + " · ") + Coins.full(points) + (time > 0 ? " · " + Metrics.clock((long) time) : "")));
        }
        add(cards, "Dojo", dojo);

        List<String[]> crimson = new ArrayList<>();
        String faction = Json.str(r, "stats.crimsonIsle.selectedFaction");
        if (faction != null) crimson.add(row("Faction", ProfileData.title(faction)));
        count(crimson, "Mage Reputation", Json.num(r, "stats.crimsonIsle.magesReputation"));
        count(crimson, "Barbarian Reputation", Json.num(r, "stats.crimsonIsle.barbariansReputation"));
        add(cards, "Crimson Isle", crimson);

        List<String[]> fights = new ArrayList<>();
        count(fights, "Kills", Json.num(r, "stats.playerStats.killsDeaths.totalKills"));
        count(fights, "Deaths", Json.num(r, "stats.playerStats.killsDeaths.totalDeaths"));
        double kd = Json.num(r, "stats.playerStats.derivations.killDeathRatio");
        if (!Double.isNaN(kd) && kd > 0) fights.add(row("Kills per Death", String.format(Locale.US, "%.1f", kd)));
        double damage = Json.num(r, "stats.playerStats.damage.highestDamage");
        if (!Double.isNaN(damage) && damage > 0) fights.add(row("Highest Hit", Coins.compact(damage)));
        count(fights, "Bestiary Milestone", Json.num(r, "stats.bestiary.milestoneLevel"));
        add(cards, "Fights", fights);

        // The mobs killed most, leaving out the totals Hypixel files among them.
        List<String[]> top = new ArrayList<>();
        for (JsonElement e : Json.arr(r, "stats.playerStats.derivations.topKills")) {
            String mob = Json.str(e, "mobId");
            if (mob == null || mob.equals("total") || mob.equals("unknown")) continue;
            count(top, ProfileData.title(mob), Json.num(e, "count", 0));
            if (top.size() >= 8) break;
        }
        add(cards, "Most Killed", top);

        List<String[]> dragons = new ArrayList<>();
        count(dragons, "Dragons Summoned", Json.num(r, "stats.playerStats.dragonFight.amountSummoned.total"));
        count(dragons, "Superior Summoned", Json.num(r, "stats.playerStats.dragonFight.amountSummoned.superior"));
        count(dragons, "Crystals Destroyed", Json.num(r, "stats.playerStats.dragonFight.enderCrystalsDestroyed"));
        double most = Json.num(r, "stats.playerStats.dragonFight.mostDamage.best");
        if (!Double.isNaN(most) && most > 0) dragons.add(row("Most Damage", Coins.compact(most)));
        add(cards, "Ender Dragons", dragons);

        List<String[]> mythos = new ArrayList<>();
        count(mythos, "Burrows Dug", Json.num(r, "stats.playerStats.mythos.burrowsDugNext.total")
                + Json.num(r, "stats.playerStats.mythos.burrowsDugTreasure.total", 0));
        count(mythos, "Chains Completed", Json.num(r, "stats.playerStats.mythos.burrowsChainsComplete.total"));
        count(mythos, "Inquisitors", Json.num(r, "stats.playerStats.mythos.killsMinosInquisitor"));
        count(mythos, "Minos Champions", Json.num(r, "stats.playerStats.mythos.killsMinosChampion"));
        count(mythos, "Minotaurs", Json.num(r, "stats.playerStats.mythos.killsMinotaur"));
        count(mythos, "Gaia Constructs", Json.num(r, "stats.playerStats.mythos.killsGaiaConstruct"));
        count(mythos, "Siamese Lynxes", Json.num(r, "stats.playerStats.mythos.killsSiameseLynx"));
        count(mythos, "Minos Hunters", Json.num(r, "stats.playerStats.mythos.killsMinosHunter"));
        add(cards, "Mythological Ritual", mythos);
        return cards;
    }

    // ---- Foraging and Fishing --------------------------------------------------------------------

    private int foraging(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        y = skillHeader(graphics, "Foraging", x, y);
        JsonElement r = d.raw;
        List<Tile> tiles = new ArrayList<>();
        tiles.add(skillTile(d, "foraging", "Foraging"));
        double hotfXp = Json.num(r, "stats.foraging.hotfExperience", 0);
        tiles.add(new Tile(null, "Heart of the Forest", "Tier " + (int) Json.num(r, "stats.foraging.hotfLevel", 0),
                hotfXp > 0 ? Coins.compact(hotfXp) + " XP" : null));
        tiles.add(new Tile(null, "Center of the Forest", (int) Json.num(r, "stats.foraging.centerOfTheForestLevel", 0) + " / 5", null));
        double gifts = 0;
        for (var e : Json.obj(r, "stats.foraging.treeGifts").entrySet()) gifts += Json.num(e.getValue(), "totalGifts", 0);
        tiles.add(new Tile(null, "Tree Gifts", Coins.full(gifts), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        String metric = metricFor(Page.SKILLS);
        y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y, w, 120) + 6;
        double spent = Json.num(r, "stats.foraging.hotfTokensSpent", 0);
        y = skillTree(graphics, "Heart of the Forest", SkillTrees.hotf(), Json.obj(r, "stats.foraging.nodes"),
                Json.obj(r, "stats.foraging.nodeToggles"), "center_of_the_forest", (int) Json.num(r, "stats.foraging.hotfLevel", 0),
                spent > 0 ? Coins.full(spent) + " tokens spent" : null, x, y, w) + 6;
        return cards(graphics, foragingCards(d), x, y, w);
    }

    private static List<Card> foragingCards(ProfileData d) {
        JsonElement r = d.raw;
        List<Card> cards = new ArrayList<>();

        List<String[]> gifts = new ArrayList<>();
        for (var e : Json.obj(r, "stats.foraging.treeGifts").entrySet()) {
            int tier = (int) Json.num(e.getValue(), "milestoneTier", 0);
            double total = Json.num(e.getValue(), "totalGifts", 0);
            gifts.add(row(ProfileData.title(e.getKey()), "Milestone " + tier + " / 7 · " + Coins.full(total)));
        }
        add(cards, "Tree Gifts", gifts);

        List<String[]> forest = new ArrayList<>();
        count(forest, "Forest Whispers", Json.num(r, "stats.foraging.forestsWhispers"));
        count(forest, "Whispers Spent", Json.num(r, "stats.foraging.forestsWhispersSpent"));
        count(forest, "Hina Chapter", Json.num(r, "stats.foraging.hinaChapterTier"));
        String ability = Json.str(r, "stats.foraging.selectedAxeAbility");
        if (ability != null) forest.add(row("Axe Ability", ProfileData.title(ability)));
        add(cards, "Galatea", forest);

        // The personal bests are the logs of each tree's contest and two NPCs' own records.
        List<String[]> bests = new ArrayList<>();
        for (var e : Json.obj(r, "stats.foraging.personalBests").entrySet()) {
            double n = Json.num(e.getValue());
            if (Double.isNaN(n)) continue;
            bests.add(row(ProfileData.title(e.getKey().toLowerCase(Locale.ROOT)), Coins.full(n)));
        }
        add(cards, "Personal Bests", bests);
        return cards;
    }

    private int fishing(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        y = skillHeader(graphics, "Fishing", x, y);
        JsonElement r = d.raw;
        List<Tile> tiles = new ArrayList<>();
        tiles.add(skillTile(d, "fishing", "Fishing"));
        String rank = Json.str(r, "stats.fishing.trophyRank");
        tiles.add(new Tile(null, "Trophy Fish", Coins.full(Json.num(r, "stats.fishing.totalTrophyFishCaught", 0)),
                rank == null ? null : ProfileData.title(rank) + " Trophy Hunter"));
        double water = Json.num(r, "stats.fishing.waterSeaCreatureKills", 0);
        double lava = Json.num(r, "stats.fishing.lavaSeaCreatureKills", 0);
        tiles.add(new Tile(null, "Sea Creatures", Coins.full(Json.num(r, "stats.fishing.seaCreatureKills", 0)),
                water + lava > 0 ? Coins.compact(water) + " water · " + Coins.compact(lava) + " lava" : null));
        double large = Json.num(r, "stats.fishing.largeTreasuresCaught", 0);
        tiles.add(new Tile(null, "Treasures", Coins.full(Json.num(r, "stats.fishing.treasuresCaught", 0)),
                large > 0 ? Coins.full(large) + " large" : null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        String metric = metricFor(Page.SKILLS);
        y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y, w, 120) + 6;
        y = trophyFish(graphics, d, x, y, w) + 6;
        return cards(graphics, fishingCards(d), x, y, w);
    }

    /** Tier colours of the Trophy Fish, bronze to diamond. */
    private static final int[] TROPHY_TIER_COLORS = {0xFFD08A4E, 0xFFC9D1D9, 0xFFFFC94D, 0xFF6FE3F2};

    /**
     * Every Trophy Fish with its head in the best tier caught, the total and the count of each tier;
     * a tier not caught yet is a dim dash, a fish not caught at all is greyed out.
     */
    private int trophyFish(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonObject counts = Json.obj(d.raw, "stats.fishing.trophyFishCounts");
        int cols = w >= 540 ? 3 : w >= 340 ? 2 : 1;
        int gap = 6;
        int pad = 8;
        int cw = (w - 2 * pad - (cols - 1) * gap) / cols;
        int ch = 38;
        List<TrophyFish.Fish> all = TrophyFish.ALL;
        int rows = (all.size() + cols - 1) / cols;
        int h = 24 + rows * (ch + 4) - 4 + pad;
        card(graphics, x, y, w, h);
        text(graphics, "Trophy Fish", x + pad, y + 7, AlpakaStyle.TEXT);
        // The tier totals as a legend on the right, each in its tier's colour.
        int lx = x + w - pad;
        for (int t = TrophyFish.TIERS.length - 1; t >= 0; t--) {
            String label = Coins.full(Json.num(d.raw, "stats.fishing.trophyFishTierCounts." + TrophyFish.TIERS[t], 0)) + " " + ProfileData.title(TrophyFish.TIERS[t]);
            lx -= textW(label);
            text(graphics, label, lx, y + 7, ink(TROPHY_TIER_COLORS[t]));
            lx -= 10;
        }
        for (int i = 0; i < all.size(); i++) {
            TrophyFish.Fish f = all.get(i);
            int cx = x + pad + (i % cols) * (cw + gap);
            int cy = y + 22 + (i / cols) * (ch + 4);
            int total = (int) Json.num(counts, f.id(), 0);
            int best = -1;
            int[] tier = new int[TrophyFish.TIERS.length];
            for (int t = 0; t < tier.length; t++) {
                tier[t] = (int) Json.num(counts, f.id() + "_" + TrophyFish.TIERS[t], 0);
                if (tier[t] > 0) best = t;
            }
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW_SELECTED);
            ItemStack head = trophyHead(f.heads()[Math.max(0, best)]);
            // At twice its size, so the head's pixels stay whole and it reads at a glance.
            if (head != null) stackIcon(graphics, head, cx + 3, cy + 3, 2.0f);
            int tx = cx + 41;
            int tw = cw - 41 - 6;
            int nameColor = total > 0 ? ink(ProfileData.rarityColor(f.rarity())) : AlpakaStyle.TEXT_MUTED;
            String sum = Coins.full(total);
            text(graphics, AlpakaStyle.ellipsize(this.font, f.name(), tw - textW(sum) - 6), tx, cy + 8, nameColor);
            text(graphics, sum, tx + tw - textW(sum), cy + 8, total > 0 ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
            int slot = tw / tier.length;
            for (int t = 0; t < tier.length; t++) {
                String n = tier[t] > 0 ? Coins.compact(tier[t]) : "—";
                text(graphics, n, tx + t * slot, cy + 22, tier[t] > 0 ? ink(TROPHY_TIER_COLORS[t]) : MarketIcons.fade(AlpakaStyle.TEXT_MUTED, 0.5f));
            }
            if (hover) {
                StringBuilder tipText = new StringBuilder(f.name()).append(" (").append(ProfileData.title(f.rarity())).append("): ")
                        .append(f.description());
                tipText.append(total > 0 ? " Caught " + Coins.full(total) + " times" : " Not caught yet");
                tipText.append(best >= 0 ? ", best tier " + ProfileData.title(TrophyFish.TIERS[best]) + "." : ".");
                tip("trophy" + i, tipText.toString());
            }
        }
        return y + h;
    }

    private static final Map<String, ItemStack> TROPHY_HEADS = new HashMap<>();

    /** A Trophy Fish's head; null before a world is joined, when items cannot be built yet. */
    private static ItemStack trophyHead(String hash) {
        ItemStack known = TROPHY_HEADS.get(hash);
        if (known != null) return known;
        try {
            ItemStack built = ItemIcons.head(TrophyFish.texture(hash));
            TROPHY_HEADS.put(hash, built);
            return built;
        } catch (RuntimeException notBoundYet) {
            return null;
        }
    }

    private static List<Card> fishingCards(ProfileData d) {
        JsonElement r = d.raw;
        List<Card> cards = new ArrayList<>();

        List<String[]> catches = new ArrayList<>();
        count(catches, "Items Fished", Json.num(r, "stats.fishing.itemsFished"));
        count(catches, "Normal Catches", Json.num(r, "stats.fishing.normalCatches"));
        count(catches, "Treasures", Json.num(r, "stats.fishing.treasuresCaught"));
        count(catches, "Large Treasures", Json.num(r, "stats.fishing.largeTreasuresCaught"));
        count(catches, "Festival Sharks", Json.num(r, "stats.miscStats.festivals.fishingFestivalSharksKilled"));
        String dolphin = Json.str(r, "stats.fishing.dolphinMilestone.currentRarity");
        if (dolphin != null) catches.add(row("Dolphin Pet Milestone", ProfileData.title(dolphin)));
        String last = Json.str(r, "stats.fishing.lastCaughtTrophyFish");
        if (last != null) {
            String[] parts = last.split("/");
            String name = parts[0];
            for (TrophyFish.Fish f : TrophyFish.ALL) if (f.id().equals(parts[0])) name = f.name();
            catches.add(row("Last Trophy Fish", name + (parts.length > 1 ? " " + ProfileData.title(parts[1]) : "")));
        }
        add(cards, "Catches", catches);

        JsonObject kills = Json.obj(r, "stats.playerStats.killsDeaths.kills");
        for (String[][] list : new String[][][] {SeaCreatures.RARE_WATER, SeaCreatures.RARE_LAVA}) {
            List<String[]> rows = new ArrayList<>();
            for (String[] creature : list) count(rows, creature[1], SeaCreatures.kills(kills, creature[0]));
            add(cards, list == SeaCreatures.RARE_WATER ? "Rare Water Creatures" : "Rare Lava Creatures", rows);
        }
        return cards;
    }

    /** The states of a tree's perk, as their colours. */
    private static final int TREE_LOCKED = 0xFF3A3A42;

    /**
     * A Heart of the Mountain or Forest as the game lays it out: each perk on its place in the grid,
     * joined to the perks beside it, the top tier at the top. A perk not taken is dark, one under way
     * pink with its level in it, a maxed one gold, a switched-off one faded; abilities are round.
     * Hovering a perk shows its tooltip from the game.
     */
    private int skillTree(GuiGraphicsExtractor graphics, String title, SkillTrees.Tree tree, JsonObject levels, JsonObject toggles,
                          String core, int tier, String info, int x, int y, int w) {
        int pad = 8;
        if (tree == null || tree.perks().isEmpty()) {
            card(graphics, x, y, w, 36);
            text(graphics, title, x + pad, y + 7, AlpakaStyle.TEXT);
            text(graphics, "Loading…", x + pad, y + 21, AlpakaStyle.TEXT_MUTED);
            return y + 36;
        }
        int cell = Math.min(68, (w - 2 * pad) / tree.columns());
        int nodeW = Math.min(cell - 10, 34);
        int nodeH = 16;
        int rowH = 26;
        int gridW = cell * tree.columns();
        int gx = x + (w - gridW) / 2;
        int top = y + 24;
        int h = 24 + tree.rows() * rowH + 18;
        card(graphics, x, y, w, h);
        text(graphics, title, x + pad, y + 7, AlpakaStyle.TEXT);
        int maxed = 0;
        int taken = 0;
        for (SkillTrees.Perk p : tree.perks()) {
            int level = (int) Json.num(levels, p.id(), 0);
            if (level > 0) taken++;
            if (level >= p.maxLevel()) maxed++;
        }
        String summary = maxed + " / " + tree.perks().size() + " maxed" + (info != null ? " · " + info : "");
        text(graphics, summary, x + w - pad - textW(summary), y + 7, AlpakaStyle.TEXT_MUTED);

        // The links first, so the perks sit on top of them.
        for (SkillTrees.Perk p : tree.perks()) {
            int ax = gx + p.x() * cell + cell / 2;
            int ay = top + p.y() * rowH + rowH / 2;
            boolean on = Json.num(levels, p.id(), 0) > 0;
            SkillTrees.Perk right = SkillTrees.at(tree, p.x() + 1, p.y());
            if (right != null) {
                boolean both = on && Json.num(levels, right.id(), 0) > 0;
                graphics.fill(ax + nodeW / 2, ay - 1, ax + cell - nodeW / 2, ay + 1, both ? MarketIcons.fade(AlpakaStyle.accent(), 0.55f) : TREE_LOCKED);
            }
            SkillTrees.Perk below = SkillTrees.at(tree, p.x(), p.y() + 1);
            if (below != null) {
                boolean both = on && Json.num(levels, below.id(), 0) > 0;
                graphics.fill(ax - 1, ay + nodeH / 2, ax + 1, ay + rowH - nodeH / 2, both ? MarketIcons.fade(AlpakaStyle.accent(), 0.55f) : TREE_LOCKED);
            }
        }
        for (SkillTrees.Perk p : tree.perks()) {
            int level = (int) Json.num(levels, p.id(), 0);
            boolean off = toggles.has(p.id()) && !Json.bool(toggles, p.id());
            int cx = gx + p.x() * cell + cell / 2 - nodeW / 2;
            int cy = top + p.y() * rowH + rowH / 2 - nodeH / 2;
            int fill = level <= 0 ? TREE_LOCKED : level >= p.maxLevel() ? ink(GOLD) : AlpakaStyle.accent();
            if (off) fill = MarketIcons.fade(fill, 0.35f);
            int radius = p.ability() ? nodeH / 2 : 4;
            boolean hover = hover(cx, cy, nodeW, nodeH);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, nodeW, nodeH, radius, fill);
            if (hover) ModernGuiUtils.drawRoundedOutline(graphics, cx, cy, nodeW, nodeH, radius, AlpakaStyle.TEXT);
            if (p.maxLevel() > 1 && level > 0) {
                String n = String.valueOf(level);
                text(graphics, n, cx + (nodeW - textW(n)) / 2, cy + 4, 0xFF1B1B20);
            }
            if (hover) {
                // The perk's own tooltip, lore and numbers for its level, as the game shows it.
                Boolean enabled = toggles.has(p.id()) ? !off : null;
                List<Component> lines = new ArrayList<>();
                for (String line : SkillTrees.tooltip(tree, p, levels, core, tier, enabled)) lines.add(Component.literal(line));
                hoveredLines = lines;
            }
        }

        // A legend for the colours.
        int ly = y + h - 14;
        int lx = x + pad;
        int[] colors = {ink(GOLD), AlpakaStyle.accent(), TREE_LOCKED, MarketIcons.fade(AlpakaStyle.accent(), 0.35f)};
        String[] labels = {"Maxed", "Under way", "Not taken", "Switched off"};
        for (int i = 0; i < labels.length; i++) {
            ModernGuiUtils.drawRoundedRect(graphics, lx, ly + 1, 7, 7, 2, colors[i]);
            text(graphics, labels[i], lx + 11, ly, AlpakaStyle.TEXT_MUTED);
            lx += 11 + textW(labels[i]) + 12;
        }
        String took = taken + " taken";
        text(graphics, took, x + w - pad - textW(took), ly, AlpakaStyle.TEXT_MUTED);
        return y + h;
    }

    // ---- Dungeons -------------------------------------------------------------------------------

    private int dungeons(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.CATACOMBS, "Catacombs", String.format(Locale.US, "%.2f", Levels.catacombs(d.catacombs.xp())), null));
        tiles.add(new Tile(Metrics.RUNS, "Total Runs", Coins.full(d.totalRuns), null));
        tiles.add(new Tile(null, "Total Secrets", Coins.full(d.secretsFound), null));
        // Per completed run, Entrance and Master Mode included, as the profile viewers count it.
        tiles.add(new Tile(null, "Secrets / Run", d.totalRuns > 0 ? String.format(Locale.US, "%.2f", d.secretsFound / d.totalRuns) : "—", null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        y = levelGrid(graphics, d.classes, Metrics.CLASS, x, y, w, d.classAverageOverflow) + 6;

        // Floors: completions and the fastest S+ of each, Entrance to F7 and M1 to M7.
        int h = 24 + 2 * 34 + 6;
        card(graphics, x, y, w, h);
        text(graphics, "Floor Completions", x + 8, y + 7, AlpakaStyle.TEXT);
        String hint = "fastest S+ below";
        text(graphics, hint, x + w - 8 - textW(hint), y + 7, AlpakaStyle.TEXT_MUTED);
        int cols = 8;
        int cellW = (w - 16) / cols;
        int normal = 0;
        int master = 0;
        for (ProfileData.Floor f : d.floors) {
            int index = f.master() ? master++ : normal++;
            int cx = x + 8 + (f.master() ? index + 1 : index) * cellW;
            int cy = y + 22 + (f.master() ? 34 : 0);
            boolean played = f.completions() > 0;
            ModernGuiUtils.drawRoundedRect(graphics, cx + 1, cy, cellW - 2, 31, AlpakaStyle.ROW_RADIUS,
                    played ? AlpakaStyle.ROW_HOVER : MarketIcons.fade(AlpakaStyle.ROW_HOVER, 0.5f));
            String label = f.label().equals("Entrance") ? "E" : f.label();
            text(graphics, label, cx + 5, cy + 3, f.master() ? ink(0xFFFF6B6B) : AlpakaStyle.TEXT_MUTED);
            String runs = Coins.compact(f.completions());
            text(graphics, runs, cx + cellW - 5 - textW(runs), cy + 3, played ? AlpakaStyle.TEXT : AlpakaStyle.DIVIDER);
            long best = f.fastestSPlus() > 0 ? f.fastestSPlus() : f.fastestS();
            String time = best > 0 ? Metrics.clock(best) : "—";
            text(graphics, time, cx + 5, cy + 18, best > 0 ? (f.fastestSPlus() > 0 ? AlpakaStyle.accent() : AlpakaStyle.TEXT_SOFT) : AlpakaStyle.DIVIDER);
            if (hover(cx + 1, cy, cellW - 2, 31) && played) {
                tip(f, f.label() + ": " + Coins.full(f.completions()) + " completions, best score " + f.bestScore()
                        + (f.fastestSPlus() > 0 ? ", fastest S+ " + Metrics.clock(f.fastestSPlus()) : "")
                        + (f.fastestS() > 0 ? ", fastest S " + Metrics.clock(f.fastestS()) : "") + ".");
            }
        }
        y += h;
        y = essence(graphics, d, x, y + 6, w);
        String metric = metricFor(Page.DUNGEONS);
        return chartCard(graphics, metric, 0, x, y + 6, w, 130);
    }

    /** Every essence the game has, so one the player has none of shows as none rather than missing. */
    private static final List<String> ESSENCES = List.of("WITHER", "UNDEAD", "DRAGON", "DIAMOND", "GOLD", "ICE", "SPIDER", "CRIMSON", "FOREST");

    /** The essence the player holds, most first, each with its item; Elite keys them "WITHER", "GOLD", ... */
    private int essence(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        Map<String, Double> amounts = new LinkedHashMap<>();
        for (String id : ESSENCES) amounts.put(id, 0.0);
        for (var e : Json.obj(d.raw, "stats.miscStats.essence").entrySet()) {
            if (e.getValue().isJsonPrimitive()) amounts.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue().getAsDouble());
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(amounts.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int cellH = 20;
        int cols = Math.max(1, Math.min(sorted.size(), (w - 16) / 96));
        int cellW = (w - 16) / cols;
        int rows = (sorted.size() + cols - 1) / cols;
        int h = 24 + rows * (cellH + 2) + 4;
        card(graphics, x, y, w, h);
        text(graphics, "Essence", x + 8, y + 7, AlpakaStyle.TEXT);
        for (int i = 0; i < sorted.size(); i++) {
            String id = sorted.get(i).getKey();
            double amount = sorted.get(i).getValue();
            String name = ProfileData.title(id.toLowerCase(Locale.ROOT));
            int cx = x + 8 + (i % cols) * cellW;
            int cy = y + 22 + (i / cols) * (cellH + 2);
            boolean has = amount > 0;
            ModernGuiUtils.drawRoundedRect(graphics, cx + 1, cy, cellW - 2, cellH, AlpakaStyle.ROW_RADIUS,
                    has ? AlpakaStyle.ROW_HOVER : MarketIcons.fade(AlpakaStyle.ROW_HOVER, 0.5f));
            stackIcon(graphics, ItemIcons.forItem("ESSENCE_" + id, name + " Essence"), cx + 3, cy + 2, 1.0f);
            String value = Coins.compact(amount);
            int vx = cx + cellW - 6 - textW(value);
            text(graphics, AlpakaStyle.ellipsize(this.font, name, vx - cx - 26), cx + 22, cy + 6, has ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
            text(graphics, value, vx, cy + 6, has ? AlpakaStyle.TEXT : AlpakaStyle.DIVIDER);
            if (hover(cx + 1, cy, cellW - 2, cellH)) tip("essence:" + id, name + " Essence: " + Coins.full(amount) + ".");
        }
        return y + h;
    }

    // ---- Slayers --------------------------------------------------------------------------------

    private int slayers(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        int kills = 0;
        int levels = 0;
        int maxLevels = 0;
        for (ProfileData.Slayer s : d.slayers) {
            for (int k : s.kills()) kills += k;
            levels += s.level();
            maxLevels += s.maxLevel();
        }
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.SLAYER_TOTAL, "Slayer XP", Coins.compact(d.slayerXp), null));
        tiles.add(new Tile(null, "Bosses Killed", Coins.full(kills), null));
        tiles.add(new Tile(null, "Slayer Levels", levels + " / " + maxLevels, null));
        y = tiles(graphics, tiles, x, y, w, 3) + 6;

        String selected = metricFor(Page.SLAYERS);
        int rowH = 38;
        for (ProfileData.Slayer s : d.slayers) {
            String metric = Metrics.SLAYER + s.id();
            boolean hover = hover(x, y, w, rowH);
            ModernGuiUtils.drawRoundedRect(graphics, x, y, w, rowH, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (metric.equals(selected)) ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, rowH, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
            Item icon = SKILL_ICONS.get(s.id());
            if (icon != null) icon(graphics, icon, x + 4, y + 3, 2.0f);
            int tx = x + 42;
            text(graphics, s.name(), tx, y + 7, AlpakaStyle.TEXT_SOFT);
            String level = "Level " + s.level();
            int levelX = tx + textW(s.name()) + 8;
            text(graphics, level, levelX, y + 7, s.maxed() ? ink(GOLD) : AlpakaStyle.TEXT);
            int levelEnd = levelX + textW(level);
            if (s.maxed()) levelEnd = maxBadge(graphics, levelEnd + 5, y + 7);
            int right = x + w - 8;
            String xp = s.maxed() ? Coins.full(s.xp()) + " XP" : Coins.compact(s.xpForNext()) + " XP to " + (s.level() + 1);
            if (levelEnd + 12 + textW(xp) < right) text(graphics, xp, right - textW(xp), y + 7, AlpakaStyle.TEXT_MUTED);
            if (s.maxed()) maxedBar(graphics, tx, y + 19, w - 42 - 8, 3, 1);
            else bar(graphics, tx, y + 19, w - 42 - 8, 3, s.progress(), AlpakaStyle.accent());

            // The kills of each tier, as far as the boss has tiers, and the recent pace.
            int tiers = s.id().equals("zombie") || s.id().equals("vampire") || s.kills()[4] > 0 ? 5 : 4;
            StringBuilder killText = new StringBuilder();
            for (int t = 0; t < tiers; t++) {
                if (t > 0) killText.append("   ");
                killText.append("T").append(t + 1).append(" ").append(Coins.compact(s.kills()[t]));
            }
            String killsLine = killText.toString();
            text(graphics, killsLine, tx, y + 26, AlpakaStyle.TEXT_MUTED);
            double rate = session.ratePerDay(metric, 7);
            if (rate > 0) {
                String pace = "+" + Coins.compact(rate) + " XP/day";
                if (tx + textW(killsLine) + 12 + textW(pace) < right) text(graphics, pace, right - textW(pace), y + 26, ink(ProfileChart.UP));
            }
            hit(x, y, w, rowH, () -> chosen.put(Page.SLAYERS, metric));
            if (hover) {
                tip(metric, s.name() + ": " + Coins.full(s.xp()) + " XP" + (s.maxed() ? "" : ", " + Coins.full(s.xpForNext())
                        + " to level " + (s.level() + 1)) + ". Bosses killed by tier: " + killsLine.replace("   ", ", ") + ". Click for its graph.");
            }
            y += rowH + 4;
        }
        return chartCard(graphics, selected, 0, x, y + 2, w, 130);
    }

    // ---- Networth -------------------------------------------------------------------------------

    /** One category of the breakdown: what it is called and what it holds in all. */
    private record Part(String label, double value) {}

    /** One part of an item's worth as the networth priced it, its {@link ItemValue.Group} by name. */
    private record Piece(String group, String label, double count, double value) {}

    /**
     * One thing the networth counts - a stack in a slot, a pet, what a sack holds, an essence -
     * with the category it is listed under, where it is in the game and what its worth is made
     * of. {@code item} is the inventory item and {@code pet} the pet, to draw it and to open it
     * where it is; null when there is none.
     */
    private record Holding(String name, String id, int count, String category, String where, double value,
                           List<Piece> pieces, JsonElement item, ProfileData.Pet pet, String inventory) {}

    /** A row of the item list: one holding, or every holding of the same item when they are grouped. */
    private record Line(String key, List<Holding> holdings, double value, long count) {
        Holding first() {
            return holdings.get(0);
        }
    }

    /** The rows the item list starts with, and how many more each "Show More" adds. */
    private static final int WORTH_FIRST = 15;
    private static final int WORTH_MORE = 25;
    private static final int WORTH_ROW_H = 18;
    /** The most parts and places an opened row lists before it sums up the rest. */
    private static final int WORTH_DETAIL_MAX = 8;
    /** Where the Value Spread card splits the items, from the top down. */
    private static final double[] SPREAD_AT = {1e9, 1e8, 1e7, 1e6, 1e5, 0};
    private static final String[] SPREAD_LABELS = {"1B+", "100M – 1B", "10M – 100M", "1M – 10M", "100k – 1M", "Under 100k"};

    private JsonElement breakdownFor;
    private String breakdownProfile;
    private int holdingsVersion;
    private List<Part> parts = List.of();
    private List<Holding> holdings = List.of();
    /** The categories with something in them to list; Coins has nothing. */
    private java.util.Set<String> holdingCategories = java.util.Set.of();
    /** The category the insights and the item list are narrowed to, by its label; null for all of them. */
    private String worthFilter;
    /** Whether the item list puts the same item from different places on one row. */
    private boolean worthGrouped;
    private int worthShown = WORTH_FIRST;
    /** The row of the item list that is folded open, by its key; null for none. */
    private String worthOpen;
    /** What the filter lets through, worked out again only when the filter, grouping or networth changes. */
    private String worthFor;
    private List<Holding> worthIn = List.of();
    private List<Line> worthLines = List.of();
    private double worthTotal;
    private final double[] worthSources = new double[ItemValue.Group.values().length];
    private final int[] spreadCounts = new int[SPREAD_AT.length];
    private final double[] spreadValues = new double[SPREAD_AT.length];

    /** The networth, or what it is waiting for: it is worked out after the profile arrived, once prices are there. */
    private String networthText(ProfileData d) {
        if (d.networth > 0) return Coins.compact(d.networth);
        return session.networthState == ProfileSession.State.FAILED ? "—" : "…";
    }

    private int networth(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.NETWORTH, "Networth", networthText(d), null));
        // Plain cards: the history has next to nothing for these.
        tiles.add(new Tile(null, "Liquid", d.networth > 0 ? Coins.compact(d.liquidNetworth) : networthText(d), "can be sold as it is"));
        tiles.add(new Tile(null, "Purse", Coins.compact(d.purse), null));
        tiles.add(coop(session)
                ? new Tile(null, "Bank", Coins.compact(d.bank) + " / " + Coins.compact(d.personalBank), "co-op / personal")
                : new Tile(null, "Bank", Coins.compact(d.bank + d.personalBank), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        if (!d.inventoryApi) {
            y = note(graphics, "The Inventory API is off for this profile, so the networth only counts coins. Turn it on in SkyBlock's settings under API Settings.", x, y, w) + 6;
        }
        y = chartCard(graphics, metricFor(Page.NETWORTH), 0, x, y, w, 130) + 6;

        JsonElement answer = session.networth();
        if (answer == null) {
            boolean failed = session.networthState == ProfileSession.State.FAILED;
            int h = 40;
            card(graphics, x, y, w, h);
            ProfileChart.message(graphics, this.font, failed ? "Couldn't load the prices. Click to try again." : "Working out the networth…", x, y, w, h);
            if (failed) hit(x, y, w, h, session::retryFailed);
            return y + h;
        }
        if (answer != breakdownFor) {
            breakdownFor = answer;
            readBreakdown(answer, d);
        }

        y = worthBreakdown(graphics, x, y, w) + 6;
        if (holdings.isEmpty()) return y;
        narrowWorth();
        if (worthIn.isEmpty()) return y;
        int sourcesH = sourcesHeight();
        int spreadH = spreadHeight();
        if (w >= 380) {
            // Side by side, as tall as the taller of them.
            int lw = (w - 6) / 2;
            int h = Math.max(sourcesH, spreadH);
            worthSourcesCard(graphics, x, y, lw, h);
            worthSpreadCard(graphics, x + lw + 6, y, w - lw - 6, h);
            y += h + 6;
        } else {
            worthSourcesCard(graphics, x, y, w, sourcesH);
            y += sourcesH + 6;
            worthSpreadCard(graphics, x, y, w, spreadH);
            y += spreadH + 6;
        }
        return worthItems(graphics, x, y, w);
    }

    /** The color a category has in the breakdown, which its items' bars in the list share. */
    private int categoryColor(String label) {
        for (int i = 0; i < parts.size(); i++) if (parts.get(i).label().equals(label)) return ink(PALETTE[i % PALETTE.length]);
        return AlpakaStyle.TEXT_MUTED;
    }

    private static int groupColor(ItemValue.Group group) {
        return ink(PALETTE[group.ordinal() % PALETTE.length]);
    }

    private static ItemValue.Group group(String name) {
        try {
            return ItemValue.Group.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ItemValue.Group.BASE;
        }
    }

    private void filterWorth(String label) {
        worthFilter = label == null || label.equals(worthFilter) ? null : label;
        worthShown = WORTH_FIRST;
        worthOpen = null;
    }

    private boolean hasHoldings(String label) {
        return holdingCategories.contains(label);
    }

    /** Where the networth sits: one bar split by category, and the categories listed under it; a click narrows the page to one. */
    private int worthBreakdown(GuiGraphicsExtractor graphics, int x, int y, int w) {
        double total = 0;
        for (Part p : parts) total += p.value();
        int listCols = w >= 380 ? 2 : 1;
        int rows = (parts.size() + listCols - 1) / listCols;
        int h = 24 + 12 + rows * 13 + 6;
        card(graphics, x, y, w, h);
        text(graphics, "Breakdown", x + 8, y + 7, AlpakaStyle.TEXT);
        if (worthFilter != null) {
            String all = "Show All";
            chip(graphics, all, x + w - 8 - textW(all) - 14, y + 4, false, () -> filterWorth(null));
        } else if (!holdings.isEmpty()) {
            String hint = "Click a category to look closer";
            if (textW("Breakdown") + 24 + textW(hint) < w) text(graphics, hint, x + w - 8 - textW(hint), y + 7, AlpakaStyle.TEXT_MUTED);
        }
        int bx = x + 8;
        int bw = w - 16;
        int by = y + 22;
        ModernGuiUtils.drawRoundedRect(graphics, bx, by, bw, 7, 3, AlpakaStyle.TRACK);
        double acc = 0;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            int from = bx + (int) Math.round(acc / total * bw);
            acc += p.value();
            int to = bx + (int) Math.round(acc / total * bw);
            if (to - from < 1) continue;
            boolean chosen = worthFilter == null || worthFilter.equals(p.label());
            int color = ink(PALETTE[i % PALETTE.length]);
            ModernGuiUtils.drawRect(graphics, from, by, to - from, 7, chosen ? color : MarketIcons.fade(color, 0.3f));
            if (hasHoldings(p.label())) {
                String label = p.label();
                if (hover(from, by - 2, to - from, 11)) tip(p, label + ": " + Coins.compact(p.value()) + ". Click to look closer.");
                hit(from, by - 2, to - from, 11, () -> filterWorth(label));
            }
        }
        int colW = (w - 16) / listCols;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            int cx = x + 8 + (i % listCols) * colW;
            int cy = y + 36 + (i / listCols) * 13;
            boolean clickable = hasHoldings(p.label());
            boolean selected = p.label().equals(worthFilter);
            boolean hover = clickable && hover(cx - 3, cy - 2, colW - 4, 12);
            if (hover || selected) {
                ModernGuiUtils.drawRoundedRect(graphics, cx - 3, cy - 2, colW - 4, 12, 3, selected ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW_HOVER);
            }
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy + 2, 5, 5, 2, ink(PALETTE[i % PALETTE.length]));
            int labelColor = selected ? AlpakaStyle.accent() : worthFilter != null ? AlpakaStyle.TEXT_MUTED : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
            text(graphics, p.label(), cx + 9, cy, labelColor);
            String value = Coins.compact(p.value());
            String share = String.format(Locale.US, "%.1f%%", total > 0 ? p.value() / total * 100 : 0);
            int right = cx + colW - 10;
            text(graphics, share, right - textW(share), cy, AlpakaStyle.TEXT_MUTED);
            text(graphics, value, right - textW(share) - 8 - textW(value), cy, AlpakaStyle.TEXT);
            if (clickable) {
                String label = p.label();
                hit(cx - 3, cy - 2, colW - 4, 12, () -> filterWorth(label));
            }
        }
        return y + h;
    }

    /** Works out what the filter lets through, the rows of the list and the sums of both insight cards. */
    private void narrowWorth() {
        String key = holdingsVersion + "/" + worthFilter + "/" + worthGrouped;
        if (key.equals(worthFor)) return;
        worthFor = key;
        List<Holding> in = new ArrayList<>();
        java.util.Arrays.fill(worthSources, 0);
        java.util.Arrays.fill(spreadCounts, 0);
        java.util.Arrays.fill(spreadValues, 0);
        double total = 0;
        Map<String, List<Holding>> byKey = new LinkedHashMap<>();
        for (int i = 0; i < holdings.size(); i++) {
            Holding h = holdings.get(i);
            if (worthFilter != null && !worthFilter.equals(h.category())) continue;
            in.add(h);
            total += h.value();
            if (h.pieces().isEmpty()) {
                // Sacks and essence are the items themselves, nothing put on them.
                worthSources[ItemValue.Group.BASE.ordinal()] += h.value();
            } else {
                for (Piece p : h.pieces()) worthSources[group(p.group()).ordinal()] += p.value();
            }
            int bucket = SPREAD_AT.length - 1;
            for (int b = 0; b < SPREAD_AT.length; b++) {
                if (h.value() >= SPREAD_AT[b]) {
                    bucket = b;
                    break;
                }
            }
            spreadCounts[bucket]++;
            spreadValues[bucket] += h.value();
            byKey.computeIfAbsent(worthGrouped ? sameItem(h) : "#" + i, k -> new ArrayList<>()).add(h);
        }
        List<Line> lines = new ArrayList<>();
        byKey.forEach((k, list) -> {
            double value = 0;
            long count = 0;
            for (Holding h : list) {
                value += h.value();
                count += h.count();
            }
            lines.add(new Line(k, List.copyOf(list), value, count));
        });
        lines.sort((a, b) -> Double.compare(b.value(), a.value()));
        worthIn = List.copyOf(in);
        worthLines = List.copyOf(lines);
        worthTotal = total;
    }

    /**
     * What makes two holdings the same item for the grouped list: the same id under the same name.
     * Enchanted books all share both, so for them the enchantments on them count too.
     */
    private static String sameItem(Holding h) {
        String key = h.id() + "|" + SkyblockUtils.cleanColor(h.name());
        if ("ENCHANTED_BOOK".equals(h.id())) {
            StringBuilder b = new StringBuilder(key);
            for (Piece p : h.pieces()) b.append('|').append(p.label());
            return b.toString();
        }
        return key;
    }

    private int sourcesShown() {
        int n = 0;
        for (double v : worthSources) if (v > 0) n++;
        return Math.max(1, n);
    }

    private int sourcesHeight() {
        return 36 + sourcesShown() * 12 + 4;
    }

    /** What the items are worth as they are and what was put on them: stars, enchantments, gemstones and so on. */
    private void worthSourcesCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        card(graphics, x, y, w, h);
        text(graphics, "Value Sources", x + 8, y + 7, AlpakaStyle.TEXT);
        double base = worthSources[ItemValue.Group.BASE.ordinal()];
        if (worthTotal > base) {
            String extra = "+" + Coins.compact(worthTotal - base) + " on top";
            if (textW("Value Sources") + 24 + textW(extra) < w) {
                text(graphics, extra, x + w - 8 - textW(extra), y + 7, AlpakaStyle.TEXT_MUTED);
                if (hover(x + w - 8 - textW(extra), y + 5, textW(extra), 11)) {
                    tip("worth-extra", "What was put on the items - stars, enchantments, gemstones, reforges and more - adds "
                            + Coins.compact(worthTotal - base) + " to the " + Coins.compact(base) + " the items are worth as they are.");
                }
            }
        }
        int bx = x + 8;
        int bw = w - 16;
        int by = y + 22;
        ModernGuiUtils.drawRoundedRect(graphics, bx, by, bw, 7, 3, AlpakaStyle.TRACK);
        double acc = 0;
        ItemValue.Group[] groups = ItemValue.Group.values();
        List<ItemValue.Group> order = new ArrayList<>();
        for (ItemValue.Group g : groups) if (worthSources[g.ordinal()] > 0) order.add(g);
        order.sort((a, b) -> Double.compare(worthSources[b.ordinal()], worthSources[a.ordinal()]));
        for (ItemValue.Group g : order) {
            int from = bx + (int) Math.round(acc / worthTotal * bw);
            acc += worthSources[g.ordinal()];
            int to = bx + (int) Math.round(acc / worthTotal * bw);
            if (to - from >= 1) ModernGuiUtils.drawRect(graphics, from, by, to - from, 7, groupColor(g));
        }
        int ry = y + 36;
        for (ItemValue.Group g : order) {
            double v = worthSources[g.ordinal()];
            ModernGuiUtils.drawRoundedRect(graphics, x + 8, ry + 2, 5, 5, 2, groupColor(g));
            String share = String.format(Locale.US, "%.1f%%", v / worthTotal * 100);
            String value = Coins.compact(v);
            int right = x + w - 8;
            text(graphics, share, right - textW(share), ry, AlpakaStyle.TEXT_MUTED);
            int valueX = right - textW(share) - 8 - textW(value);
            text(graphics, value, valueX, ry, AlpakaStyle.TEXT);
            text(graphics, AlpakaStyle.ellipsize(this.font, g.title, valueX - 6 - (x + 17)), x + 17, ry, AlpakaStyle.TEXT_SOFT);
            ry += 12;
        }
    }

    private int spreadRows() {
        int n = 0;
        for (int c : spreadCounts) if (c > 0) n++;
        return Math.max(1, n);
    }

    private int spreadHeight() {
        return 24 + spreadRows() * 13 + 14;
    }

    /** How the worth is spread over cheap and dear items, and how much of it the few dearest hold. */
    private void worthSpreadCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        card(graphics, x, y, w, h);
        text(graphics, "Value Spread", x + 8, y + 7, AlpakaStyle.TEXT);
        String counted = Coins.full(worthIn.size()) + (worthIn.size() == 1 ? " item" : " items");
        if (textW("Value Spread") + 24 + textW(counted) < w) text(graphics, counted, x + w - 8 - textW(counted), y + 7, AlpakaStyle.TEXT_MUTED);
        int labelW = 0;
        int countW = 0;
        int valueW = 0;
        for (int b = 0; b < SPREAD_AT.length; b++) {
            if (spreadCounts[b] == 0) continue;
            labelW = Math.max(labelW, textW(SPREAD_LABELS[b]));
            countW = Math.max(countW, textW("×" + Coins.full(spreadCounts[b])));
            valueW = Math.max(valueW, textW(Coins.compact(spreadValues[b])));
        }
        int barX = x + 8 + labelW + 6;
        int barW = x + w - 8 - valueW - 8 - countW - 8 - barX;
        double most = 0;
        for (double v : spreadValues) most = Math.max(most, v);
        int ry = y + 22;
        for (int b = 0; b < SPREAD_AT.length; b++) {
            if (spreadCounts[b] == 0) continue;
            text(graphics, SPREAD_LABELS[b], x + 8, ry, AlpakaStyle.TEXT_SOFT);
            if (barW >= 16) {
                ModernGuiUtils.drawRoundedRect(graphics, barX, ry + 2, barW, 5, 2, AlpakaStyle.TRACK);
                int fill = (int) Math.round(spreadValues[b] / most * barW);
                if (fill >= 2) ModernGuiUtils.drawRoundedRect(graphics, barX, ry + 2, fill, 5, 2, MarketIcons.fade(AlpakaStyle.TEXT_SOFT, 0.75f));
            }
            String count = "×" + Coins.full(spreadCounts[b]);
            String value = Coins.compact(spreadValues[b]);
            text(graphics, value, x + w - 8 - textW(value), ry, AlpakaStyle.TEXT);
            text(graphics, count, x + w - 8 - valueW - 8 - textW(count), ry, AlpakaStyle.TEXT_MUTED);
            if (hover(x + 4, ry - 2, w - 8, 12)) {
                tip(SPREAD_LABELS[b], Coins.full(spreadCounts[b]) + (spreadCounts[b] == 1 ? " item" : " items") + " worth "
                        + SPREAD_LABELS[b].replace("Under", "under") + " each, " + Coins.compact(spreadValues[b]) + " together ("
                        + String.format(Locale.US, "%.1f%%", spreadValues[b] / worthTotal * 100) + ").");
            }
            ry += 13;
        }
        // How top-heavy it is: the share of the dearest item and of the ten dearest, one stack each.
        double top1 = worthIn.isEmpty() ? 0 : maxValue(worthIn);
        double top10 = topSum(worthIn, 10);
        String line = "Top item " + percent(top1 / worthTotal) + " · Top 10 " + percent(top10 / worthTotal);
        text(graphics, AlpakaStyle.ellipsize(this.font, line, w - 16), x + 8, y + h - 13, AlpakaStyle.TEXT_MUTED);
    }

    private static double maxValue(List<Holding> list) {
        double max = 0;
        for (Holding h : list) max = Math.max(max, h.value());
        return max;
    }

    private static double topSum(List<Holding> list, int n) {
        List<Double> values = new ArrayList<>(list.size());
        for (Holding h : list) values.add(h.value());
        values.sort(java.util.Comparator.reverseOrder());
        double sum = 0;
        for (int i = 0; i < Math.min(n, values.size()); i++) sum += values.get(i);
        return sum;
    }

    private static String percent(double share) {
        return String.format(Locale.US, "%.1f%%", share * 100);
    }

    /** Opens a holding where the dashboard shows it, or null where it doesn't (sacks, essence). */
    private Runnable worthOpener(Holding h) {
        if (h.pet() != null) return opener(Page.PETS, null, null, h.pet());
        if (h.item() != null && h.inventory() != null) return opener(h.inventory(), h.item());
        return null;
    }

    private static String openerTitle(Holding h) {
        if (h.pet() != null) return "Show in Pets";
        if ("museum".equals(h.inventory())) return "Show in Museum";
        return "Show in Inventory";
    }

    /** The parts of a row's worth, those of every holding on it added up by what they are. */
    private static List<Piece> pieces(Line line) {
        if (line.holdings().size() == 1) return line.first().pieces();
        Map<String, Piece> merged = new LinkedHashMap<>();
        for (Holding h : line.holdings()) {
            for (Piece p : h.pieces()) {
                merged.merge(p.group() + "|" + p.label(), p,
                        (a, b) -> new Piece(a.group(), a.label(), a.count() + b.count(), a.value() + b.value()));
            }
        }
        List<Piece> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> Double.compare(b.value(), a.value()));
        return out;
    }

    /** How tall the part a row folds open is. */
    private int detailHeight(Line line) {
        int h = 4;
        List<Piece> pieces = pieces(line);
        h += pieces.isEmpty() ? 12 : Math.min(WORTH_DETAIL_MAX, pieces.size()) * 12 + (pieces.size() > WORTH_DETAIL_MAX ? 12 : 0);
        if (line.holdings().size() > 1) {
            int places = line.holdings().size();
            h += 6 + 12 + Math.min(WORTH_DETAIL_MAX, places) * 12 + (places > WORTH_DETAIL_MAX ? 12 : 0);
        } else if (worthOpener(line.first()) != null) {
            h += 4 + 15;
        }
        return h + 6;
    }

    /** Every item the networth counts, dearest first, a few at a time; a click on one shows what it is worth and where it is. */
    private int worthItems(GuiGraphicsExtractor graphics, int x, int y, int w) {
        List<Line> lines = worthLines;
        int shown = Math.min(worthShown, lines.size());
        int h = 24;
        for (int i = 0; i < shown; i++) {
            h += WORTH_ROW_H;
            if (lines.get(i).key().equals(worthOpen)) h += detailHeight(lines.get(i));
        }
        boolean more = lines.size() > shown;
        boolean fewer = shown > WORTH_FIRST;
        if (more || fewer) h += 22;
        h += 4;
        card(graphics, x, y, w, h);
        String title = worthFilter == null ? "Items" : "Items · " + worthFilter;
        text(graphics, title, x + 8, y + 7, AlpakaStyle.TEXT);
        String group = "Group Same Items";
        int groupX = x + w - 8 - textW(group) - 14;
        chip(graphics, group, groupX, y + 4, worthGrouped, () -> {
            worthGrouped = !worthGrouped;
            worthOpen = null;
        });
        String count = Coins.full(lines.size()) + (lines.size() == 1 ? " item" : " items");
        if (x + 8 + textW(title) + 12 + textW(count) < groupX - 8) text(graphics, count, groupX - 8 - textW(count), y + 7, AlpakaStyle.TEXT_MUTED);

        double top = lines.isEmpty() ? 1 : lines.get(0).value();
        double all = Math.max(1, worthTotalAll());
        int rankW = textW(shown + ".");
        String widest = "100.0%";
        int shareW = textW(widest);
        int ry = y + 22;
        for (int i = 0; i < shown; i++) {
            Line line = lines.get(i);
            Holding first = line.first();
            boolean open = line.key().equals(worthOpen);
            boolean visible = ry <= clipY + clipH && ry + WORTH_ROW_H >= clipY;
            if (visible) {
                boolean hover = hover(x + 4, ry, w - 8, WORTH_ROW_H - 1);
                if (hover || open) {
                    ModernGuiUtils.drawRoundedRect(graphics, x + 4, ry, w - 8, WORTH_ROW_H - 1, AlpakaStyle.ROW_RADIUS,
                            open ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW_HOVER);
                }
                String rank = (i + 1) + ".";
                text(graphics, rank, x + 8 + rankW - textW(rank), ry + 5, AlpakaStyle.TEXT_MUTED);
                int ix = x + 8 + rankW + 4;
                ItemStack stack = first.item() != null ? InventoryItems.stack(first.item())
                        : first.pet() != null ? PetIcons.icon(first.pet())
                        : ItemIcons.forItem(first.id(), SkyblockUtils.cleanColor(first.name()));
                if (stack != null && !stack.isEmpty()) graphics.item(stack, ix, ry);
                int right = x + w - 18;
                AlpakaStyle.chevron(graphics, x + w - 12, ry + 8, open, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
                String share = percent(line.value() / all);
                text(graphics, share, right - textW(share), ry + 5, AlpakaStyle.TEXT_MUTED);
                String value = Coins.compact(line.value());
                int valueX = right - shareW - 8 - textW(value);
                text(graphics, value, valueX, ry + 5, AlpakaStyle.TEXT);
                String where = line.holdings().size() > 1 ? line.holdings().size() + " places" : first.where();
                int whereX = valueX - 10 - textW(where);
                int nameX = ix + 20;
                boolean roomForWhere = whereX - nameX > 60;
                if (roomForWhere) text(graphics, where, whereX, ry + 5, AlpakaStyle.TEXT_MUTED);
                int nameMax = (roomForWhere ? whereX : valueX) - 8 - nameX;
                String amount = line.count() > 1 ? "×" + Coins.full(line.count()) : "";
                int amountW = amount.isEmpty() ? 0 : textW(amount) + 5;
                styled(graphics, first.name(), nameX, ry + 3, nameMax - amountW);
                if (!amount.isEmpty()) {
                    int nameW = Math.min(nameMax - amountW, this.font.width(GuiFont.text(first.name())));
                    text(graphics, amount, nameX + nameW + 5, ry + 3, AlpakaStyle.TEXT_MUTED);
                }
                // How it compares with the dearest row, in its category's color.
                int barMax = Math.max(0, nameMax);
                int fill = (int) Math.round(line.value() / top * barMax);
                if (fill >= 1) ModernGuiUtils.drawRect(graphics, nameX, ry + 13, fill, 1, MarketIcons.fade(categoryColor(first.category()), 0.7f));
                String key = line.key();
                hit(x + 4, ry, w - 8, WORTH_ROW_H - 1, () -> worthOpen = key.equals(worthOpen) ? null : key);
                if (hover && first.item() != null) hoveredItem = first.item();
            }
            ry += WORTH_ROW_H;
            if (open) ry = worthDetail(graphics, line, x + 4, ry, w - 8);
        }
        if (more || fewer) {
            int cx = x + 8;
            int cy = ry + 4;
            if (more) {
                int next = Math.min(WORTH_MORE, lines.size() - shown);
                cx += chip(graphics, "Show " + next + " More", cx, cy, false, () -> worthShown = shown + next) + 4;
                cx += chip(graphics, "Show All (" + Coins.full(lines.size()) + ")", cx, cy, false, () -> worthShown = Integer.MAX_VALUE) + 4;
            }
            if (fewer) {
                chip(graphics, "Show Fewer", cx, cy, false, () -> {
                    worthShown = WORTH_FIRST;
                    worthOpen = null;
                });
            }
        }
        return y + h;
    }

    /** The networth over everything, which each row's share is of, whatever the filter. */
    private double worthTotalAll() {
        double total = 0;
        for (Part p : parts) total += p.value();
        return total;
    }

    /** A row folded open: what its worth is made of, and where it is. */
    private int worthDetail(GuiGraphicsExtractor graphics, Line line, int x, int y, int w) {
        int h = detailHeight(line);
        boolean visible = y <= clipY + clipH && y + h >= clipY;
        if (!visible) return y + h;
        ModernGuiUtils.drawRect(graphics, x + 10, y + 2, 1, h - 6, AlpakaStyle.LINE);
        int lx = x + 18;
        int right = x + w - 14;
        int ry = y + 4;
        List<Piece> pieces = pieces(line);
        if (pieces.isEmpty()) {
            double each = line.value() / Math.max(1, line.count());
            String text = Coins.full(line.count()) + " × " + Coins.compact(each) + " each";
            text(graphics, text, lx, ry, AlpakaStyle.TEXT_SOFT);
            ry += 12;
        } else {
            int n = Math.min(WORTH_DETAIL_MAX, pieces.size());
            for (int i = 0; i < n; i++) {
                Piece p = pieces.get(i);
                ItemValue.Group g = group(p.group());
                ModernGuiUtils.drawRoundedRect(graphics, lx, ry + 2, 5, 5, 2, groupColor(g));
                String share = percent(p.value() / line.value());
                String value = Coins.compact(p.value());
                text(graphics, share, right - textW(share), ry, AlpakaStyle.TEXT_MUTED);
                int valueX = right - textW("100.0%") - 8 - textW(value);
                text(graphics, value, valueX, ry, AlpakaStyle.TEXT);
                String label = p.label() + (p.count() > 1 ? " ×" + Coins.full(p.count()) : "");
                String what = g == ItemValue.Group.BASE ? "" : g.title + " · ";
                text(graphics, AlpakaStyle.ellipsize(this.font, what + label, valueX - 8 - (lx + 9)), lx + 9, ry,
                        g == ItemValue.Group.BASE ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
                ry += 12;
            }
            if (pieces.size() > n) {
                double rest = 0;
                for (int i = n; i < pieces.size(); i++) rest += pieces.get(i).value();
                String more = (pieces.size() - n) + " more parts";
                text(graphics, more, lx + 9, ry, AlpakaStyle.TEXT_MUTED);
                String value = Coins.compact(rest);
                text(graphics, value, right - textW("100.0%") - 8 - textW(value), ry, AlpakaStyle.TEXT_MUTED);
                ry += 12;
            }
        }
        if (line.holdings().size() > 1) {
            ry += 6;
            text(graphics, "Where", lx, ry, AlpakaStyle.TEXT);
            ry += 12;
            List<Holding> places = new ArrayList<>(line.holdings());
            places.sort((a, b) -> Double.compare(b.value(), a.value()));
            int n = Math.min(WORTH_DETAIL_MAX, places.size());
            for (int i = 0; i < n; i++) {
                Holding place = places.get(i);
                Runnable open = worthOpener(place);
                boolean hover = open != null && hover(lx - 3, ry - 2, right - lx + 6, 12);
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, lx - 3, ry - 2, right - lx + 6, 12, 3, AlpakaStyle.ROW_HOVER);
                String value = Coins.compact(place.value());
                text(graphics, value, right - textW("100.0%") - 8 - textW(value), ry, AlpakaStyle.TEXT);
                String label = place.where() + (place.count() > 1 ? " ×" + Coins.full(place.count()) : "");
                text(graphics, AlpakaStyle.ellipsize(this.font, label, right - textW("100.0%") - 16 - textW(value) - lx), lx, ry,
                        hover ? AlpakaStyle.accent() : AlpakaStyle.TEXT_SOFT);
                if (open != null) {
                    hit(lx - 3, ry - 2, right - lx + 6, 12, open);
                    if (hover) tip(place, "Click to show it there.");
                }
                ry += 12;
            }
            if (places.size() > n) {
                text(graphics, "and " + (places.size() - n) + " more places", lx, ry, AlpakaStyle.TEXT_MUTED);
            }
        } else {
            Runnable open = worthOpener(line.first());
            if (open != null) chip(graphics, openerTitle(line.first()), lx, ry + 4, false, open);
        }
        return y + h;
    }

    private void readBreakdown(JsonElement answer, ProfileData d) {
        if (!d.profileId.equals(breakdownProfile)) {
            // Another profile: the list starts over.
            breakdownProfile = d.profileId;
            worthFilter = null;
            worthShown = WORTH_FIRST;
            worthOpen = null;
        }
        Map<String, Double> grouped = new LinkedHashMap<>();
        List<Holding> list = new ArrayList<>();
        List<ProfileData.Pet> petsLeft = new ArrayList<>(d.pets);
        for (var category : Json.obj(answer, "categories").entrySet()) {
            String key = category.getKey();
            String label = categoryLabel(key);
            double total = Json.num(category.getValue(), "total", 0);
            if (total > 0) grouped.merge(label, total, Double::sum);
            for (JsonElement e : Json.arr(category.getValue(), "items")) {
                double value = Json.num(e, "networth", 0);
                String id = Json.str(e, "item.skyblockId");
                if (!(value > 0) || id == null) continue;
                int count = Math.max(1, (int) Json.num(e, "item.count", 1));
                List<Piece> pieces = new ArrayList<>();
                for (JsonElement p : Json.arr(e, "parts")) {
                    pieces.add(new Piece(Json.str(p, "group"), String.valueOf(Json.str(p, "label")), Json.num(p, "count", 1), Json.num(p, "value", 0)));
                }
                pieces.sort((a, b) -> Double.compare(b.value(), a.value()));
                String inventory = Json.str(e, "inventory");
                String slot = Json.str(e, "slot");
                JsonElement item = inventory == null || slot == null ? null : Json.obj(session.inventory(inventory), "items").get(slot);
                if (item != null && !item.isJsonObject()) item = null;
                ProfileData.Pet pet = null;
                String name;
                String where;
                switch (key) {
                    case "pets" -> {
                        pet = takePet(petsLeft, id, Json.str(e, "tier"), (int) Json.num(e, "level", -1));
                        name = pet != null ? "§7[Lvl " + pet.level() + "] " + rarityCode(pet.tier()) + pet.name() : "§f" + Json.str(e, "item.name");
                        where = pet != null && pet.active() ? "Pets · Active" : "Pets";
                    }
                    case "sacks", "essence" -> {
                        String title = ItemIcons.name(id);
                        name = "§f" + (title != null ? title : ProfileData.title(id));
                        where = label;
                    }
                    default -> {
                        String title = Json.str(e, "item.name");
                        name = title != null ? title : "§f" + ProfileData.title(id);
                        where = inventory != null && slot != null ? where(inventory, slot) : label;
                    }
                }
                list.add(new Holding(name, id, count, label, where, value, List.copyOf(pieces), item, pet, inventory));
            }
        }
        List<Part> out = new ArrayList<>();
        grouped.forEach((label, value) -> out.add(new Part(label, value)));
        out.sort((a, b) -> Double.compare(b.value(), a.value()));
        list.sort((a, b) -> Double.compare(b.value(), a.value()));
        parts = List.copyOf(out);
        holdings = List.copyOf(list);
        java.util.Set<String> categories = new java.util.HashSet<>();
        for (Holding h : list) categories.add(h.category());
        holdingCategories = categories;
        holdingsVersion++;
        if (worthFilter != null && !hasHoldings(worthFilter)) worthFilter = null;
    }

    /** The profile's pet a networth entry is about, by kind, rarity and level; each pet is taken once. */
    private static ProfileData.Pet takePet(List<ProfileData.Pet> pets, String type, String tier, int level) {
        for (int i = 0; i < pets.size(); i++) {
            ProfileData.Pet p = pets.get(i);
            if (p.type().equals(type) && (tier == null || tier.equalsIgnoreCase(p.tier())) && (level < 0 || p.level() == level)) {
                return pets.remove(i);
            }
        }
        return null;
    }

    private static String categoryLabel(String key) {
        if (key.startsWith("backpack") || key.equals("icons_backpack")) return "Backpacks";
        return switch (key) {
            case "currency" -> "Coins";
            case "talisman_bag" -> "Accessories";
            case "ender_chest" -> "Ender Chest";
            case "personal_vault" -> "Personal Vault";
            case "sacks", "sacks_bag" -> "Sacks";
            case "wardrobe", "equipment_wardrobe" -> "Wardrobe";
            case "armor", "equipment" -> "Equipped";
            case "farming_toolkit", "hunting_toolkit" -> "Toolkits";
            case "potion_bag", "quiver", "fishing_bag" -> "Bags";
            default -> ProfileData.title(key);
        };
    }

    // ---- Pets -----------------------------------------------------------------------------------

    private int pets(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        Missing.Neu neu = Missing.neu();
        if (neu != null && maxPetsFor != d) {
            maxPets = Missing.maxPets(d.pets, neu, "bingo".equals(gameMode(session)));
            maxPetsFor = d;
        }
        List<Tile> tiles = new ArrayList<>();
        // Plain cards: the page has no graph to pick, and these counts rarely move.
        tiles.add(new Tile(null, "Pets", Coins.full(d.pets.size()), null));
        tiles.add(new Tile(null, "Max Pets", neu == null ? "…" : maxPets.maxed() + " / " + maxPets.total(),
                "max level and rarity"));
        tiles.add(new Tile(null, "Pet Skins", Coins.full(d.petSkins), null));
        tiles.add(new Tile(null, "Pet Score", Coins.full(d.petScore), "highest reached"));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        if (d.pets.isEmpty()) return note(graphics, "No pets on this profile.", x, y, w);

        // The rarities, as one bar.
        Map<String, Integer> byRarity = new LinkedHashMap<>();
        for (int i = ProfileData.RARITIES.length - 1; i >= 0; i--) byRarity.put(ProfileData.RARITIES[i], 0);
        for (ProfileData.Pet p : d.pets) byRarity.merge(p.tier() == null ? "COMMON" : p.tier(), 1, Integer::sum);
        int h = 40;
        card(graphics, x, y, w, h);
        int bx = x + 8;
        int bw = w - 16;
        int acc = 0;
        ModernGuiUtils.drawRoundedRect(graphics, bx, y + 8, bw, 7, 3, AlpakaStyle.TRACK);
        int lx = bx;
        for (var e : byRarity.entrySet()) {
            if (e.getValue() == 0) continue;
            int from = bx + Math.round(acc / (float) d.pets.size() * bw);
            acc += e.getValue();
            int to = bx + Math.round(acc / (float) d.pets.size() * bw);
            int color = ink(ProfileData.rarityColor(e.getKey()));
            ModernGuiUtils.drawRect(graphics, from, y + 8, to - from, 7, color);
            String label = ProfileData.title(e.getKey()) + " " + e.getValue();
            if (lx + textW(label) + 10 < x + w) {
                ModernGuiUtils.drawRoundedRect(graphics, lx, y + 24, 5, 5, 2, color);
                text(graphics, label, lx + 8, y + 22, AlpakaStyle.TEXT_SOFT);
                lx += textW(label) + 18;
            }
        }
        y += h + 6;

        int cols = Math.max(2, w / 170);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 40;
        for (int i = 0; i < d.pets.size(); i++) {
            ProfileData.Pet p = d.pets.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (p == focusPet) focusAt(cy, ch);
            if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (p == focusPet) focusMark(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS);
            else if (p.active()) ModernGuiUtils.drawRoundedOutline(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
            // The pet's head, in the skin it wears, as the game shows it; a placeholder while it loads.
            ItemStack head = PetIcons.icon(p);
            // Twice the size of an item, so the head can be made out; a whole multiple stays sharp.
            if (head != null) {
                stackIcon(graphics, head, cx + 4, cy + 4, 2.0f);
            } else {
                ModernGuiUtils.drawRoundedRect(graphics, cx + 6, cy + 6, 28, 28, 4, AlpakaStyle.ROW_SELECTED);
            }
            int tx = cx + 42;
            int tw = cw - 42 - 6;
            String level = "Lvl " + p.level();
            text(graphics, level, tx + tw - textW(level), cy + 9, p.level() >= p.maxLevel() ? ink(GOLD) : AlpakaStyle.TEXT_SOFT);
            text(graphics, AlpakaStyle.ellipsize(this.font, p.name(), tw - 6 - textW(level)), tx, cy + 9, ink(ProfileData.rarityColor(p.tier())));
            String held = p.active() ? "Active" + (p.heldItem() == null ? "" : " · " + heldItem(p.heldItem())) : p.heldItem() == null ? "" : heldItem(p.heldItem());
            text(graphics, AlpakaStyle.ellipsize(this.font, held, tw), tx, cy + 21, p.active() ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
            if (p.level() < p.maxLevel()) bar(graphics, tx, cy + ch - 5, tw, 1, p.progress(), MarketIcons.fade(AlpakaStyle.accent(), 0.7f));
            if (hover) {
                tip(p, petTip(p));
            }
        }
        int rows = (d.pets.size() + cols - 1) / cols;
        y += rows * (ch + gap) - gap;
        return missingPets(graphics, d, x, y + 12, w, cols, cw, ch, gap);
    }

    private ProfileData maxPetsFor;
    private Missing.MaxPets maxPets;
    private ProfileData missingPetsFor;
    private List<Missing.MissingPet> missingPets = List.of();

    /** Every pet the profile has none of, greyed out, in the highest rarity it comes in. */
    private int missingPets(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w, int cols, int cw, int ch, int gap) {
        Missing.Neu neu = Missing.neu();
        if (neu == null) {
            text(graphics, "Missing Pets", x, y, AlpakaStyle.TEXT);
            text(graphics, "Loading the list of every pet…", x, y + 14, AlpakaStyle.TEXT_MUTED);
            return y + 22;
        }
        if (missingPetsFor != d) {
            java.util.Set<String> owned = new java.util.HashSet<>();
            for (ProfileData.Pet p : d.pets) owned.add(p.type());
            missingPets = Missing.pets(owned, neu, "bingo".equals(gameMode(session)));
            missingPetsFor = d;
        }
        List<Missing.MissingPet> missing = missingPets;
        if (missing.isEmpty()) return y - 12;
        text(graphics, "Missing Pets", x, y, AlpakaStyle.TEXT);
        text(graphics, String.valueOf(missing.size()), x + textW("Missing Pets") + 6, y, AlpakaStyle.TEXT_MUTED);
        y += 14;
        for (int i = 0; i < missing.size(); i++) {
            Missing.MissingPet m = missing.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW : AlpakaStyle.PANEL);
            ItemStack head = PetIcons.icon(new ProfileData.Pet(m.type(), m.name(), m.tier(), 1, 100, 0, null, null, false, 0, 0));
            if (head != null) {
                stackIcon(graphics, head, cx + 4, cy + 4, 2.0f);
                veils.add(new int[] {cx + 4, cy + 4, 32, 32});
            } else {
                ModernGuiUtils.drawRoundedRect(graphics, cx + 6, cy + 6, 28, 28, 4, AlpakaStyle.ROW);
            }
            int tx = cx + 42;
            int tw = cw - 42 - 6;
            text(graphics, AlpakaStyle.ellipsize(this.font, m.name(), tw), tx, cy + 9, AlpakaStyle.TEXT_MUTED);
            String up = "Up to " + ProfileData.title(m.tier());
            text(graphics, AlpakaStyle.ellipsize(this.font, up, tw), tx, cy + 21, MarketIcons.fade(ink(ProfileData.rarityColor(m.tier())), 0.55f));
            if (hover) tip(m, "Not on this profile yet. Comes up to " + ProfileData.title(m.tier()) + ".");
        }
        int rows = (missing.size() + cols - 1) / cols;
        return y + rows * (ch + gap) - gap;
    }

    /**
     * What the card doesn't already say: the exact XP, which goes on growing past the max level and
     * sets two level 100 pets apart, how far it is past the max or short of it, and the skin.
     */
    private static String petTip(ProfileData.Pet p) {
        StringBuilder tip = new StringBuilder(Coins.full(Math.floor(p.exp())) + " XP");
        if (p.expForMax() > 0) {
            double over = p.exp() - p.expForMax();
            tip.append(over >= 0
                    ? "\n" + Coins.full(Math.floor(over)) + " XP over max level"
                    : "\n" + Coins.full(Math.ceil(-over)) + " XP to level " + p.maxLevel());
        }
        if (p.skin() != null) tip.append("\nSkin: ").append(ProfileData.title(p.skin()));
        return tip.toString();
    }

    private static String heldItem(String id) {
        String name = id.startsWith("PET_ITEM_") ? id.substring("PET_ITEM_".length()) : id;
        return ProfileData.title(name);
    }

    // ---- Collections ----------------------------------------------------------------------------

    private JsonElement collectionsFor;
    private ProfileData collectionsData;
    private List<ProfileData.Collection> collectionList = List.of();

    private int collections(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement raw = d.raw;
        List<Tile> tiles = new ArrayList<>();
        JsonElement resources = session.collections();
        if (resources != null && (resources != collectionsFor || collectionsData != d)) {
            collectionsFor = resources;
            collectionsData = d;
            collectionList = d.collections(resources);
        }
        int maxed = 0;
        for (ProfileData.Collection c : collectionList) if (c.maxed()) maxed++;
        tiles.add(new Tile(null, "Maxed Collections", resources == null ? "…" : maxed + " / " + collectionList.size(), null));
        // A plain card: the page has no graph to pick, and the count rarely moves.
        tiles.add(new Tile(null, "Unique Minions", Coins.full(Json.num(raw, "minionSummary.uniqueTiersCrafted", 0)), null));
        double toNext = Json.num(raw, "minionSummary.uniqueTiersToNextSlot", Double.NaN);
        tiles.add(new Tile(null, "Minion Slots", (int) Json.num(raw, "minionSummary.currentSlots", 0) + " / " + (int) Json.num(raw, "minionSummary.maxSlots", 0),
                Double.isNaN(toNext) || toNext <= 0 ? null : "next in " + (int) toNext + " crafts"));
        tiles.add(new Tile(null, "Pelts", Coins.full(Json.num(raw, "stats.miscStats.pelts", 0)), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        if (!d.collectionsApi) return note(graphics, "The Collections API is off for this profile. Turn it on in SkyBlock's settings under API Settings.", x, y, w);
        if (resources == null) {
            boolean failed = session.collectionsState == ProfileSession.State.FAILED;
            int h = 40;
            card(graphics, x, y, w, h);
            ProfileChart.message(graphics, this.font, failed ? "Couldn't load the collection tiers. Click to try again." : "Loading collections…", x, y, w, h);
            if (failed) hit(x, y, w, h, session::retryFailed);
            return y + h;
        }

        Map<String, List<ProfileData.Collection>> byCategory = new LinkedHashMap<>();
        for (ProfileData.Collection c : collectionList) byCategory.computeIfAbsent(c.category(), k -> new ArrayList<>()).add(c);
        if (skillDetail != null) {
            List<ProfileData.Collection> list = byCategory.get(skillDetail);
            if (list != null) return collectionCategory(graphics, skillDetail, list, x, y, w);
            skillDetail = null;
        }
        // One card per skill, its collections a click away: all of them on one page went on for screens.
        int cols = Math.max(2, w / 160);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 38;
        int i = 0;
        for (var e : byCategory.entrySet()) {
            String category = e.getKey();
            List<ProfileData.Collection> list = e.getValue();
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            i++;
            int done = 0, tiers = 0, maxTiers = 0;
            for (ProfileData.Collection c : list) {
                if (c.maxed()) done++;
                tiers += Math.min(c.tier(), c.maxTier());
                maxTiers += c.maxTier();
            }
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            Item skillIcon = SKILL_ICONS.get(category.toLowerCase(Locale.ROOT));
            if (skillIcon != null) {
                icon(graphics, skillIcon, cx + 3, cy + 3, 2.0f);
            } else {
                ItemStack first = collectionIcon(list.get(0));
                if (!first.isEmpty()) stackIcon(graphics, first, cx + 3, cy + 3, 2.0f);
            }
            int tx = cx + 40;
            int tw = cw - 40 - 6;
            boolean all = done == list.size();
            String count = done + " / " + list.size();
            int countW = all ? badgeW() : textW(count);
            if (all) maxBadge(graphics, tx + tw - countW, cy + 7);
            else text(graphics, count, tx + tw - countW, cy + 7, AlpakaStyle.TEXT_MUTED);
            String name = ProfileData.title(category);
            text(graphics, AlpakaStyle.ellipsize(this.font, name, tw - 8 - countW), tx, cy + 7, AlpakaStyle.TEXT_SOFT);
            double progress = maxTiers == 0 ? 0 : tiers / (double) maxTiers;
            levelBar(graphics, tx, cy + 19, tw, 2, progress, all);
            text(graphics, (int) Math.floor(progress * 100) + "% of tiers", tx, cy + 24, AlpakaStyle.TEXT_MUTED);
            hit(cx, cy, cw, ch, () -> skillDetail = category);
            if (hover) tip(category, name + ": " + done + " of " + list.size() + " collections maxed. Click for them.");
        }
        return y + ((byCategory.size() + cols - 1) / cols) * (ch + gap) - gap;
    }

    /** One skill's collections, each with its tier and what the next one takes. */
    private int collectionCategory(GuiGraphicsExtractor graphics, String category, List<ProfileData.Collection> list, int x, int y, int w) {
        String back = "‹ All Collections";
        int bw = textW(back) + 14;
        boolean backHover = hover(x, y, bw, 15);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, bw, 15, 7, backHover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
        text(graphics, back, x + 7, y + 4, backHover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
        hit(x, y, bw, 15, () -> skillDetail = null);
        String name = ProfileData.title(category);
        int done = 0;
        for (ProfileData.Collection c : list) if (c.maxed()) done++;
        text(graphics, name, x + bw + 8, y + 4, AlpakaStyle.TEXT);
        text(graphics, done + " / " + list.size() + " maxed", x + bw + 8 + textW(name) + 8, y + 4, AlpakaStyle.TEXT_MUTED);
        y += 15 + 8;

        int cols = Math.max(2, w / 160);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 38;
        for (int i = 0; i < list.size(); i++) {
            ProfileData.Collection c = list.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            // The item at twice its size, as the game's collection menu shows it.
            ItemStack icon = collectionIcon(c);
            if (!icon.isEmpty()) stackIcon(graphics, icon, cx + 3, cy + 3, 2.0f);
            int tx = cx + 40;
            int tw = cw - 40 - 6;
            String tier = c.tier() == 0 ? "—" : roman(c.tier());
            int tierW = c.maxed() ? maxedTier(graphics, tier, tx + tw, cy + 7) : textW(tier);
            if (!c.maxed()) text(graphics, tier, tx + tw - tierW, cy + 7, c.tier() == 0 ? AlpakaStyle.DIVIDER : AlpakaStyle.TEXT);
            text(graphics, AlpakaStyle.ellipsize(this.font, c.name() == null ? c.id() : c.name(), tw - 8 - tierW), tx, cy + 7,
                    c.amount() > 0 ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
            double progress = c.maxed() ? 1 : c.nextAt() > c.prevAt() ? (c.amount() - c.prevAt()) / (double) (c.nextAt() - c.prevAt()) : 0;
            levelBar(graphics, tx, cy + 19, tw, 2, progress, c.maxed());
            String amount = Coins.compact(c.amount()) + (c.maxed() || c.nextAt() <= 0 ? "" : " / " + Coins.compact(c.nextAt()));
            text(graphics, amount, tx, cy + 24, AlpakaStyle.TEXT_MUTED);
            if (hover) {
                tip(c, (c.name() == null ? c.id() : c.name()) + ": " + Coins.full(c.amount()) + " collected, tier " + c.tier()
                        + " of " + c.maxTier() + (c.maxed() ? "." : ". " + Coins.full(c.nextAt() - c.amount()) + " more for the next tier."));
            }
        }
        return y + ((list.size() + cols - 1) / cols) * (ch + gap) - gap;
    }

    /** Collections whose id names no item the game knows now: the item their menu shows. */
    private static final Map<String, String> COLLECTION_ITEMS = Map.of(
            "MUSHROOM_COLLECTION", "RED_MUSHROOM:0", "GEMSTONE_COLLECTION", "ROUGH_RUBY_GEM", "SEEDS", "WHEAT_SEEDS:0");

    /**
     * A collection's item. Its id is the item's 1.8 name ({@code INK_SACK:3}, {@code NETHER_STALK})
     * for the vanilla ones, so those go through the legacy names first; what that does not know is
     * one of SkyBlock's own items ({@code MOONFLOWER}, {@code UMBER}).
     */
    private static ItemStack collectionIcon(ProfileData.Collection c) {
        String id = COLLECTION_ITEMS.getOrDefault(c.id(), c.id());
        if (!id.contains(":")) {
            ItemStack vanilla = ItemIcons.forItem(id + ":0", c.name());
            if (!vanilla.isEmpty()) return vanilla;
        }
        return ItemIcons.forItem(id, c.name());
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
                "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV", "XXV"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    // ---- Bestiary -------------------------------------------------------------------------------

    private List<Bestiary.IslandDef> bestiaryRepo;
    private ProfileData bestiaryData;
    private List<Bestiary.Island> bestiaryList = List.of();
    private static final Map<String, ItemStack> BESTIARY_ICONS = new HashMap<>();

    private int bestiary(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement raw = d.raw;
        List<Bestiary.IslandDef> repo = Bestiary.repo();
        if (repo != null && (repo != bestiaryRepo || bestiaryData != d)) {
            bestiaryRepo = repo;
            bestiaryData = d;
            bestiaryList = Bestiary.of(repo, Json.at(raw, "stats.bestiary.mobKills"));
        }
        if (skillDetail != null) {
            for (Bestiary.Island island : bestiaryList) {
                if (island.name().equals(skillDetail)) return bestiaryIsland(graphics, island, x, y, w);
            }
            skillDetail = null;
        }
        int maxed = 0, families = 0;
        for (Bestiary.Island i : bestiaryList) {
            maxed += i.maxed();
            families += i.families().size();
        }
        List<Tile> tiles = new ArrayList<>();
        // Plain cards: the milestone's history has gaps, so no graph to pick.
        tiles.add(new Tile(null, "Milestone", Coins.full(d.bestiaryMilestone), null));
        tiles.add(new Tile(null, "Maxed Families", repo == null ? "…" : maxed + " / " + families, null));
        tiles.add(new Tile(null, "Kills", Coins.full(Json.num(raw, "stats.bestiaryDerivations.totalKills", 0)), null));
        tiles.add(new Tile(null, "Deaths", Coins.full(Json.num(raw, "stats.bestiaryDerivations.totalDeaths", 0)), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        if (repo == null) {
            int h = 40;
            card(graphics, x, y, w, h);
            ProfileChart.message(graphics, this.font, Bestiary.failed() ? "Couldn't load the Bestiary families. Trying again shortly."
                    : "Loading the Bestiary…", x, y, w, h);
            return y + h;
        }

        // One card per island, the families a click away: all of them on one page went on for screens.
        int cols = Math.max(2, w / 160);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 38;
        for (int i = 0; i < bestiaryList.size(); i++) {
            Bestiary.Island island = bestiaryList.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            ItemStack icon = bestiaryIcon(island.def().texture(), island.def().item());
            if (!icon.isEmpty()) stackIcon(graphics, icon, cx + 3, cy + 3, 2.0f);
            int tx = cx + 40;
            int tw = cw - 40 - 6;
            boolean done = island.maxed() == island.families().size();
            String count = island.maxed() + " / " + island.families().size();
            int countW = done ? badgeW() : textW(count);
            if (done) maxBadge(graphics, tx + tw - countW, cy + 7);
            else text(graphics, count, tx + tw - countW, cy + 7, AlpakaStyle.TEXT_MUTED);
            text(graphics, AlpakaStyle.ellipsize(this.font, island.name(), tw - 8 - countW), tx, cy + 7, AlpakaStyle.TEXT_SOFT);
            levelBar(graphics, tx, cy + 19, tw, 2, island.progress(), done);
            text(graphics, (int) Math.floor(island.progress() * 100) + "% of tiers", tx, cy + 24, AlpakaStyle.TEXT_MUTED);
            String name = island.name();
            hit(cx, cy, cw, ch, () -> skillDetail = name);
            if (hover) tip(island, name + ": " + island.maxed() + " of " + island.families().size() + " families maxed. Click for them.");
        }
        return y + ((bestiaryList.size() + cols - 1) / cols) * (ch + gap) - gap;
    }

    /** One island's families, each with its tier and the kills to the next. */
    private int bestiaryIsland(GuiGraphicsExtractor graphics, Bestiary.Island island, int x, int y, int w) {
        y = backRow(graphics, "‹ All Islands", island.name(), island.maxed() + " / " + island.families().size() + " maxed", x, y);
        List<Bestiary.Family> list = island.families();
        int cols = Math.max(2, w / 160);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 38;
        for (int i = 0; i < list.size(); i++) {
            Bestiary.Family f = list.get(i);
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            ItemStack icon = bestiaryIcon(f.def().texture(), f.def().item());
            if (!icon.isEmpty()) {
                stackIcon(graphics, icon, cx + 3, cy + 3, 2.0f);
                if (f.kills() == 0) veils.add(new int[] {cx + 3, cy + 3, 32, 32});
            }
            int tx = cx + 40;
            int tw = cw - 40 - 6;
            String tier = f.tier() == 0 ? "—" : roman(f.tier());
            int tierW = f.maxed() ? maxedTier(graphics, tier, tx + tw, cy + 7) : textW(tier);
            if (!f.maxed()) text(graphics, tier, tx + tw - tierW, cy + 7, f.tier() == 0 ? AlpakaStyle.DIVIDER : AlpakaStyle.TEXT);
            text(graphics, AlpakaStyle.ellipsize(this.font, f.def().name(), tw - 8 - tierW), tx, cy + 7,
                    f.kills() > 0 ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
            double progress = f.maxed() ? 1 : (f.kills() - f.prevAt()) / (double) (f.nextAt() - f.prevAt());
            levelBar(graphics, tx, cy + 19, tw, 2, progress, f.maxed());
            String kills = Coins.full(f.kills()) + (f.maxed() ? "" : " / " + Coins.full(f.nextAt()));
            text(graphics, kills, tx, cy + 24, AlpakaStyle.TEXT_MUTED);
            if (hover) {
                tip(f, f.def().name() + ": " + Coins.full(f.kills()) + " kills, tier " + f.tier() + " of " + f.maxTier()
                        + (f.maxed() ? "." : ". " + Coins.full(f.nextAt() - f.kills()) + " more for the next tier."));
            }
        }
        return y + ((list.size() + cols - 1) / cols) * (ch + gap) - gap;
    }

    /** A family's or island's head, or the item NEU gives it instead (a Dragon Egg for the dragons). */
    private static ItemStack bestiaryIcon(String texture, String item) {
        String key = texture != null ? texture : "item:" + item;
        ItemStack known = BESTIARY_ICONS.get(key);
        if (known != null) return known;
        ItemStack built = ItemStack.EMPTY;
        try {
            if (texture != null) {
                built = ItemIcons.head(texture);
            } else if (item != null) {
                Identifier id = Identifier.tryParse("minecraft:" + item);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) built = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            }
        } catch (RuntimeException notBoundYet) {
            // Items get their components once a world is joined; until then, no icon.
            return ItemStack.EMPTY;
        }
        BESTIARY_ICONS.put(key, built);
        return built;
    }

    // ---- Inventories ----------------------------------------------------------------------------

    /** The tabs of the inventory page: what each shows, and the inventories it needs. */
    private enum InvTab {
        INVENTORY("Inventory"), ENDER_CHEST("Ender Chest"), BACKPACKS("Backpacks"), WARDROBE("Wardrobe"),
        ACCESSORIES("Accessories"), VAULT("Vault"), BAGS("Bags");

        final String title;

        InvTab(String title) {
            this.title = title;
        }

        boolean present(ProfileData d) {
            return switch (this) {
                case INVENTORY -> d.inventories.containsKey("inventory");
                case ENDER_CHEST -> d.inventories.containsKey("ender_chest");
                case BACKPACKS -> !backpacks(d).isEmpty();
                case WARDROBE -> d.inventories.containsKey("wardrobe");
                case ACCESSORIES -> d.inventories.containsKey("talisman_bag");
                case VAULT -> d.inventories.containsKey("personal_vault");
                case BAGS -> BAGS_SHOWN.stream().anyMatch(b -> d.inventories.containsKey(b[0]));
            };
        }
    }

    private static final List<String[]> BAGS_SHOWN = List.of(
            new String[] {"potion_bag", "Potion Bag"}, new String[] {"quiver", "Quiver"}, new String[] {"sacks_bag", "Sack of Sacks"},
            new String[] {"farming_toolkit", "Farming Toolkit"}, new String[] {"hunting_toolkit", "Hunting Toolkit"});
    private static InvTab invTab = InvTab.INVENTORY;
    /** Which of the Rift's two inventories its page shows: false the inventory, true the Ender Chest. */
    private static boolean riftEnderChest;
    private static final int SLOT = 18;
    /**
     * How many times its size a slot is drawn: 2 for a single inventory with room to spare, so its
     * items read as in the game; a whole multiple keeps every texel on whole pixels.
     */
    private int slotScale = 1;
    /**
     * The tab bar pinned to the top of the view while the page scrolls under it: the band it
     * covers, where nothing of the content may light up or take a click. Empty when not pinned.
     */
    private int pinTop = Integer.MIN_VALUE;
    private int pinBottom = Integer.MIN_VALUE;
    private boolean drawingPin;
    /** A section of nine slots in a card: the slots and the card's padding. */
    private static final int SECTION_W = 9 * SLOT + 16;
    /** The item under the mouse this frame, whose tooltip the game draws after it. */
    private JsonElement hoveredItem;
    /** A tooltip in the game's own look, for things not from an inventory; null for none. */
    private List<Component> hoveredLines;

    /** The backpacks the profile has, in the order of their slots in the Storage menu. */
    private static List<String> backpacks(ProfileData d) {
        List<String> out = new ArrayList<>();
        for (String name : d.inventories.keySet()) if (BACKPACK_KEY.matcher(name).matches()) out.add(name);
        out.sort(java.util.Comparator.comparingInt(n -> Integer.parseInt(n.substring("backpack_".length()))));
        return out;
    }

    private int inventories(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        if (!d.inventoryApi) return apiOff(graphics, "Inventory", x, y, w);
        // The tabs, as chips; only those the profile has something for.
        List<InvTab> tabs = new ArrayList<>();
        for (InvTab t : InvTab.values()) if (t.present(d)) tabs.add(t);
        if (tabs.isEmpty()) return note(graphics, "Nothing in this profile's inventories.", x, y, w);
        if (!tabs.contains(invTab)) invTab = tabs.get(0);
        int rows = 1;
        int rowX = x;
        for (InvTab t : tabs) {
            int cw = textW(t.title) + 14;
            if (rowX + cw > x + w) {
                rows++;
                rowX = x;
            }
            rowX += cw + 4;
        }
        int barH = rows * 18 - 3;
        int contentY = y + barH + 8;
        // Scrolled past, the bar stays at the top of the view and the page runs on beneath it.
        int barY = Math.max(y, clipY + 4);
        boolean pinned = barY > y;
        if (pinned) {
            pinTop = clipY;
            pinBottom = barY + barH + 4;
        }
        int bottom = invContent(graphics, d, x, contentY, w);
        if (pinned) {
            // Above the items, which are drawn over everything else of their stratum.
            graphics.nextStratum();
            veils.removeIf(v -> v[1] < pinBottom);
            graphics.fill(x - PAD, clipY, x + w + PAD, pinBottom, AlpakaStyle.PANEL);
            graphics.fill(x - PAD, pinBottom - 1, x + w + PAD, pinBottom, AlpakaStyle.LINE);
        }
        drawingPin = true;
        invTabs(graphics, tabs, x, barY, w);
        drawingPin = false;
        return bottom;
    }

    private void invTabs(GuiGraphicsExtractor graphics, List<InvTab> tabs, int x, int y, int w) {
        int cx = x;
        int cy = y;
        for (InvTab t : tabs) {
            int cw = textW(t.title) + 14;
            if (cx + cw > x + w) {
                cx = x;
                cy += 18;
            }
            chip(graphics, t.title, cx, cy, t == invTab, () -> invTab = t);
            cx += cw + 4;
        }
    }

    /** One tab of a row of tabs, as a chip; returns its width. */
    private int chip(GuiGraphicsExtractor graphics, String title, int x, int y, boolean selected, Runnable pick) {
        int cw = textW(title) + 14;
        boolean hover = hover(x, y, cw, 15);
        int bg = selected ? MarketIcons.fade(AlpakaStyle.accent(), hover ? 0.35f : 0.24f) : hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, cw, 15, 7, bg);
        text(graphics, title, x + 7, y + 4, selected ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
        hit(x, y, cw, 15, pick);
        return cw;
    }

    private int invContent(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        return switch (invTab) {
            case INVENTORY -> playerInventory(graphics, "Inventory", "inventory", "armor", "equipment", true, x, y, w);
            case ENDER_CHEST -> pagedSections(graphics, "ender_chest", "Page", x, y, w);
            case BACKPACKS -> backpackSections(graphics, d, x, y, w);
            case WARDROBE -> wardrobe(graphics, d, x, y, w);
            case ACCESSORIES -> accessories(graphics, x, pagedSections(graphics, "talisman_bag", "Page", x, y, w) + 12, w);
            case VAULT -> single(graphics, new Section("Personal Vault", null, "personal_vault", range(0, slotsOf(d, "personal_vault"))), x, y, w);
            case BAGS -> {
                List<Section> sections = new ArrayList<>();
                for (String[] bag : BAGS_SHOWN) {
                    if (d.inventories.containsKey(bag[0])) sections.add(new Section(bag[1], null, bag[0], range(0, slotsOf(d, bag[0]))));
                }
                yield flow(graphics, sections, x, y, w);
            }
        };
    }

    private static int slotsOf(ProfileData d, String name) {
        ProfileData.InventoryRef ref = d.inventories.get(name);
        return ref == null ? 0 : ref.slots();
    }

    private static List<String> range(int from, int count) {
        List<String> keys = new ArrayList<>(count);
        for (int i = from; i < from + count; i++) keys.add(String.valueOf(i));
        return keys;
    }

    /** A card of slots: a title, an optional icon beside it, and the slots of one inventory by key. */
    private record Section(String title, JsonElement icon, String inventory, List<String> keys) {}

    /**
     * Sections side by side as far as they fit, each into the shortest column, as the More page does.
     * Too few to fill the width, they sit together in the middle, under the page's other centered
     * cards, rather than at the left with an empty column beside them.
     */
    private int flow(GuiGraphicsExtractor graphics, List<Section> sections, int x, int y, int w) {
        int cols = Math.max(1, (w + 8) / (SECTION_W + 8));
        int gap;
        if (sections.size() < cols) {
            cols = Math.max(1, sections.size());
            gap = 8;
            x += (w - cols * SECTION_W - (cols - 1) * gap) / 2;
        } else {
            gap = cols > 1 ? (w - cols * SECTION_W) / (cols - 1) : 0;
        }
        int[] heights = new int[cols];
        for (Section s : sections) {
            int col = 0;
            for (int i = 1; i < cols; i++) if (heights[i] < heights[col]) col = i;
            int sx = x + col * (SECTION_W + gap);
            heights[col] += section(graphics, s, sx, y + heights[col]) + 6;
        }
        int tallest = 0;
        for (int h : heights) tallest = Math.max(tallest, h);
        return y + tallest;
    }

    /** One inventory alone on its tab: twice the size when it fits, in the middle of the view. */
    private int single(GuiGraphicsExtractor graphics, Section s, int x, int y, int w) {
        slotScale = 9 * SLOT * 2 + 16 <= w ? 2 : 1;
        int sw = 9 * SLOT * slotScale + 16;
        int rows = Math.max(1, (s.keys().size() + 8) / 9);
        int h = section(graphics, s, x + (w - sw) / 2, middle(y, 22 + rows * SLOT * slotScale + 8));
        slotScale = 1;
        return y + h;
    }

    /**
     * Where a card of this height starts to sit in the middle of the view below {@code y}, rather
     * than stuck to its top; at {@code y} when it doesn't fit.
     */
    private int middle(int y, int h) {
        return y + Math.max(0, (clipY + clipH - 6 - y - h) / 2);
    }

    private int section(GuiGraphicsExtractor graphics, Section s, int x, int y) {
        int rows = Math.max(1, (s.keys().size() + 8) / 9);
        int slot = SLOT * slotScale;
        int sectionW = 9 * slot + 16;
        int h = 22 + rows * slot + 8;
        card(graphics, x, y, sectionW, h);
        int tx = x + 8;
        if (s.icon() != null) {
            ItemStack icon = InventoryItems.stack(s.icon());
            if (s.icon() == focusItem) {
                // A backpack's icon from the Storage menu: the card is where it is.
                focusAt(y, h);
                focusMark(graphics, x + 5, y + 2, 18, 18, 3);
            }
            if (!icon.isEmpty()) {
                stackIcon(graphics, icon, x + 6, y + 3, 1.0f);
                tx = x + 26;
            }
        }
        text(graphics, AlpakaStyle.ellipsize(this.font, s.title(), sectionW - (tx - x) - 8), tx, y + 7, AlpakaStyle.TEXT);
        JsonElement inv = session.inventory(s.inventory());
        int gx = x + 8;
        int gy = y + 22;
        if (inv == null) {
            boolean failed = session.inventoryState(s.inventory()) == ProfileSession.State.FAILED;
            ProfileChart.message(graphics, this.font, failed ? "Couldn't load. Click to try again." : "Loading…", x, gy, sectionW, rows * slot);
            if (failed) hit(x, gy, sectionW, rows * slot, session::retryFailed);
            return h;
        }
        slots(graphics, Json.obj(inv, "items"), s.keys(), gx, gy, 9);
        return h;
    }

    /** Slots in rows of {@code cols}, by key; a null key leaves a gap, a key without an item an empty slot. */
    private void slots(GuiGraphicsExtractor graphics, com.google.gson.JsonObject items, List<String> keys, int x, int y, int cols) {
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (key == null) continue;
            int size = SLOT * slotScale;
            int sx = x + (i % cols) * size;
            int sy = y + (i / cols) * size;
            JsonElement item = items.get(key);
            if (item != null && item == focusItem) focusAt(sy, size);
            // Far outside the view: not even the slot is drawn; an Ender Chest has 405 of them.
            if (sy > clipY + clipH || sy + size < clipY) continue;
            slot(graphics, item, sx, sy);
        }
    }

    private void slot(GuiGraphicsExtractor graphics, JsonElement item, int sx, int sy) {
        int size = SLOT * slotScale - 1;
        boolean has = InventoryItems.present(item);
        boolean hover = has && hover(sx, sy, size, size);
        ModernGuiUtils.drawRoundedRect(graphics, sx, sy, size, size, 2 * slotScale, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.PANEL);
        if (!has) return;
        if (item == focusItem) {
            focusAt(sy, size);
            focusMark(graphics, sx, sy, size, size, 2 * slotScale);
        }
        ItemStack stack = InventoryItems.stack(item);
        if (!stack.isEmpty()) {
            int inset = (size - 16 * slotScale) / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(sx + inset, sy + inset);
            graphics.pose().scale(slotScale, slotScale);
            graphics.item(stack, 0, 0);
            graphics.itemDecorations(this.font, stack, 0, 0);
            graphics.pose().popMatrix();
        }
        if (hover) hoveredItem = item;
    }

    /** The inventory as the game shows it: armor and equipment beside the three rows, the hotbar below. */
    private int playerInventory(GuiGraphicsExtractor graphics, String title, String invName, String armorName, String equipmentName,
                                boolean centered, int x, int y, int w) {
        JsonElement inv = session.inventory(invName);
        JsonElement armor = session.inventory(armorName);
        JsonElement equipment = session.inventory(equipmentName);
        // Twice the size when there is room, in the middle of the page, as the game's own screen.
        slotScale = 16 + 11 * SLOT * 2 + 8 * 2 <= w ? 2 : 1;
        int slot = SLOT * slotScale;
        int sideW = 2 * slot + 8 * slotScale;
        int cw = 16 + sideW + 9 * slot;
        int h = 22 + 4 * slot + 6 * slotScale + 8;
        x += (w - cw) / 2;
        if (centered) y = middle(y, h);
        card(graphics, x, y, cw, h);
        text(graphics, title, x + 8, y + 7, AlpakaStyle.TEXT);
        int gy = y + 22;
        if (armor != null) slots(graphics, Json.obj(armor, "items"), List.of("3", "2", "1", "0"), x + 8, gy, 1);
        if (equipment != null) slots(graphics, Json.obj(equipment, "items"), List.of("0", "1", "2", "3"), x + 8 + slot, gy, 1);
        int mx0 = x + 8 + sideW;
        if (inv == null) {
            boolean failed = session.inventoryState(invName) == ProfileSession.State.FAILED;
            ProfileChart.message(graphics, this.font, failed ? "Couldn't load. Click to try again." : "Loading…", mx0, gy, 9 * slot, 4 * slot);
            if (failed) hit(mx0, gy, 9 * slot, 4 * slot, session::retryFailed);
            slotScale = 1;
            return y + h;
        }
        com.google.gson.JsonObject items = Json.obj(inv, "items");
        slots(graphics, items, range(9, 27), mx0, gy, 9);
        slots(graphics, items, range(0, 9), mx0, gy + 3 * slot + 6 * slotScale, 9);
        slotScale = 1;
        return y + h;
    }

    /**
     * Below the bag: the accessories still to get, and those owned only in a lower version, greyed
     * out, most Magical Power first. They count from the bag and the inventory, as the game does.
     */
    private int accessories(GuiGraphicsExtractor graphics, int x, int y, int w) {
        JsonElement bag = session.inventory("talisman_bag");
        JsonElement inv = session.inventory("inventory");
        Map<String, String> tiers = ItemIcons.accessories();
        Missing.Neu neu = Missing.neu();
        if (bag == null || inv == null || tiers == null || neu == null) {
            text(graphics, "Missing Accessories", x, y, AlpakaStyle.TEXT);
            text(graphics, "Loading…", x, y + 14, AlpakaStyle.TEXT_MUTED);
            return y + 22;
        }
        if (gapsFromBag != bag || gapsFromInv != inv) {
            java.util.Set<String> owned = new java.util.HashSet<>();
            for (JsonElement source : List.of(bag, inv)) {
                for (var e : Json.obj(source, "items").entrySet()) {
                    String id = Json.str(e.getValue(), "skyblockId");
                    if (id != null && tiers.containsKey(id)) owned.add(id);
                }
            }
            accessoryGaps = Missing.accessories(owned, tiers, neu, ItemIcons::name);
            gapsFromBag = bag;
            gapsFromInv = inv;
        }
        List<Missing.Gap> gaps = accessoryGaps;
        List<Missing.Gap> missing = new ArrayList<>();
        List<Missing.Gap> upgrades = new ArrayList<>();
        for (Missing.Gap g : gaps) (g.upgradeOf() == null ? missing : upgrades).add(g);
        if (!upgrades.isEmpty()) y = gapCard(graphics, "Upgrades", upgrades, x, y, w) + 6;
        if (!missing.isEmpty()) y = gapCard(graphics, "Missing", missing, x, y, w) + 6;
        return y - 6;
    }

    private JsonElement gapsFromBag;
    private JsonElement gapsFromInv;
    private List<Missing.Gap> accessoryGaps = List.of();

    /** A card of accessories to get, greyed out, with what they'd add in total. */
    private int gapCard(GuiGraphicsExtractor graphics, String title, List<Missing.Gap> gaps, int x, int y, int w) {
        int cols = Math.max(1, (w - 16) / SLOT);
        int rows = (gaps.size() + cols - 1) / cols;
        int h = 22 + rows * SLOT + 8;
        card(graphics, x, y, w, h);
        int power = 0;
        for (Missing.Gap g : gaps) power += g.power();
        text(graphics, title, x + 8, y + 7, AlpakaStyle.TEXT);
        String sum = gaps.size() + " · +" + power + " Magical Power";
        text(graphics, sum, x + w - 8 - textW(sum), y + 7, AlpakaStyle.TEXT_MUTED);
        int gx = x + 8;
        int gy = y + 22;
        for (int i = 0; i < gaps.size(); i++) {
            Missing.Gap g = gaps.get(i);
            int sx = gx + (i % cols) * SLOT;
            int sy = gy + (i / cols) * SLOT;
            if (sy > clipY + clipH || sy + SLOT < clipY) continue;
            boolean hover = hover(sx, sy, SLOT - 1, SLOT - 1);
            ModernGuiUtils.drawRoundedRect(graphics, sx, sy, SLOT - 1, SLOT - 1, 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.PANEL);
            ItemStack stack = ItemIcons.forItem(g.id(), g.name());
            if (!stack.isEmpty()) graphics.item(stack, sx, sy);
            if (!hover) veils.add(new int[] {sx, sy, SLOT - 1, SLOT - 1});
            if (hover) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(g.name()).withColor(ProfileData.rarityColor(g.tier())));
                lines.add(Component.literal("+" + g.power() + " Magical Power").withColor(0xFF55FF55));
                if (g.upgradeOf() != null) {
                    String from = ItemIcons.name(g.upgradeOf());
                    lines.add(Component.literal("Upgrade of " + (from == null ? ProfileData.title(g.upgradeOf()) : from)).withColor(0xFFAAAAAA));
                }
                hoveredLines = lines;
            }
        }
        return y + h;
    }

    /** One inventory in pages of 45 slots, such as the Ender Chest; pages with nothing in them are left out. */
    private int pagedSections(GuiGraphicsExtractor graphics, String name, String label, int x, int y, int w) {
        return pagedSections(graphics, name, label, slotsOf(session.data, name), x, y, w);
    }

    private int pagedSections(GuiGraphicsExtractor graphics, String name, String label, int slots, int x, int y, int w) {
        JsonElement inv = session.inventory(name);
        if (inv == null) return flow(graphics, List.of(new Section(label + " 1", null, name, range(0, Math.min(45, slots)))), x, y, w);
        com.google.gson.JsonObject items = Json.obj(inv, "items");
        List<Section> sections = new ArrayList<>();
        for (int page = 0; page * 45 < slots; page++) {
            List<String> keys = range(page * 45, Math.min(45, slots - page * 45));
            boolean any = false;
            for (String k : keys) any |= InventoryItems.present(items.get(k));
            if (any) sections.add(new Section(label + " " + (page + 1), null, name, keys));
        }
        if (sections.isEmpty()) return note(graphics, "Empty.", x, y, w);
        return flow(graphics, sections, x, y, w);
    }

    /** Every backpack in its own card, named as the Storage menu names its slot and drawn with its own icon. */
    private int backpackSections(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement icons = session.inventory("icons_backpack");
        List<Section> sections = new ArrayList<>();
        for (String name : backpacks(d)) {
            int number = Integer.parseInt(name.substring("backpack_".length()));
            JsonElement icon = icons == null ? null : Json.obj(icons, "items").get(String.valueOf(number));
            sections.add(new Section("Backpack " + (number + 1), InventoryItems.present(icon) ? icon : null, name, range(0, slotsOf(d, name))));
        }
        return flow(graphics, sections, x, y, w);
    }

    /**
     * The wardrobe as columns, one per set: the four armor pieces and below them the four pieces of
     * equipment saved with it. The set worn right now is not in the wardrobe but on the player, so
     * its column shows the armor and equipment being worn, outlined.
     */
    /**
     * The armor and the equipment wardrobe, each as the game pages it, nine sets a page, the pages
     * side by side as far as they fit. The two are separate in the game - set 3 of armor can be
     * worn with set 2 of equipment - so each has its own worn set, outlined. A worn set's pieces
     * are on the player rather than in the wardrobe, so they come from the armor and equipment.
     */
    private int wardrobe(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        JsonElement armor = session.inventory("armor");
        y = wardrobePages(graphics, d, "wardrobe", "Armor", armor,
                (set, p) -> set + ":" + pieces[p], p -> String.valueOf(3 - p), x, y, w);
        if (!d.inventories.containsKey("equipment_wardrobe")) return y;
        JsonElement equipment = session.inventory("equipment");
        return wardrobePages(graphics, d, "equipment_wardrobe", "Equipment", equipment,
                (set, p) -> set + ":EQUIPMENT_SLOT_" + (p + 1), p -> String.valueOf(p), x, y + 8, w);
    }

    private int wardrobePages(GuiGraphicsExtractor graphics, ProfileData d, String name, String title, JsonElement worn,
                              java.util.function.BiFunction<Integer, Integer, String> key,
                              java.util.function.IntFunction<String> wornKey, int x, int y, int w) {
        JsonElement wardrobe = session.inventory(name);
        ProfileData.InventoryRef ref = d.inventories.get(name);
        int sets = Math.max(9, ref == null ? 0 : ref.slots() / 4);
        int equipped = (int) Json.num(ref == null ? null : ref.metadata(), "equipped_set", -1);
        int colW = SLOT + 4;
        int pageW = 16 + 9 * colW - 4;
        int pageH = 22 + 10 + 4 * SLOT + 8;
        int gap = 8;
        int pages = (sets + 8) / 9;
        int perRow = Math.max(1, Math.min(pages, (w + gap) / (pageW + gap)));
        int rowW = perRow * pageW + (perRow - 1) * gap;
        int x0 = x + (w - rowW) / 2;
        int rows = (pages + perRow - 1) / perRow;
        if (wardrobe == null) {
            card(graphics, x0, y, rowW, pageH);
            text(graphics, title, x0 + 8, y + 7, AlpakaStyle.TEXT);
            ProfileChart.message(graphics, this.font, "Loading…", x0, y + 22, rowW, pageH - 22);
            return y + pageH;
        }
        com.google.gson.JsonObject items = Json.obj(wardrobe, "items");
        com.google.gson.JsonObject wornItems = worn == null ? new com.google.gson.JsonObject() : Json.obj(worn, "items");
        for (int page = 0; page < pages; page++) {
            int px = x0 + (page % perRow) * (pageW + gap);
            int pyTop = y + (page / perRow) * (pageH + gap);
            card(graphics, px, pyTop, pageW, pageH);
            text(graphics, title + " · Page " + (page + 1), px + 8, pyTop + 7, AlpakaStyle.TEXT);
            for (int i = page * 9; i < Math.min(sets, page * 9 + 9); i++) {
                int set = i + 1;
                int sx = px + 8 + (i % 9) * colW;
                int sy = pyTop + 22;
                boolean isWorn = set == equipped;
                String label = String.valueOf(set);
                text(graphics, label, sx + (SLOT - 1 - textW(label)) / 2, sy, isWorn ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
                int py = sy + 10;
                for (int p = 0; p < 4; p++) {
                    JsonElement item = isWorn ? wornItems.get(wornKey.apply(p)) : items.get(key.apply(set, p));
                    slot(graphics, item, sx, py + p * SLOT);
                }
                if (isWorn) {
                    ModernGuiUtils.drawRoundedOutline(graphics, sx - 2, py - 2, SLOT + 3, 4 * SLOT + 3, 3,
                            MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
                }
            }
        }
        return y + rows * (pageH + gap) - gap;
    }

    // ---- Cards ----------------------------------------------------------------------------------

    private record Card(String title, List<String[]> rows) {}

    /** Cards of rows, each into the shortest of up to three columns. */
    private int cards(GuiGraphicsExtractor graphics, List<Card> cards, int x, int y, int w) {
        int cols = w >= 540 ? 3 : w >= 340 ? 2 : 1;
        int gap = 6;
        int cw = (w - (cols - 1) * gap) / cols;
        int[] heights = new int[cols];
        for (Card c : cards) {
            int col = 0;
            for (int i = 1; i < cols; i++) if (heights[i] < heights[col]) col = i;
            int cx = x + col * (cw + gap);
            int cy = y + heights[col];
            int h = 22 + c.rows().size() * 11 + 4;
            card(graphics, cx, cy, cw, h);
            text(graphics, c.title(), cx + 8, cy + 7, AlpakaStyle.TEXT);
            int ry = cy + 21;
            for (String[] row : c.rows()) {
                String value = row[1];
                int vw = textW(value);
                text(graphics, value, cx + cw - 8 - vw, ry, AlpakaStyle.TEXT);
                text(graphics, AlpakaStyle.ellipsize(this.font, row[0], cw - 22 - vw), cx + 8, ry, AlpakaStyle.TEXT_MUTED);
                ry += 11;
            }
            heights[col] += h + gap;
        }
        int tallest = 0;
        for (int hgt : heights) tallest = Math.max(tallest, hgt);
        return y + tallest - gap;
    }

    /** The row above a sub-page: the way back, the sub-page's name and how far it is. */
    private int backRow(GuiGraphicsExtractor graphics, String back, String title, String note, int x, int y) {
        int bw = textW(back) + 14;
        boolean backHover = hover(x, y, bw, 15);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, bw, 15, 7, backHover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
        text(graphics, back, x + 7, y + 4, backHover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
        hit(x, y, bw, 15, () -> skillDetail = null);
        text(graphics, title, x + bw + 8, y + 4, AlpakaStyle.TEXT);
        text(graphics, note, x + bw + 8 + textW(title) + 8, y + 4, AlpakaStyle.TEXT_MUTED);
        return y + 15 + 8;
    }

    private static String date(double epochMs) {
        return DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(Instant.ofEpochMilli((long) epochMs).atZone(ZoneId.systemDefault()));
    }

    // ---- Museum ---------------------------------------------------------------------------------

    private Museum.Repo museumRepo;
    private JsonElement museumFor;
    private Museum.Donated museum;
    /** What each item in the museum is worth, by its uuid, from the networth; taken-out items are valued where they are. */
    private JsonElement museumValuesFor;
    private Map<String, Double> museumValues = Map.of();
    private static final String SPECIAL = "special";
    private static final int CELL = 20;

    /** Each category's icon, after the skill it belongs to. */
    private static Item museumIcon(String category) {
        return switch (category) {
            case "combat" -> Items.DIAMOND_SWORD;
            case "farming" -> Items.GOLDEN_HOE;
            case "mining" -> Items.IRON_PICKAXE;
            case "fishing" -> Items.FISHING_ROD;
            case "foraging" -> Items.JUNGLE_SAPLING;
            case "dungeoneering" -> Items.WITHER_SKELETON_SKULL;
            case "hunting" -> Items.LEAD;
            default -> Items.NETHER_STAR;
        };
    }

    private int museum(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        if (!Json.bool(d.raw, "api.museum")) return apiOff(graphics, "Museum", x, y, w);
        if (!d.inventories.containsKey("museum") && session.inventoryState("museum") == ProfileSession.State.READY) {
            return note(graphics, "Nothing donated to the museum yet, or its API is switched off.", x, y, w);
        }
        Museum.Repo repo = Museum.repo();
        // The museum inventory rather than the networth's museum category: only it still lists the
        // items taken out, which count as donated all the same.
        JsonElement inventory = session.inventory("museum");
        JsonElement answer = session.networth();
        if (repo != null && inventory != null && (repo != museumRepo || inventory != museumFor)) {
            museumRepo = repo;
            museumFor = inventory;
            museum = Museum.of(repo, Json.obj(inventory, "items").asMap().values());
        }
        if (answer != null && answer != museumValuesFor) {
            museumValuesFor = answer;
            Map<String, Double> values = new HashMap<>();
            for (JsonElement item : Json.arr(answer, "categories.museum.items")) {
                String uuid = Json.str(item, "item.uuid");
                if (uuid != null) values.put(uuid, Json.num(item, "networth", 0));
            }
            museumValues = values;
        }
        Museum.Donated m = repo != null && inventory != null ? museum : null;
        if (m != null && focusMuseum) {
            // A search result opens the category its item was donated in.
            focusMuseum = false;
            if (containsSame(m.special(), focusItem)) skillDetail = SPECIAL;
            for (Museum.Category c : m.categories()) {
                for (Museum.Entry e : c.entries()) if (containsSame(e.items(), focusItem)) skillDetail = c.def().id();
            }
            focusView = viewKey();
        }
        if (m != null && skillDetail != null) {
            if (skillDetail.equals(SPECIAL)) return museumSpecial(graphics, m, x, y, w);
            for (Museum.Category c : m.categories()) {
                if (c.def().id().equals(skillDetail)) return museumCategory(graphics, repo, c, x, y, w);
            }
            skillDetail = null;
        }

        double value = Json.num(answer, "categories.museum.total");
        List<Tile> tiles = new ArrayList<>();
        // Plain cards: the museum has no history to draw.
        tiles.add(new Tile(null, "Items Donated", m == null ? "…" : m.donated() + " / " + repo.total(), null));
        tiles.add(new Tile(null, "Museum Value", answer == null ? "…" : Double.isNaN(value) ? "—" : Coins.compact(value), null));
        tiles.add(new Tile(null, "Special Items", m == null ? "…" : Coins.full(m.special().size()), null));
        tiles.add(new Tile(null, "Taken Out", m == null ? "…" : Coins.full(m.borrowed()), "still count as donated"));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        if (m == null) {
            boolean failed = inventory == null ? session.inventoryState("museum") == ProfileSession.State.FAILED : Museum.failed();
            int h = 40;
            card(graphics, x, y, w, h);
            ProfileChart.message(graphics, this.font, failed ? "Couldn't load the museum. Click to try again." : "Loading the museum…", x, y, w, h);
            if (failed && inventory == null) hit(x, y, w, h, session::retryFailed);
            return y + h;
        }

        // One card per category, the items a click away, as on the Bestiary page.
        int count = m.categories().size() + 1;
        int cols = Math.max(2, w / 160);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 38;
        for (int i = 0; i < count; i++) {
            boolean special = i == m.categories().size();
            Museum.Category c = special ? null : m.categories().get(i);
            String id = special ? SPECIAL : c.def().id();
            String name = special ? "Special" : c.name();
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            icon(graphics, museumIcon(id), cx + 3, cy + 3, 2.0f);
            int tx = cx + 40;
            int tw = cw - 40 - 6;
            boolean done = !special && c.donated() == c.entries().size();
            String amount = special ? Coins.full(m.special().size()) : c.donated() + " / " + c.entries().size();
            int amountW = done ? badgeW() : textW(amount);
            if (done) maxBadge(graphics, tx + tw - amountW, cy + 7);
            else text(graphics, amount, tx + tw - amountW, cy + 7, AlpakaStyle.TEXT_MUTED);
            text(graphics, AlpakaStyle.ellipsize(this.font, name, tw - 8 - amountW), tx, cy + 7, AlpakaStyle.TEXT_SOFT);
            if (special) {
                // Special items have no end, so no bar either.
                text(graphics, "no limit", tx, cy + 24, AlpakaStyle.TEXT_MUTED);
            } else {
                levelBar(graphics, tx, cy + 19, tw, 2, c.progress(), done);
                text(graphics, (int) Math.floor(c.progress() * 100) + "% donated", tx, cy + 24, AlpakaStyle.TEXT_MUTED);
            }
            hit(cx, cy, cw, ch, () -> skillDetail = id);
            if (hover) {
                tip(id, special ? m.special().size() + " special items donated. Click for them."
                        : name + ": " + c.donated() + " of " + c.entries().size() + " donated. Click for them.");
            }
        }
        return y + ((count + cols - 1) / cols) * (ch + gap) - gap;
    }

    /** A category's items and sets, the ones not donated yet greyed out. */
    private int museumCategory(GuiGraphicsExtractor graphics, Museum.Repo repo, Museum.Category c, int x, int y, int w) {
        y = backRow(graphics, "‹ All Categories", c.name(), c.donated() + " / " + c.entries().size() + " donated", x, y);
        int cols = Math.max(1, (w + 2) / CELL);
        List<Museum.Entry> entries = c.entries();
        for (int i = 0; i < entries.size(); i++) {
            Museum.Entry e = entries.get(i);
            int sx = x + (i % cols) * CELL;
            int sy = y + (i / cols) * CELL;
            boolean focus = focusItem != null && containsSame(e.items(), focusItem);
            if (focus) focusAt(sy, CELL - 2);
            if (sy > clipY + clipH || sy + CELL < clipY) continue;
            JsonElement shown = museumItem(repo, e);
            ItemStack stack = shown != null ? InventoryItems.stack(shown) : ItemIcons.forItem(Museum.iconId(repo, e.id()), null);
            museumSlot(graphics, stack, sx, sy, !e.donated(), e.borrowed(), museumTip(repo, e), focus);
        }
        return y + ((entries.size() + cols - 1) / cols) * CELL - 2;
    }

    private int museumSpecial(GuiGraphicsExtractor graphics, Museum.Donated m, int x, int y, int w) {
        y = backRow(graphics, "‹ All Categories", "Special", m.special().size() + " donated", x, y);
        if (m.special().isEmpty()) return note(graphics, "No special items donated yet.", x, y, w);
        int cols = Math.max(1, (w + 2) / CELL);
        for (int i = 0; i < m.special().size(); i++) {
            JsonElement item = m.special().get(i);
            int sx = x + (i % cols) * CELL;
            int sy = y + (i / cols) * CELL;
            if (item == focusItem) focusAt(sy, CELL - 2);
            if (sy > clipY + clipH || sy + CELL < clipY) continue;
            boolean out = Museum.borrowed(item);
            List<Component> lines = new ArrayList<>();
            String name = Json.str(item, "name");
            lines.add(Component.literal(name == null ? ProfileData.title(String.valueOf(Json.str(item, "skyblockId"))) : name));
            lines.add(Component.literal(out ? "§eDonated, taken out right now" : "§aDonated"));
            double value = museumValue(List.of(item));
            if (value > 0) lines.add(Component.literal("§7Value: §6" + Coins.compact(value)));
            museumSlot(graphics, InventoryItems.stack(item), sx, sy, false, out, lines, item == focusItem);
        }
        return y + ((m.special().size() + cols - 1) / cols) * CELL - 2;
    }

    /** A slot of the museum: greyed out while missing, with a dot in the corner while taken out. */
    private void museumSlot(GuiGraphicsExtractor graphics, ItemStack stack, int sx, int sy, boolean missing, boolean out, List<Component> tip,
                            boolean focus) {
        int size = CELL - 2;
        boolean hover = hover(sx, sy, size, size);
        ModernGuiUtils.drawRoundedRect(graphics, sx, sy, size, size, 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
        if (focus) focusMark(graphics, sx, sy, size, size, 2);
        if (!stack.isEmpty()) graphics.item(stack, sx + 1, sy + 1);
        if (missing) veils.add(new int[] {sx, sy, size, size});
        if (out) borrowedDots.add(new int[] {sx + size - 5, sy + 1});
        if (hover) hoveredLines = tip;
    }

    /** The donated item an entry is drawn by: a set's piece NEU shows it by when that is there. */
    private static JsonElement museumItem(Museum.Repo repo, Museum.Entry e) {
        if (e.items().isEmpty()) return null;
        String icon = Museum.iconId(repo, e.id());
        for (JsonElement item : e.items()) {
            if (icon.equals(Json.str(item, "skyblockId"))) return item;
        }
        return e.items().get(0);
    }

    /** The worth of donated items still in the museum; the networth values the ones taken out where they are. */
    private double museumValue(List<JsonElement> items) {
        double v = 0;
        for (JsonElement item : items) {
            String uuid = Json.str(item, "uuid");
            if (uuid != null) v += museumValues.getOrDefault(uuid, 0.0);
        }
        return v;
    }

    private List<Component> museumTip(Museum.Repo repo, Museum.Entry e) {
        String name = ItemIcons.name(Museum.iconId(repo, e.id()));
        String color = "§f";
        if (!e.items().isEmpty()) {
            String donatedName = Json.str(e.items().get(0), "name");
            if (donatedName != null && donatedName.startsWith("§") && donatedName.length() > 1) color = donatedName.substring(0, 2);
            if (name == null && donatedName != null) name = SkyblockUtils.cleanColor(donatedName);
        }
        if (name == null) {
            name = ProfileData.title(e.id());
        } else if (Museum.isSet(repo, e.id())) {
            // The set by its piece's name without the piece: "Tank Miner Helmet" is the Tank Miner Armor.
            int space = name.lastIndexOf(' ');
            name = (space > 0 ? name.substring(0, space) : name) + " Armor";
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(color + name));
        if (!e.donated()) {
            lines.add(Component.literal("§cNot donated yet"));
        } else if (e.items().isEmpty()) {
            lines.add(Component.literal("§aDonated through a better tier"));
        } else {
            lines.add(Component.literal(e.borrowed() ? "§eDonated, taken out right now" : "§aDonated"));
            double value = museumValue(e.items());
            if (value > 0) lines.add(Component.literal("§7Value: §6" + Coins.compact(value)));
        }
        return lines;
    }

    // ---- Rift -----------------------------------------------------------------------------------

    /** The Timecharms by Hypixel's id, in the order the Rift hands them out. */
    private static final String[][] TIMECHARMS = {
            {"wyldly_supreme", "Supreme Timecharm"}, {"mirrored", "Mirrorverse Timecharm"},
            {"chicken_n_egg", "Chicken N Egg Timecharm"}, {"citizen", "SkyBlock Citizen Timecharm"},
            {"lazy_living", "Living Timecharm"}, {"slime", "Globulate Timecharm"},
            {"vampiric", "Vampiric Timecharm"}, {"mountain", "Celestial Timecharm"}};

    private int rift(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement r = Json.at(d.raw, "stats.rift");
        if (r == null) return note(graphics, "This profile hasn't been to the Rift yet.", x, y, w);
        Map<String, Double> secured = new HashMap<>();
        for (JsonElement t : Json.arr(r, "securedTrophies")) {
            String type = Json.str(t, "type");
            if (type != null) secured.put(type, Json.num(t, "timestamp", 0));
        }
        List<Tile> tiles = new ArrayList<>();
        double souls = Json.num(r, "enigmaSoulsFound", 0);
        double soulsTotal = Json.num(r, "enigmaSoulsTotal", 0);
        tiles.add(new Tile(null, "Enigma Souls", (int) souls + (soulsTotal > 0 ? " / " + (int) soulsTotal : ""), null));
        tiles.add(new Tile(null, "Motes", Coins.compact(Json.num(r, "motesPurse", 0)), null));
        tiles.add(new Tile(null, "Montezuma's Cats", Json.arr(r, "foundCats").size() + " / 9", null));
        tiles.add(new Tile(null, "Timecharms", secured.size() + " / " + TIMECHARMS.length, null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;

        List<Card> cards = new ArrayList<>();
        List<String[]> charms = new ArrayList<>();
        for (String[] t : TIMECHARMS) {
            Double at = secured.get(t[0]);
            charms.add(row(t[1], at == null ? "—" : at > 0 ? date(at) : "Secured"));
        }
        add(cards, "Timecharms", charms);

        List<String[]> progress = new ArrayList<>();
        count(progress, "Eyes Unlocked", Json.arr(r, "unlockedEyes").size());
        count(progress, "Areas Unlocked", Json.arr(r, "lifetimePurchasedBoundaries").size());
        if (Json.bool(r, "consumedPrism")) progress.add(row("Rift Prism", "Consumed"));
        if (Json.bool(r, "unlockedRiftCatPet")) progress.add(row("Montezuma Pet", "Unlocked"));
        add(cards, "Progress", progress);

        List<String[]> stacks = new ArrayList<>();
        count(stacks, "Lifetime Motes", Json.num(r, "lifetimeMotesEarned"));
        count(stacks, "Motes Orbs Picked Up", Json.num(r, "motesOrbPickup"));
        count(stacks, "Grubber Stacks", Json.num(r, "grubberStacks"));
        add(cards, "Stats", stacks);
        y = cards(graphics, cards, x, y, w) + 12;
        return riftInventories(graphics, x, y, w);
    }

    /**
     * The Rift's inventory and Ender Chest, which come with the profile like every other inventory:
     * one at a time, on two tabs as the Inventories page has them, each in the middle of the page.
     */
    private int riftInventories(GuiGraphicsExtractor graphics, int x, int y, int w) {
        String problem = session.riftProblem();
        if (problem != null) return note(graphics, problem, x, y, w);
        int cx = x;
        cx += chip(graphics, "Inventory", cx, y, !riftEnderChest, () -> riftEnderChest = false) + 4;
        chip(graphics, "Ender Chest", cx, y, riftEnderChest, () -> riftEnderChest = true);
        y += 15 + 8;
        if (!riftEnderChest) {
            return playerInventory(graphics, "Rift Inventory", "rift_inventory", "rift_armor", "rift_equipment", false, x, y, w);
        }
        JsonElement chest = session.inventory("rift_ender_chest");
        int slots = chest == null ? 45 : Json.obj(chest, "items").size();
        return pagedSections(graphics, "rift_ender_chest", "Page", slots, x, y, w);
    }

    // ---- Rabbits (Chocolate Factory) ------------------------------------------------------------

    /** The rarities' colors, as Hypixel writes them. */
    private static final int[] RARITY_COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF, 0xFF55FFFF};

    private int chocolate(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement c = Json.at(d.raw, "chocolateFactory");
        double produced = Json.num(c, "totalChocolate", 0);
        if (c == null || produced <= 0) return note(graphics, "This profile hasn't opened the Chocolate Factory yet.", x, y, w);
        Map<String, Integer> all = Hoppity.rabbits();
        int collection = 0;
        if (all != null) for (int n : all.values()) collection += n;
        // By rarity once Hoppity's list is there; until then every rabbit the profile names counts.
        int[][] found = Hoppity.found(Json.obj(c, "rabbits"));
        double unique = 0;
        if (found == null) unique = Json.obj(c, "rabbits").size();
        else for (int n : found[0]) unique += n;

        List<Tile> tiles = new ArrayList<>();
        // Plain cards: the page has no graph.
        tiles.add(new Tile(null, "Chocolate Produced", Coins.compact(produced), null));
        tiles.add(new Tile(null, "Prestige", Coins.full(Json.num(c, "prestige", 0)), null));
        tiles.add(new Tile(null, "Chocolate", Coins.compact(Json.num(c, "chocolate", 0)), null));
        tiles.add(new Tile(null, "Unique Rabbits", Coins.full(unique) + (collection > 0 ? " / " + collection : ""), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        y = rabbits(graphics, found, all, x, y, w) + 6;

        List<Card> cards = new ArrayList<>();
        List<String[]> factory = new ArrayList<>();
        double sincePrestige = Json.num(c, "chocolateSincePrestige");
        if (sincePrestige > 0) factory.add(row("Since Prestige", Coins.compact(sincePrestige)));
        double spent = Json.num(c, "chocolateSpent");
        if (spent > 0) factory.add(row("Chocolate Spent", Coins.compact(spent)));
        count(factory, "Cocoa Fortune Upgrades", Json.num(c, "cocoaFortuneUpgrades"));
        count(factory, "Refined Truffles Eaten", Json.num(c, "refinedTrufflesConsumed"));
        if (Json.bool(c, "unlockedZorro")) factory.add(row("Zorro", "Unlocked"));
        double viewed = Json.num(c, "lastViewed");
        if (viewed > 0) factory.add(row("Last Visited", Metrics.ago((long) viewed)));
        add(cards, "Factory", factory);
        return cards(graphics, cards, x, y, w);
    }

    /** Hoppity's collection by rarity: the rabbits found of each, and how often one turned up. */
    private int rabbits(GuiGraphicsExtractor graphics, int[][] counts, Map<String, Integer> all, int x, int y, int w) {
        int rowH = 13;
        int h = 24 + Hoppity.RARITIES.length * rowH + 2;
        card(graphics, x, y, w, h);
        text(graphics, "Rabbits", x + 8, y + 7, AlpakaStyle.TEXT);
        // Above the right column, which counts every rabbit found, duplicates too.
        String hint = "with duplicates";
        if (w > 260) text(graphics, hint, x + w - 8 - textW(hint), y + 7, AlpakaStyle.TEXT_MUTED);
        int labelW = textW("Legendary") + 12;
        int foundW = textW("00,000 found") + 8;
        int countW = textW("000 / 000") + 10;
        int barX = x + 8 + labelW;
        int barW = w - 16 - labelW - countW - foundW;
        int ry = y + 22;
        for (int i = 0; i < Hoppity.RARITIES.length; i++) {
            String rarity = Hoppity.RARITIES[i];
            int found = counts == null ? 0 : counts[0][i];
            int seen = counts == null ? 0 : counts[1][i];
            Integer of = all == null ? null : all.get(rarity);
            int color = ink(RARITY_COLORS[i]);
            String name = ProfileData.title(rarity);
            text(graphics, name, x + 8, ry, color);
            boolean done = of != null && found >= of;
            if (barW > 20) {
                if (done) maxedBar(graphics, barX, ry + 3, barW, 2, 1);
                else bar(graphics, barX, ry + 3, barW, 2, of == null || of == 0 ? 0 : found / (double) of, color);
            }
            String amount = of == null ? String.valueOf(found) : found + " / " + of;
            text(graphics, amount, barX + barW + countW - textW(amount) - 4, ry, done ? ink(GOLD) : AlpakaStyle.TEXT);
            String total = Coins.full(seen) + " found";
            text(graphics, total, x + w - 8 - textW(total), ry, AlpakaStyle.TEXT_MUTED);
            if (hover(x, ry - 2, w, rowH)) {
                tip("rabbit." + rarity, name + ": " + found + (of == null ? "" : " of " + of) + " rabbits, found "
                        + Coins.full(seen) + " times with the duplicates.");
            }
            ry += rowH;
        }
        return y + h;
    }

    private static String[] row(String label, String value) {
        return new String[] {label, value};
    }

    private static void count(List<String[]> rows, String label, double value) {
        if (!Double.isNaN(value) && value != 0) rows.add(row(label, value >= 10_000_000 ? Coins.compact(value) : Coins.full(value)));
    }

    private static void coins(List<String[]> rows, String label, double value) {
        if (!Double.isNaN(value)) rows.add(row(label, Coins.compact(value)));
    }

    private static void add(List<Card> cards, String title, List<String[]> rows) {
        if (!rows.isEmpty()) cards.add(new Card(title, rows));
    }

    // ---- Item search ----------------------------------------------------------------------------

    private final EditableText itemSearch = new EditableText(40);
    /** The field is shown in place of the sidebar's label: opened from its magnifier, closed again once empty. */
    private boolean itemSearchOpen;
    private boolean itemSearchFocused;
    private long itemCaretFrom;

    /**
     * One thing the profile owns, as the search finds it: its name in the game's colors, the text
     * it is matched by, where it is, and the page it is on.
     */
    private record Found(String name, String haystack, String where, int count, JsonElement item, String iconId,
                         ProfileData.Pet pet, Runnable open) {}

    private List<Found> searchIndex = List.of();
    private String searchIndexKey;
    private String resultsFor;
    private List<Found> results = List.of();
    /** At most this many rows: a single letter finds thousands, and nobody scrolls through them. */
    private static final int MAX_RESULTS = 400;

    private boolean searching() {
        return !itemSearch.getText().isBlank() && session.data != null;
    }

    /** The inventories in the order the search lists them: what is carried first, the museum last. */
    private static int inventoryRank(String name) {
        if (name.startsWith("backpack_")) {
            try {
                return 400 + Integer.parseInt(name.substring("backpack_".length()));
            } catch (NumberFormatException e) {
                return 499;
            }
        }
        return switch (name) {
            case "inventory" -> 0;
            case "armor" -> 1;
            case "equipment" -> 2;
            case "ender_chest" -> 300;
            case "wardrobe" -> 500;
            case "equipment_wardrobe" -> 501;
            case "talisman_bag" -> 600;
            case "personal_vault" -> 700;
            case "museum" -> 900;
            case "icons_backpack" -> 950;
            default -> 800;
        };
    }

    /** Where in an inventory an item sits, as the game names it: "Ender Chest · Page 2", "Backpack 4". */
    private static String where(String inventory, String key) {
        if (inventory.startsWith("backpack_")) {
            try {
                return "Backpack " + (Integer.parseInt(inventory.substring("backpack_".length())) + 1);
            } catch (NumberFormatException e) {
                return "Backpack";
            }
        }
        switch (inventory) {
            case "ender_chest":
                try {
                    return "Ender Chest · Page " + (Integer.parseInt(key) / 45 + 1);
                } catch (NumberFormatException e) {
                    return "Ender Chest";
                }
            case "wardrobe":
            case "equipment_wardrobe":
                int colon = key.indexOf(':');
                return colon > 0 ? "Wardrobe · Set " + key.substring(0, colon) : "Wardrobe";
            case "inventory": return "Inventory";
            case "armor": return "Armor";
            case "equipment": return "Equipment";
            case "talisman_bag": return "Accessory Bag";
            case "museum": return "Museum";
            case "icons_backpack": return "Storage";
            default:
                for (String[] bag : BAGS_SHOWN) if (bag[0].equals(inventory)) return bag[1];
                return categoryLabel(inventory);
        }
    }

    /**
     * Leaves the search for the page an item or pet is shown on, with the tab or view it is in
     * ({@code view}), scrolled to it and marked there.
     */
    private Runnable opener(Page target, Runnable view, JsonElement item, ProfileData.Pet pet) {
        return () -> {
            itemSearch.setText("");
            itemSearchOpen = false;
            itemSearchFocused = false;
            page = target;
            lastPage = target;
            skillDetail = null;
            if (view != null) view.run();
            focusItem = item;
            focusPet = pet;
            focusData = session.data;
            focusSince = System.currentTimeMillis();
            focusScroll = true;
            focusMuseum = target == Page.MUSEUM;
            focusView = viewKey();
        };
    }

    private Runnable opener(String inventory, JsonElement item) {
        if (inventory.equals("museum")) return opener(Page.MUSEUM, null, item, null);
        if (inventory.startsWith(ProfileSession.RIFT)) {
            boolean chest = inventory.equals(ProfileSession.RIFT + "ender_chest");
            return opener(Page.RIFT, () -> riftEnderChest = chest, item, null);
        }
        InvTab tab;
        if (inventory.startsWith("backpack_") || inventory.equals("icons_backpack")) tab = InvTab.BACKPACKS;
        else tab = switch (inventory) {
            case "inventory", "armor", "equipment" -> InvTab.INVENTORY;
            case "ender_chest" -> InvTab.ENDER_CHEST;
            case "wardrobe", "equipment_wardrobe" -> InvTab.WARDROBE;
            case "talisman_bag" -> InvTab.ACCESSORIES;
            case "personal_vault" -> InvTab.VAULT;
            default -> InvTab.BAGS;
        };
        return opener(Page.INVENTORY, () -> invTab = tab, item, null);
    }

    // ---- Search target --------------------------------------------------------------------------

    /**
     * The item or pet a search result was opened on, by identity: the page scrolls to it once and
     * marks it until another page, tab or profile is shown. Null for none.
     */
    private JsonElement focusItem;
    private ProfileData.Pet focusPet;
    private ProfileData focusData;
    private long focusSince;
    /** Still to be scrolled to; it is, the first frame it is drawn on a settled view. */
    private boolean focusScroll;
    /** Its museum category still to be found, once the museum has loaded. */
    private boolean focusMuseum;
    /** The view it was opened on, see {@link #viewKey}. */
    private String focusView;
    /** Where it was drawn this frame, on screen; MIN_VALUE when not. */
    private int focusY = Integer.MIN_VALUE;
    private int focusH;

    /** The page, sub-page and tab shown, which the marked item belongs to. */
    private String viewKey() {
        return page + "/" + skillDetail + "/" + (page == Page.INVENTORY ? invTab : page == Page.RIFT ? riftEnderChest : "");
    }

    private boolean focused() {
        return focusItem != null || focusPet != null;
    }

    private void dropMark() {
        focusItem = null;
        focusPet = null;
        focusData = null;
        focusScroll = false;
        focusMuseum = false;
    }

    /** Notes where the marked thing is drawn; called for it even when it is outside the view. */
    private void focusAt(int y, int h) {
        if (focusY == Integer.MIN_VALUE) {
            focusY = y;
            focusH = h;
        }
    }

    /** How strongly the mark shows: three pulses on arrival, so the eye finds it, then steady. */
    private float focusGlow() {
        long t = System.currentTimeMillis() - focusSince;
        if (t >= 1800) return 1.0f;
        return 0.5f + 0.5f * (float) Math.cos(t / 600.0 * 2 * Math.PI);
    }

    /** The mark around a slot, card or cell of the thing a search result was opened on. */
    private void focusMark(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius) {
        float glow = focusGlow();
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, radius, MarketIcons.fade(AlpakaStyle.accent(), 0.12f + 0.2f * glow));
        ModernGuiUtils.drawRoundedOutline(graphics, x - 1, y - 1, w + 2, h + 2, radius + 1, MarketIcons.fade(AlpakaStyle.accent(), 0.45f + 0.55f * glow));
    }

    private static boolean containsSame(List<JsonElement> items, JsonElement item) {
        for (JsonElement e : items) if (e == item) return true;
        return false;
    }

    private static String haystack(String name, String id) {
        String plain = name == null ? "" : SkyblockUtils.cleanColor(name);
        return (plain + " " + (id == null ? "" : id.replace('_', ' '))).toLowerCase(Locale.ROOT);
    }

    /**
     * Everything the profile owns, from every inventory loaded so far plus the sacks and pets;
     * asking also starts loading the inventories not asked for yet. Built again whenever another
     * inventory has come in.
     */
    private List<Found> searchIndex(ProfileData d, int[] loaded) {
        List<String> names = new ArrayList<>(d.inventories.keySet());
        names.sort(java.util.Comparator.comparingInt(ProfileDashboardScreen::inventoryRank));
        StringBuilder key = new StringBuilder(d.profileId).append('/').append(System.identityHashCode(d));
        List<JsonElement> answers = new ArrayList<>();
        for (String name : names) {
            JsonElement inv = session.inventory(name);
            answers.add(inv);
            if (inv != null) loaded[0]++;
            key.append(inv == null ? '-' : '+');
        }
        loaded[1] = names.size();
        if (key.toString().equals(searchIndexKey)) return searchIndex;

        List<Found> index = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            JsonElement inv = answers.get(i);
            if (inv == null) continue;
            String name = names.get(i);
            for (var e : Json.obj(inv, "items").entrySet()) {
                JsonElement item = e.getValue();
                if (!InventoryItems.present(item)) continue;
                // A museum item taken out is found where it really is, not twice.
                if (name.equals("museum") && Museum.borrowed(item)) continue;
                String title = Json.str(item, "name");
                String id = Json.str(item, "skyblockId");
                if (title == null && id == null) continue;
                index.add(new Found(title != null ? title : ProfileData.title(id), haystack(title, id), where(name, e.getKey()),
                        (int) Json.num(item, "count", 1), item, null, null, opener(name, item)));
            }
        }
        for (var e : Json.obj(d.raw, "sacks").entrySet()) {
            double n = Json.num(e.getValue());
            if (!(n > 0)) continue;
            String id = e.getKey();
            String title = ItemIcons.name(id);
            index.add(new Found("§f" + (title != null ? title : ProfileData.title(id)), haystack(title, id), "Sacks",
                    (int) Math.min(Integer.MAX_VALUE, n), null, id, null, null));
        }
        for (ProfileData.Pet p : d.pets) {
            String title = "§7[Lvl " + p.level() + "] " + rarityCode(p.tier()) + p.name();
            index.add(new Found(title, haystack(p.name() + " pet", p.type()), p.active() ? "Pets · Active" : "Pets", 1, null, null, p,
                    opener(Page.PETS, null, null, p)));
        }
        searchIndex = List.copyOf(index);
        searchIndexKey = key.toString();
        resultsFor = null;
        return searchIndex;
    }

    private static String rarityCode(String tier) {
        if (tier == null) return "§f";
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            case "SPECIAL", "VERY_SPECIAL" -> "§c";
            default -> "§f";
        };
    }

    /** Every word of the query somewhere in the item's name or id. */
    private static List<Found> match(List<Found> index, String query) {
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Found> out = new ArrayList<>();
        for (Found f : index) {
            boolean all = true;
            for (String word : words) {
                if (!f.haystack().contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) out.add(f);
        }
        return out;
    }

    private int itemResults(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        String query = itemSearch.getText().trim();
        int[] loaded = new int[2];
        List<Found> index = searchIndex(d, loaded);
        String resultsKey = query + "\u0000" + searchIndexKey;
        if (!resultsKey.equals(resultsFor)) {
            resultsFor = resultsKey;
            results = match(index, query);
        }

        String title = results.isEmpty() ? "No items match “" + query + "”" : Coins.full(results.size()) + (results.size() == 1 ? " item" : " items")
                + " match “" + query + "”";
        text(graphics, AlpakaStyle.ellipsize(this.font, title, w - 140), x + 2, y + 2, AlpakaStyle.TEXT);
        String status = !d.inventoryApi ? "Inventory API off" : loaded[0] < loaded[1]
                ? "Loading inventories " + loaded[0] + " / " + loaded[1] + "…" : "Esc to clear";
        text(graphics, status, x + w - 2 - textW(status), y + 2, AlpakaStyle.TEXT_MUTED);
        y += 16;
        if (!d.inventoryApi) {
            y = note(graphics, "The Inventory API is off for this profile, so only its sacks and pets can be searched.", x, y, w) + 6;
        }
        if (results.isEmpty()) return y;

        int rowH = 20;
        int shown = Math.min(MAX_RESULTS, results.size());
        for (int i = 0; i < shown; i++) {
            Found f = results.get(i);
            int ry = y + i * rowH;
            if (ry > clipY + clipH || ry + rowH < clipY) continue;
            boolean hover = hover(x, ry, w, rowH - 2);
            ModernGuiUtils.drawRoundedRect(graphics, x, ry, w, rowH - 2, AlpakaStyle.ROW_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            ItemStack stack = f.item() != null ? InventoryItems.stack(f.item())
                    : f.pet() != null ? PetIcons.icon(f.pet())
                    : ItemIcons.forItem(f.iconId(), SkyblockUtils.cleanColor(f.name()));
            // A pet's head is null while its texture is still being looked up.
            if (stack != null && !stack.isEmpty()) graphics.item(stack, x + 2, ry + 1);
            String where = f.where();
            int whereW = textW(where);
            text(graphics, where, x + w - 8 - whereW, ry + 5, AlpakaStyle.TEXT_MUTED);
            String count = f.count() > 1 ? "x" + Coins.full(f.count()) : "";
            int nameX = x + 24;
            int nameMax = x + w - 16 - whereW - (count.isEmpty() ? 0 : textW(count) + 8) - nameX;
            styled(graphics, f.name(), nameX, ry + 5, nameMax);
            if (!count.isEmpty()) {
                int nameW = Math.min(nameMax, this.font.width(GuiFont.text(f.name())));
                text(graphics, count, nameX + nameW + 6, ry + 5, AlpakaStyle.TEXT_MUTED);
            }
            if (f.open() != null) hit(x, ry, w, rowH - 2, f.open());
            if (hover) {
                if (f.item() != null) {
                    hoveredItem = f.item();
                } else {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(f.name()));
                    lines.add(Component.literal("§7" + where + (f.count() > 1 ? " · " + Coins.full(f.count()) : "")));
                    hoveredLines = lines;
                }
            }
        }
        y += shown * rowH;
        if (results.size() > shown) {
            y = note(graphics, Coins.full(results.size() - shown) + " more. Type a little more to narrow the search.", x, y + 4, w);
        }
        return y;
    }

    // ---- Small parts ----------------------------------------------------------------------------

    private int apiOff(GuiGraphicsExtractor graphics, String api, int x, int y, int w) {
        return note(graphics, "The " + api + " API is off for this profile. Turn it on in SkyBlock's settings under API Settings, then refresh in a few minutes.", x, y, w);
    }

    /** A card with a sentence in it. */
    private int note(GuiGraphicsExtractor graphics, String text, int x, int y, int w) {
        List<net.minecraft.network.chat.FormattedText> lines = GuiFont.splitLines(this.font, text, w - 20);
        int h = lines.size() * 10 + 12;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.10f));
        int ly = y + 7;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            graphics.text(this.font, net.minecraft.locale.Language.getInstance().getVisualOrder(line), x + 10, ly, AlpakaStyle.TEXT_SOFT, false);
            ly += 10;
        }
        return y + h;
    }

    private void card(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, AlpakaStyle.ROW);
    }

    /** The gold of a maxed level. */
    private static final int GOLD = 0xFFFFC94D;

    /**
     * A maxed level: its whole number in gold beside a MAX badge, the badge at the card's right
     * edge {@code right}, so the badges line up whatever the numbers' widths.
     */
    private void maxBadge(GuiGraphicsExtractor graphics, String level, int right, int y) {
        int px = right - badgeW();
        maxBadge(graphics, px, y);
        text(graphics, level, px - 5 - textW(level), y, ink(GOLD));
    }

    /** A maxed tier: the gold MAX badge at the right edge with the tier before it; returns the width both take. */
    private int maxedTier(GuiGraphicsExtractor graphics, String tier, int right, int y) {
        maxBadge(graphics, tier, right, y);
        return badgeW() + 5 + textW(tier);
    }

    /** The gold MAX badge with its left edge at {@code x}, on a text line at {@code y}; returns its right edge. */
    private int maxBadge(GuiGraphicsExtractor graphics, int x, int y) {
        int pw = badgeW();
        ModernGuiUtils.drawRoundedRect(graphics, x, y - 2, pw, 11, 3, ink(GOLD));
        text(graphics, "MAX", x + 3, y, ink(0xFF1A1A1A));
        return x + pw;
    }

    private int badgeW() {
        return textW("MAX") + 6;
    }

    /** A progress bar: in the accent while under way, in chroma once maxed. */
    private static void levelBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double progress, boolean maxed) {
        if (maxed) maxedBar(graphics, x, y, w, h, progress);
        else bar(graphics, x, y, w, h, progress, AlpakaStyle.accent());
    }

    /**
     * The chroma of a maxed bar at a point on screen: a soft rainbow drifting slowly to the right.
     * It sets maxed bars apart from the accent, which the player may have set to gold.
     */
    private static int chroma(int x) {
        double t = System.currentTimeMillis() / 6000.0;
        float hue = (float) ((t - x / 220.0) % 1.0 + 1.0) % 1.0f;
        return ink(0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.45f, 0.95f) & 0xFFFFFF));
    }

    /**
     * A maxed level's bar: on the usual track, so where the fill ends stays plain, filled in chroma,
     * with a soft light running over it now and then.
     */
    private static void maxedBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double progress) {
        int radius = Math.max(0, h / 2);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, radius, AlpakaStyle.TRACK);
        int fw = (int) Math.round(Math.max(0, Math.min(1, progress)) * w);
        if (fw <= 0) return;
        // Rounded ends from the first and last colour, and the columns between in 2 px steps.
        int end = Math.max(1, Math.min(fw, 2));
        ModernGuiUtils.drawRoundedRect(graphics, x, y, Math.max(end * 2, h), h, radius, chroma(x));
        ModernGuiUtils.drawRoundedRect(graphics, x + fw - Math.max(end * 2, h), y, Math.max(end * 2, h), h, radius, chroma(x + fw - 1));
        for (int sx = end; sx < fw - end; sx += 2) {
            graphics.fill(x + sx, y, x + Math.min(fw - end, sx + 2), y + h, chroma(x + sx));
        }
        shine(graphics, x, y, fw, h);
    }

    /** How often the light runs over the gold bars, how long one pass takes, and how wide it is. */
    private static final long SHINE_PERIOD_MS = 6000;
    private static final double SHINE_SWEEP_MS = 1200;
    private static final int SHINE_HALF = 22;

    /**
     * The light over a gold bar's fill. Each pass starts a little later the further right and down
     * the bar sits, so it travels over the cards together instead of flashing on all at once. The
     * end columns are left out so the bar's rounded ends stay round.
     */
    private static void shine(GuiGraphicsExtractor graphics, int x, int y, int fw, int h) {
        if (fw <= 2) return;
        long delay = (long) ((x + 2L * y) * 1.5);
        long local = Math.floorMod(System.currentTimeMillis() - delay, SHINE_PERIOD_MS);
        if (local > SHINE_SWEEP_MS) return;
        double pos = -SHINE_HALF + (fw + 2.0 * SHINE_HALF) * (local / SHINE_SWEEP_MS);
        int from = Math.max(1, (int) Math.floor(pos - SHINE_HALF));
        int to = Math.min(fw - 1, (int) Math.ceil(pos + SHINE_HALF));
        for (int sx = from; sx < to; sx++) {
            double d = 1 - Math.abs(sx + 0.5 - pos) / SHINE_HALF;
            if (d <= 0) continue;
            int alpha = (int) Math.round(d * d * 0.6 * 255);
            graphics.fill(x + sx, y, x + sx + 1, y + h, ink((alpha << 24) | 0xFFFFFF));
        }
    }

    private static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double progress, int color) {
        int radius = Math.max(0, h / 2);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, radius, AlpakaStyle.TRACK);
        int fw = (int) Math.round(Math.max(0, Math.min(1, progress)) * w);
        if (fw > 0) ModernGuiUtils.drawRoundedRect(graphics, x, y, Math.max(fw, h), h, radius, color);
    }

    private static final Map<Item, ItemStack> STACKS = new HashMap<>();

    /**
     * An item drawn as an icon. Items only get their components once a world is joined, so opened
     * from the main menu's config there are no icons, rather than a crash.
     */
    private static void stackIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    private static void icon(GuiGraphicsExtractor graphics, Item item, int x, int y, float scale) {
        ItemStack stack = STACKS.get(item);
        if (stack == null) {
            try {
                stack = new ItemStack(item);
            } catch (RuntimeException notBoundYet) {
                return;
            }
            STACKS.put(item, stack);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    /**
     * A card's big number. In a TrueType font it is drawn in the font's own larger cut
     * ({@link GuiFont#title}): the normal text under a scaled pose was resampled, and blurry. The
     * pixel font has no such cut and is scaled as before.
     */
    /** How wide a number is in the large cut, or in the normal size where there is none. */
    private int bigWidth(String value) {
        Component big = GuiFont.title(value);
        return big != null ? this.font.width(big) : (int) Math.ceil(textW(value) * 1.35f);
    }

    private void value(GuiGraphicsExtractor graphics, String value, int x, int y, int maxW) {
        Component big = GuiFont.title(value);
        if (big != null && this.font.width(big) <= maxW) {
            // The larger cut's capitals reach higher above the line than the scaled text did, so it
            // sits lower to keep clear of the card's title.
            graphics.text(this.font, big, x, y + 4, AlpakaStyle.TEXT, false);
            return;
        }
        if (big != null) {
            // Too long for the large cut: the normal size, cut to fit, stays sharp.
            text(graphics, AlpakaStyle.ellipsize(this.font, value, maxW), x, y + 2, AlpakaStyle.TEXT);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(1.35f, 1.35f);
        text(graphics, AlpakaStyle.ellipsize(this.font, value, (int) (maxW / 1.35f)), 0, 0, AlpakaStyle.TEXT);
        graphics.pose().popMatrix();
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(this.font, GuiFont.text(text), x, y, color, false);
    }

    /** Text that carries SkyBlock's own color codes, cut to fit. */
    private void styled(GuiGraphicsExtractor graphics, String text, int x, int y, int maxW) {
        Component component = GuiFont.text(text);
        if (this.font.width(component) > maxW) {
            String plain = net.alpaka.addons.utils.SkyblockUtils.cleanColor(text);
            String start = text.length() >= 2 && text.charAt(0) == '§' ? text.substring(0, 2) : "";
            component = GuiFont.text(start + AlpakaStyle.ellipsize(this.font, plain, maxW));
        }
        graphics.text(this.font, component, x, y, AlpakaStyle.TEXT, false);
    }

    private int textW(String text) {
        return GuiFont.width(this.font, text);
    }

    private static int ink(int color) {
        return ProfileChart.ink(color);
    }

    /** Whether the mouse is on this area, and inside the content where the content is drawn. */
    private boolean hover(int x, int y, int w, int h) {
        // Dragging the scrollbar, nothing it passes over lights up.
        if (menuOpen || scrollDragging) return false;
        if (clipping && !AlpakaStyle.inside(mx, my, clipX, clipY, clipW, clipH)) return false;
        if (clipping && !drawingPin && my >= pinTop && my < pinBottom) return false;
        return AlpakaStyle.inside(mx, my, x, y, w, h);
    }

    private int clippedMouseX() {
        return menuOpen || !AlpakaStyle.inside(mx, my, clipX, clipY, clipW, clipH) ? -10000 : mx;
    }

    private int clippedMouseY() {
        return menuOpen || !AlpakaStyle.inside(mx, my, clipX, clipY, clipW, clipH) ? -10000 : my;
    }

    /** A click area in the content: only the part of it that is in view. */
    private void hit(int x, int y, int w, int h, Runnable action) {
        if (clipping) {
            int top = Math.max(y, drawingPin ? clipY : Math.max(clipY, pinBottom));
            int bottom = Math.min(y + h, clipY + clipH);
            if (bottom <= top) return;
            hits.add(new Hit(x, top, w, bottom - top, action));
        } else {
            hits.add(new Hit(x, y, w, h, action));
        }
    }

    private void headerHit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private void tip(Object key, String text) {
        hoveredTipKey = key;
        hoveredTip = text;
    }

    private String rangeWords() {
        return switch (range) {
            case WEEK -> "7 days";
            case MONTH -> "30 days";
            case QUARTER -> "90 days";
            case YEAR -> "year";
            case ALL -> "whole history";
        };
    }

    private void openLink(String title, String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new net.alpaka.addons.client.AlpakaConfirmScreen(this, title,
                "Opens this profile in your browser: " + url, "Open", false,
                () -> net.minecraft.util.Util.getPlatform().openUri(url)));
    }

    private void lookUp(String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
        session = session(name);
        searchedPending = session.query;
        search.setText("");
        searchFocused = false;
        recent = List.of();
        recentPicked = null;
        itemSearch.setText("");
        itemSearchOpen = false;
        searchIndex = List.of();
        searchIndexKey = null;
        menuOpen = false;
        faceFor = null;
        chosen.clear();
        breakdownFor = null;
        collectionsData = null;
        skillDetail = null;
    }

    // ---- Screenshot tour ------------------------------------------------------------------------

    /** For the screenshot tour: the dashboard on a player and page, already open rather than popping open. */
    public static ProfileDashboardScreen devOpen(String player, String page) {
        ProfileDashboardScreen screen = new ProfileDashboardScreen(session(player), null);
        screen.page = Page.valueOf(page);
        screen.openTimeMs = 1L;
        return screen;
    }

    /** For the screenshot tour: the tab of the inventory page, by name ("BACKPACKS"). */
    public static void devInventoryTab(String name) {
        invTab = InvTab.valueOf(name);
    }

    /** For the screenshot tour: the graphs' range, by name ("ALL"). */
    public static void devRange(String name) {
        range = ProfileChart.Range.valueOf(name);
    }

    /** For the screenshot tour: the page scrolled to its end. */
    public static ProfileDashboardScreen devOpenScrolled(String player, String page) {
        ProfileDashboardScreen screen = devOpen(player, page);
        screen.devScrollToEnd = true;
        return screen;
    }

    /** For the screenshot tour: the Farming or Mining page of the Skills page, at the top or the end. */
    public static ProfileDashboardScreen devOpenSkill(String player, String skill, boolean scrolled) {
        ProfileDashboardScreen screen = devOpen(player, "SKILLS");
        screen.skillDetail = skill;
        screen.chosen.put(Page.SKILLS, Metrics.SKILL + skill);
        screen.devScrollToEnd = scrolled;
        return screen;
    }

    /** For the screenshot tour: a page's sub-page, such as a Museum category ("combat"). */
    public static ProfileDashboardScreen devOpenDetail(String player, String page, String detail) {
        ProfileDashboardScreen screen = devOpen(player, page);
        screen.skillDetail = detail;
        return screen;
    }

    /** For the screenshot tour: scrolls the page down by this many pixels. */
    public void devScroll(int pixels) {
        scroll.current = scroll.target = Math.min(scroll.max, scroll.target + pixels);
    }

    private boolean devScrollToEnd;

    /** For the screenshot tour: whether the page has what it loads. */
    public boolean devReady() {
        ProfileSession s = session;
        if (s.state == ProfileSession.State.LOADING) return false;
        if (s.data == null) return true;
        if (page == Page.NETWORTH) return s.networthState == ProfileSession.State.READY || s.networthState == ProfileSession.State.FAILED;
        if (page == Page.COLLECTIONS) return s.collectionsState == ProfileSession.State.READY || s.collectionsState == ProfileSession.State.FAILED;
        if (page == Page.MUSEUM) {
            ProfileSession.State museumState = s.inventoryState("museum");
            return (museumState == ProfileSession.State.READY || museumState == ProfileSession.State.FAILED)
                    && (Museum.repo() != null || Museum.failed());
        }
        if (page == Page.CHOCOLATE) return Hoppity.rabbits() != null;
        return true;
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) rightHeld = true;
        if (event.button() != 0) return true;
        click = event;
        clickFrom = searchFocused ? search : itemSearchFocused ? itemSearch : null;
        searchFocused = false;
        itemSearchFocused = false;
        if (itemSearch.isEmpty()) itemSearchOpen = false;
        if (menuOpen) {
            // Only the list takes the click; anywhere else, the chip included, closes it.
            for (int i = hits.size() - 1; i >= menuHitsFrom; i--) {
                Hit hit = hits.get(i);
                if (AlpakaStyle.inside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
                    AlpakaStyle.click();
                    hit.action().run();
                    return true;
                }
            }
            menuOpen = false;
            return true;
        }
        if (!overRecent(event.x(), event.y()) && pressScrollbar(event.x(), event.y())) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (AlpakaStyle.inside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
                AlpakaStyle.click();
                hit.action().run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (scrollDragging) {
            dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        scrollDragging = false;
        if (event.button() == 1) rightHeld = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && !menuOpen && !overRecent(mouseX, mouseY) && AlpakaStyle.inside(mouseX, mouseY, clipX, clipY, clipW, clipH)) scroll.by(-scrollY * 24);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (itemSearchFocused) {
            if (event.isEscape()) {
                // The first Escape clears the search, the next closes the field.
                if (itemSearch.isEmpty()) {
                    itemSearchFocused = false;
                    itemSearchOpen = false;
                } else {
                    itemSearch.setText("");
                }
                return true;
            }
            if (event.isConfirmation()) {
                itemSearchFocused = false;
                if (itemSearch.isEmpty()) itemSearchOpen = false;
                return true;
            }
            itemSearch.keyPressed(event);
            itemCaretFrom = System.currentTimeMillis();
            return true;
        }
        if (searchFocused) {
            if (event.isEscape()) {
                searchFocused = false;
                return true;
            }
            if (event.isConfirmation()) {
                lookUp(recentPicked != null ? recentPicked : search.getText().trim());
                return true;
            }
            int key = event.key();
            if (!recent.isEmpty() && (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP)) {
                // The arrows walk the recent players, and past either end back to what is typed.
                int at = recentPicked == null ? -1 : recent.indexOf(recentPicked);
                int next = key == GLFW.GLFW_KEY_DOWN ? at + 1 : (at < 0 ? recent.size() : at) - 1;
                recentPicked = next >= 0 && next < recent.size() ? recent.get(next) : null;
                return true;
            }
            if (!recent.isEmpty() && key == GLFW.GLFW_KEY_TAB) {
                // Fills the field with the picked player, or the first; the pick stays, so a second
                // Tab fills the same name again.
                String player = recentPicked != null ? recentPicked : recent.get(0);
                search.setText(player);
                recentPicked = player;
                caretFrom = System.currentTimeMillis();
                return true;
            }
            String before = search.getText();
            search.keyPressed(event);
            if (!before.equals(search.getText())) recentPicked = null;
            caretFrom = System.currentTimeMillis();
            return true;
        }
        if (event.isEscape() && menuOpen) {
            menuOpen = false;
            return true;
        }
        if (event.isEscape() && searching()) {
            itemSearch.setText("");
            itemSearchOpen = false;
            return true;
        }
        if (event.isEscape() || (this.minecraft != null && this.minecraft.options.keyInventory.matches(event))) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (itemSearchFocused) {
            for (char c : Character.toChars(event.codepoint())) {
                if (c >= 32 && c != 127 && c != '§') itemSearch.insert(c);
            }
            itemCaretFrom = System.currentTimeMillis();
            return true;
        }
        if (!searchFocused) return false;
        for (char c : Character.toChars(event.codepoint())) {
            if (Character.isLetterOrDigit(c) && c < 128 || c == '_') search.insert(c);
        }
        recentPicked = null;
        caretFrom = System.currentTimeMillis();
        return true;
    }
}
