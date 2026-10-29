package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.GuiItemFade;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.wheel.WheelMesh;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

/**
 * The Auction House and the Bazaar, redrawn in the config menu's design: categories with their own
 * icons down the side, the search and the controls in the header and footer, and the auctions or
 * products as cards that show their price, time and seller at a glance.
 *
 * <h2>Only the look changes</h2>
 *
 * This is still Hypixel's chest. The screen is a vanilla chest screen whose drawing was replaced;
 * the menu, its slots and closing it are vanilla's own. Every card, tile and button stands for one
 * slot of that chest, and a click on it is one click on that slot through {@link Outbound#menuClick}
 * - the same click, with the same button, that clicking the slot in the vanilla chest sends. There
 * are no extra clicks, no clicks of the mod's own, no drags, number-key swaps or drops: mouse
 * events that are not a plain click on a drawn control are swallowed, so nothing reaches the
 * invisible vanilla slots underneath. Keys close the menu, and with Enable Hotkeys on press its
 * buttons: 1 to 9 the numbered auctions, products and actions, Shift with a number a category,
 * the arrows the page, / or Ctrl+F the search, Backspace the way back.
 *
 * "Classic View" in the footer switches back to the vanilla chest for that market until the game
 * restarts, in case Hypixel shows something this screen does not lay out well.
 */
public class MarketScreen extends ContainerScreen {

    // ---- Layout, in GUI pixels ------------------------------------------------------------------

    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int FOOTER_H = 26;
    private static final int SIDE_ROW_H = 18;
    private static final int FILTER_ROW_H = 24;
    private static final int GROUP_LABEL_H = 14;
    private static final int CARD_H = 38;
    private static final int CARD_MIN_W = 116;
    private static final int GAP = 4;
    private static final int TILE_MIN_W = 96;
    private static final int TILE_MAX_W = 136;
    private static final int TILE_H = 88;
    private static final int CELL = 18;
    private static final int LIST_ROW_H = 17;
    private static final int LIST_HEADER_H = 16;

    // ---- Colors (the rest are AlpakaStyle's) ----------------------------------------------------

    /** Coins, in Hypixel's gold. */
    private static final int COIN = 0xFFFFAA00;
    private static final int GREEN = 0xFF55FF55;
    private static final int AQUA = 0xFF55FFFF;
    private static final int RED = 0xFFFF5555;

    // ---- The markets' faces ----------------------------------------------------------------------

    /** The skin of the Auction Master, the Hub's auctioneer, as Hypixel's NPC wears it. */
    private static final String AUCTION_MASTER_SKIN = "ewogICJ0aW1lc3RhbXAiIDogMTU5NzMwNTI3MzM5MiwKICAicHJvZmlsZUlkIiA6ICI0MWQzYWJjMmQ3NDk0MDBjOTA5MGQ1"
            + "NDM0ZDAzODMxYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZWdha2xvb24iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVl"
            + "LAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3Jh"
            + "ZnQubmV0L3RleHR1cmUvMzE5NzFkNzk2ZGM0ZGM5YWYzOGNjOTcwNjc5NjNkZTRlZDJiZjk5OTIyNDI1NzQxNmRmYjg0MTQ4"
            + "OGZkOWUwZCIKICAgIH0KICB9Cn0=";
    /** The skin of the Hub's Bazaar NPC. */
    private static final String BAZAAR_SKIN = "eyJ0aW1lc3RhbXAiOjE1NzMyMjM2NDc4NDcsInByb2ZpbGVJZCI6IjQxZDNhYmMyZDc0OTQwMGM5MDkwZDU0MzRkMDM4MzFi"
            + "IiwicHJvZmlsZU5hbWUiOiJNZWdha2xvb24iLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6"
            + "eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2MyMzJlMzgyMDg5NzQyOTE1NzYxOWIwZWUw"
            + "OTlmZWMwNjI4ZjYwMmZmZjEyYjY5NWRlNTRhZWYxMWQ5MjNhZDcifX19";
    private static net.minecraft.world.item.component.ResolvableProfile auctionMaster;
    private static net.minecraft.world.item.component.ResolvableProfile bazaarNpc;

    /** The NPC who runs this market, whose face the header shows in place of an icon. */
    private static net.minecraft.world.item.component.ResolvableProfile npc(MarketKind kind) {
        if (kind == MarketKind.BAZAAR) {
            if (bazaarNpc == null) bazaarNpc = ItemIcons.profile(BAZAAR_SKIN);
            return bazaarNpc;
        }
        if (auctionMaster == null) auctionMaster = ItemIcons.profile(AUCTION_MASTER_SKIN);
        return auctionMaster;
    }

    // ---- State that outlives one menu -----------------------------------------------------------

    /** Markets switched back to the vanilla chest by the player, for the rest of the session. */
    private static final EnumSet<MarketKind> CLASSIC = EnumSet.noneOf(MarketKind.class);
    /** Whether the inventory drawer is open; kept as the player moves from page to page. */
    private static boolean inventoryOpen = false;
    private static boolean historyOpen = false;
    private static final Map<String, Double> LIST_POSITIONS = new java.util.LinkedHashMap<>();

    // ---- State ----------------------------------------------------------------------------------

    public final MarketKind kind;
    private final String plainTitle;
    private final int rows;

    private long openTimeMs = 0L;
    /** Opened in place of another market page: no opening pop, the same sidebar where it was. */
    private boolean continued = false;
    private long lastFrameMs = System.currentTimeMillis();

    private MarketMenu model;
    private List<MarketMenu.Slot> slots = List.of();
    private long modelSignature = Long.MIN_VALUE;
    /** When the content last appeared, for the cards' staggered entrance. */
    private long contentSinceMs = 0L;
    private final Map<ItemStack, Integer> rarityCache = new IdentityHashMap<>();

    private final Map<Integer, Float> hoverProgress = new HashMap<>();
    /** The selected category's marker, gliding to its row. */
    private float indicatorY = Float.NaN;
    private int indicatorColor = 0;
    private final Scroll contentScroll = new Scroll();
    private final Scroll sideScroll = new Scroll();
    private String listPositionKey;
    private ItemStack lastTooltipStack = ItemStack.EMPTY;
    private long itemTipSince;
    private boolean draggingScroll;
    private int scrollTrackY, scrollTrackH, scrollThumbH;

    private int pressedSlot = -1;
    private long pressedAtMs = 0L;
    /**
     * What the clicked slot held. Hypixel answers a click on a menu item by putting the item on the
     * cursor for a moment; the slot is empty until the next page arrives or the item goes back. The
     * card stays as it was meanwhile, rather than leaving a gap in the grid.
     */
    private ItemStack pressedStack = ItemStack.EMPTY;
    private static final long KEEP_PRESSED_MS = 1500L;
    private static final long PRESS_MS = 240L;

    /** The item under the mouse this frame, whose vanilla tooltip is shown. */
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private String hoveredTip = null;
    private Object hoveredTipKey = null;
    private Object lastTipKey = null;
    private long tipSince = 0L;

    /** Clickable areas of the last frame, front-most last. */
    private final List<Hit> hits = new ArrayList<>();

    // Areas of the last frame, for routing the mouse wheel.
    private int sideX, sideW, contentX, contentY, contentW, contentH;
    /** The inventory drawer, while open: what is under it takes no mouse. */
    private int drawerX, drawerY, drawerW, drawerH;
    /** The slots the number keys press in the last frame: key 1 is the first. */
    private final List<Integer> keyActions = new ArrayList<>();
    /** The categories Shift and a number key pick in the last frame, top to bottom. */
    private final List<Integer> keyCategories = new ArrayList<>();
    /** Create Auction in the sidebar in the last frame, which C presses; null without one. */
    private Integer keyCreateAuction;
    /** The Auction House's Reset Settings in the last frame, which R presses; null without one. */
    private Integer keyResetSettings;
    /**
     * Number keys held down, so holding one presses its button once and not again with every
     * repeat. Shared by all market screens: the press may open the next page before the release.
     */
    private static final Set<Integer> HELD_KEYS = new HashSet<>();

    private interface Action {
        void run(int button, boolean shift);
    }

    private record Hit(int x, int y, int w, int h, Action action, boolean sound) {
        boolean contains(double mx, double my) {
            return AlpakaStyle.inside(mx, my, x, y, w, h);
        }
    }

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

    public MarketScreen(ChestMenu menu, Inventory inventory, Component title, MarketKind kind) {
        super(menu, inventory, title);
        this.kind = kind;
        this.plainTitle = SkyblockUtils.cleanColor(title.getString());
        this.rows = menu.getRowCount();
    }

    /**
     * Opens the market screen for a chest Hypixel is opening, in place of the vanilla one, exactly
     * the way vanilla opens its own: the same menu made from the packet, set as the player's open
     * menu. Returns false, leaving it to vanilla, for any chest that is not a market's.
     */
    public static boolean tryOpen(MenuType<?> type, Minecraft mc, int containerId, Component title) {
        if (mc.player == null || !isChest(type)) return false;
        String plain = SkyblockUtils.cleanColor(title.getString());
        MarketKind kind = MarketTitles.classify(plain, Markets.navigationContext(mc.gui.screen()));
        if (kind != null) Markets.menuOpened(plain);
        if (kind == null || !Markets.skinned(kind)) return false;
        if (!(type.create(containerId, mc.player.getInventory()) instanceof ChestMenu menu)) return false;

        MarketScreen screen = new MarketScreen(menu, mc.player.getInventory(), title, kind);
        if (mc.gui.screen() instanceof MarketScreen previous && previous.kind == kind) {
            screen.continueFrom(previous);
        } else if (Markets.recentContext(mc.gui.screen()) == kind) {
            screen.continued = true;
        }
        mc.player.containerMenu = menu;
        mc.gui.setScreen(screen);
        return true;
    }

    private static boolean isChest(MenuType<?> type) {
        return type == MenuType.GENERIC_9x1 || type == MenuType.GENERIC_9x2 || type == MenuType.GENERIC_9x3
                || type == MenuType.GENERIC_9x4 || type == MenuType.GENERIC_9x5 || type == MenuType.GENERIC_9x6;
    }

    /** Takes over from the page it replaces, so turning a page doesn't look like a new window. */
    private void continueFrom(MarketScreen previous) {
        continued = true;
        indicatorY = previous.indicatorY;
        indicatorColor = previous.indicatorColor;
        sideScroll.current = previous.sideScroll.current;
        sideScroll.target = previous.sideScroll.target;
    }

    /** Whether Hypixel's own chest shows instead of this screen's layout ("Classic" in the footer). */
    public boolean classic() {
        return CLASSIC.contains(kind);
    }

    @Override
    protected void init() {
        super.init();
        if (openTimeMs == 0L) openTimeMs = continued ? 1L : System.currentTimeMillis();
        lastFrameMs = System.currentTimeMillis();
        // The drawer opens by itself where an item from the inventory is wanted.
        if (plainTitle.startsWith("Create") && kind == MarketKind.AUCTION_HOUSE) inventoryOpen = true;
    }

    // ---- The menu, taken apart ------------------------------------------------------------------

    /**
     * The chest as a {@link MarketMenu}, parsed again only when an item changed. A new item from
     * the server is always a new stack, so the stacks' identities and counts are enough to tell.
     */
    private MarketMenu model() {
        long signature = 17;
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = stack(i);
            signature = signature * 31 + System.identityHashCode(stack);
            signature = signature * 31 + stack.getCount();
        }
        if (model != null && signature == modelSignature) return model;

