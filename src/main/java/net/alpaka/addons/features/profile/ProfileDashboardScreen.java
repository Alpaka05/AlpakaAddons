package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
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
 * The numbers come from Elite ({@link ProfileApi}); the graphs from Elite's skill history and the
 * dashboard's own snapshots ({@link ProfileHistory}). Any player can be looked up from the search
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
    private static final long SESSION_KEEP_MS = 5 * 60_000L;

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
    private boolean searchFocused = false;
    private long caretFrom = 0L;
    private boolean profileMenuOpen = false;
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
        mc.gui.setScreen(new ProfileDashboardScreen(session(player), parent));
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

        session.syncSkyKings();
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        // With the profile list open, nothing under it lights up.
        mx = mouseX;
        my = mouseY;

        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        AlpakaStyle.panel(graphics, x, y, w, h);

        drawHeader(graphics, x, y, w);
        AlpakaStyle.headerLine(graphics, x, y, w);
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
        if (devScrollToEnd) scroll.current = scroll.target = scroll.max;
        scroll.update(deltaSec);
        AlpakaStyle.scrollFades(graphics, clipX, clipY, clipW, clipH, scroll.offset() > 0, scroll.offset() + 1 < scroll.max);
        graphics.disableScissor();

        for (Runnable overlay : overlays) overlay.run();
        menuHitsFrom = hits.size();
        if (profileMenuOpen) drawProfileMenu(graphics, x, y, w);

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
            boolean hover = AlpakaStyle.inside(mx, my, tx, ty - 3, cw, 14) && !profileMenuOpen;
            int bg = profileMenuOpen ? MarketIcons.fade(AlpakaStyle.accent(), 0.3f) : hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW;
            ModernGuiUtils.drawRoundedRect(graphics, tx, ty - 3, cw, 14, 7, bg);
            text(graphics, label, tx + 7, ty, profileMenuOpen || hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            AlpakaStyle.chevron(graphics, tx + cw - 7, ty + 4, profileMenuOpen, AlpakaStyle.TEXT_MUTED);
            profileChipX = tx;
            profileChipW = cw;
            if (s.profiles.size() > 1) {
                headerHit(tx, ty - 3, cw, 14, () -> profileMenuOpen = !profileMenuOpen);
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
                tip("refresh", fresh + "Asks Elite for the profile again; Elite reads it from Hypixel every few minutes at most.");
            }
        }

        int sw = Math.min(120, Math.max(70, w / 6));
        int sx = rx - 8 - sw;
        if (sx > tx + 90) {
            boolean hoverSearch = AlpakaStyle.inside(mx, my, sx, by - 1, sw, AlpakaStyle.BUTTON_H);
            AlpakaStyle.textField(graphics, this.font, sx, by - 1, sw, AlpakaStyle.BUTTON_H, search.getText(), "Look up a player",
                    searchFocused, hoverSearch, caretFrom);
            headerHit(sx, by - 1, sw, AlpakaStyle.BUTTON_H, () -> {
                searchFocused = true;
                caretFrom = System.currentTimeMillis();
            });
            if (hoverSearch && !searchFocused) tip("search", "Type a player's name and press Enter to see their profile.");
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
                profileMenuOpen = false;
                session.switchProfile(id);
            }));
            ry += rowH;
        }
    }

    /** The player's face: from the skin Elite names, or the game's own profile for the player. */
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
        // the item search opens from it.
        int footerH = 30;
        boolean footer = open && h >= 12 + pages * 17 + footerH + 12;
        int rowH = Math.max(14, Math.min(SIDE_ROW_H, (h - 10 - 12 - (footer ? footerH : 0)) / pages - 1));
        int rowY = y + 8;

        // The arrow that folds the sidebar down to its icons and opens it again: at the end of the
        // label's line, or alone in the middle while folded.
        int arrow = 12;
        int ax = open ? rowX + rowW - arrow : rowX + (rowW - arrow) / 2;
        int ay = rowY - 3;
        boolean hoverArrow = AlpakaStyle.inside(mx, my, ax, ay, arrow, arrow) && !profileMenuOpen;
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

        if (!open) {
            // No label or search while folded or sliding.
        } else if (itemSearchOpen || !itemSearch.isEmpty()) {
            // The search through every item the profile owns, where the label was.
            int fh = 14;
            int fy = rowY - 3;
            boolean hoverField = AlpakaStyle.inside(mx, my, rowX, fy, rowW, fh) && !profileMenuOpen;
            AlpakaStyle.textField(graphics, this.font, rowX, fy, rowW - arrow - 2, fh, itemSearch.getText(), "Search Items",
                    itemSearchFocused, hoverField, itemCaretFrom);
            hits.add(new Hit(rowX, fy, rowW - arrow - 2, fh, () -> {
                itemSearchFocused = true;
                itemCaretFrom = System.currentTimeMillis();
            }));
        } else {
            AlpakaStyle.groupLabel(graphics, this.font, "Profile", x + PAD, rowY);
            // The magnifier just after the label opens the item search in its place.
            int gx = x + PAD + textW("Profile") + 4;
            int gy = rowY - 2;
            boolean hoverGlass = AlpakaStyle.inside(mx, my, gx - 2, gy - 1, 13, 12) && !profileMenuOpen;
            MarketIcons.draw(graphics, MarketIcons.Glyph.SEARCH, gx, gy + 1, 8, hoverGlass ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
            hits.add(new Hit(gx - 2, gy - 1, 13, 12, () -> {
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
            boolean hover = AlpakaStyle.inside(mx, my, rowX, rowY, rowW, rowH) && !profileMenuOpen;
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
        if (!footer) return;

        // At the bottom: how fresh the numbers are, and where they come from.
        int fy = y + h - footerH;
        ProfileData d = session.data;
        if (d != null && d.lastUpdated > 0) {
            text(graphics, "Updated " + Metrics.ago(d.lastUpdated * 1000L), x + PAD, fy, AlpakaStyle.TEXT_MUTED);
        }
        boolean skykings = !AlpakaConfig.instance.skykingsApiKey.isBlank();
        String source = "Data Sources";
        int sw = textW(source);
        boolean hoverSource = AlpakaStyle.inside(mx, my, x + PAD - 2, fy + 10, sw + 4, 11);
        text(graphics, source, x + PAD, fy + 12, hoverSource ? AlpakaStyle.accent() : AlpakaStyle.DIVIDER);
        if (session.playerName != null) {
            String url = ProfileApi.ELITE_SITE + session.playerName + (d != null && d.profileName != null ? "/" + d.profileName : "");
            hits.add(new Hit(x + PAD - 2, fy + 10, sw + 4, 11, () -> openLink("Open Elite", url)));
            if (hoverSource) {
                tip("source", "Profile data from Elite (elitebot.dev), which reads it from Hypixel; the history from Elite and "
                        + "Soopy (soopy.dev), pet heads, the Bestiary, the Museum and Hoppity's rabbits from the NEU repository. Soopy works networth out differently, so its "
                        + "older networth is scaled to match Elite's."
                        + (skykings ? " With your key, SkyKings' daily history too" + (session.skykingsNote != null
                                && !session.skykingsNote.equals("loading") ? ": " + session.skykingsNote : ".")
                                : " A SkyKings API key in the config adds SkyKings' daily history.")
                        + " Not affiliated with Hypixel. Click to open this profile on Elite.");
            }
        }
    }

    // ---- Content --------------------------------------------------------------------------------

    /** Draws the page and returns where it ends. */
    private int drawContent(GuiGraphicsExtractor graphics, int x, int y, int w, long now) {
        ProfileSession s = session;
        if (!AlpakaConfig.instance.allowApiCalls) {
            int h = clipH - 2 * PAD;
            ProfileChart.message(graphics, this.font, "The dashboard loads profiles from Elite, so it needs Network Features.", x, y, w, h / 2);
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

    /** Cards where the page will be, softly pulsing, while Elite answers. */
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
        tiles.add(new Tile(Metrics.NETWORTH, "Networth", d.networth > 0 ? Coins.compact(d.networth) : "API off", null));
        tiles.add(new Tile(Metrics.SB_LEVEL, "SkyBlock Level", String.format(Locale.US, "%.2f", d.skyblockLevel), null));
        tiles.add(new Tile(Metrics.SKILL_AVG, "Skill Average", d.skillsApi ? String.format(Locale.US, "%.2f", d.skillAverage) : "API off", null));
        tiles.add(new Tile(Metrics.CATACOMBS, "Catacombs", String.format(Locale.US, "%.2f", Levels.catacombs(d.catacombs.xp())), null));
        tiles.add(new Tile(Metrics.SLAYER_TOTAL, "Slayer XP", Coins.compact(d.slayerXp), null));
        tiles.add(new Tile(Metrics.MAGICAL_POWER, "Magical Power", Coins.full(d.magicalPower), null));
        tiles.add(new Tile(Metrics.PETS_MAXED, "Max Level Pets", d.petsMaxed + " / " + d.pets.size(), null));
        tiles.add(d.farmingWeight > 0
                ? new Tile(Metrics.FARMING_WEIGHT, "Farming Weight", Coins.full(Math.round(d.farmingWeight * 10) / 10.0), null)
                : new Tile(Metrics.BANK, "Bank", Coins.compact(d.bank + d.personalBank), null));
        y = tiles(graphics, tiles, x, y, w, 4);

        String metric = metricFor(Page.OVERVIEW);
        y = chartCard(graphics, metric, metricMaxLevel(d, metric), x, y + 6, w, 130);

        int colW = (w - 6) / 2;
        int leftEnd = nextUp(graphics, d, x, y + 6, w >= 420 ? colW : w);
        int rightEnd = w >= 420 ? milestones(graphics, d, x + colW + 6, y + 6, colW, now) : milestones(graphics, d, x, leftEnd + 6, w, now);
        return Math.max(leftEnd, rightEnd);
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
        // all time are only worked out again then; the 30 days are still cut every frame.
        List<String> metrics = new ArrayList<>();
        List<java.util.function.DoubleToIntFunction> levels = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ProfileData.Level s : d.skills) {
            int max = s.maxLevel();
            metrics.add(Metrics.SKILL + s.id());
            names.add(s.name());
            levels.add(xp -> (int) Levels.skill(s.id(), xp, max));
        }
        metrics.add(Metrics.CATACOMBS);
        names.add("Catacombs");
        levels.add(xp -> (int) Math.min(50, Levels.catacombs(xp)));
        for (ProfileData.Level c : d.classes) {
            metrics.add(Metrics.CLASS + c.id());
            names.add(c.name());
            levels.add(xp -> (int) Math.min(50, Levels.catacombs(xp)));
        }
        metrics.add(Metrics.SB_LEVEL);
        names.add("SkyBlock Level");
        levels.add(level -> (int) Math.floor(level));

        List<List<ProfileHistory.Point>> series = new ArrayList<>(metrics.size());
        for (String metric : metrics) series.add(session.series(metric));
        if (d != milestoneData || !sameLists(series, milestoneSeries)) {
            List<Milestone> all = new ArrayList<>();
            for (int i = 0; i < metrics.size(); i++) {
                levelUps(all, series.get(i), metrics.get(i), names.get(i), levels.get(i));
            }
            all.sort((a, b) -> Long.compare(b.timeMs(), a.timeMs()));
            milestoneData = d;
            milestoneSeries = series;
            milestoneAll = all;
        }
        long since = now - 30 * ProfileHistory.DAY;
        List<Milestone> out = new ArrayList<>();
        for (Milestone m : milestoneAll) {
            if (m.timeMs() >= since) out.add(m);
        }
        return out;
    }

    private ProfileData milestoneData;
    private List<List<ProfileHistory.Point>> milestoneSeries = List.of();
    private List<Milestone> milestoneAll = List.of();

    private static boolean sameLists(List<List<ProfileHistory.Point>> a, List<List<ProfileHistory.Point>> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

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
    private record Tile(String metric, String label, String value, String note) {}

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
            text(graphics, AlpakaStyle.ellipsize(this.font, t.label(), tw - 12), tx + 7, ty + 6, AlpakaStyle.TEXT_MUTED);
            value(graphics, t.value(), tx + 7, ty + 18, tw - 14);
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
                    tip(t, (t.note() != null ? t.note() + " " : "") + (Double.isNaN(change) ? "No history in this range yet."
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
     * the networth has no history yet, but Elite's skill history usually does.
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

    /** The Garden's crops: their key in Elite's garden data, their name, and how they look. */
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
            int milestone = (int) Json.num(r, "garden.cropMilestoneLevels." + key, 0);
            String level = "Milestone " + milestone;
            text(graphics, AlpakaStyle.ellipsize(this.font, name, tw - textW(level) - 6), tx, cy + 7, AlpakaStyle.TEXT_SOFT);
            text(graphics, level, tx + tw - textW(level), cy + 7, AlpakaStyle.TEXT);
            double amount = Json.num(r, "garden.crops." + key, 0);
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
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsDouble() > 0) {
                pests.add(row(ProfileData.title(CAMEL_HUMP.matcher(e.getKey()).replaceAll("$1_$2")), Coins.full(e.getValue().getAsDouble())));
            }
        }
        add(cards, "Pests Killed", pests);

        List<String[]> bonus = new ArrayList<>();
        for (var e : Json.obj(r, "farmingWeight.bonusWeight").entrySet()) {
            if (e.getValue().isJsonPrimitive()) bonus.add(row(e.getKey(), String.format(Locale.US, "%.2f", e.getValue().getAsDouble())));
        }
        add(cards, "Bonus Weight", bonus);
        return cards;
    }

    private int mining(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        y = skillHeader(graphics, "Mining", x, y);
        JsonElement r = d.raw;
        List<Tile> tiles = new ArrayList<>();
        tiles.add(skillTile(d, "mining", "Mining"));
        // Elite's Heart of the Mountain tier stays at 1 for profiles past Hypixel's skill tree
        // rework; Peak of the Mountain is right for everyone.
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
            if (e.getValue().isJsonPrimitive()) count(glacite, ProfileData.title(e.getKey()) + " Corpses", e.getValue().getAsDouble());
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

    /** The Dojo's tests by Elite's id, with the names the Dojo gives them. */
    private static final String[][] DOJO_TESTS = {
            {"mob_kb", "Force"}, {"wall_jump", "Stamina"}, {"archer", "Mastery"}, {"sword_swap", "Discipline"},
            {"snake", "Swiftness"}, {"lock_head", "Control"}, {"fireball", "Tenacity"}};
    /** Kuudra's tiers by Elite's id, lowest first. */
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

        // Elite's personal bests are the logs of each tree's contest and two NPCs' own records.
        List<String[]> bests = new ArrayList<>();
        for (var e : Json.obj(r, "stats.foraging.personalBests").entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            bests.add(row(ProfileData.title(e.getKey().toLowerCase(Locale.ROOT)), Coins.full(e.getValue().getAsDouble())));
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
        String metric = metricFor(Page.DUNGEONS);
        return chartCard(graphics, metric, 0, x, y + 6, w, 130);
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

    private record Part(String label, double value) {}

    private record Valuable(String name, int count, String where, double value) {}

    private JsonElement breakdownFor;
    private List<Part> parts = List.of();
    private List<Valuable> valuables = List.of();

    private int networth(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.NETWORTH, "Networth", Coins.compact(d.networth), null));
        // Plain cards: the history has next to nothing for these.
        tiles.add(new Tile(null, "Liquid", Coins.compact(d.liquidNetworth), "sells right away"));
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
            ProfileChart.message(graphics, this.font, failed ? "Couldn't load the breakdown. Click to try again." : "Loading the breakdown…", x, y, w, h);
            if (failed) hit(x, y, w, h, session::retryFailed);
            return y + h;
        }
        if (answer != breakdownFor) {
            breakdownFor = answer;
            readBreakdown(answer);
        }

        // Where the networth sits: one bar split by category, and the categories listed under it.
        double total = 0;
        for (Part p : parts) total += p.value();
        int listCols = w >= 380 ? 2 : 1;
        int rows = (parts.size() + listCols - 1) / listCols;
        int h = 24 + 12 + rows * 13 + 6;
        card(graphics, x, y, w, h);
        text(graphics, "Breakdown", x + 8, y + 7, AlpakaStyle.TEXT);
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
            if (to - from >= 1) ModernGuiUtils.drawRect(graphics, from, by, to - from, 7, ink(PALETTE[i % PALETTE.length]));
        }
        int colW = (w - 16) / listCols;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            int cx = x + 8 + (i % listCols) * colW;
            int cy = y + 36 + (i / listCols) * 13;
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy + 2, 5, 5, 2, ink(PALETTE[i % PALETTE.length]));
            text(graphics, p.label(), cx + 9, cy, AlpakaStyle.TEXT_SOFT);
            String value = Coins.compact(p.value());
            String share = String.format(Locale.US, "%.1f%%", total > 0 ? p.value() / total * 100 : 0);
            int right = cx + colW - 10;
            text(graphics, share, right - textW(share), cy, AlpakaStyle.TEXT_MUTED);
            text(graphics, value, right - textW(share) - 8 - textW(value), cy, AlpakaStyle.TEXT);
        }
        y += h + 6;

        if (valuables.isEmpty()) return y;
        int shown = Math.min(12, valuables.size());
        h = 24 + shown * 14 + 4;
        card(graphics, x, y, w, h);
        text(graphics, "Most Valuable Items", x + 8, y + 7, AlpakaStyle.TEXT);
        int ry = y + 22;
        for (int i = 0; i < shown; i++) {
            Valuable v = valuables.get(i);
            String rank = (i + 1) + ".";
            text(graphics, rank, x + 8, ry, AlpakaStyle.TEXT_MUTED);
            String value = Coins.compact(v.value());
            String where = v.where();
            int right = x + w - 8;
            text(graphics, value, right - textW(value), ry, AlpakaStyle.TEXT);
            int whereX = right - textW(value) - 10 - textW(where);
            text(graphics, where, whereX, ry, AlpakaStyle.TEXT_MUTED);
            String name = v.name() + (v.count() > 1 ? " §8x" + v.count() : "");
            int nameX = x + 24;
            styled(graphics, name, nameX, ry, whereX - 8 - nameX);
            ry += 14;
        }
        return y + h;
    }

    private void readBreakdown(JsonElement answer) {
        Map<String, Double> grouped = new LinkedHashMap<>();
        List<Valuable> items = new ArrayList<>();
        for (var category : Json.obj(answer, "categories").entrySet()) {
            String label = categoryLabel(category.getKey());
            double total = Json.num(category.getValue(), "total", 0);
            if (total > 0) grouped.merge(label, total, Double::sum);
            for (JsonElement item : Json.arr(category.getValue(), "items")) {
                String name = Json.str(item, "item.name");
                String id = Json.str(item, "item.skyblockId");
                double value = Json.num(item, "networth", 0);
                // Sacks and essence name raw ids; pets come without a name at all.
                if (name == null || id == null || name.equals(id) || value <= 0
                        || category.getKey().equals("sacks") || category.getKey().equals("essence")) continue;
                items.add(new Valuable(name, (int) Json.num(item, "item.count", 1), label, value));
            }
        }
        List<Part> list = new ArrayList<>();
        grouped.forEach((label, value) -> list.add(new Part(label, value)));
        list.sort((a, b) -> Double.compare(b.value(), a.value()));
        items.sort((a, b) -> Double.compare(b.value(), a.value()));
        parts = List.copyOf(list);
        valuables = List.copyOf(items.subList(0, Math.min(20, items.size())));
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
            if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
            boolean hover = hover(cx, cy, cw, ch);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (p.active()) ModernGuiUtils.drawRoundedOutline(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
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
            boolean selected = t == invTab;
            boolean hover = hover(cx, cy, cw, 15);
            int bg = selected ? MarketIcons.fade(AlpakaStyle.accent(), hover ? 0.35f : 0.24f) : hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW;
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, 15, 7, bg);
            text(graphics, t.title, cx + 7, cy + 4, selected ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            hit(cx, cy, cw, 15, () -> invTab = t);
            cx += cw + 4;
        }
    }

    private int invContent(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        return switch (invTab) {
            case INVENTORY -> playerInventory(graphics, d, x, y, w);
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

    /** Sections side by side as far as they fit, each into the shortest column, as the More page does. */
    private int flow(GuiGraphicsExtractor graphics, List<Section> sections, int x, int y, int w) {
        int cols = Math.max(1, (w + 8) / (SECTION_W + 8));
        int gap = cols > 1 ? (w - cols * SECTION_W) / (cols - 1) : 0;
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
            // Far outside the view: not even the slot is drawn; an Ender Chest has 405 of them.
            if (sy > clipY + clipH || sy + size < clipY) continue;
            slot(graphics, items.get(key), sx, sy);
        }
    }

    private void slot(GuiGraphicsExtractor graphics, JsonElement item, int sx, int sy) {
        int size = SLOT * slotScale - 1;
        boolean has = InventoryItems.present(item);
        boolean hover = has && hover(sx, sy, size, size);
        ModernGuiUtils.drawRoundedRect(graphics, sx, sy, size, size, 2 * slotScale, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.PANEL);
        if (!has) return;
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
    private int playerInventory(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        JsonElement inv = session.inventory("inventory");
        JsonElement armor = session.inventory("armor");
        JsonElement equipment = session.inventory("equipment");
        // Twice the size when there is room, in the middle of the page, as the game's own screen.
        slotScale = 16 + 11 * SLOT * 2 + 8 * 2 <= w ? 2 : 1;
        int slot = SLOT * slotScale;
        int sideW = 2 * slot + 8 * slotScale;
        int cw = 16 + sideW + 9 * slot;
        int h = 22 + 4 * slot + 6 * slotScale + 8;
        x += (w - cw) / 2;
        y = middle(y, h);
        card(graphics, x, y, cw, h);
        text(graphics, "Inventory", x + 8, y + 7, AlpakaStyle.TEXT);
        int gy = y + 22;
        if (armor != null) slots(graphics, Json.obj(armor, "items"), List.of("3", "2", "1", "0"), x + 8, gy, 1);
        if (equipment != null) slots(graphics, Json.obj(equipment, "items"), List.of("0", "1", "2", "3"), x + 8 + slot, gy, 1);
        int mx0 = x + 8 + sideW;
        if (inv == null) {
            ProfileChart.message(graphics, this.font, "Loading…", mx0, gy, 9 * slot, 4 * slot);
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
        JsonElement inv = session.inventory(name);
        int slots = slotsOf(session.data, name);
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
        if (!d.inventories.containsKey("museum")) return note(graphics, "Nothing donated to the museum yet.", x, y, w);
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
            if (sy > clipY + clipH || sy + CELL < clipY) continue;
            JsonElement shown = museumItem(repo, e);
            ItemStack stack = shown != null ? InventoryItems.stack(shown) : ItemIcons.forItem(Museum.iconId(repo, e.id()), null);
            museumSlot(graphics, stack, sx, sy, !e.donated(), e.borrowed(), museumTip(repo, e));
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
            if (sy > clipY + clipH || sy + CELL < clipY) continue;
            boolean out = Museum.borrowed(item);
            List<Component> lines = new ArrayList<>();
            String name = Json.str(item, "name");
            lines.add(Component.literal(name == null ? ProfileData.title(String.valueOf(Json.str(item, "skyblockId"))) : name));
            lines.add(Component.literal(out ? "§eDonated, taken out right now" : "§aDonated"));
            double value = museumValue(List.of(item));
            if (value > 0) lines.add(Component.literal("§7Value: §6" + Coins.compact(value)));
            museumSlot(graphics, InventoryItems.stack(item), sx, sy, false, out, lines);
        }
        return y + ((m.special().size() + cols - 1) / cols) * CELL - 2;
    }

    /** A slot of the museum: greyed out while missing, with a dot in the corner while taken out. */
    private void museumSlot(GuiGraphicsExtractor graphics, ItemStack stack, int sx, int sy, boolean missing, boolean out, List<Component> tip) {
        int size = CELL - 2;
        boolean hover = hover(sx, sy, size, size);
        ModernGuiUtils.drawRoundedRect(graphics, sx, sy, size, size, 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
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

    /** The Timecharms by Elite's id, in the order the Rift hands them out. */
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
        count(progress, "Portals", Json.num(r, "totalPortals"));
        if (Json.bool(r, "consumedPrism")) progress.add(row("Rift Prism", "Consumed"));
        if (Json.bool(r, "unlockedRiftCatPet")) progress.add(row("Montezuma Pet", "Unlocked"));
        add(cards, "Progress", progress);

        List<String[]> stacks = new ArrayList<>();
        count(stacks, "Lifetime Motes", Json.num(r, "lifetimeMotesEarned"));
        count(stacks, "Motes Orbs Picked Up", Json.num(r, "motesOrbPickup"));
        count(stacks, "McGrubber's Burgers", Json.num(r, "maxBurgers"));
        count(stacks, "Grubber Stacks", Json.num(r, "grubberStacks"));
        count(stacks, "Croak Stacks", Json.num(r, "croakStacks"));
        count(stacks, "Siren Statue Kills", Json.num(r, "sirenStatueKills"));
        add(cards, "Stats", stacks);
        return cards(graphics, cards, x, y, w);
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
        double unique = Json.sum(c, "uniqueRabbits");

        List<Tile> tiles = new ArrayList<>();
        // Plain cards: the page has no graph.
        tiles.add(new Tile(null, "Chocolate Produced", Coins.compact(produced), null));
        tiles.add(new Tile(null, "Prestige", Coins.full(Json.num(c, "prestige", 0)), null));
        tiles.add(new Tile(null, "Chocolate", Coins.compact(Json.num(c, "chocolate", 0)), null));
        tiles.add(new Tile(null, "Unique Rabbits", Coins.full(unique) + (collection > 0 ? " / " + collection : ""), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        y = rabbits(graphics, c, all, x, y, w) + 6;

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
    private int rabbits(GuiGraphicsExtractor graphics, JsonElement c, Map<String, Integer> all, int x, int y, int w) {
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
            int found = (int) Json.num(c, "uniqueRabbits." + rarity, 0);
            int seen = (int) Json.num(c, "totalRabbits." + rarity, 0);
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

    /** Leaves the search for the page an item is shown on. */
    private Runnable opener(Page target, InvTab tab, String detail) {
        return () -> {
            itemSearch.setText("");
            itemSearchOpen = false;
            itemSearchFocused = false;
            page = target;
            lastPage = target;
            skillDetail = detail;
            if (tab != null) invTab = tab;
        };
    }

    private Runnable opener(String inventory) {
        if (inventory.equals("museum")) return opener(Page.MUSEUM, null, null);
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
        return opener(Page.INVENTORY, tab, null);
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
            Runnable open = opener(name);
            for (var e : Json.obj(inv, "items").entrySet()) {
                JsonElement item = e.getValue();
                if (!InventoryItems.present(item)) continue;
                // A museum item taken out is found where it really is, not twice.
                if (name.equals("museum") && Museum.borrowed(item)) continue;
                String title = Json.str(item, "name");
                String id = Json.str(item, "skyblockId");
                if (title == null && id == null) continue;
                index.add(new Found(title != null ? title : ProfileData.title(id), haystack(title, id), where(name, e.getKey()),
                        (int) Json.num(item, "count", 1), item, null, null, open));
            }
        }
        for (var e : Json.obj(d.raw, "sacks").entrySet()) {
            if (!e.getValue().isJsonPrimitive() || e.getValue().getAsDouble() <= 0) continue;
            String id = e.getKey();
            String title = ItemIcons.name(id);
            index.add(new Found("§f" + (title != null ? title : ProfileData.title(id)), haystack(title, id), "Sacks",
                    (int) Math.min(Integer.MAX_VALUE, e.getValue().getAsDouble()), null, id, null, null));
        }
        Runnable pets = opener(Page.PETS, null, null);
        for (ProfileData.Pet p : d.pets) {
            String title = "§7[Lvl " + p.level() + "] " + rarityCode(p.tier()) + p.name();
            index.add(new Found(title, haystack(p.name() + " pet", p.type()), p.active() ? "Pets · Active" : "Pets", 1, null, null, p, pets));
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
            if (!stack.isEmpty()) graphics.item(stack, x + 2, ry + 1);
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
        if (profileMenuOpen) return false;
        if (clipping && !AlpakaStyle.inside(mx, my, clipX, clipY, clipW, clipH)) return false;
        if (clipping && !drawingPin && my >= pinTop && my < pinBottom) return false;
        return AlpakaStyle.inside(mx, my, x, y, w, h);
    }

    private int clippedMouseX() {
        return profileMenuOpen || !AlpakaStyle.inside(mx, my, clipX, clipY, clipW, clipH) ? -10000 : mx;
    }

    private int clippedMouseY() {
        return profileMenuOpen || !AlpakaStyle.inside(mx, my, clipX, clipY, clipW, clipH) ? -10000 : my;
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

    private void lookUp() {
        String name = search.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
        session = session(name);
        search.setText("");
        searchFocused = false;
        itemSearch.setText("");
        itemSearchOpen = false;
        searchIndex = List.of();
        searchIndexKey = null;
        profileMenuOpen = false;
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
        if (event.button() != 0) return true;
        searchFocused = false;
        itemSearchFocused = false;
        if (itemSearch.isEmpty()) itemSearchOpen = false;
        if (profileMenuOpen) {
            // Only the list takes the click; anywhere else, the chip included, closes it.
            for (int i = hits.size() - 1; i >= menuHitsFrom; i--) {
                Hit hit = hits.get(i);
                if (AlpakaStyle.inside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
                    AlpakaStyle.click();
                    hit.action().run();
                    return true;
                }
            }
            profileMenuOpen = false;
            return true;
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && !profileMenuOpen && AlpakaStyle.inside(mouseX, mouseY, clipX, clipY, clipW, clipH)) scroll.by(-scrollY * 24);
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
            itemSearch.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown());
            itemCaretFrom = System.currentTimeMillis();
            return true;
        }
        if (searchFocused) {
            if (event.isEscape()) {
                searchFocused = false;
                return true;
            }
            if (event.isConfirmation()) {
                lookUp();
                return true;
            }
            search.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown());
            caretFrom = System.currentTimeMillis();
            return true;
        }
        if (event.isEscape() && profileMenuOpen) {
            profileMenuOpen = false;
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
        caretFrom = System.currentTimeMillis();
        return true;
    }
}
