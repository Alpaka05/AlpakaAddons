package net.alpaka.addons.features.storagepreview;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor;
import net.alpaka.addons.utils.TextInputFocus;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.List;

/**
 * The Storage Preview's freeze key: pressed over a backpack or ender chest icon in the Storage menu,
 * it pins that icon's tooltip where it is on screen, so the mouse can move onto the remembered items
 * and show each one's own tooltip without opening the page. Pressed again, or when the menu closes,
 * the pinned tooltip goes away; pressed over another page's icon, that page is pinned instead.
 *
 * While a tooltip is pinned, the menu's own tooltips stay hidden: on the way from the icon to the
 * pinned tooltip the mouse passes over other pages, whose tooltips would pop up over it. The pinned
 * tooltip is drawn here, at a fixed place, rather than handed to the game's tooltip code each frame,
 * which would place it again by the mouse (and Scrollable Tooltips would treat it and an item's
 * tooltip drawn over it as one tooltip changing size every frame).
 *
 * Unbound until the player picks a key. Only draws what is remembered; nothing is sent to the server.
 */
public final class StoragePreviewFreeze {
    private StoragePreviewFreeze() {}

    public static KeyMapping KEY;

    /** Gap kept between the tooltip and the screen edge, like the game's tooltip positioner. */
    private static final int EDGE = 4;
    /** Room around the tooltip's content that still counts as on it: its frame is drawn outside. */
    private static final int FRAME = 4;
    /** How far one notch of the wheel moves a pinned tooltip taller than the screen. */
    private static final int SCROLL_STEP = 12;
    private static final int HOVER_COLOR = 0x80FFFFFF;

    /** The menu the pinned tooltip belongs to, or null while nothing is pinned. */
    private static Screen screen = null;
    private static List<ClientTooltipComponent> components = List.of();
    private static StoragePreviewTooltip preview = null;
    private static Identifier style = null;
    /** Where the tooltip's content starts, as pinned, and its size, as the game's tooltip code measures it. */
    private static int pinnedX, pinnedY, width, height;
    /** Where it was drawn last, kept on screen. */
    private static int boxX, boxY;