        boolean wasEmpty = model == null || model.empty;
        List<MarketMenu.Slot> parsed = new ArrayList<>(rows * 9);
        for (int i = 0; i < rows * 9; i++) parsed.add(slotOf(i, stack(i)));
        slots = parsed;
        model = MarketMenu.parse(kind, plainTitle, rows, parsed);
        modelSignature = signature;
        rarityCache.clear();
        if (wasEmpty && !model.empty) contentSinceMs = System.currentTimeMillis();
        return model;
    }

    static MarketMenu.Slot slotOf(int index, ItemStack stack) {
        if (stack.isEmpty()) return new MarketMenu.Slot(index, "air", "", List.of(), false);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String name = SkyblockUtils.cleanColor(stack.getHoverName().getString());
        List<String> lore = new ArrayList<>();
        ItemLore itemLore = stack.get(DataComponents.LORE);
        if (itemLore != null) {
            for (Component line : itemLore.lines()) lore.add(SkyblockUtils.cleanColor(line.getString()));
        }
        return new MarketMenu.Slot(index, id, name, lore, stack.hasFoil());
    }

    private ItemStack stack(int slot) {
        if (slot < 0 || slot >= menu.slots.size()) return ItemStack.EMPTY;
        ItemStack stack = menu.getSlot(slot).getItem();
        if (stack.isEmpty() && slot == pressedSlot && slot < rows * 9 && !pressedStack.isEmpty()
                && System.currentTimeMillis() - pressedAtMs < KEEP_PRESSED_MS) {
            return pressedStack;
        }
        return stack;
    }

    private MarketMenu.Slot plain(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : new MarketMenu.Slot(slot, "air", "", List.of(), false);
    }

    /** One click on a slot, as vanilla sends it for the same press: a shift click is a quick move there too. */
    private void click(int slot, int button, boolean shift) {
        click(slot, button, shift, Outbound.Cause.MENU_CLICK);
    }

    private void click(int slot, int button, boolean shift, Outbound.Cause cause) {
        ContainerInput input = shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        ItemStack before = stack(slot).copy();
        if (Outbound.menuClick(menu, slot, button, input, cause)) {
            if (model != null && model.hero != null && slot != model.hero && model.content.contains(slot)) {
                Markets.rememberTrade(kind, stack(model.hero), plain(slot).name());
            }
            pressedSlot = slot;
            pressedAtMs = System.currentTimeMillis();
            pressedStack = before;
        }
    }

    // ---- Geometry -------------------------------------------------------------------------------

    private int panelW() {
        MarketMenu m = model();
        int desired = confirmation(m) ? 330
                : m.layout == MarketMenu.Layout.TILES && m.hero == null && !m.hasSidebar() ? 360
                : Math.max(360, Math.min(560, Math.round(this.width * 0.8f)));
        return Math.max(240, Math.min(desired, this.width - 24));
    }

    private int panelH() {
        MarketMenu m = model();
        int desired = confirmation(m) ? HEADER_H + FOOTER_H + 50 + confirmationLines(m).size() * 12
                : m.layout == MarketMenu.Layout.TILES && m.hero == null && !m.hasSidebar() ? 184
                : Math.max(m.hero == null ? 200 : 212, Math.min(320, Math.round(this.height * 0.84f)));
        if (inventoryOpen && !confirmation(m)) desired += 106;
        return Math.max(confirmation(m) ? 128 : 150, Math.min(desired, this.height - 28));
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
        if (classic()) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        AlpakaStyle.backdrop(graphics, this.width, this.height, 0.55f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        keyActions.clear();
        keyCategories.clear();
        keyCreateAuction = null;
        keyResetSettings = null;
        if (classic()) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            drawClassicToggle(graphics, mouseX, mouseY);
            return;
        }
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        hoveredStack = ItemStack.EMPTY;
        hoveredTipKey = null;
        hoveredTip = null;

        MarketMenu m = model();
        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();

        // The drawer is placed first: what lies under it must not light up or take clicks.
        int footerY = y + h - FOOTER_H;
        drawerW = 9 * CELL + 12;
        drawerH = 18 + 4 * CELL + 4 + 8;
        drawerX = x + (w - drawerW) / 2;
        drawerY = footerY - 4 - drawerH;
        boolean showInventory = inventoryOpen && !confirmation(m);
        boolean underDrawer = showInventory && AlpakaStyle.inside(mouseX, mouseY, drawerX, drawerY, drawerW, drawerH);
        int mx = underDrawer ? -10000 : mouseX;
        int my = underDrawer ? -10000 : mouseY;

        boolean scaling = !continued && AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);

        AlpakaStyle.panel(graphics, x, y, w, h);

        sideW = m.hasSidebar() ? Math.min(126, Math.max(108, w / 3)) : 0;
        sideX = x;
        contentX = x + sideW + (sideW > 0 ? 1 : 0);
        contentY = y + HEADER_H;
        contentW = x + w - contentX;
        contentH = h - HEADER_H - FOOTER_H;
        if (showInventory) contentH = Math.max(48, contentH - drawerH - 8);

        drawHeader(graphics, m, mx, my, x, y, w);
        AlpakaStyle.headerLine(graphics, x, y, w);
        if (sideW > 0) {
            int sideHits = hits.size();
            drawSidebar(graphics, m, mx, my, x, contentY, sideW, contentH, deltaSec);
            for (int i = hits.size() - 1; i >= sideHits; i--) {
                Hit hit = hits.get(i);
                int bottom = Math.min(contentY + contentH, hit.y() + hit.h());
                if (bottom <= hit.y()) hits.remove(i);
                else hits.set(i, new Hit(hit.x(), hit.y(), hit.w(), bottom - hit.y(), hit.action(), hit.sound()));
            }
            ModernGuiUtils.drawRect(graphics, x + sideW, contentY + 6, 1, contentH - 12, AlpakaStyle.LINE);
        }
        drawContent(graphics, m, mx, my, deltaSec, now);
        drawFooter(graphics, m, mx, my, x, footerY, w);
        if (showInventory) drawDrawer(graphics, mouseX, mouseY);

        if (hoveredTipKey != lastTipKey) {
            lastTipKey = hoveredTipKey;
            tipSince = now;
        }
        if (hoveredTip != null && now - tipSince >= 250) {
            AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, hoveredTip);
        }

        AlpakaStyle.endOpen(graphics, scaling);

        // The item on the cursor, and the hovered item's own tooltip, as vanilla draws them.
        extractCarriedItem(graphics, mouseX, mouseY);
        if (hoveredStack != lastTooltipStack) {
            lastTooltipStack = hoveredStack;
            itemTipSince = now;
        }
        if (!hoveredStack.isEmpty() && now - itemTipSince >= 180 && menu.getCarried().isEmpty()
                && !net.alpaka.addons.features.itemvalue.ItemValuePopup.hidesTooltipOf(this, hoveredStack)) {
            graphics.setTooltipForNextFrame(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my, int x, int y, int w) {
        int accent = AlpakaStyle.accent();
        // The NPC who runs the market, his face where a window has its icon: flat, as the tab list
        // shows players, which reads better this small than a head turned away.
        int chip = 20;
        int chipX = x + PAD;
        int chipY = y + (HEADER_H - chip) / 2;
        net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(graphics, npc(kind), chipX, chipY, chip);

        // From the right: close, back, then the search, which leaves the rest to the title.
        int right = x + w - PAD;
        int button = 16;
        int by = y + (HEADER_H - button) / 2;
        int closeX = right - button;
        boolean hoverClose = AlpakaStyle.inside(mx, my, closeX, by, button, button);
        glyphButton(graphics, closeX, by, button, MarketIcons.Glyph.CLOSE, hoverClose, AlpakaStyle.DANGER);
        hits.add(new Hit(closeX, by, button, button, (b, s) -> onClose(), true));
        if (hoverClose) tip("close", "Close");
        right = closeX - 4;

        Integer back = m.controls.get(MarketMenu.Control.BACK);
        if (back != null) {
            int backX = right - button;
            boolean hoverBack = AlpakaStyle.inside(mx, my, backX, by, button, button);
            glyphButton(graphics, backX, by, button, MarketIcons.Glyph.BACK, hoverBack, accent);
            hits.add(new Hit(backX, by, button, button, (b, s) -> click(back, b, s), false));
            if (hoverBack) tip("back", (m.backTarget != null ? "Back to " + m.backTarget : "Go Back")
                    + keyHint(MarketKeys.of(kind).back()));
            right = backX - 6;
        }

        Integer search = m.controls.get(MarketMenu.Control.SEARCH);
        if (search != null) {
            int sw = Math.max(96, Math.min(186, (int) (w * 0.3)));
            int sh = 18;
            int sx = right - sw;
            int sy = y + (HEADER_H - sh) / 2;
            boolean hover = AlpakaStyle.inside(mx, my, sx, sy, sw, sh);
            boolean active = m.query != null && !m.query.isEmpty();
            ModernGuiUtils.drawRoundedRect(graphics, sx, sy, sw, sh, sh / 2, hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.FIELD);
            if (active) ModernGuiUtils.drawRoundedOutline(graphics, sx, sy, sw, sh, sh / 2, AlpakaStyle.accentDim());
            else if (hover) ModernGuiUtils.drawRoundedOutline(graphics, sx, sy, sw, sh, sh / 2, AlpakaStyle.LINE);
            MarketIcons.draw(graphics, MarketIcons.Glyph.SEARCH, sx + 7, sy + 4.5f, 9, active || hover ? accent : AlpakaStyle.TEXT_MUTED);
            String text = active ? m.query : "Search " + (kind == MarketKind.BAZAAR ? "the Bazaar" : "auctions") + "…";
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, text, sw - 26)), sx + 20, sy + 5,
                    active ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED, false);
            hits.add(new Hit(sx, sy, sw, sh, (b, s) -> click(search, b, s), false));
            if (hover) tip("search", "Search" + (Markets.hotkeys(kind) ? " · / or Ctrl/Cmd+F" : ""));
            right = sx - 8;
        }

        int tx = chipX + chip + 7;
        int room = right - tx;
        List<String> crumbs = m.query != null ? List.of("Search", "\"" + m.query + "\"") : MarketTitles.breadcrumb(plainTitle, kind);
        Component title = GuiFont.title(kind.displayName);
        boolean large = title != null;
        if (title == null) title = GuiFont.text(kind.displayName);
        if (this.font.width(title) > room) title = GuiFont.text(AlpakaStyle.ellipsize(this.font, kind.displayName, room));
        int titleH = large ? 12 : 8;
        if (crumbs.isEmpty()) {
            graphics.text(this.font, title, tx, y + (HEADER_H - titleH) / 2, AlpakaStyle.TEXT, false);
        } else {
            // The same lines as the config's logo and version, so the title keeps clear of the edge.
            graphics.text(this.font, title, tx, y + 8, AlpakaStyle.TEXT, false);
            String path = String.join("  ›  ", crumbs);
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, path, room)), tx, y + 22,
                    AlpakaStyle.TEXT_MUTED, false);
        }
    }

    /** A square button holding one of the market glyphs: gray, lit in {@code hoverColor} on a soft square on hover. */
    private void glyphButton(GuiGraphicsExtractor graphics, int x, int y, int size, MarketIcons.Glyph glyph, boolean hover, int hoverColor) {
        if (hover) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, size, size, AlpakaStyle.FIELD_RADIUS,
                    hoverColor == AlpakaStyle.DANGER ? MarketIcons.fade(AlpakaStyle.DANGER, 0.2f) : AlpakaStyle.ROW_SELECTED);
        }
        float g = size * 0.5f;
        MarketIcons.draw(graphics, glyph, x + (size - g) / 2f, y + (size - g) / 2f, g, hover ? hoverColor : AlpakaStyle.TEXT_MUTED);
    }

    // ---- Sidebar --------------------------------------------------------------------------------

    private static final MarketMenu.Control[] FILTERS = {MarketMenu.Control.SORT, MarketMenu.Control.TIER, MarketMenu.Control.BIN_FILTER};
    private static final MarketMenu.Control[] ACTIONS = {MarketMenu.Control.MANAGE_ORDERS, MarketMenu.Control.SELL_INVENTORY, MarketMenu.Control.SELL_SACKS};

    private static int count(MarketMenu m, MarketMenu.Control[] controls) {
        int n = 0;
        for (MarketMenu.Control c : controls) if (m.controls.containsKey(c)) n++;
        return n;
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my, int x, int y, int w, int h, float deltaSec) {
        int clipY = y + 2;
        int clipH = h - 4;
        boolean inClip = AlpakaStyle.inside(mx, my, x, clipY, w, clipH);
        int filters = count(m, FILTERS);
        int actions = count(m, ACTIONS);
        int extras = extrasInFooter(m) ? 0 : m.extras.size();

        // Compact rows leave more room for the filters, which keep both their title and value.
        int natural = 6 + group(m.categories.size(), GROUP_LABEL_H, SIDE_ROW_H) + group(filters, GROUP_LABEL_H, FILTER_ROW_H)
                + group(actions, GROUP_LABEL_H, SIDE_ROW_H) + group(extras, GROUP_LABEL_H, SIDE_ROW_H);
        boolean compact = natural > clipH;
        int rowH = compact ? 16 : SIDE_ROW_H;
        int filterH = FILTER_ROW_H;

        graphics.enableScissor(x, clipY, x + w, clipY + clipH);
        int rowY = y + 6 - sideScroll.offset();
        int start = rowY;
        int rowX = x + 4;
        int rowW = w - 8;
        boolean first = true;

        if (!m.categories.isEmpty()) {
            rowY = groupStart(graphics, "Categories", x, rowY, w, compact, first);
            first = false;
            float targetY = Float.NaN;
            int targetColor = 0;
            int at = rowY;
            for (MarketMenu.Category category : m.categories) {
                if (category.selected()) {
                    targetY = at;
                    targetColor = category.kind().color;
                }
                at += rowH + 1;
            }
            // The marker glides from the category it was on, also across a page change.
            if (!Float.isNaN(targetY)) {
                if (Float.isNaN(indicatorY)) indicatorY = targetY;
                indicatorY = PloppAnimation.interpolate(indicatorY, targetY, deltaSec, 16.0f);
                indicatorColor = indicatorColor == 0 ? targetColor : ModernGuiUtils.lerpColor(indicatorColor, targetColor, Math.min(1f, deltaSec * 12f));
                int iy = Math.round(indicatorY);
                ModernGuiUtils.drawRoundedRect(graphics, rowX, iy, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_SELECTED);
                ModernGuiUtils.drawRoundedRect(graphics, rowX, iy + 4, 2, rowH - 8, 1, indicatorColor);
            }
            for (MarketMenu.Category category : m.categories) {
                boolean hover = inClip && AlpakaStyle.inside(mx, my, rowX, rowY, rowW, rowH);
                if (hover && !category.selected()) {
                    ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                }
                // Hypixel's own item for the category, as the other buttons show theirs.
                int slot = category.slot();
                sideItem(graphics, stack(slot), rowX, rowY, rowH);
                int text = category.selected() || hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
                int nameW = rowW - 26;
                int categoryKey = rowY >= clipY && rowY + rowH <= clipY + clipH ? nextCategoryKey(slot) : 0;
                // The keys show while Shift is held, the one that picks a category with a number.
                if (categoryKey > 0 && this.minecraft != null && this.minecraft.hasShiftDown()) {
                    nameW = keyChip(graphics, categoryKey, rowX + rowW - 4, rowY + (rowH - 8) / 2, 1f) - 4 - (rowX + 21);
                }
                graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, category.name(), nameW)),
                        rowX + 21, rowY + (rowH - 8) / 2, text, false);
                if (visible(rowY, rowH, clipY, clipH)) {
                    hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, rowH, (b, s) -> click(slot, b, s), false));
                }
                if (hover) hoveredStack = stack(slot);
                rowY += rowH + 1;
            }
            rowY += compact ? 2 : 6;
        }

        if (filters > 0) {
            rowY = groupStart(graphics, "Filters", x, rowY, w, compact, first);
            first = false;
            for (MarketMenu.Control c : FILTERS) {
                Integer slot = m.controls.get(c);
                if (slot == null) continue;
                String label = switch (c) {
                    case SORT -> "Sort";
                    case TIER -> "Item Tier";
                    default -> "BIN Filter";
                };
                String value = m.controlValues.getOrDefault(c, plain(slot).name());
                boolean hover = inClip && AlpakaStyle.inside(mx, my, rowX, rowY, rowW, filterH);
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, filterH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                // Hypixel's own item for the filter, as the categories show theirs; our glyph only
                // while the slot is still empty.
                ItemStack item = stack(slot);
                if (!item.isEmpty()) {
                    sideItem(graphics, item, rowX, rowY, filterH);
                } else {
                    MarketIcons.Glyph glyph = switch (c) {
                        case SORT -> MarketIcons.Glyph.SORT;
                        case TIER -> MarketIcons.Glyph.TIER;
                        default -> MarketIcons.Glyph.BIN;
                    };
                    MarketIcons.draw(graphics, glyph, rowX + 7, rowY + (filterH - 9) / 2f, 9, hover ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
                }
                graphics.text(this.font, GuiFont.text(label), rowX + 21, rowY + 3, AlpakaStyle.TEXT_MUTED, false);
                graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, value, rowW - 33)), rowX + 21, rowY + 13,
                        hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
                MarketIcons.draw(graphics, MarketIcons.Glyph.SORT, rowX + rowW - 10, rowY + 9, 8,
                        hover ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
                if (visible(rowY, filterH, clipY, clipH)) {
                    int s = slot;
                    hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, filterH, (b, sh) -> click(s, b, sh), false));
                }
                if (hover) hoveredStack = stack(slot);
                rowY += filterH + 1;
            }
            rowY += compact ? 2 : 6;
        }

        if (actions > 0) {
            rowY = groupStart(graphics, "Actions", x, rowY, w, compact, first);
            first = false;
            for (MarketMenu.Control c : ACTIONS) {
                Integer slot = m.controls.get(c);
                if (slot == null) continue;
                String label = switch (c) {
                    case MANAGE_ORDERS -> "Manage Orders";
                    case SELL_INVENTORY -> "Sell Inventory";
                    default -> "Sell Sacks";
                };
                MarketIcons.Glyph glyph = switch (c) {
                    case MANAGE_ORDERS -> MarketIcons.Glyph.ORDERS;
                    case SELL_INVENTORY -> MarketIcons.Glyph.SELL;
                    default -> MarketIcons.Glyph.SACK;
                };
                // Hypixel's own item for the action too; our glyph only while the slot is still empty.
                ItemStack item = stack(slot);
                rowY = sideRow(graphics, mx, my, inClip, rowX, rowY, rowW, rowH, clipY, clipH, slot, label,
                        item.isEmpty() ? glyph : null, item);
            }
            rowY += 6;
        }

        if (extras > 0) {
            rowY = groupStart(graphics, "More", x, rowY, w, compact, first);
            for (int slot : m.extras) {
                rowY = sideRow(graphics, mx, my, inClip, rowX, rowY, rowW, rowH, clipY, clipH, slot, plain(slot).name(), null, stack(slot),
                        extraKey(slot));
            }
        }

        // rowY - start is the content's height whatever the scroll, so the limit does not move with it.
        sideScroll.max = Math.max(0, rowY - start + 4 - clipH);
        sideScroll.update(deltaSec);
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, sideScroll.offset() > 0, sideScroll.offset() + 1 < sideScroll.max);
        graphics.disableScissor();
    }

    /** The height a sidebar group takes: its label and its rows, and the gap after it. */
    private static int group(int rows, int labelH, int rowH) {
        return rows == 0 ? 0 : labelH + rows * (rowH + 1) + 6;
    }

    /** A group's label, or in the compact sidebar a hairline between groups. Returns where its rows start. */
    private int groupStart(GuiGraphicsExtractor graphics, String label, int x, int y, int w, boolean compact, boolean first) {
        if (!compact) {
            AlpakaStyle.groupLabel(graphics, this.font, label, x + PAD, y + 2);
            return y + GROUP_LABEL_H;
        }
        if (first) return y;
        ModernGuiUtils.drawRect(graphics, x + PAD, y - 3, w - PAD * 2, 1, AlpakaStyle.LINE);
        return y + 2;
    }

    /** A sidebar button for a slot: a glyph of ours or the item itself, and a name. */
    private int sideRow(GuiGraphicsExtractor graphics, int mx, int my, boolean inClip, int rowX, int rowY, int rowW, int rowH,
                        int clipY, int clipH, int slot, String label, MarketIcons.Glyph glyph, ItemStack item) {
        return sideRow(graphics, mx, my, inClip, rowX, rowY, rowW, rowH, clipY, clipH, slot, label, glyph, item, null);
    }

    /** A sidebar row; with a key, that key in a chip at its end and the label short of it. */
    private int sideRow(GuiGraphicsExtractor graphics, int mx, int my, boolean inClip, int rowX, int rowY, int rowW, int rowH,
                        int clipY, int clipH, int slot, String label, MarketIcons.Glyph glyph, ItemStack item, String key) {
        boolean hover = inClip && AlpakaStyle.inside(mx, my, rowX, rowY, rowW, rowH);
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
        if (glyph != null) {
            MarketIcons.draw(graphics, glyph, rowX + 7, rowY + (rowH - 9) / 2f, 9, hover ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED);
        } else if (item != null) {
            sideItem(graphics, item, rowX, rowY, rowH);
        }
        int labelW = rowW - 26;
        if (key != null) labelW = keyChip(graphics, key, rowX + rowW - 4, rowY + (rowH - 8) / 2, 1f) - 4 - (rowX + 21);
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, label, labelW)), rowX + 21, rowY + (rowH - 8) / 2,
                hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
        if (visible(rowY, rowH, clipY, clipH)) {
            hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, rowH, (b, s) -> click(slot, b, s), false));
        }
        if (hover) hoveredStack = stack(slot);
        return rowY + rowH + 1;
    }

    /**
     * An item at the start of a sidebar row, at its full 16 pixels on whole pixels: scaled down or
     * placed between pixels, its texture turns blurry.
     */
    private static void sideItem(GuiGraphicsExtractor graphics, ItemStack item, int rowX, int rowY, int rowH) {
        graphics.item(item, rowX + 3, rowY + (rowH - 16) / 2);
    }

    // ---- Content --------------------------------------------------------------------------------

    private boolean confirmation(MarketMenu m) {
        return m.confirmationSlot() != null;
    }

    private List<String> confirmationLines(MarketMenu m) {
        return m.confirmationLines;
    }

    private int drawConfirmation(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my,
                                 int cx, int cy, int cw, int ch) {
        int slot = m.confirmationSlot();
        int top = cy + 10 - contentScroll.offset();
        int y = top;
        for (String line : confirmationLines(m)) {
            int color = line.toLowerCase(Locale.ROOT).matches(".*(cost|coins|earn|total|price).*" )
                    ? MarketInk.ink(COIN) : AlpakaStyle.TEXT;
            for (FormattedText part : GuiFont.splitLines(this.font, line, cw - 32)) {
                graphics.text(this.font, Language.getInstance().getVisualOrder(part), cx + 16, y, color, false);
                y += 12;
            }
        }
        int bw = (cw - 36) / 2;
        int by = y + 10;
        String confirm = plain(slot).name();
        if (confirm.equalsIgnoreCase("Confirm") && plainTitle.equals("Confirm Purchase")) confirm = "Buy Item";
        confirmationButton(graphics, slot, confirm, cx + 12, by, bw, mx, my, true);
        confirmationButton(graphics, m.cancelSlot, "Cancel", cx + 24 + bw, by, bw, mx, my, false);
        return by - top + AlpakaStyle.BUTTON_H + 20;
    }

    private void confirmationButton(GuiGraphicsExtractor graphics, int slot, String label, int x, int y,
                                    int w, int mx, int my, boolean primary) {
        boolean hover = AlpakaStyle.inside(mx, my, x, y, w, AlpakaStyle.BUTTON_H);
        AlpakaStyle.button(graphics, this.font, x, y, w, AlpakaStyle.BUTTON_H, label, hover,
                primary ? AlpakaStyle.ButtonKind.PRIMARY : AlpakaStyle.ButtonKind.PLAIN);
        int key = y >= contentY && y + AlpakaStyle.BUTTON_H <= contentY + contentH ? nextKey(slot) : 0;
        if (key > 0) keyChip(graphics, key, x + w - 5, y + (AlpakaStyle.BUTTON_H - 8) / 2, 1f);
        hits.add(new Hit(x, y, w, AlpakaStyle.BUTTON_H, (b, s) -> click(slot, b, s), false));
        if (hover) hoveredStack = stack(slot);
    }

    private String positionKey(MarketMenu m) {
        return kind + "|" + plainTitle + "|" + m.page + "|" + m.query + "|" + m.controlValues + "|"
                + m.categories.stream().filter(MarketMenu.Category::selected).map(MarketMenu.Category::name).toList();
    }

    private void drawContent(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my, float deltaSec, long now) {
        int cx = contentX;
        int cy = contentY;
        int cw = contentW;
        int ch = contentH;
        boolean inClip = AlpakaStyle.inside(mx, my, cx, cy, cw, ch);

        if (m.empty) {
            // Hypixel sends the items a moment after the window; turning a page, that moment is
            // usually too short to be worth drawing anything for.
            if (now - openTimeMs > 150 || !continued) drawSkeleton(graphics, cx, cy, cw, ch, now);
            return;
        }
        if (m.content.isEmpty()) {
            int iconSize = 22;
            MarketIcons.draw(graphics, MarketIcons.Glyph.BOX, cx + (cw - iconSize) / 2f, cy + ch / 2f - 22, iconSize, AlpakaStyle.DIVIDER);
            String empty = m.query == null ? "Nothing Here Yet" : "No Results — Try Another Search or Filter";
            empty = AlpakaStyle.ellipsize(this.font, empty, cw - 16);
            graphics.text(this.font, GuiFont.text(empty), cx + (cw - GuiFont.width(this.font, empty)) / 2, cy + ch / 2 + 6,
                    AlpakaStyle.TEXT_MUTED, false);
            return;
        }

        if (m.layout == MarketMenu.Layout.GRID) {
            String key = positionKey(m);
            if (!key.equals(listPositionKey)) {
                listPositionKey = key;
                contentScroll.current = contentScroll.target = LIST_POSITIONS.getOrDefault(key, 0.0);
            }
        }
        int firstHit = hits.size();
        graphics.enableScissor(cx, cy + 1, cx + cw, cy + ch);
        int contentHeight = confirmation(m) ? drawConfirmation(graphics, m, mx, my, cx, cy, cw, ch)
                : m.layout == MarketMenu.Layout.GRID
                ? drawGrid(graphics, m, mx, my, inClip, cx, cy, cw, ch, now)
                : drawTiles(graphics, m, mx, my, inClip, cx, cy, cw, ch, now);
        // The inventory has its own space; clipped rows never accept clicks outside the viewport.
        for (int i = hits.size() - 1; i >= firstHit; i--) {
            Hit hit = hits.get(i);
            int left = Math.max(cx, hit.x());
            int top = Math.max(cy, hit.y());
            int right = Math.min(cx + cw, hit.x() + hit.w());
            int bottom = Math.min(cy + ch, hit.y() + hit.h());
            if (right <= left || bottom <= top) hits.remove(i);
            else hits.set(i, new Hit(left, top, right - left, bottom - top, hit.action(), hit.sound()));
        }
        contentScroll.max = Math.max(0, contentHeight - ch);
        contentScroll.update(deltaSec);
        if (m.layout == MarketMenu.Layout.GRID) {
            LIST_POSITIONS.put(listPositionKey, contentScroll.target);
            if (LIST_POSITIONS.size() > 32) LIST_POSITIONS.remove(LIST_POSITIONS.keySet().iterator().next());
        }
        boolean above = contentScroll.offset() > 0;
        boolean below = contentScroll.offset() + 1 < contentScroll.max;
        AlpakaStyle.scrollFades(graphics, cx, cy + 1, cw, ch - 1, above, below);
        if (contentScroll.max > 0) {
            int trackH = ch - 12;
            int thumbH = Math.max(16, (int) (trackH * (ch / (double) (ch + contentScroll.max))));
            int thumbY = cy + 6 + (int) ((trackH - thumbH) * (contentScroll.current / contentScroll.max));
            ModernGuiUtils.drawRoundedRect(graphics, cx + cw - 4, thumbY, 2, thumbH, 1, inClip ? AlpakaStyle.TEXT_MUTED : AlpakaStyle.DIVIDER);
            scrollTrackY = cy + 6;
            scrollTrackH = trackH;
            scrollThumbH = thumbH;
            hits.add(new Hit(cx + cw - 7, cy + 2, 7, ch - 4, (b, s) -> draggingScroll = b == 0, false));
        }
        graphics.disableScissor();
        updateHover(deltaSec);
    }

    /** Placeholder cards while the items are on their way, breathing so it reads as loading. */
    private void drawSkeleton(GuiGraphicsExtractor graphics, int cx, int cy, int cw, int ch, long now) {
        int innerW = cw - 12;
        int cols = Math.max(1, (innerW + GAP) / (CARD_MIN_W + GAP));
        int cardW = (innerW - (cols - 1) * GAP) / cols;
        int rowsShown = Math.max(1, (ch - 12 + GAP) / (CARD_H + GAP));
        for (int i = 0; i < cols * rowsShown; i++) {
            int px = cx + 6 + (i % cols) * (cardW + GAP);
            int py = cy + 6 + (i / cols) * (CARD_H + GAP);
            float wave = 0.5f + 0.5f * (float) Math.sin(now / 260.0 - i * 0.45);
            int base = ModernGuiUtils.lerpColor(AlpakaStyle.ROW, AlpakaStyle.ROW_HOVER, wave);
            int bar = ModernGuiUtils.lerpColor(AlpakaStyle.ROW_SELECTED, AlpakaStyle.DIVIDER, wave * 0.6f);
            ModernGuiUtils.drawRoundedRect(graphics, px, py, cardW, CARD_H, AlpakaStyle.CARD_RADIUS, base);
            ModernGuiUtils.drawRoundedRect(graphics, px + 8, py + 8, 22, 22, 5, bar);
            ModernGuiUtils.drawRoundedRect(graphics, px + 36, py + 9, Math.max(10, cardW - 60), 6, 3, bar);
            ModernGuiUtils.drawRoundedRect(graphics, px + 36, py + 22, Math.max(8, (cardW - 60) / 2), 6, 3, bar);
        }
    }

    /** How far into its entrance the card with this index is, 0..1, eased. */
    private float entrance(int index, long now) {
        float t = (now - contentSinceMs - index * 22L) / 220.0f;
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private int drawGrid(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my, boolean inClip,
                         int cx, int cy, int cw, int ch, long now) {
        LoreInfo.Type type = m.info.get(m.content.get(0)).type;
        if (type == LoreInfo.Type.AUCTION || type == LoreInfo.Type.PRODUCT || type == LoreInfo.Type.ORDER) {
            boolean sameType = m.content.stream().allMatch(slot -> m.info.get(slot).type == type);
            if (sameType) return drawListings(graphics, m, type, mx, my, cx, cy, cw, ch);
        }
        int innerX = cx + 6;
        int innerW = cw - 14;
        int cols = Math.max(1, (innerW + GAP) / (CARD_MIN_W + GAP));
        int cardW = (innerW - (cols - 1) * GAP) / cols;
        int count = m.content.size();
        int gridRows = (count + cols - 1) / cols;
        int top = cy + 6 - contentScroll.offset();
        for (int i = 0; i < count; i++) {
            int slot = m.content.get(i);
            int px = innerX + (i % cols) * (cardW + GAP);
            int py = top + (i / cols) * (CARD_H + GAP);
            if (py + CARD_H < cy || py > cy + ch) continue;
            int key = py >= cy && py + CARD_H <= cy + ch ? nextKey(slot) : 0;
            float in = entrance(i, now);
            drawCard(graphics, slot, m.info.get(slot), px, py + Math.round((1 - in) * 6), cardW, CARD_H, mx, my, inClip, in, cy, ch,
                    key, true);
        }
        return gridRows * (CARD_H + GAP) - GAP + 12;
    }

    /** Fixed columns keep prices aligned while the item names keep the remaining space. */
    private int drawListings(GuiGraphicsExtractor graphics, MarketMenu m, LoreInfo.Type type, int mx, int my,
                             int cx, int cy, int cw, int ch) {
        int x = cx + 6;
        int w = cw - 14;
        int right = x + w - 5;
        int listY = cy + LIST_HEADER_H;
        int rowH = type == LoreInfo.Type.ORDER ? CARD_H + 3 : LIST_ROW_H;
        graphics.text(this.font, GuiFont.text(type == LoreInfo.Type.ORDER ? "Your Orders" : "Item"), x + 24, cy + 4, AlpakaStyle.TEXT_MUTED, false);
        if (type == LoreInfo.Type.PRODUCT) {
            rightText(graphics, "Instant Buy", right - 58, cy + 4, AlpakaStyle.TEXT_MUTED);
            rightText(graphics, "Instant Sell", right, cy + 4, AlpakaStyle.TEXT_MUTED);
        } else if (type == LoreInfo.Type.AUCTION) {
            rightText(graphics, "Price", right - 49, cy + 4, AlpakaStyle.TEXT_MUTED);
            rightText(graphics, "Ends", right, cy + 4, AlpakaStyle.TEXT_MUTED);
        }
        graphics.enableScissor(cx, listY, cx + cw, cy + ch);
        int top = listY - contentScroll.offset();
        for (int i = 0; i < m.content.size(); i++) {
            int py = top + i * rowH;
            if (py + rowH <= listY || py >= cy + ch) continue;
            int slot = m.content.get(i);
            int key = py >= listY && py + rowH <= cy + ch ? nextKey(slot) : 0;
            boolean hover = AlpakaStyle.inside(mx, my, x, Math.max(py, listY), w,
                    Math.max(0, Math.min(py + rowH, cy + ch) - Math.max(py, listY)));
            LoreInfo info = m.info.get(slot);
            if (type == LoreInfo.Type.ORDER) {
                drawCard(graphics, slot, info, x, py, w, CARD_H, mx, my, hover, 1f, listY, cy + ch - listY, key);
                continue;
            }
            ModernGuiUtils.drawRoundedRect(graphics, x, py, w, rowH - 1, 3,
                    hover ? AlpakaStyle.ROW_HOVER : i % 2 == 0 ? AlpakaStyle.ROW : AlpakaStyle.PANEL);
            ItemStack item = stack(slot);
            graphics.item(item, x + 14, py);
            graphics.itemDecorations(this.font, item, x + 14, py);
            int nameX = x + 35;
            int nameEnd = right - (type == LoreInfo.Type.PRODUCT ? 116 : 109);
            styled(graphics, item.getHoverName(), nameX, py + 4, Math.max(12, nameEnd - nameX), AlpakaStyle.TEXT);
            if (type == LoreInfo.Type.PRODUCT) {
                rightText(graphics, info.buyPrice == null ? "—" : Coins.compact(info.buyPrice), right - 58, py + 4, MarketInk.ink(COIN));
                rightText(graphics, info.sellPrice == null ? "—" : Coins.compact(info.sellPrice), right, py + 4, MarketInk.ink(COIN));
            } else {
                pill(graphics, nameEnd + 4, py + 4, info.isOver() ? "End" : info.isBin() ? "BIN" : "BID",
                        MarketInk.ink(info.isOver() ? RED : info.isBin() ? GREEN : AQUA), 1f);
                Double price = info.auctionPrice();
                rightText(graphics, price == null ? "—" : Coins.compact(price), right - 49, py + 4, MarketInk.ink(COIN));
                String time = info.isOver() ? info.status.replace("!", "") : info.endsIn == null ? "—" : shortTime(info.endsIn);
                if (GuiFont.width(this.font, time) > 45 && info.endsIn != null && !info.isOver()) time = time.split(" ")[0];
                time = AlpakaStyle.ellipsize(this.font, time, 45);
                rightText(graphics, time, right, py + 4, Coins.seconds(time) >= 0 && Coins.seconds(time) < 300 ? MarketInk.ink(RED) : AlpakaStyle.TEXT_MUTED);
            }
            if (key > 0) keyChip(graphics, key, x + 12, py + 4, 1f);
            int hitTop = Math.max(py, listY);
            int hitBottom = Math.min(py + rowH, cy + ch);
            hits.add(new Hit(x, hitTop, w, hitBottom - hitTop, (b, s) -> click(slot, b, s), false));
            if (hover) hoveredStack = item;
        }
        graphics.disableScissor();
        return LIST_HEADER_H + m.content.size() * rowH + 4;
    }

    private void rightText(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(this.font, GuiFont.text(text), right - GuiFont.width(this.font, text), y, color, false);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int slot, LoreInfo info, int px, int py, int w, int h,
                          int mx, int my, boolean inClip, float alpha, int clipY, int clipH) {
        drawCard(graphics, slot, info, px, py, w, h, mx, my, inClip, alpha, clipY, clipH, 0);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int slot, LoreInfo info, int px, int py, int w, int h,
                          int mx, int my, boolean inClip, float alpha, int clipY, int clipH, int key) {
        drawCard(graphics, slot, info, px, py, w, h, mx, my, inClip, alpha, clipY, clipH, key, false);
    }

    /**
     * A card; with a key from 1 to 9, that number as the key that presses it: in the top corner, or
     * in a list of auctions, where the name needs the room, at the end of the price line.
     */
    private void drawCard(GuiGraphicsExtractor graphics, int slot, LoreInfo info, int px, int py, int w, int h,
                          int mx, int my, boolean inClip, float alpha, int clipY, int clipH, int key, boolean keyOnItem) {
        boolean pressing = beginPress(graphics, slot, px, py, w, h);
        try {
            drawCardBody(graphics, slot, info, px, py, w, h, mx, my, inClip, alpha, clipY, clipH, key, keyOnItem);
        } finally {
            if (pressing) graphics.pose().popMatrix();
        }
    }

    /**
     * A clicked card or tile gives a little under the click: it dips to 96.5 % and springs back over
     * {@link #PRESS_MS}, with the accent flashing round it. Returns whether a pose was pushed.
     */
    private boolean beginPress(GuiGraphicsExtractor graphics, int slot, int x, int y, int w, int h) {
        if (slot != pressedSlot) return false;
        long since = System.currentTimeMillis() - pressedAtMs;
        if (since >= PRESS_MS) return false;
        float scale = 1f - 0.035f * (float) Math.sin(Math.PI * since / (double) PRESS_MS);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-cx, -cy);
        return true;
    }

    private void drawCardBody(GuiGraphicsExtractor graphics, int slot, LoreInfo info, int px, int py, int w, int h,
                              int mx, int my, boolean inClip, float alpha, int clipY, int clipH, int key, boolean keyOnPriceLine) {
        ItemStack stack = stack(slot);
        boolean hover = inClip && AlpakaStyle.inside(mx, my, px, py, w, h);
        if (hover) hoverTarget(slot);
        float hv = hoverProgress.getOrDefault(slot, 0f);
        int rarity = rarity(stack);

        int bg = ModernGuiUtils.lerpColor(AlpakaStyle.ROW, AlpakaStyle.ROW_HOVER, hv);
        ModernGuiUtils.drawRoundedRect(graphics, px, py, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(bg, alpha));
        sheen(graphics, px, py, w, alpha);
        if (hv > 0.01f) {
            ModernGuiUtils.drawRoundedOutline(graphics, px, py, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(rarity, 0.45f * hv * alpha));
        }
        drawPressFlash(graphics, slot, px, py, w, h);

        boolean compact = h < CARD_H;
        int well = compact ? 18 : 22;
        int wx = px + 8;
        // On a full card the item sits below the name, beside the two lines under it, so the name
        // has the card's whole width; a compact card has one line and no room below for it.
        int wy = compact ? py + (h - well) / 2 : py + 14;
        ModernGuiUtils.drawRoundedRect(graphics, wx, wy, well, well, 5, MarketIcons.fade(rarity, (0.10f + 0.08f * hv) * alpha));
        GuiItemFade.begin(alpha);
        int inset = (well - 16) / 2;
        graphics.item(stack, wx + inset, wy + inset);
        graphics.itemDecorations(this.font, stack, wx + inset, wy + inset);
        GuiItemFade.end();

        int tx = wx + well + 6;
        int tw = px + w - 6 - tx;
        int textAlpha = Math.round(alpha * 255) << 24;
        Component name = stack.getHoverName();
        int nameStart = compact ? tx : wx;
        int nameX = nameStart;
        int nameEnd = tx + tw;
        // The room the key takes at the end of the second line, which the lines leave free.
        int reserve = 0;
        if (key > 0 && keyOnPriceLine) reserve = tx + tw + 4 - keyChip(graphics, key, tx + tw, py + 16, alpha);
        else if (key > 0) nameEnd = keyChip(graphics, key, px + w - 6, py + 5, alpha) - 4;
        // An order's name starts with BUY or SELL; that becomes a label, so the product's name fits.
        if (info != null && info.type == LoreInfo.Type.ORDER) {
            String plainName = SkyblockUtils.cleanColor(name.getString());
            for (String side : new String[] {"BUY ", "SELL "}) {
                if (plainName.startsWith(side)) {
                    name = withoutPrefix(name, side.length());
                    nameX += pill(graphics, nameStart, py + 5, side.trim(), side.startsWith("B") ? MarketInk.ink(GREEN) : MarketInk.ink(COIN), alpha) + 4;
                }
            }
        }
        styled(graphics, name, nameX, py + 5, nameEnd - nameX, 0x00FFFFFF | textAlpha);
        switch (info == null ? LoreInfo.Type.GENERIC : info.type) {
            case AUCTION -> auctionLines(graphics, info, tx, py, tw, alpha, reserve);
            case PRODUCT -> productLines(graphics, info, tx, py, tw, alpha);
            case ORDER -> orderLines(graphics, info, tx, py, tw, alpha);
            default -> genericLines(graphics, stack, info, tx, py, tw, alpha, compact ? 1 : 2, reserve);
        }

        if (py + h > clipY && py < clipY + clipH) {
            int top = Math.max(py, clipY);
            int bottom = Math.min(py + h, clipY + clipH);
            hits.add(new Hit(px, top, w, bottom - top, (b, s) -> click(slot, b, s), false));
        }
        if (hover) hoveredStack = stack;
    }

    private void auctionLines(GuiGraphicsExtractor graphics, LoreInfo info, int tx, int py, int tw, float alpha, int reserve) {
        int ly = py + 16;
        int lx = tx;
        if (info.isOver()) {
            boolean sold = info.status.toLowerCase(Locale.ROOT).contains("sold");
            lx += pill(graphics, lx, ly, info.status.replace("!", ""), sold ? MarketInk.ink(GREEN) : MarketInk.ink(RED), alpha) + 4;
        } else {
            lx += pill(graphics, lx, ly, info.isBin() ? "BIN" : "BID", info.isBin() ? MarketInk.ink(GREEN) : MarketInk.ink(AQUA), alpha) + 4;
        }
        Double price = info.auctionPrice();
        if (price != null) {
            String text = Coins.compact(price);
            graphics.text(this.font, GuiFont.text(text), lx, ly, MarketIcons.fade(MarketInk.ink(COIN), alpha), false);
            lx += GuiFont.width(this.font, text);
            if (!info.isBin() && info.bids != null && info.bids > 0) {
                String bids = "  " + info.bids + (info.bids == 1 ? " bid" : " bids");
                if (lx + GuiFont.width(this.font, bids) <= tx + tw - reserve) {
                    graphics.text(this.font, GuiFont.text(bids), lx, ly, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
                }
            }
        }

        int ly3 = py + 27;
        int rx = tx;
        if (info.endsIn != null && !info.isOver()) {
            long seconds = Coins.seconds(info.endsIn);
            int color = seconds >= 0 && seconds < 300 ? MarketInk.ink(RED) : AlpakaStyle.TEXT_MUTED;
            MarketIcons.draw(graphics, MarketIcons.Glyph.CLOCK, rx, ly3, 8, MarketIcons.fade(color, alpha));
            rx += 10;
            String time = shortTime(info.endsIn);
            graphics.text(this.font, GuiFont.text(time), rx, ly3, MarketIcons.fade(color, alpha), false);
            rx += GuiFont.width(this.font, time) + 8;
        }
        String who = info.seller != null ? withoutRank(info.seller) : info.bidder != null ? withoutRank(info.bidder) : null;
        if (who != null) {
            int room = tx + tw - rx - 10;
            if (room > 18) {
                String name = AlpakaStyle.ellipsize(this.font, who, room);
                int nameW = GuiFont.width(this.font, name);
                int nx = tx + tw - nameW;
                MarketIcons.draw(graphics, MarketIcons.Glyph.PERSON, nx - 10, ly3, 8, MarketIcons.fade(AlpakaStyle.DIVIDER, alpha));
                graphics.text(this.font, GuiFont.text(name), nx, ly3, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
            }
        }
    }

    private void productLines(GuiGraphicsExtractor graphics, LoreInfo info, int tx, int py, int tw, float alpha) {
        priceLine(graphics, "Buy", info.buyPrice, tx, py + 16, alpha);
        int ly3 = py + 27;
        int end = priceLine(graphics, "Sell", info.sellPrice, tx, ly3, alpha);
        if (info.buyPrice != null && info.sellPrice != null && info.buyPrice > 0) {
            double margin = (info.buyPrice - info.sellPrice) / info.buyPrice * 100.0;
            String text = String.format(java.util.Locale.ROOT, "Spread %.1f%%", margin);
            int w = GuiFont.width(this.font, text);
            if (tx + tw - w - 8 > end) {
                int chipX = tx + tw - w - 6;
                ModernGuiUtils.drawRoundedRect(graphics, chipX - 1, ly3 - 2, w + 8, 11, 3, MarketIcons.fade(AlpakaStyle.ROW_SELECTED, alpha));
                graphics.text(this.font, GuiFont.text(text), chipX + 3, ly3, MarketIcons.fade(AlpakaStyle.TEXT_SOFT, alpha), false);
            }
        }
    }

    /** "Buy ● 5.3": a label, a coin and the amount in gold. Returns where it ends. */
    private int priceLine(GuiGraphicsExtractor graphics, String label, Double coins, int x, int y, float alpha) {
        graphics.text(this.font, GuiFont.text(label), x, y, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
        int lx = x + 22;
        if (coins == null) {
            graphics.text(this.font, GuiFont.text("—"), lx, y, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
            return lx + 8;
        }
        String text = Coins.compact(coins);
        graphics.text(this.font, GuiFont.text(text), lx, y, MarketIcons.fade(MarketInk.ink(COIN), alpha), false);
        return lx + GuiFont.width(this.font, text);
    }

    private void orderLines(GuiGraphicsExtractor graphics, LoreInfo info, int tx, int py, int tw, float alpha) {
        int ly = py + 16;
        int lx = tx;
        if (info.amount != null) {
            String amount = Coins.full(info.amount) + "x";
            graphics.text(this.font, GuiFont.text(amount), lx, ly, MarketIcons.fade(AlpakaStyle.TEXT_SOFT, alpha), false);
            lx += GuiFont.width(this.font, amount) + 6;
        }
        if (info.unitPrice != null) {
            graphics.text(this.font, GuiFont.text(Coins.compact(info.unitPrice) + " each"), lx, ly, MarketIcons.fade(MarketInk.ink(COIN), alpha), false);
        }
        float ratio = info.fillRatio();
        if (ratio >= 0) {
            boolean claim = info.clickHint != null && info.clickHint.toLowerCase(Locale.ROOT).contains("claim");
            String pct = claim ? "Ready to Claim" : Math.round(ratio * 100) + "% Filled";
            int pw = GuiFont.width(this.font, pct);
            int barW = tw - pw - 6;
            int by = py + 30;
            ModernGuiUtils.drawRoundedRect(graphics, tx, by, barW, 3, 1, MarketIcons.fade(AlpakaStyle.TRACK, alpha));
            int fill = Math.round(barW * ratio);
            if (fill > 0) ModernGuiUtils.drawRoundedRect(graphics, tx, by, fill, 3, 1, MarketIcons.fade(ratio >= 1f ? MarketInk.ink(GREEN) : AlpakaStyle.accent(), alpha));
            graphics.text(this.font, GuiFont.text(pct), tx + tw - pw, py + 27, MarketIcons.fade(ratio >= 1f ? MarketInk.ink(GREEN) : AlpakaStyle.TEXT_MUTED, alpha), false);
        }
    }

    /**
     * A card with no auction, product or order in it: a coin line if the lore has one ("Price per
     * unit ● 5.3"), else the lore's first lines in their own colors.
     */
    private void genericLines(GuiGraphicsExtractor graphics, ItemStack stack, LoreInfo info, int tx, int py, int tw, float alpha,
                              int lines, int reserve) {
        if (info != null && !info.coinLines.isEmpty()) {
            int y = py + 16;
            for (int i = 0; i < Math.min(lines, info.coinLines.size()); i++) {
                LoreInfo.CoinLine line = info.coinLines.get(i);
                String value = Coins.compact(line.coins());
                int valueW = GuiFont.width(this.font, value);
                int lineW = i == 0 ? tw - reserve : tw;
                String label = AlpakaStyle.ellipsize(this.font, line.label(), Math.max(0, lineW - valueW - 6));
                graphics.text(this.font, GuiFont.text(label), tx, y, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
                int vx = tx + lineW - valueW;
                graphics.text(this.font, GuiFont.text(value), vx, y, MarketIcons.fade(MarketInk.ink(COIN), alpha), false);
                y += 11;
            }
            // Below a single coin line, the lore's first line that is not one: "Price: …" again would only repeat it.
            if (info.coinLines.size() == 1 && lines > 1) loreLines(graphics, stack, tx, py + 27, tw, 1, alpha, true);
            return;
        }
        loreLines(graphics, stack, tx, py + 16, tw - reserve, lines, alpha);
    }

    /** A faint line of light along a card's top edge, as if lit from above. */
    private static void sheen(GuiGraphicsExtractor graphics, int x, int y, int w, float alpha) {
        ModernGuiUtils.drawRect(graphics, x + AlpakaStyle.CARD_RADIUS, y, w - AlpakaStyle.CARD_RADIUS * 2, 1,
                MarketIcons.fade(0x14FFFFFF, alpha));
    }

    /**
     * The name without its first visible characters, each remaining part keeping its own style.
     * Formatting codes written into the text are kept and not counted, so the rest stays colored.
     */
    static Component withoutPrefix(Component name, int chars) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int[] skip = {chars};
        name.visit((style, text) -> {
            StringBuilder part = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    part.append(c).append(text.charAt(++i));
                } else if (skip[0] > 0) {
                    skip[0]--;
                } else {
                    part.append(c);
                }
            }
            if (!part.isEmpty()) out.append(Component.literal(part.toString()).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** The item's first lore lines that say something, in their own colors. */
    private void loreLines(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int maxW, int lines, float alpha) {
        loreLines(graphics, stack, x, y, maxW, lines, alpha, false);
    }

    private void loreLines(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int maxW, int lines, float alpha,
                           boolean skipCoins) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return;
        int drawn = 0;
        for (Component line : lore.styledLines()) {
            String plain = SkyblockUtils.cleanColor(line.getString()).trim();
            if (plain.isEmpty() || plain.chars().allMatch(c -> c == '-' || c == '▬' || c == ' ')) continue;
            if (skipCoins && plain.toLowerCase(java.util.Locale.ROOT).matches(".*\\d coins?$")) continue;
            styled(graphics, line, x, y + drawn * 11, maxW, AlpakaStyle.TEXT_MUTED & 0x00FFFFFF | (Math.round(alpha * 255) << 24));
            if (++drawn >= lines) break;
        }
    }

    /** A small rounded label: BIN, BID, Sold, Expired. Returns its width. */
    private int pill(GuiGraphicsExtractor graphics, int x, int y, String text, int color, float alpha) {
        int w = GuiFont.width(this.font, text) + 6;
        ModernGuiUtils.drawRoundedRect(graphics, x, y - 2, w, 11, 3, MarketIcons.fade(color, 0.16f * alpha));
        graphics.text(this.font, GuiFont.text(text), x + 3, y, MarketIcons.fade(color, alpha), false);
        return w;
    }

    // ---- Tiles ----------------------------------------------------------------------------------

    private enum TileKind { PLAIN, PRIMARY, DANGER }

    private int drawTiles(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my, boolean inClip,
                          int cx, int cy, int cw, int ch, long now) {
        int ax = cx + 8;
        int ay = cy + 8;
        int aw = cw - 16;
        int ah = ch - 16;
        int index = 0;
        List<Integer> tiles = new ArrayList<>(m.content);
        if (m.hero != null) tiles.remove(m.hero);
        if (m.hero != null) {
            return drawDetailPage(graphics, m, tiles, mx, my, inClip, ax, cy + 4, aw, ch - 8, cy, ch, index, now);
        }
        if (tiles.isEmpty()) return ch;

        int cols = Math.max(1, Math.min(tiles.size(), (aw + GAP) / (TILE_MIN_W + GAP)));
        // Spread evenly over rows rather than leaving one tile alone on the last.
        int tileRows = (tiles.size() + cols - 1) / cols;
        cols = (tiles.size() + tileRows - 1) / tileRows;
        // Where large tiles would not fit, beside a hero on a small window say, the actions become
        // cards like the auctions', which take a third of the height.
        if (tileRows * 64 + (tileRows - 1) * GAP > ah) {
            return drawActionCards(graphics, m, tiles, mx, my, inClip, ax, ay, aw, ah, cy, ch, index, now);
        }
        int tileW = Math.min(TILE_MAX_W, (aw - (cols - 1) * GAP) / cols);
        int tileH = Math.max(64, Math.min(TILE_H, (ah - (tileRows - 1) * GAP) / tileRows));
        int total = tileRows * tileH + (tileRows - 1) * GAP;
        int top = ay + Math.max(0, (ah - total) / 2) - contentScroll.offset();
        for (int r = 0; r < tileRows; r++) {
            int inRow = Math.min(cols, tiles.size() - r * cols);
            int rowW = inRow * tileW + (inRow - 1) * GAP;
            int left = ax + (aw - rowW) / 2;
            for (int c = 0; c < inRow; c++) {
                int slot = tiles.get(r * cols + c);
                int px = left + c * (tileW + GAP);
                int py = top + r * (tileH + GAP);
                float in = entrance(index++, now);
                int key = py >= cy && py + tileH <= cy + ch ? nextKey(slot) : 0;
                drawTile(graphics, slot, m.info.get(slot), px, py + Math.round((1 - in) * 6), tileW, tileH, mx, my, inClip, in, key);
            }
        }
        return total + 16;
    }

    private int drawActionCards(GuiGraphicsExtractor graphics, MarketMenu m, List<Integer> slots, int mx, int my, boolean inClip,
                                int ax, int ay, int aw, int ah, int cy, int ch, int index, long now) {
        return drawActionCards(graphics, m, slots, mx, my, inClip, ax, ay, aw, ah, cy, ch, index, now, CARD_MIN_W, CARD_H);
    }

    private int drawActionCards(GuiGraphicsExtractor graphics, MarketMenu m, List<Integer> slots, int mx, int my, boolean inClip,
                                int ax, int ay, int aw, int ah, int cy, int ch, int index, long now, int minW, int cardH) {
        // No more columns than cards, none wider than reads well, the row centred: a lone action
        // card is not stretched across the whole width.
        int cols = Math.max(1, Math.min(slots.size(), (aw + GAP) / (minW + GAP)));
        int cardW = Math.min(240, (aw - (cols - 1) * GAP) / cols);
        int rowW = cols * cardW + (cols - 1) * GAP;
        int left = ax + (aw - rowW) / 2;
        int cardRows = (slots.size() + cols - 1) / cols;
        int total = cardRows * (cardH + GAP) - GAP;
        int top = ay + Math.max(0, (ah - total) / 2) - contentScroll.offset();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            int px = left + (i % cols) * (cardW + GAP);
            int py = top + (i / cols) * (cardH + GAP);
            float in = entrance(index + i, now);
            drawCard(graphics, slot, m.info.get(slot), px, py + Math.round((1 - in) * 6), cardW, cardH, mx, my, inClip, in, cy, ch,
                    py >= cy && py + cardH <= cy + ch ? nextKey(slot) : 0);
        }
        return total + 16;
    }

    /** Keep current market data independent of the optional, expandable history. */
    private int drawDetailPage(GuiGraphicsExtractor graphics, MarketMenu m, List<Integer> actions, int mx, int my, boolean inClip,
                              int ax, int ay, int aw, int ah, int cy, int ch, int index, long now) {
        int heroW = Math.max(144, Math.min(210, Math.round(aw * 0.48f)));
        int rightX = ax + heroW + 8;
        int rightW = aw - heroW - 8;
        int bodyH = Math.max(ah, kind == MarketKind.BAZAAR ? 144 : 112);
        int top = ay - contentScroll.offset();
        boolean fits = drawHero(graphics, m.hero, m.info.get(m.hero), actions, ax, top, heroW, bodyH,
                mx, my, inClip, 1f, cy, ch, index, now);
        if (!fits && !actions.isEmpty()) {
            int actionsY = top + bodyH + 8;
            bodyH += drawActionCards(graphics, m, actions, mx, my, inClip, ax, actionsY, heroW, 120,
                    cy, ch, index, now) + 8;
        }

        ItemTags.Tag tag = AlpakaConfig.instance.allowApiCalls
                ? ItemTags.of(stack(m.hero), plain(m.hero).name(), kind)
                : new ItemTags.Tag(ItemTags.Lookup.UNKNOWN, null);
        List<MarketStats.Level> orders = null;
        List<MarketStats.Level> offers = null;
        if (kind == MarketKind.BAZAAR) {
            for (int slot : m.content) {
                String name = plain(slot).name().toLowerCase(Locale.ROOT);
                if (name.equals("create buy order")) orders = MarketStats.levels(plain(slot).lore());
                else if (name.equals("create sell offer")) offers = MarketStats.levels(plain(slot).lore());
            }
        }
        int statsH;
        if (orders != null || offers != null) {
            statsH = MarketStats.bookMaxHeight();
            MarketStats.drawOrderBook(graphics, this.font, orders == null ? List.of() : orders,
                    offers == null ? List.of() : offers, rightX, top, rightW, statsH);
        } else {
            statsH = MarketStats.auctionHeight(rightW);
            if (tag.tag() != null) {
                LoreInfo info = m.info.get(m.hero);
                Double price = info == null ? null : info.auctionPrice();
                if (price != null) price /= Math.max(1, stack(m.hero).getCount());
                MarketStats.drawAuctionStats(graphics, this.font, tag.tag(), price,
                        PriceHistory.Range.of(AlpakaConfig.instance.marketPriceHistoryRange), rightX, top, rightW, statsH);
            } else {
                ModernGuiUtils.drawRoundedRect(graphics, rightX, top, rightW, statsH, 5, AlpakaStyle.ROW);
                String note = AlpakaConfig.instance.allowApiCalls ? "Market Data Unavailable" : "Market Data Needs Network Features";
                int noteY = top + 10;
                for (FormattedText line : GuiFont.splitLines(this.font, note, rightW - 16)) {
                    graphics.text(this.font, Language.getInstance().getVisualOrder(line), rightX + 8, noteY, AlpakaStyle.TEXT_MUTED, false);
                    noteY += 11;
                }
            }
        }
        int rightH = statsH;
        if (PriceGraph.enabled()) {
            int by = top + statsH + 6;
            boolean hover = inClip && AlpakaStyle.inside(mx, my, rightX, by, rightW, 18);
            AlpakaStyle.button(graphics, this.font, rightX, by, rightW, 18,
                    historyOpen ? "Hide Price History" : "Show Price History", hover, AlpakaStyle.ButtonKind.PLAIN);
            hits.add(new Hit(rightX, by, rightW, 18, (b, s) -> historyOpen = !historyOpen, true));
            rightH += 24;
            if (historyOpen) {
                int graphY = by + 24;
                PriceGraph.draw(graphics, this.font, kind, tag, rightX, graphY, rightW, 112, mx, my,
                        (x, y, w, h, action, text) -> {
                            hits.add(new Hit(x, y, w, h, (b, s) -> action.run(), true));
                            if (text != null && inClip && AlpakaStyle.inside(mx, my, x, y, w, h)) tip("graph:" + x + ":" + y, text);
                        });
                rightH += 118;
            }
        }
        return Math.max(bodyH, rightH) + 8;
    }

    private TileKind tileKind(int slot) {
        MarketMenu.Slot s = plain(slot);
        String name = s.name().toLowerCase(Locale.ROOT);
        String id = s.itemId();
        if (name.startsWith("cancel") || id.equals("red_terracotta") || id.equals("barrier")) return TileKind.DANGER;
        // Selling the inventory or the sacks asks "Are you sure?": the other button is the yes.
        if (plainTitle.equals("Are you sure?")) return TileKind.PRIMARY;
        // The step that completes what the player came for: confirming, buying the item or bidding
        // when it can be afforded (Hypixel shows a gold nugget then, a poisonous potato if not),
        // collecting what was won or sold.
        if (name.startsWith("confirm") || name.startsWith("collect") || name.startsWith("claim")
                || (id.equals("gold_nugget") && (name.contains("buy") || name.contains("bid")))
                || id.equals("lime_terracotta") || id.equals("green_terracotta") || id.equals("emerald_block")) {
            return TileKind.PRIMARY;
        }
        return TileKind.PLAIN;
    }

    private void drawTile(GuiGraphicsExtractor graphics, int slot, LoreInfo info, int px, int py, int w, int h,
                          int mx, int my, boolean inClip, float alpha, int key) {
        boolean pressing = beginPress(graphics, slot, px, py, w, h);
        try {
            drawTileBody(graphics, slot, info, px, py, w, h, mx, my, inClip, alpha);
            if (key > 0) keyChip(graphics, key, px + w - 5, py + 5, alpha);
        } finally {
            if (pressing) graphics.pose().popMatrix();
        }
    }

    private void drawTileBody(GuiGraphicsExtractor graphics, int slot, LoreInfo info, int px, int py, int w, int h,
                              int mx, int my, boolean inClip, float alpha) {
        ItemStack stack = stack(slot);
        boolean hover = inClip && AlpakaStyle.inside(mx, my, px, py, w, h);
        if (hover) hoverTarget(slot);
        float hv = hoverProgress.getOrDefault(slot, 0f);
        TileKind kind = tileKind(slot);
        int tint = switch (kind) {
            case PRIMARY -> AlpakaStyle.accent();
            case DANGER -> AlpakaStyle.DANGER;
            default -> rarity(stack);
        };

        int bg = ModernGuiUtils.lerpColor(AlpakaStyle.ROW, AlpakaStyle.ROW_HOVER, hv);
        ModernGuiUtils.drawRoundedRect(graphics, px, py, w, h, AlpakaStyle.CARD_RADIUS + 1, MarketIcons.fade(bg, alpha));
        sheen(graphics, px, py, w, alpha);
        float edge = kind == TileKind.PLAIN ? 0.45f * hv : 0.35f + 0.45f * hv;
        if (edge > 0.01f) {
            ModernGuiUtils.drawRoundedOutline(graphics, px, py, w, h, AlpakaStyle.CARD_RADIUS + 1, MarketIcons.fade(tint, edge * alpha));
        }
        drawPressFlash(graphics, slot, px, py, w, h);

        // The item large, on a glow of its color that brightens on hover.
        int iconCx = px + w / 2;
        int iconCy = py + 26;
        WheelMesh glow = new WheelMesh(1.0f / guiScale());
        glow.radialGradient(iconCx, iconCy, 24f, MarketIcons.fade(tint, (0.22f + 0.14f * hv) * alpha), tint & 0x00FFFFFF);
        glow.submit(graphics);
        GuiItemFade.begin(alpha);
        drawScaledItem(graphics, stack, iconCx - 16, iconCy - 16 - Math.round(hv * 1.5f), 2.0f);
        GuiItemFade.end();

        int textAlpha = Math.round(alpha * 255) << 24;
        styledCentered(graphics, stack.getHoverName(), iconCx, py + 48, w - 10, 0x00FFFFFF | textAlpha);
        int ly = py + 61;
        int lines = 0;
        if (info != null) {
            for (LoreInfo.CoinLine line : info.coinLines) {
                if (ly + 9 > py + h) break;
                coinLineCentered(graphics, line, iconCx, ly, w - 10, alpha);
                ly += 11;
                if (++lines >= 2) break;
            }
        }
        if (lines == 0 && h >= 72) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.styledLines()) {
                    String plain = SkyblockUtils.cleanColor(line.getString()).trim();
                    if (plain.isEmpty() || plain.chars().allMatch(c -> c == '-' || c == '▬' || c == ' ')) continue;
                    if (ly + 9 > py + h - 2) break;
                    styledCentered(graphics, line, iconCx, ly, w - 10, AlpakaStyle.TEXT_MUTED & 0x00FFFFFF | textAlpha);
                    ly += 11;
                    if (++lines >= 2) break;
                }
            }
        }
        hits.add(new Hit(px, py, w, h, (b, s) -> click(slot, b, s), false));
        if (hover) hoveredStack = stack;
    }

    /** "Price per unit ● 5.3", centred. */
    private void coinLineCentered(GuiGraphicsExtractor graphics, LoreInfo.CoinLine line, int cx, int y, int maxW, float alpha) {
        String value = Coins.compact(line.coins());
        int valueW = GuiFont.width(this.font, value);
        String label = AlpakaStyle.ellipsize(this.font, line.label(), Math.max(0, maxW - valueW - 6));
        int labelW = GuiFont.width(this.font, label);
        int total = labelW + 5 + valueW;
        int x = cx - total / 2;
        graphics.text(this.font, GuiFont.text(label), x, y, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
        x += labelW + 5;
        graphics.text(this.font, GuiFont.text(value), x, y, MarketIcons.fade(MarketInk.ink(COIN), alpha), false);
    }

    /** A slim action button in the hero: the item, the name and the key that presses it. */
    private static final int HERO_BUTTON_H = 18;
    private static final int HERO_BUTTON_GAP = 3;

    /**
     * The one auction or product a page is about, with its prices or seller in rows; the whole lore
     * is its tooltip. Given actions, they go at its foot, numbered for the keys 1 to 9: as slim
     * buttons by default, or cards where there is ample room. Returns
     * whether the actions are drawn here; if even that does not fit, they are not.
     */
    private boolean drawHero(GuiGraphicsExtractor graphics, int slot, LoreInfo info, List<Integer> actions, int px, int py, int w, int h,
                             int mx, int my, boolean inClip, float alpha, int clipY, int clipH, int index, long now) {
        ItemStack stack = stack(slot);
        boolean hover = inClip && AlpakaStyle.inside(mx, my, px, py, w, h);
        int rarity = rarity(stack);

        int n = actions.size();
        boolean compact = h < 174;
        int rowStep = compact ? 12 : 15;
        int header = compact ? 24 : 28;
        int buttonH = compact ? 16 : HERO_BUTTON_H;
        int buttonGap = compact ? 1 : HERO_BUTTON_GAP;
        int groupH = compact ? 10 : 14;
        int rowsH = heroRows(info) * rowStep;
        int bottom = py + h - 6;
        int roomSmall = bottom - (py + header + rowsH + 2);
        int buttonsH = n * (buttonH + buttonGap) - buttonGap;
        int pairsH = (n + 1) / 2 * (buttonH + buttonGap) - buttonGap;
        boolean buttons = true;
        int cols = 1;
        boolean fits = n > 0;
        boolean grouped = kind == MarketKind.BAZAAR && actions.size() == 4
                && actions.stream().allMatch(action -> List.of("buy instantly", "sell instantly", "create buy order", "create sell offer")
                .contains(plain(action).name().toLowerCase(Locale.ROOT)));
        if (grouped) {
            actions = new ArrayList<>(actions);
            actions.sort(java.util.Comparator.comparingInt(action -> List.of("buy instantly", "create buy order", "sell instantly", "create sell offer")
                    .indexOf(plain(action).name().toLowerCase(Locale.ROOT))));
            buttonsH += groupH * 2;
        }
        if (grouped && buttonsH <= roomSmall) {
            // Buy and Sell each keep their instant and order actions together.
        } else if (!grouped && (n == 0 || (roomSmall >= 160 && n * (CARD_H + GAP) - GAP <= roomSmall))) {
            buttons = false;
        } else if (buttonsH <= roomSmall) {
            // Slim actions leave room for the item's data above them.
        } else if (!grouped && pairsH <= roomSmall && (w - 12 - HERO_BUTTON_GAP) / 2 >= 72) {
            cols = 2;
        } else {
            fits = false;
        }

        ModernGuiUtils.drawRoundedRect(graphics, px, py, w, h, AlpakaStyle.CARD_RADIUS + 1, MarketIcons.fade(AlpakaStyle.ROW, alpha));
        ModernGuiUtils.drawRoundedOutline(graphics, px, py, w, h, AlpakaStyle.CARD_RADIUS + 1, MarketIcons.fade(rarity, 0.3f * alpha));

        int iconCx = px + w / 2;
        int textAlpha = Math.round(alpha * 255) << 24;
        int nameW = Math.min(w - 36, this.font.width(GuiFont.restyle(stack.getHoverName())));
        int left = iconCx - (16 + 4 + nameW) / 2;
        GuiItemFade.begin(alpha);
        graphics.item(stack, left, py + 6);
        GuiItemFade.end();
        styled(graphics, stack.getHoverName(), left + 20, py + 10, nameW, 0x00FFFFFF | textAlpha);
        int ry = py + header;

        int rowX = px + 8;
        int rowW = w - 16;
        if (info != null && info.type == LoreInfo.Type.AUCTION) {
            Double price = info.auctionPrice();
            if (price != null) {
                ry = heroRow(graphics, info.isBin() ? "Buy It Now" : info.topBid != null ? "Top Bid" : "Starting Bid",
                        Coins.full(price), MarketInk.ink(COIN), true, rowX, ry, rowW, alpha, rowStep);
            }
            if (info.seller != null) ry = heroRow(graphics, "Seller", withoutRank(info.seller), AlpakaStyle.TEXT, false, rowX, ry, rowW, alpha, rowStep);
            if (info.bids != null) ry = heroRow(graphics, "Bids", String.valueOf(info.bids), AlpakaStyle.TEXT, false, rowX, ry, rowW, alpha, rowStep);
            if (info.endsIn != null) {
                long seconds = Coins.seconds(info.endsIn);
                ry = heroRow(graphics, "Ends In", info.endsIn, seconds >= 0 && seconds < 300 ? MarketInk.ink(RED) : AlpakaStyle.TEXT, false, rowX, ry, rowW, alpha, rowStep);
            }
            if (info.status != null) ry = heroRow(graphics, "Status", info.status, info.isOver() ? MarketInk.ink(RED) : MarketInk.ink(GREEN), false, rowX, ry, rowW, alpha, rowStep);
        } else if (info != null && info.type == LoreInfo.Type.PRODUCT) {
            if (info.buyPrice != null) ry = heroRow(graphics, "Buy Price", Coins.full(info.buyPrice), MarketInk.ink(COIN), true, rowX, ry, rowW, alpha, rowStep);
            if (info.sellPrice != null) ry = heroRow(graphics, "Sell Price", Coins.full(info.sellPrice), MarketInk.ink(COIN), true, rowX, ry, rowW, alpha, rowStep);
        }
        if (info != null && info.type == LoreInfo.Type.AUCTION) {
            int actionsHeight = fits ? ((n + cols - 1) / cols) * ((buttons ? buttonH : CARD_H) + (buttons ? buttonGap : GAP)) : 0;
            int lines = Math.min(4, (bottom - actionsHeight - ry - 10) / 11);
            if (lines > 0) loreLines(graphics, stack, rowX + 2, ry + 6, rowW - 4, lines, alpha);
        }
        // Before the actions, which are drawn over the card and take their own clicks and tooltips.
        hits.add(new Hit(px, py, w, h, (b, s) -> click(slot, b, s), false));
        if (hover) hoveredStack = stack;
        if (!fits) return false;

        int x = px + 6;
        int aw = w - 12;
        int itemH = buttons ? buttonH : CARD_H;
        int gap = buttons ? buttonGap : GAP;
        int itemW = (aw - (cols - 1) * gap) / cols;
        int rows = (n + cols - 1) / cols;
        int y0 = bottom - (rows * (itemH + gap) - gap);
        if (grouped) y0 -= groupH * 2;
        for (int i = 0; i < n; i++) {
            int action = actions.get(i);
            int ax = x + (i % cols) * (itemW + gap);
            int ay = y0 + (i / cols) * (itemH + gap);
            if (grouped) {
                ay += i < 2 ? groupH : groupH * 2;
                if (i == 0 || i == 2) {
                    graphics.text(this.font, GuiFont.text(i == 0 ? "Buy" : "Sell"), ax + 2, ay - groupH + 1,
                            i == 0 ? MarketInk.ink(GREEN) : MarketInk.ink(COIN), false);
                }
            }
            int key = ay >= clipY && ay + itemH <= clipY + clipH ? nextKey(action) : 0;
            float in = entrance(index + i, now);
            ay += Math.round((1 - in) * 6);
            if (buttons) {
                drawHeroButton(graphics, action, ax, ay, itemW, itemH, key, mx, my, inClip, in);
            } else {
                drawCard(graphics, action, model.info.get(action), ax, ay, itemW, itemH, mx, my, inClip, in, clipY, clipH, key);
            }
        }
        return true;
    }

    /**
     * The next number key, 1 to 9, for an action drawn this frame, in the order they are drawn;
     * 0 once all nine are taken.
     */
    private int nextKey(int slot) {
        if (keyActions.size() >= 9 || !Markets.hotkeys(kind)) return 0;
        keyActions.add(slot);
        return keyActions.size();
    }

    /**
     * The key of one of the Auction House's extra buttons, as its chip shows it, or null when the
     * button has none: Create Auction, the one thing done from Manage Auctions besides looking (C
     * unless rebound), and Reset Settings for the browser's filters (R unless rebound). Remembers
     * the button for the key.
     */
    private String extraKey(int slot) {
        if (kind != MarketKind.AUCTION_HOUSE || !Markets.hotkeys(kind)) return null;
        String name = plain(slot).name().trim();
        MarketKeys.Set keys = MarketKeys.of(kind);
        KeyMapping mapping;
        if (name.equalsIgnoreCase("Create Auction") && keyCreateAuction == null) {
            mapping = keys.createAuction();
            if (mapping == null || mapping.isUnbound()) return null;
            keyCreateAuction = slot;
        } else if (name.equalsIgnoreCase("Reset Settings") && keyResetSettings == null) {
            mapping = keys.resetSettings();
            if (mapping == null || mapping.isUnbound()) return null;
            keyResetSettings = slot;
        } else {
            return null;
        }
        return mapping.getTranslatedKeyMessage().getString();
    }

    /** The next key, 1 to 9, that picks a category with Shift; 0 once all nine are taken. */
    private int nextCategoryKey(int slot) {
        if (keyCategories.size() >= 9 || !Markets.hotkeys(kind)) return 0;
        keyCategories.add(slot);
        return keyCategories.size();
    }

    /** The key's number in a small chip ending at right, its text at y. Returns where the chip starts. */
    private int keyChip(GuiGraphicsExtractor graphics, int key, int right, int y, float alpha) {
        return keyChip(graphics, String.valueOf(key), right, y, alpha);
    }

    private int keyChip(GuiGraphicsExtractor graphics, String label, int right, int y, float alpha) {
        int kw = GuiFont.width(this.font, label) + 6;
        int kx = right - kw;
        ModernGuiUtils.drawRoundedRect(graphics, kx, y - 2, kw, 11, 3, MarketIcons.fade(AlpakaStyle.ROW_SELECTED, alpha));
        graphics.text(this.font, GuiFont.text(label), kx + 3, y, MarketIcons.fade(AlpakaStyle.TEXT_SOFT, alpha), false);
        return kx;
    }

    /** How many rows of prices, seller and time the hero shows for this item. */
    private static int heroRows(LoreInfo info) {
        if (info == null) return 0;
        if (info.type == LoreInfo.Type.AUCTION) {
            int rows = 0;
            if (info.auctionPrice() != null) rows++;
            if (info.seller != null) rows++;
            if (info.bids != null) rows++;
            if (info.endsIn != null) rows++;
            if (info.status != null) rows++;
            return rows;
        }
        if (info.type == LoreInfo.Type.PRODUCT) return (info.buyPrice != null ? 1 : 0) + (info.sellPrice != null ? 1 : 0);
        return 0;
    }

    private void drawHeroButton(GuiGraphicsExtractor graphics, int slot, int x, int y, int w, int h, int key,
                                int mx, int my, boolean inClip, float alpha) {
        boolean pressing = beginPress(graphics, slot, x, y, w, h);
        try {
            ItemStack stack = stack(slot);
            boolean hover = inClip && AlpakaStyle.inside(mx, my, x, y, w, h);
            if (hover) hoverTarget(slot);
            float hv = hoverProgress.getOrDefault(slot, 0f);
            int bg = ModernGuiUtils.lerpColor(AlpakaStyle.PANEL, AlpakaStyle.ROW_HOVER, hv);
            ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(bg, alpha));
            int tint = rarity(stack);
            if (hv > 0.01f) {
                ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(tint, 0.45f * hv * alpha));
            }
            drawPressFlash(graphics, slot, x, y, w, h);
            GuiItemFade.begin(alpha);
            graphics.item(stack, x + 3, y + (h - 16) / 2);
            GuiItemFade.end();
            int textY = y + (h - 8) / 2;
            int end = x + w - 5;
            if (key > 0) end = keyChip(graphics, key, end, textY, alpha) - 4;
            styled(graphics, stack.getHoverName(), x + 22, textY, end - x - 22, 0x00FFFFFF | (Math.round(alpha * 255) << 24));
            hits.add(new Hit(x, y, w, h, (b, s) -> click(slot, b, s), false));
            if (hover) hoveredStack = stack;
        } finally {
            if (pressing) graphics.pose().popMatrix();
        }
    }

    private int heroRow(GuiGraphicsExtractor graphics, String label, String value, int color, boolean coin,
                        int x, int y, int w, float alpha, int step) {
        ModernGuiUtils.drawRoundedRect(graphics, x - 2, y - 3, w + 4, step - 1, 3, MarketIcons.fade(AlpakaStyle.PANEL, 0.55f * alpha));
        graphics.text(this.font, GuiFont.text(label), x + 2, y, MarketIcons.fade(AlpakaStyle.TEXT_MUTED, alpha), false);
        int labelW = GuiFont.width(this.font, label);
        int room = w - labelW - 16;
        // A full amount that does not fit is written short rather than cut: "1.14B", not "1,140,0…".
        if (coin && GuiFont.width(this.font, value) > room) {
            Double amount = Coins.parseLore(value);
            if (amount != null) value = Coins.compact(amount);
        }
        String shown = AlpakaStyle.ellipsize(this.font, value, room);
        int vw = GuiFont.width(this.font, shown);
        int vx = x + w - 2 - vw;
        graphics.text(this.font, GuiFont.text(shown), vx, y, MarketIcons.fade(color, alpha), false);
        return y + step;
    }

    // ---- Footer ---------------------------------------------------------------------------------

    private String extraLabel(int slot) {
        String name = plain(slot).name();
        return name.equals("Advanced Search") ? "Advanced" : AlpakaStyle.ellipsize(this.font, name, 90);
    }

    private boolean extrasInFooter(MarketMenu m) {
        if (m.extrasInFooter()) return true;
        if (m.extras.size() > 2) return false;
        int used = PAD * 2 + footButtonWidth("Classic") + footButtonWidth("Inventory") + 16;
        if (m.layout == MarketMenu.Layout.GRID && !m.content.isEmpty()) used += GuiFont.width(this.font, countLabel(m)) + 8;
        if (m.controls.containsKey(MarketMenu.Control.NEXT) || m.controls.containsKey(MarketMenu.Control.PREVIOUS)) {
            String page = pageLabel(m);
            used += 46 + GuiFont.width(this.font, page);
        }
        for (int slot : m.extras) used += GuiFont.width(this.font, extraLabel(slot)) + 30
                + (plain(slot).name().equals("Create Auction") || plain(slot).name().equals("Reset Settings") ? 16 : 0);
        return used <= panelW();
    }

    private String pageLabel(MarketMenu m) {
        return m.page > 0 ? (m.pages > 0 ? "Page " + m.page + " of " + m.pages : "Page " + m.page) : "Page";
    }

    private String keyHint(KeyMapping mapping) {
        return Markets.hotkeys(kind) && !mapping.isUnbound() ? " · " + mapping.getTranslatedKeyMessage().getString() : "";
    }

    private void drawFooter(GuiGraphicsExtractor graphics, MarketMenu m, int mx, int my, int x, int y, int w) {
        ModernGuiUtils.drawRect(graphics, x + PAD, y, w - PAD * 2, 1, AlpakaStyle.LINE);
        int by = y + (FOOTER_H - 16) / 2 + 1;
        int lx = x + PAD;
        Integer previous = m.controls.get(MarketMenu.Control.PREVIOUS);
        Integer next = m.controls.get(MarketMenu.Control.NEXT);
        if (previous != null || next != null) {
            boolean hoverPrev = previous != null && AlpakaStyle.inside(mx, my, lx, by, 16, 16);
            AlpakaStyle.chevronButton(graphics, lx, by, 16, true, hoverPrev, previous != null);
            if (previous != null) {
                hits.add(new Hit(lx, by, 16, 16, (b, s) -> click(previous, b, s), false));
                if (hoverPrev) tip("previous", "Previous Page" + keyHint(MarketKeys.of(kind).previousPage()));
            }
            lx += 20;
            String page = pageLabel(m);
            graphics.text(this.font, GuiFont.text(page), lx, by + 4, AlpakaStyle.TEXT_SOFT, false);
            lx += GuiFont.width(this.font, page) + 4;
            boolean hoverNext = next != null && AlpakaStyle.inside(mx, my, lx, by, 16, 16);
            AlpakaStyle.chevronButton(graphics, lx, by, 16, false, hoverNext, next != null);
            if (next != null) {
                int nx = lx;
                hits.add(new Hit(nx, by, 16, 16, (b, s) -> click(next, b, s), false));
                if (hoverNext) tip("next", "Next Page" + keyHint(MarketKeys.of(kind).nextPage()));
            }
            lx += 22;
        }
        if (!m.empty && !m.content.isEmpty() && m.layout == MarketMenu.Layout.GRID) {
            String count = countLabel(m);
            graphics.text(this.font, GuiFont.text(count), lx, by + 4, AlpakaStyle.TEXT_MUTED, false);
            lx += GuiFont.width(this.font, count) + 8;
        }

        // On the right: the inventory drawer and the way back to the vanilla chest.
        int bw = footButtonWidth("Classic");
        int rx = x + w - PAD - bw;
        boolean hoverClassic = AlpakaStyle.inside(mx, my, rx, by, bw, 16);
        footButton(graphics, rx, by, bw, MarketIcons.Glyph.CLASSIC, "Classic", hoverClassic, false);
        hits.add(new Hit(rx, by, bw, 16, (b, s) -> CLASSIC.add(kind), true));
        if (hoverClassic) tip("classic", "Show Hypixel's menu. Alpaka View switches back.");
        if (confirmation(m)) return;
        int iw = footButtonWidth("Inventory");
        int ix = rx - 4 - iw;
        boolean hoverInv = AlpakaStyle.inside(mx, my, ix, by, iw, 16);
        footButton(graphics, ix, by, iw, MarketIcons.Glyph.INVENTORY, "Inventory", hoverInv, inventoryOpen);
        hits.add(new Hit(ix, by, iw, 16, (b, s) -> inventoryOpen = !inventoryOpen, true));

        // A page's one or two extra buttons, such as the Bazaar's View Graphs, before them.
        if (extrasInFooter(m)) {
            int ex = ix - 8;
            for (int slot : m.extras) {
                String label = extraLabel(slot);
                String keyLabel = extraKey(slot);
                int ew = GuiFont.width(this.font, label) + 26 + (keyLabel != null ? GuiFont.width(this.font, keyLabel) + 8 : 0);
                ex -= ew;
                if (ex < lx + 4) break;
                boolean hover = AlpakaStyle.inside(mx, my, ex, by, ew, 16);
                ModernGuiUtils.drawRoundedRect(graphics, ex, by, ew, 16, AlpakaStyle.ROW_RADIUS, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
                graphics.pose().pushMatrix();
                graphics.pose().translate(ex + 4, by + 2);
                graphics.pose().scale(0.75f, 0.75f);
                graphics.item(stack(slot), 0, 0);
                graphics.pose().popMatrix();
                graphics.text(this.font, GuiFont.text(label), ex + 18, by + 4, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
                if (keyLabel != null) keyChip(graphics, keyLabel, ex + ew - 4, by + 4, 1f);
                hits.add(new Hit(ex, by, ew, 16, (b, s) -> click(slot, b, s), false));
                if (hover) hoveredStack = stack(slot);
                ex -= 4;
            }
        }
    }

    private String countLabel(MarketMenu m) {
        int n = m.content.size();
        LoreInfo.Type type = LoreInfo.Type.GENERIC;
        for (int slot : m.content) {
            LoreInfo info = m.info.get(slot);
            if (info != null && info.isRich()) {
                type = info.type;
                break;
            }
        }
        String noun = switch (type) {
            case AUCTION -> n == 1 ? "auction" : "auctions";
            case PRODUCT -> n == 1 ? "product" : "products";
            case ORDER -> n == 1 ? "order" : "orders";
            default -> n == 1 ? "option" : "options";
        };
        return n + " " + noun;
    }

    private int footButtonWidth(String label) {
        return GuiFont.width(this.font, label) + 26;
    }

    private void footButton(GuiGraphicsExtractor graphics, int x, int y, int w, MarketIcons.Glyph glyph, String label,
                            boolean hover, boolean active) {
        int accent = AlpakaStyle.accent();
        int bg = active ? MarketIcons.fade(accent, hover ? 0.35f : 0.22f) : hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, 16, AlpakaStyle.ROW_RADIUS, bg);
        int color = active ? (hover ? AlpakaStyle.TEXT : accent) : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
        MarketIcons.draw(graphics, glyph, x + 6, y + 4, 8, color);
        graphics.text(this.font, GuiFont.text(label), x + 18, y + 4, color, false);
    }

    // ---- The inventory drawer -------------------------------------------------------------------

    private void drawDrawer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        int x = drawerX;
        int y = drawerY;
        AlpakaStyle.popover(graphics, x, y, drawerW, drawerH);
        AlpakaStyle.groupLabel(graphics, this.font, "Inventory", x + 8, y + 6);
        // The whole drawer takes the click, so one between two cells does not fall through to a card.
        hits.add(new Hit(x, y, drawerW, drawerH, (b, s) -> { }, false));
        int first = rows * 9;
        for (int i = 0; i < 36; i++) {
            int slot = first + i;
            if (slot >= menu.slots.size()) break;
            int cx = x + 6 + (i % 9) * CELL;
            int cy = y + 18 + (i / 9) * CELL + (i >= 27 ? 4 : 0);
            boolean hover = AlpakaStyle.inside(mouseX, mouseY, cx, cy, CELL - 1, CELL - 1);
            ModernGuiUtils.drawRoundedRect(graphics, cx, cy, CELL - 1, CELL - 1, AlpakaStyle.FIELD_RADIUS,
                    hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.FIELD);
            ItemStack stack = stack(slot);
            if (!stack.isEmpty()) {
                graphics.item(stack, cx, cy);
                graphics.itemDecorations(this.font, stack, cx, cy);
            }
            drawPressFlash(graphics, slot, cx, cy, CELL - 1, CELL - 1);
            hits.add(new Hit(cx, cy, CELL - 1, CELL - 1, (b, s) -> click(slot, b, s), false));
            if (hover && !stack.isEmpty()) hoveredStack = stack;
        }
    }

    // ---- Classic view ---------------------------------------------------------------------------

    /** In classic view, a small button beside the vanilla chest leads back to this screen. */
    private void drawClassicToggle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String label = "Alpaka View";
        int w = footButtonWidth(label);
        int x = this.leftPos + this.imageWidth + 6;
        int y = this.topPos;
        if (x + w > this.width - 4) {
            x = this.leftPos + this.imageWidth - w;
            y = this.topPos - 20;
        }
        boolean hover = AlpakaStyle.inside(mouseX, mouseY, x, y, w, 16);
        graphics.nextStratum();
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, 16, AlpakaStyle.ROW_RADIUS, 0.6f);
        footButton(graphics, x, y, w, MarketIcons.of(kind), label, hover, true);
        hits.add(new Hit(x, y, w, 16, (b, s) -> CLASSIC.remove(kind), true));
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private void tip(Object key, String text) {
        hoveredTipKey = key;
        hoveredTip = text;
    }

    private final java.util.Set<Integer> hoveredThisFrame = new java.util.HashSet<>();

    private void hoverTarget(int slot) {
        hoveredThisFrame.add(slot);
    }

    /** Eases each card's hover towards whether the mouse is on it. */
    private void updateHover(float deltaSec) {
        for (int slot : hoveredThisFrame) hoverProgress.putIfAbsent(slot, 0f);
        java.util.Iterator<Map.Entry<Integer, Float>> it = hoverProgress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> e = it.next();
            float target = hoveredThisFrame.contains(e.getKey()) ? 1f : 0f;
            float v = PloppAnimation.interpolate(e.getValue(), target, deltaSec, 18.0f);
            if (target == 0f && v < 0.01f) it.remove();
            else e.setValue(v);
        }
        hoveredThisFrame.clear();
    }

    /** A clicked control lights up in the accent for a moment, so the click is seen to have gone out. */
    private void drawPressFlash(GuiGraphicsExtractor graphics, int slot, int x, int y, int w, int h) {
        if (slot != pressedSlot) return;
        long since = System.currentTimeMillis() - pressedAtMs;
        if (since > 320) return;
        float strength = 1f - since / 320f;
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), strength));
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, AlpakaStyle.CARD_RADIUS, MarketIcons.fade(AlpakaStyle.accent(), 0.12f * strength));
    }

    /** The item drawn larger: the pose carries the scale, which the item renderer takes into account. */
    private void drawScaledItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    /** Text in its own colors and the menu font, cut with an ellipsis to fit. */
    private void styled(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxW, int color) {
        Component restyled = GuiFont.restyle(text);
        if (this.font.width(restyled) <= maxW) {
            graphics.text(this.font, restyled, x, y, color, false);
            return;
        }
        Component dots = GuiFont.text("…");
        FormattedText cut = this.font.substrByWidth(restyled, Math.max(0, maxW - this.font.width(dots)));
        graphics.text(this.font, Language.getInstance().getVisualOrder(cut), x, y, color, false);
        graphics.text(this.font, dots, x + this.font.width(cut), y, AlpakaStyle.TEXT_MUTED & 0x00FFFFFF | (color & 0xFF000000), false);
    }

    private void styledCentered(GuiGraphicsExtractor graphics, Component text, int cx, int y, int maxW, int color) {
        int w = Math.min(this.font.width(GuiFont.restyle(text)), maxW);
        styled(graphics, text, cx - w / 2, y, maxW, color);
    }

    /**
     * The item's rarity color, from the color of its name, made a little brighter where Hypixel's
     * darker tones would vanish on the dark panel. Used for the card's edge and glow; the name
     * itself keeps Hypixel's own color.
     */
    private int rarity(ItemStack stack) {
        Integer cached = rarityCache.get(stack);
        if (cached != null) return cached;
        int color = vivid(nameColor(stack.getHoverName()));
        rarityCache.put(stack, color);
        return color;
    }

    public static int nameColor(Component name) {
        int[] found = {0xFFFFFF};
        name.visit((style, text) -> {
            if (text.isBlank()) return Optional.empty();
            int legacy = text.indexOf('§');
            if (legacy >= 0 && legacy + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(text.charAt(legacy + 1));
                net.minecraft.network.chat.TextColor color = format == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(format);
                if (color != null) {
                    found[0] = color.getValue();
                    return Optional.of(Boolean.TRUE);
                }
            }
            if (style.getColor() != null) found[0] = style.getColor().getValue();
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        return found[0];
    }

    public static int vivid(int rgb) {
        return MarketInk.ink(vividOnDark(rgb));
    }

    private static int vividOnDark(int rgb) {
        return switch (rgb & 0xFFFFFF) {
            case 0xFFFFFF -> MarketInk.light() ? AlpakaStyle.TEXT : 0xFFD8D8D8;
            case 0x55FF55 -> 0xFF5BE38A;
            case 0x5555FF -> 0xFF6E8BFF;
            case 0xAA00AA -> 0xFFC264F2;
            case 0xFFAA00 -> 0xFFFFB347;
            case 0xFF55FF -> 0xFFFF73E3;
            case 0x55FFFF -> 0xFF5CE1F0;
            case 0xFF5555 -> 0xFFFF6B6B;
            case 0xAA0000 -> 0xFFE04848;
            default -> rgb | 0xFF000000;
        };
    }

    /** "5h 3m 12s" as "5h 3m": the two largest parts are what matters on a card. */
    static String shortTime(String time) {
        String[] parts = time.trim().split("\\s+");
        if (parts.length <= 2) return time.trim();
        return parts[0] + " " + parts[1];
    }

    /** "[MVP+] Alpaka05" as "Alpaka05". */
    static String withoutRank(String name) {
        return name.replaceFirst("^\\[[^\\]]*\\]\\s*", "").trim();
    }

    private static boolean visible(int y, int h, int clipY, int clipH) {
        return y + h > clipY && y < clipY + clipH;
    }

    private static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 2 : (int) Math.max(1, mc.getWindow().getGuiScale());
    }

    @Override
    public void removed() {
        Markets.left(kind);
        super.removed();
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // A market key bound to a side button of the mouse, say back and forward for the pages.
        if (!classic() && event.button() > 1 && model != null && Markets.hotkeys(kind)) {
            Integer target = boundTarget(mapping -> mapping.matchesMouse(event));
            if (target != null) {
                AlpakaStyle.click();
                click(target, 0, false, Outbound.Cause.KEY_PRESS);
                return true;
            }
        }
        // Front-most first: later hits were drawn on top.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (classic() && !hit.sound()) continue;
            if (hit.contains(event.x(), event.y())) {
                if (event.button() != 0 && event.button() != 1) return true;
                if (hit.sound()) AlpakaStyle.click();
                hit.action().run(event.button(), event.hasShiftDown());
                return true;
            }
        }
        if (classic()) return super.mouseClicked(event, doubleClick);
        // Anywhere else on this screen a click does nothing: the vanilla slots underneath are not
        // where they are drawn, and must not be clicked by accident.
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroll = false;
        return classic() ? super.mouseReleased(event) : true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!classic() && draggingScroll && event.button() == 0 && contentScroll.max > 0) {
            double ratio = (event.y() - scrollTrackY - scrollThumbH / 2.0) / Math.max(1, scrollTrackH - scrollThumbH);
            contentScroll.target = Math.max(0, Math.min(contentScroll.max, ratio * contentScroll.max));
            contentScroll.current = contentScroll.target;
            return true;
        }
        return classic() ? super.mouseDragged(event, deltaX, deltaY) : true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (classic()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (scrollY == 0) return true;
        if (inventoryOpen && AlpakaStyle.inside(mouseX, mouseY, drawerX, drawerY, drawerW, drawerH)) return true;
        if (sideW > 0 && mouseX < sideX + sideW) sideScroll.by(-scrollY * 20);
        else if (AlpakaStyle.inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) contentScroll.by(-scrollY * 24);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (classic()) return super.keyPressed(event);
        // Keys close the menu or press a button. Vanilla's number keys and drop key would
        // click the slot under the mouse, which here is not the one drawn there, so they never reach
        // it. Any other key is left to the game, so a screenshot or fullscreen still works.
        if (event.isEscape() || (this.minecraft != null && this.minecraft.options.keyInventory.matches(event))) {
            onClose();
            return true;
        }
        Integer target = Markets.hotkeys(kind) ? hotkeyTarget(event) : null;
        if (target != null) {
            // One plain click per press, as the mouse would; holding the key does not repeat it.
            if (HELD_KEYS.add(event.key())) {
                AlpakaStyle.click();
                click(target, 0, false, Outbound.Cause.KEY_PRESS);
            }
            return true;
        }
        return false;
    }

    /**
     * The slot a key presses: 1 to 9 the numbered actions or auctions, Shift with 1 to 9 the
     * categories, Ctrl or Cmd with F the search, and the market's own keys (MarketKeys): the arrows
     * the page, Backspace the way back, C Create Auction, R Reset Settings, unless rebound in the
     * settings.
     */
    private Integer hotkeyTarget(KeyEvent event) {
        if (model == null) return null;
        int mods = event.modifiers();
        boolean command = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        int key = event.key();
        if (command && !alt && !shift) return key == GLFW.GLFW_KEY_F ? model.controls.get(MarketMenu.Control.SEARCH) : null;
        if (command || alt) return null;
        int digit = digit(key);
        if (digit >= 0) {
            List<Integer> targets = shift ? keyCategories : keyActions;
            return digit < targets.size() ? targets.get(digit) : null;
        }
        if (shift) return null;
        return boundTarget(mapping -> mapping.matches(event));
    }

    /** The slot the market's key that matches presses, or null when none does or its button is not shown. */
    private Integer boundTarget(java.util.function.Predicate<KeyMapping> test) {
        MarketKeys.Set keys = MarketKeys.of(kind);
        java.util.function.Predicate<KeyMapping> matches = mapping -> !mapping.isUnbound() && test.test(mapping);
        if (matches.test(keys.previousPage())) return model.controls.get(MarketMenu.Control.PREVIOUS);
        if (matches.test(keys.nextPage())) return model.controls.get(MarketMenu.Control.NEXT);
        if (matches.test(keys.back())) return model.controls.get(MarketMenu.Control.BACK);
        if (matches.test(keys.search())) return model.controls.get(MarketMenu.Control.SEARCH);
        if (keys.createAuction() != null && matches.test(keys.createAuction())) return keyCreateAuction;
        if (keys.resetSettings() != null && matches.test(keys.resetSettings())) return keyResetSettings;
        return null;
    }

    /** "/" opens the search; read as a character, so it works wherever the keyboard has it. */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (classic()) return super.charTyped(event);
        if (event.codepoint() == '/' && model != null && Markets.hotkeys(kind)) {
            Integer search = model.controls.get(MarketMenu.Control.SEARCH);
            if (search != null) {
                AlpakaStyle.click();
                click(search, 0, false, Outbound.Cause.KEY_PRESS);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        HELD_KEYS.remove(event.key());
        return super.keyReleased(event);
    }

    /** 0 for the key 1 (on the number row or the keypad) up to 8 for 9; -1 for any other key. */
    private static int digit(int key) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return key - GLFW.GLFW_KEY_1;
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) return key - GLFW.GLFW_KEY_KP_1;
        return -1;
    }

    /**
     * The item under the mouse in the last frame, as this screen draws it, for the Item Value key.
     * Empty in classic view, where the slots under the mouse are vanilla's own.
     */
    public ItemStack hoveredItem() {
        return classic() ? ItemStack.EMPTY : hoveredStack;
    }

    // ---- For the screenshot tour ----------------------------------------------------------------

    /**
     * For the screenshot tour: a click on a slot as Hypixel answers it - the item leaves the slot
     * for the cursor - without anything being sent.
     */
    void devPressLikeHypixel(int slot) {
        pressedStack = stack(slot).copy();
        pressedSlot = slot;
        pressedAtMs = System.currentTimeMillis();
        menu.getSlot(slot).set(ItemStack.EMPTY);
    }

    static void devInventory(boolean open) {
        inventoryOpen = open;
    }

    static void devHistory(boolean open) {
        historyOpen = open;
    }

    static void devClassic(MarketKind kind, boolean classic) {
        if (classic) CLASSIC.add(kind);
        else CLASSIC.remove(kind);
    }

    /** Opens the screen on a chest filled locally, for the screenshot tour; nothing is sent from it. */
    public static MarketScreen preview(ChestMenu menu, Inventory inventory, String title, MarketKind kind) {
        MarketScreen screen = new MarketScreen(menu, inventory, Component.literal(title), kind);
        screen.continued = true;
        return screen;
    }
}
