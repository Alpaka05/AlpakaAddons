package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.MarketIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
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

    // ---- Pages ----------------------------------------------------------------------------------

    enum Page {
        OVERVIEW("Overview", Items.NETHER_STAR),
        SKILLS("Skills", Items.DIAMOND_SWORD),
        DUNGEONS("Dungeons", Items.WITHER_SKELETON_SKULL),
        SLAYERS("Slayers", Items.ROTTEN_FLESH),
        NETWORTH("Networth", Items.GOLD_INGOT),
        PETS("Pets", Items.BONE),
        COLLECTIONS("Collections", Items.PAINTING),
        MORE("More Stats", Items.COMPASS);

        final String title;
        final Item icon;

        Page(String title, Item icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    /** What the screen was last left on, so reopening it shows the same page and range. */
    private static Page lastPage = Page.OVERVIEW;
    private static ProfileChart.Range range = ProfileChart.Range.MONTH;
    /** Sessions of this game, by who was asked for, so reopening the dashboard is instant. */
    private static final Map<String, ProfileSession> SESSIONS = new HashMap<>();
    private static final Map<String, Long> SESSION_AGE = new HashMap<>();
    private static final long SESSION_KEEP_MS = 5 * 60_000L;

    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int SIDE_W = 116;
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

    private final List<Hit> hits = new ArrayList<>();
    private final List<Runnable> overlays = new ArrayList<>();
    private final Scroll scroll = new Scroll();
    private Page scrolledPage = null;
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

    private static ProfileSession session(String player) {
        Minecraft mc = Minecraft.getInstance();
        String own = mc.getUser().getName();
        boolean isOwn = player == null || player.isBlank() || player.equalsIgnoreCase(own);
        String query = isOwn ? mc.getUser().getProfileId().toString().replace("-", "") : player.trim();
        String key = query.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        ProfileSession cached = SESSIONS.get(key);
        if (cached != null && cached.state != ProfileSession.State.FAILED && now - SESSION_AGE.getOrDefault(key, 0L) < SESSION_KEEP_MS) {
            return cached;
        }
        ProfileSession fresh = new ProfileSession(query, isOwn);
        SESSIONS.put(key, fresh);
        SESSION_AGE.put(key, now);
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
        drawSidebar(graphics, x, y + HEADER_H, h - HEADER_H, deltaSec);
        ModernGuiUtils.drawRect(graphics, x + SIDE_W, y + HEADER_H + 6, 1, h - HEADER_H - 12, AlpakaStyle.LINE);

        clipX = x + SIDE_W + 1;
        clipY = y + HEADER_H;
        clipW = w - SIDE_W - 1;
        clipH = h - HEADER_H - 4;
        if (scrolledPage != page) {
            scrolledPage = page;
            scroll.current = scroll.target = 0;
        }
        graphics.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
        clipping = true;
        int top = clipY + PAD - scroll.offset();
        int bottom = drawContent(graphics, clipX + PAD + 2, top, clipW - 2 * PAD - 4, now);
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

    /** A mark after the profile's name for the special modes. */
    private static String modeMark(ProfileSession s) {
        for (ProfileSession.ProfileRef r : s.profiles) {
            if (!r.id().equals(s.profileId) || r.gameMode() == null) continue;
            return switch (r.gameMode()) {
                case "ironman" -> " (Ironman)";
                case "bingo" -> " (Bingo)";
                case "island" -> " (Stranded)";
                default -> "";
            };
        }
        return "";
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

    private void drawSidebar(GuiGraphicsExtractor graphics, int x, int y, int h, float deltaSec) {
        int rowX = x + 4;
        int rowW = SIDE_W - 8;
        int pages = Page.values().length;
        // On a small window the label and the footer give way first, then the rows get slimmer.
        int footerH = 30;
        boolean label = h >= 12 + pages * (SIDE_ROW_H + 1) + footerH + 12;
        boolean footer = h >= pages * 17 + footerH + 12;
        int rowH = Math.max(14, Math.min(SIDE_ROW_H, (h - 10 - (label ? 12 : 0) - (footer ? footerH : 0)) / pages - 1));
        int rowY = y + (label ? 8 : 5);
        if (label) {
            AlpakaStyle.groupLabel(graphics, this.font, "Profile", x + PAD, rowY);
            rowY += 12;
        }

        float targetY = rowY + page.ordinal() * (rowH + 1);
        if (Float.isNaN(indicatorY)) indicatorY = targetY;
        indicatorY = PloppAnimation.interpolate(indicatorY, targetY, deltaSec, 16.0f);
        int iy = Math.round(indicatorY);
        ModernGuiUtils.drawRoundedRect(graphics, rowX, iy, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_SELECTED);
        ModernGuiUtils.drawRoundedRect(graphics, rowX, iy + 4, 2, rowH - 8, 1, AlpakaStyle.accent());

        for (Page p : Page.values()) {
            boolean hover = AlpakaStyle.inside(mx, my, rowX, rowY, rowW, rowH) && !profileMenuOpen;
            if (hover && p != page) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            icon(graphics, p.icon, rowX + 5, rowY + (rowH - 12) / 2, 0.75f);
            text(graphics, p.title, rowX + 22, rowY + (rowH - 8) / 2, p == page || hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            Page target = p;
            hits.add(new Hit(rowX, rowY, rowW, rowH, () -> {
                page = target;
                lastPage = target;
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
                        + "Soopy (soopy.dev), pet heads from the NEU repository. Soopy works networth out differently, so its "
                        + "older networth is scaled to match Elite's."
                        + (skykings ? " With your key, SkyKings' daily history too" + (session.skykingsNote != null
                                && !session.skykingsNote.equals("loading") ? ": " + session.skykingsNote : ".")
                                : " A SkyKings API key in the config adds SkyKings' daily history.")
                        + " Click to open this profile on Elite.");
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
        return switch (page) {
            case OVERVIEW -> overview(graphics, d, x, y, w, now);
            case SKILLS -> skills(graphics, d, x, y, w);
            case DUNGEONS -> dungeons(graphics, d, x, y, w);
            case SLAYERS -> slayers(graphics, d, x, y, w);
            case NETWORTH -> networth(graphics, d, x, y, w);
            case PETS -> pets(graphics, d, x, y, w);
            case COLLECTIONS -> collections(graphics, d, x, y, w);
            case MORE -> more(graphics, d, x, y, w);
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
        List<Milestone> out = new ArrayList<>();
        long since = now - 30 * ProfileHistory.DAY;
        for (ProfileData.Level s : d.skills) {
            int max = s.maxLevel();
            levelUps(out, Metrics.SKILL + s.id(), s.name(), since, xp -> (int) Levels.skill(s.id(), xp, max));
        }
        levelUps(out, Metrics.CATACOMBS, "Catacombs", since, xp -> (int) Math.min(50, Levels.catacombs(xp)));
        for (ProfileData.Level c : d.classes) {
            levelUps(out, Metrics.CLASS + c.id(), c.name(), since, xp -> (int) Math.min(50, Levels.catacombs(xp)));
        }
        levelUps(out, Metrics.SB_LEVEL, "SkyBlock Level", since, level -> (int) Math.floor(level));
        out.sort((a, b) -> Long.compare(b.timeMs(), a.timeMs()));
        return out;
    }

    private void levelUps(List<Milestone> out, String metric, String name, long since, java.util.function.DoubleToIntFunction level) {
        List<ProfileHistory.Point> points = session.series(metric);
        int previous = Integer.MIN_VALUE;
        for (ProfileHistory.Point p : points) {
            int now = level.applyAsInt(p.value());
            if (previous != Integer.MIN_VALUE && now > previous && p.timeMs() >= since) {
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
            boolean clickable = t.metric() != null;
            boolean hover = clickable && hover(tx, ty, tw, th);
            boolean isSelected = clickable && t.metric().equals(selected != null ? selected : defaultMetric(page));
            ModernGuiUtils.drawRoundedRect(graphics, tx, ty, tw, th, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (isSelected) ModernGuiUtils.drawRoundedOutline(graphics, tx, ty, tw, th, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
            text(graphics, AlpakaStyle.ellipsize(this.font, t.label(), tw - 12), tx + 7, ty + 6, AlpakaStyle.TEXT_MUTED);
            graphics.pose().pushMatrix();
            graphics.pose().translate(tx + 7, ty + 18);
            graphics.pose().scale(1.35f, 1.35f);
            text(graphics, AlpakaStyle.ellipsize(this.font, t.value(), (int) ((tw - 14) / 1.35f)), 0, 0, AlpakaStyle.TEXT);
            graphics.pose().popMatrix();
            if (clickable) {
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
        // A key that doesn't work would otherwise only show as graphs that stay sparse.
        String problem = session.skykingsNote;
        if (problem != null && !problem.equals("loading")) {
            // Centred under the graph, between the dates at its two ends.
            String shown = AlpakaStyle.ellipsize(this.font, "SkyKings: " + problem, w / 2);
            text(graphics, shown, x + (w - textW(shown)) / 2, y + h - 13, AlpakaStyle.DANGER_SOFT);
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
            case MORE -> Metrics.SB_LEVEL;
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
        SKILL_ICONS.put("taming", Items.LEAD);
        SKILL_ICONS.put("hunting", Items.BOW);
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

        y = levelGrid(graphics, d.skills, Metrics.SKILL, x, y, w);
        String metric = metricFor(Page.SKILLS);
        return chartCard(graphics, metric, metricMaxLevel(d, metric), x, y + 6, w, 130);
    }

    /** Levels as rows of two: icon, name, level, the bar to the next one and the recent pace. */
    private int levelGrid(GuiGraphicsExtractor graphics, List<ProfileData.Level> levels, String prefix, int x, int y, int w) {
        int cols = w >= 380 ? 2 : 1;
        int gap = 6;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 34;
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
            if (icon != null) icon(graphics, icon, cx + 7, cy + 9, 1.0f);
            int tx = cx + 29;
            int tw = cw - 29 - 8;
            boolean isSelectedClass = prefix.equals(Metrics.CLASS) && l.id().equals(session.data.selectedClass);
            String name = l.name() + (isSelectedClass ? "  •" : "");
            text(graphics, name, tx, cy + 5, AlpakaStyle.TEXT_SOFT);
            String level = l.maxed() ? "MAX " + l.level() : l.level() + " / " + l.maxLevel();
            text(graphics, level, tx + tw - textW(level), cy + 5, l.maxed() ? ink(0xFFFFC94D) : AlpakaStyle.TEXT);
            bar(graphics, tx, cy + 17, tw, 3, l.progress(), l.maxed() ? ink(0xFFFFC94D) : AlpakaStyle.accent());
            String xp = l.maxed() ? Coins.compact(l.xp()) + " XP" : Coins.compact(l.xpCurrent()) + " / " + Coins.compact(l.xpForNext());
            text(graphics, xp, tx, cy + 23, AlpakaStyle.TEXT_MUTED);
            double rate = session.ratePerDay(metric, 7);
            if (rate > 0) {
                String pace = "+" + Coins.compact(rate) + "/day";
                text(graphics, pace, tx + tw - textW(pace), cy + 23, ink(ProfileChart.UP));
            }
            hit(cx, cy, cw, ch, () -> chosen.put(page, metric));
            if (hover) {
                String tipText = l.name() + ": " + Coins.full(Math.floor(l.xp())) + " XP in total.";
                if (!l.maxed()) tipText += " " + Coins.full(Math.ceil(l.xpForNext() - l.xpCurrent())) + " XP to level " + (l.level() + 1) + ".";
                if (isSelectedClass) tipText += " Your selected class.";
                tip(metric, tipText + " Click for its graph.");
            }
        }
        int rows = (levels.size() + cols - 1) / cols;
        return y + rows * (ch + 4) - 4;
    }

    // ---- Dungeons -------------------------------------------------------------------------------

    private int dungeons(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.CATACOMBS, "Catacombs", String.format(Locale.US, "%.2f", Levels.catacombs(d.catacombs.xp())), null));
        tiles.add(new Tile(null, "Class Average", String.format(Locale.US, "%.2f", d.classAverage), null));
        tiles.add(new Tile(Metrics.RUNS, "Total Runs", Coins.full(d.totalRuns), null));
        tiles.add(new Tile(null, "Selected Class", d.selectedClass == null ? "—" : ProfileData.title(d.selectedClass), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        y = levelGrid(graphics, d.classes, Metrics.CLASS, x, y, w) + 6;

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
        int rowH = 36;
        for (ProfileData.Slayer s : d.slayers) {
            String metric = Metrics.SLAYER + s.id();
            boolean hover = hover(x, y, w, rowH);
            ModernGuiUtils.drawRoundedRect(graphics, x, y, w, rowH, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (metric.equals(selected)) ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, rowH, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.8f));
            Item icon = SKILL_ICONS.get(s.id());
            if (icon != null) icon(graphics, icon, x + 8, y + 10, 1.0f);
            int tx = x + 31;
            text(graphics, s.name(), tx, y + 6, AlpakaStyle.TEXT_SOFT);
            String level = s.maxed() ? "MAX " + s.level() : "Level " + s.level();
            int levelX = tx + textW(s.name()) + 8;
            text(graphics, level, levelX, y + 6, s.maxed() ? ink(0xFFFFC94D) : AlpakaStyle.TEXT);
            int right = x + w - 8;
            String xp = s.maxed() ? Coins.full(s.xp()) + " XP" : Coins.compact(s.xpForNext()) + " XP to " + (s.level() + 1);
            if (levelX + textW(level) + 12 + textW(xp) < right) text(graphics, xp, right - textW(xp), y + 6, AlpakaStyle.TEXT_MUTED);
            bar(graphics, tx, y + 18, w - 31 - 8, 3, s.progress(), s.maxed() ? ink(0xFFFFC94D) : AlpakaStyle.accent());

            // The kills of each tier, as far as the boss has tiers, and the recent pace.
            int tiers = s.id().equals("zombie") || s.id().equals("vampire") || s.kills()[4] > 0 ? 5 : 4;
            StringBuilder killText = new StringBuilder();
            for (int t = 0; t < tiers; t++) {
                if (t > 0) killText.append("   ");
                killText.append("T").append(t + 1).append(" ").append(Coins.compact(s.kills()[t]));
            }
            String killsLine = killText.toString();
            text(graphics, killsLine, tx, y + 25, AlpakaStyle.TEXT_MUTED);
            double rate = session.ratePerDay(metric, 7);
            if (rate > 0) {
                String pace = "+" + Coins.compact(rate) + " XP/day";
                if (tx + textW(killsLine) + 12 + textW(pace) < right) text(graphics, pace, right - textW(pace), y + 25, ink(ProfileChart.UP));
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
        tiles.add(new Tile(Metrics.LIQUID, "Liquid", Coins.compact(d.liquidNetworth), "What it would sell for right away."));
        tiles.add(new Tile(Metrics.PURSE, "Purse", Coins.compact(d.purse), null));
        tiles.add(new Tile(Metrics.BANK, "Bank", Coins.compact(d.bank + d.personalBank), null));
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
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.PETS, "Pets", Coins.full(d.pets.size()), null));
        tiles.add(new Tile(Metrics.PETS_MAXED, "Max Level", Coins.full(d.petsMaxed), null));
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

        int cols = Math.max(2, w / 150);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 30;
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
            if (head != null) {
                stackIcon(graphics, head, cx + 5, cy + 7, 1.0f);
            } else {
                ModernGuiUtils.drawRoundedRect(graphics, cx + 6, cy + 8, 14, 14, 3, AlpakaStyle.ROW_SELECTED);
            }
            int tx = cx + 25;
            int tw = cw - 25 - 6;
            String level = "Lvl " + p.level();
            text(graphics, level, tx + tw - textW(level), cy + 5, p.level() >= p.maxLevel() ? ink(0xFFFFC94D) : AlpakaStyle.TEXT_SOFT);
            text(graphics, AlpakaStyle.ellipsize(this.font, p.name(), tw - 6 - textW(level)), tx, cy + 5, ink(ProfileData.rarityColor(p.tier())));
            String held = p.active() ? "Active" + (p.heldItem() == null ? "" : " · " + heldItem(p.heldItem())) : p.heldItem() == null ? "" : heldItem(p.heldItem());
            text(graphics, AlpakaStyle.ellipsize(this.font, held, tw), tx, cy + 17, p.active() ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
            if (p.level() < p.maxLevel()) bar(graphics, tx, cy + ch - 3, tw, 1, p.progress(), MarketIcons.fade(AlpakaStyle.accent(), 0.7f));
            if (hover) {
                tip(p, ProfileData.title(p.tier()) + " " + p.name() + ", level " + p.level() + " of " + p.maxLevel()
                        + (p.heldItem() != null ? ", holding " + heldItem(p.heldItem()) : "")
                        + (p.skin() != null ? ", skin " + ProfileData.title(p.skin()) : "") + ".");
            }
        }
        int rows = (d.pets.size() + cols - 1) / cols;
        return y + rows * (ch + gap) - gap;
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
        tiles.add(new Tile(Metrics.MINIONS, "Unique Minions", Coins.full(Json.num(raw, "minionSummary.uniqueTiersCrafted", 0)), null));
        tiles.add(new Tile(null, "Minion Slots", (int) Json.num(raw, "minionSummary.currentSlots", 0) + " / " + (int) Json.num(raw, "minionSummary.maxSlots", 0), null));
        double toNext = Json.num(raw, "minionSummary.uniqueTiersToNextSlot", Double.NaN);
        tiles.add(new Tile(null, "Next Slot In", Double.isNaN(toNext) || toNext <= 0 ? "—" : (int) toNext + " crafts", "unique minion tiers"));
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
        int cols = Math.max(2, w / 160);
        int gap = 4;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 30;
        for (var e : byCategory.entrySet()) {
            List<ProfileData.Collection> list = e.getValue();
            int done = 0;
            for (ProfileData.Collection c : list) if (c.maxed()) done++;
            text(graphics, e.getKey(), x + 2, y + 2, AlpakaStyle.TEXT);
            String count = done + " / " + list.size() + " maxed";
            text(graphics, count, x + 2 + textW(e.getKey()) + 8, y + 2, AlpakaStyle.TEXT_MUTED);
            y += 14;
            for (int i = 0; i < list.size(); i++) {
                ProfileData.Collection c = list.get(i);
                int cx = x + (i % cols) * (cw + gap);
                int cy = y + (i / cols) * (ch + gap);
                if (cy > clipY + clipH + 40 || cy + ch < clipY - 40) continue;
                boolean hover = hover(cx, cy, cw, ch);
                ModernGuiUtils.drawRoundedRect(graphics, cx, cy, cw, ch, AlpakaStyle.CARD_RADIUS, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
                String tier = c.maxed() ? "MAX" : c.tier() == 0 ? "—" : roman(c.tier());
                int tierColor = c.maxed() ? ink(0xFFFFC94D) : c.tier() == 0 ? AlpakaStyle.DIVIDER : AlpakaStyle.TEXT;
                text(graphics, tier, cx + cw - 6 - textW(tier), cy + 5, tierColor);
                text(graphics, AlpakaStyle.ellipsize(this.font, c.name() == null ? c.id() : c.name(), cw - 18 - textW(tier)), cx + 6, cy + 5,
                        c.amount() > 0 ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
                double progress = c.maxed() ? 1 : c.nextAt() > c.prevAt() ? (c.amount() - c.prevAt()) / (double) (c.nextAt() - c.prevAt()) : 0;
                bar(graphics, cx + 6, cy + 17, cw - 12, 2, progress, c.maxed() ? ink(0xFFFFC94D) : AlpakaStyle.accent());
                String amount = Coins.compact(c.amount()) + (c.maxed() || c.nextAt() <= 0 ? "" : " / " + Coins.compact(c.nextAt()));
                text(graphics, amount, cx + 6, cy + 21, AlpakaStyle.TEXT_MUTED);
                if (hover) {
                    tip(c, (c.name() == null ? c.id() : c.name()) + ": " + Coins.full(c.amount()) + " collected, tier " + c.tier()
                            + " of " + c.maxTier() + (c.maxed() ? "." : ". " + Coins.full(c.nextAt() - c.amount()) + " more for the next tier."));
                }
            }
            y += ((list.size() + cols - 1) / cols) * (ch + gap) + 6;
        }
        return y;
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
                "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV", "XXV"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    // ---- More stats -----------------------------------------------------------------------------

    private record Card(String title, List<String[]> rows) {}

    private int more(GuiGraphicsExtractor graphics, ProfileData d, int x, int y, int w) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile(Metrics.SB_LEVEL, "SkyBlock Level", String.format(Locale.US, "%.2f", d.skyblockLevel), null));
        tiles.add(new Tile(Metrics.FAIRY_SOULS, "Fairy Souls", Coins.full(Json.num(d.raw, "stats.playerStats.fairySouls.collected", 0)), null));
        tiles.add(new Tile(Metrics.BESTIARY, "Bestiary", "Milestone " + d.bestiaryMilestone, null));
        tiles.add(new Tile(Metrics.KUUDRA, "Kuudra Runs", Coins.full(Json.num(d.raw, "stats.crimsonIsle.totalKuudraRuns", 0)), null));
        y = tiles(graphics, tiles, x, y, w, 4) + 6;
        y = chartCard(graphics, metricFor(Page.MORE), 0, x, y, w, 110) + 6;

        List<Card> cards = moreCards(d);
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

    private static List<Card> moreCards(ProfileData d) {
        JsonElement r = d.raw;
        List<Card> cards = new ArrayList<>();

        List<String[]> sb = new ArrayList<>();
        double firstJoin = Json.num(r, "stats.miscStats.firstJoin");
        if (firstJoin > 0) {
            sb.add(row("Playing Since", DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
                    .format(Instant.ofEpochMilli((long) firstJoin).atZone(ZoneId.systemDefault()))));
        }
        count(sb, "Kills", Json.num(r, "stats.playerStats.killsDeaths.totalKills"));
        count(sb, "Deaths", Json.num(r, "stats.playerStats.killsDeaths.totalDeaths"));
        count(sb, "Bestiary Kills", Json.num(r, "stats.bestiaryDerivations.totalKills"));
        count(sb, "Soulflow", Json.num(r, "stats.miscStats.soulflow"));
        count(sb, "Pelts", Json.num(r, "stats.miscStats.pelts"));
        add(cards, "SkyBlock", sb);

        List<String[]> coins = new ArrayList<>();
        coins(coins, "Purse", d.purse);
        coins(coins, "Bank", d.bank);
        if (d.personalBank > 0) coins(coins, "Personal Bank", d.personalBank);
        if (d.liquidNetworth > 0) coins(coins, "Liquid Networth", d.liquidNetworth);
        add(cards, "Coins", coins);

        List<String[]> mining = new ArrayList<>();
        count(mining, "Mithril Powder", Json.num(r, "stats.mining.powder.mithril.available"));
        count(mining, "Gemstone Powder", Json.num(r, "stats.mining.powder.gemstone.available"));
        count(mining, "Glacite Powder", Json.num(r, "stats.mining.powder.glacite.available"));
        count(mining, "Nucleus Runs", Json.num(r, "stats.mining.crystalNucleusRuns"));
        count(mining, "Commission Milestone", Json.num(r, "stats.mining.commissionMilestone"));
        int fossils = Json.arr(r, "stats.mining.glacite.fossilsDonated").size();
        if (fossils > 0) mining.add(row("Fossils Donated", String.valueOf(fossils)));
        add(cards, "Mining", mining);

        List<String[]> crimson = new ArrayList<>();
        String faction = Json.str(r, "stats.crimsonIsle.selectedFaction");
        if (faction != null) crimson.add(row("Faction", ProfileData.title(faction)));
        count(crimson, "Mage Reputation", Json.num(r, "stats.crimsonIsle.magesReputation"));
        count(crimson, "Barbarian Reputation", Json.num(r, "stats.crimsonIsle.barbariansReputation"));
        String[][] tiers = {{"none", "Basic"}, {"hot", "Hot"}, {"burning", "Burning"}, {"fiery", "Fiery"}, {"infernal", "Infernal"}};
        for (String[] t : tiers) {
            double n = Json.num(r, "stats.crimsonIsle.kuudraCompletions." + t[0]);
            if (n > 0) crimson.add(row("Kuudra " + t[1], Coins.full(n)));
        }
        count(crimson, "Dojo Points", Json.num(r, "stats.crimsonIsle.dojoPointsTotal"));
        add(cards, "Crimson Isle", crimson);

        List<String[]> fishing = new ArrayList<>();
        count(fishing, "Trophy Fish", Json.num(r, "stats.fishing.totalTrophyFishCaught"));
        String rank = Json.str(r, "stats.fishing.trophyRank");
        if (rank != null) fishing.add(row("Trophy Rank", ProfileData.title(rank)));
        count(fishing, "Sea Creatures", Json.num(r, "stats.fishing.seaCreatureKills"));
        count(fishing, "Treasures", Json.num(r, "stats.fishing.treasuresCaught"));
        count(fishing, "Items Fished", Json.num(r, "stats.fishing.itemsFished"));
        add(cards, "Fishing", fishing);

        List<String[]> garden = new ArrayList<>();
        count(garden, "Garden Level", Json.num(r, "garden.gardenLevel"));
        count(garden, "Unique Visitors", Json.num(r, "garden.uniqueVisitors"));
        count(garden, "Visitors Served", Json.num(r, "garden.completedVisitors"));
        count(garden, "Copper", Json.num(r, "unparsed.copper"));
        if (d.farmingWeight > 0) garden.add(row("Farming Weight", Coins.full(Math.round(d.farmingWeight * 10) / 10.0)));
        double gold = Json.num(r, "jacob.earnedMedals.gold", 0);
        double platinum = Json.num(r, "jacob.earnedMedals.platinum", 0);
        double diamond = Json.num(r, "jacob.earnedMedals.diamond", 0);
        if (gold + platinum + diamond > 0) {
            garden.add(row("Contest Medals", (int) gold + " Gold · " + (int) platinum + " Plat · " + (int) diamond + " Dia"));
        }
        add(cards, "Garden", garden);

        List<String[]> chocolate = new ArrayList<>();
        count(chocolate, "Prestige", Json.num(r, "chocolateFactory.prestige"));
        double total = Json.num(r, "chocolateFactory.totalChocolate");
        if (total > 0) chocolate.add(row("Chocolate Produced", Coins.compact(total)));
        double unique = Json.sum(r, "chocolateFactory.uniqueRabbits");
        if (unique > 0) chocolate.add(row("Unique Rabbits", Coins.full(unique)));
        add(cards, "Chocolate Factory", chocolate);

        List<String[]> rift = new ArrayList<>();
        double souls = Json.num(r, "stats.rift.enigmaSoulsFound");
        double soulsTotal = Json.num(r, "stats.rift.enigmaSoulsTotal");
        if (souls >= 0 && soulsTotal > 0) rift.add(row("Enigma Souls", (int) souls + " / " + (int) soulsTotal));
        count(rift, "Motes", Json.num(r, "stats.rift.motesPurse"));
        int cats = Json.arr(r, "stats.rift.foundCats").size();
        if (cats > 0) rift.add(row("Montezuma's Cats", cats + " / 9"));
        add(cards, "Rift", rift);

        List<String[]> essence = new ArrayList<>();
        for (var e : Json.obj(r, "stats.miscStats.essence").entrySet()) {
            if (e.getValue().isJsonPrimitive()) count(essence, ProfileData.title(e.getKey()), e.getValue().getAsDouble());
        }
        add(cards, "Essence", essence);
        return cards;
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
            int top = Math.max(y, clipY);
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
        profileMenuOpen = false;
        faceFor = null;
        chosen.clear();
        breakdownFor = null;
        collectionsData = null;
    }

    // ---- Screenshot tour ------------------------------------------------------------------------

    /** For the screenshot tour: the dashboard on a player and page, already open rather than popping open. */
    public static ProfileDashboardScreen devOpen(String player, String page) {
        ProfileDashboardScreen screen = new ProfileDashboardScreen(session(player), null);
        screen.page = Page.valueOf(page);
        screen.openTimeMs = 1L;
        return screen;
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

    private boolean devScrollToEnd;

    /** For the screenshot tour: whether the page has what it loads. */
    public boolean devReady() {
        ProfileSession s = session;
        if (s.state == ProfileSession.State.LOADING) return false;
        if (s.data == null) return true;
        if (page == Page.NETWORTH) return s.networthState == ProfileSession.State.READY || s.networthState == ProfileSession.State.FAILED;
        if (page == Page.COLLECTIONS) return s.collectionsState == ProfileSession.State.READY || s.collectionsState == ProfileSession.State.FAILED;
        return true;
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        searchFocused = false;
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
        if (event.isEscape() || (this.minecraft != null && this.minecraft.options.keyInventory.matches(event))) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused) return false;
        for (char c : Character.toChars(event.codepoint())) {
            if (Character.isLetterOrDigit(c) && c < 128 || c == '_') search.insert(c);
        }
        caretFrom = System.currentTimeMillis();
        return true;
    }
}