    public static void register() {
        KEY = AlpakaKeyBinds.register(new KeyMapping("key.alpaka.storage_preview_freeze", InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(), AlpakaKeyCategory.CATEGORY));
        ScreenEvents.AFTER_INIT.register((client, s, w, h) -> {
            if (!(s instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.allowKeyPress(s).register((in, event) -> !KEY.matches(event) || !keyPressed(in));
            ScreenMouseEvents.allowMouseClick(s).register((in, event) -> {
                if (KEY.matchesMouse(event)) return !keyPressed(in);
                // A click on the pinned tooltip does nothing, rather than clicking the slot under it.
                return !coversMouse(in);
            });
            ScreenMouseEvents.allowMouseScroll(s).register((in, mx, my, h2, v) -> !scrolled(in, v));
            ScreenEvents.remove(s).register(in -> {
                if (in == screen) clear();
            });
        });
    }

    /** Whether a key repeating is the freeze key held down in a menu, which acts once per press only. */
    public static boolean swallowsRepeat(net.minecraft.client.input.KeyEvent event) {
        if (KEY == null || !StoragePreviewFeature.isEnabled() || !KEY.matches(event)) return false;
        Screen s = Minecraft.getInstance().gui.screen();
        return s instanceof AbstractContainerScreen<?> && !TextInputFocus.isTyping(s);
    }

    /** Whether the menu's own item tooltips are to be left out: while a tooltip is pinned over it. */
    public static boolean hidesMenuTooltips(Screen in) {
        return screen != null && in == screen;
    }

    /**
     * The key in a menu: pins the hovered icon's preview, or lets go of the pinned one. True when it
     * did either, so the menu does not get the key too.
     */
    static boolean keyPressed(Screen in) {
        if (!StoragePreviewFeature.isEnabled() || TextInputFocus.isTyping(in)
                || !(in instanceof AbstractContainerScreen<?> container)) return false;
        boolean wasPinned = screen == in;
        // On the pinned tooltip the menu is told the mouse is nowhere, so no slot is hovered: the key
        // there lets go.
        Slot slot = ((AbstractContainerScreenAccessor) container).alpaka$hoveredSlot();
        ItemStack stack = slot == null || coversMouse(in) ? ItemStack.EMPTY : slot.getItem();
        StoragePreviewTooltip found = StoragePreviewFeature.previewFor(container, stack);
        if (found == null) {
            clear();
            return wasPinned;
        }
        pin(in, stack, found);
        return true;
    }

    private static void pin(Screen in, ItemStack stack, StoragePreviewTooltip found) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<ClientTooltipComponent> built = new ArrayList<>();
        for (Component line : Screen.getTooltipFromItem(mc, stack)) {
            built.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        built.add(Math.min(1, built.size()), new ClientStoragePreviewTooltip(found, true));

        // Measured as GuiGraphicsExtractor.tooltip measures it.
        int w = 0;
        int h = built.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent c : built) {
            w = Math.max(w, c.getWidth(font));
            h += c.getHeight(font);
        }

        // Pinned where the tooltip was just drawn, wherever the game or another mod had put it; only
        // when none was drawn this moment (the key pressed over another icon while one is pinned,
        // whose tooltip is hidden) is it placed the way the game places one.
        ClientStoragePreviewTooltip.Shown shown = ClientStoragePreviewTooltip.lastShown(found.items());
        if (shown != null) {
            pinnedX = shown.x();
            pinnedY = shown.y() - (built.size() > 1 ? built.get(0).getHeight(font) + 2 : 0);
        } else {
            Vector2ic pos = DefaultTooltipPositioner.INSTANCE.positionTooltip(
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    (int) Math.floor(cursorX()), (int) Math.floor(cursorY()), w, h);
            pinnedX = pos.x();
            pinnedY = pos.y();
        }
        screen = in;
        components = List.copyOf(built);
        preview = found;
        style = stack.get(DataComponents.TOOLTIP_STYLE);
        width = w;
        height = h;
        boxX = pinnedX;
        boxY = pinnedY;
    }

    private static void clear() {
        screen = null;
        components = List.of();
        preview = null;
        style = null;
    }

    /** Whether the mouse is on the pinned tooltip in this menu. */
    public static boolean coversMouse(Screen in) {
        if (screen == null || in != screen) return false;
        double mx = cursorX();
        double my = cursorY();
        return mx >= boxX - FRAME && mx < boxX + width + FRAME && my >= boxY - FRAME && my < boxY + height + FRAME;
    }

    /** The wheel on a pinned tooltip taller than the screen moves it, so its bottom can be read. */
    private static boolean scrolled(Screen in, double amount) {
        if (!coversMouse(in) || amount == 0) return false;
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (height + 2 * EDGE <= guiHeight) return false;
        pinnedY = Mth.clamp(pinnedY + (int) Math.signum(amount) * SCROLL_STEP, guiHeight - EDGE - height, EDGE);
        return true;
    }

    /**
     * Draws the pinned tooltip, line for line as the game draws an item's tooltip, and asks for the
     * tooltip of the remembered item under the mouse, which the game then draws on top.
     */
    public static void render(GuiGraphicsExtractor graphics, Screen in) {
        if (screen == null || in != screen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != screen || !StoragePreviewFeature.isEnabled()) {
            clear();
            return;
        }
        Font font = mc.font;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        // Kept on screen, also after the window was made smaller.
        boxX = Mth.clamp(pinnedX, EDGE, Math.max(EDGE, guiWidth - EDGE - width));
        boxY = height + 2 * EDGE <= guiHeight
                ? Mth.clamp(pinnedY, EDGE, guiHeight - EDGE - height)
                : Mth.clamp(pinnedY, guiHeight - EDGE - height, EDGE);

        graphics.nextStratum();
        TooltipRenderUtil.extractTooltipBackground(graphics, boxX, boxY, width, height, style);
        int y = boxY;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent line = components.get(i);
            line.extractText(graphics, font, boxX, y);
            y += line.getHeight(font) + (i == 0 ? 2 : 0);
        }
        y = boxY;
        int gridTop = boxY;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent line = components.get(i);
            if (line instanceof ClientStoragePreviewTooltip) gridTop = y + ClientStoragePreviewTooltip.MARGIN;
            line.extractImage(font, boxX, y, width, height, graphics);
            y += line.getHeight(font) + (i == 0 ? 2 : 0);
        }

        double mx = cursorX();
        double my = cursorY();
        if (mx < boxX || my < gridTop) return;
        int col = (int) ((mx - boxX) / ClientStoragePreviewTooltip.SLOT);
        int row = (int) ((my - gridTop) / ClientStoragePreviewTooltip.SLOT);
        if (col >= ClientStoragePreviewTooltip.COLUMNS) return;
        int index = row * ClientStoragePreviewTooltip.COLUMNS + col;
        if (index >= preview.items().size()) return;
        ItemStack stack = preview.items().get(index);
        if (stack.isEmpty()) return;
        int sx = boxX + col * ClientStoragePreviewTooltip.SLOT;
        int sy = gridTop + row * ClientStoragePreviewTooltip.SLOT;
        graphics.fill(sx + 1, sy + 1, sx + ClientStoragePreviewTooltip.SLOT - 1, sy + ClientStoragePreviewTooltip.SLOT - 1, HOVER_COLOR);
        graphics.setTooltipForNextFrame(font, stack, (int) Math.floor(mx), (int) Math.floor(my));
    }

    /** For the screenshot tour: whether a tooltip is pinned, and the middle of one of its slots. */
    public static boolean devPinned() {
        return screen != null;
    }

    public static int[] devSlotCenter(int index) {
        int gridTop = boxY + (components.size() > 1 ? components.get(0).getHeight(Minecraft.getInstance().font) + 2 : 0)
                + ClientStoragePreviewTooltip.MARGIN;
        return new int[] {boxX + (index % ClientStoragePreviewTooltip.COLUMNS) * ClientStoragePreviewTooltip.SLOT + 9,
                gridTop + (index / ClientStoragePreviewTooltip.COLUMNS) * ClientStoragePreviewTooltip.SLOT + 9};
    }

    /** For the screenshot tour: the key pressed in the open menu. */
    public static void devPress() {
        Screen s = Minecraft.getInstance().gui.screen();
        if (s != null) keyPressed(s);
    }

    private static double cursorX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    private static double cursorY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.getScaledYPos(mc.getWindow());
    }
}
