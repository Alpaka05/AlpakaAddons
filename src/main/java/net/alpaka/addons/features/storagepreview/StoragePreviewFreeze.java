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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Storage Preview's freeze key: pressed over a backpack or ender chest icon in the Storage menu,
 * it keeps that icon's tooltip where it is, so the mouse can move onto the remembered items and show
 * each one's own tooltip without opening the page. Pressed again, over another icon, or when the menu
 * closes, the frozen tooltip goes away.
 *
 * Unbound until the player picks a key. Only draws what is remembered; nothing is sent to the server.
 */
public final class StoragePreviewFreeze {
    private StoragePreviewFreeze() {}

    public static KeyMapping KEY;

    /** Extra room around the tooltip that still counts as on it: its frame is drawn outside its text box. */
    private static final int FRAME = 4;
    private static final int HOVER_COLOR = 0x80FFFFFF;

    /** The menu the frozen tooltip belongs to, or null while nothing is frozen. */
    private static Screen screen = null;
    private static List<Component> lines = List.of();
    private static StoragePreviewTooltip preview = null;
    private static Identifier style = null;
    /** Where the mouse was when the key was pressed: the tooltip is placed from there, as it was then. */
    private static int anchorX;
    private static int anchorY;

    /** The tooltip's box as drawn last, its text area without the frame. */
    private static int boxX, boxY, boxW, boxH;

    public static void register() {
        KEY = AlpakaKeyBinds.register(new KeyMapping("key.alpaka.storage_preview_freeze", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY));
        ScreenEvents.AFTER_INIT.register((client, s, width, height) -> {
            if (!(s instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.allowKeyPress(s).register((in, event) -> !KEY.matches(event) || !keyPressed(in));
            ScreenMouseEvents.allowMouseClick(s).register((in, event) -> {
                if (KEY.matchesMouse(event)) return !keyPressed(in);
                // A click on the frozen tooltip does nothing, rather than clicking the slot under it.
                return !coversMouse(in);
            });
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

    /**
     * The key in a menu: freezes the hovered icon's preview, or lets go of the frozen one. True when
     * it did either, so the menu does not get the key too.
     */
    static boolean keyPressed(Screen in) {
        if (!StoragePreviewFeature.isEnabled() || TextInputFocus.isTyping(in)
                || !(in instanceof AbstractContainerScreen<?> container)) return false;
        boolean wasFrozen = screen == in;
        // The menu is told the mouse is nowhere while it is on the frozen tooltip, so no slot is
        // hovered then: the key there lets go.
        Slot slot = ((AbstractContainerScreenAccessor) container).alpaka$hoveredSlot();
        ItemStack stack = slot == null || coversMouse(in) ? ItemStack.EMPTY : slot.getItem();
        StoragePreviewTooltip found = StoragePreviewFeature.previewFor(container, stack);
        if (found == null) {
            clear();
            return wasFrozen;
        }
        Minecraft mc = Minecraft.getInstance();
        screen = in;
        lines = List.copyOf(Screen.getTooltipFromItem(mc, stack));
        preview = found;
        style = stack.get(DataComponents.TOOLTIP_STYLE);
        anchorX = (int) Math.floor(cursorX());
        anchorY = (int) Math.floor(cursorY());
        boxW = 0;
        return true;
    }

    private static void clear() {
        screen = null;
        lines = List.of();
        preview = null;
        style = null;
        boxW = 0;
    }

    /** Whether the mouse is on the frozen tooltip in this menu. */
    public static boolean coversMouse(Screen in) {
        if (screen == null || in != screen || boxW <= 0) return false;
        double mx = cursorX();
        double my = cursorY();
        return mx >= boxX - FRAME && mx < boxX + boxW + FRAME && my >= boxY - FRAME && my < boxY + boxH + FRAME;
    }

    /**
     * Draws the frozen tooltip, laid out and placed exactly as the game draws an item's tooltip, and
     * asks for the tooltip of the remembered item under the mouse, which the game then draws on top.
     */
    public static void render(GuiGraphicsExtractor graphics, Screen in) {
        if (screen == null || in != screen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != screen || !StoragePreviewFeature.isEnabled()) {
            clear();
            return;
        }
        Font font = mc.font;
        List<ClientTooltipComponent> components = new ArrayList<>(lines.size() + 1);
        for (Component line : lines) components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        ClientStoragePreviewTooltip grid = new ClientStoragePreviewTooltip(preview);
        components.add(Math.min(1, components.size()), grid);

        // The same size and place as GuiGraphicsExtractor.tooltip works out, so the slots can be found.
        int w = 0;
        int h = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent c : components) {
            w = Math.max(w, c.getWidth(font));
            h += c.getHeight(font);
        }
        Vector2ic pos = DefaultTooltipPositioner.INSTANCE.positionTooltip(graphics.guiWidth(), graphics.guiHeight(),
                anchorX, anchorY, w, h);
        boxX = pos.x();
        boxY = pos.y();
        boxW = w;
        boxH = h;

        graphics.nextStratum();
        graphics.tooltip(font, components, anchorX, anchorY, DefaultTooltipPositioner.INSTANCE, style);

        // The grid sits under the first line, which is followed by two pixels of space.
        int gridTop = boxY + (components.size() > 1 ? components.get(0).getHeight(font) + 2 : 0)
                + ClientStoragePreviewTooltip.MARGIN;
        double mx = cursorX();
        double my = cursorY();
        int col = (int) Math.floor((mx - boxX) / ClientStoragePreviewTooltip.SLOT);
        int row = (int) Math.floor((my - gridTop) / ClientStoragePreviewTooltip.SLOT);
        if (mx < boxX || my < gridTop || col >= ClientStoragePreviewTooltip.COLUMNS) return;
        int index = row * ClientStoragePreviewTooltip.COLUMNS + col;
        if (index >= preview.items().size()) return;
        ItemStack stack = preview.items().get(index);
        if (stack.isEmpty()) return;
        int sx = boxX + col * ClientStoragePreviewTooltip.SLOT;
        int sy = gridTop + row * ClientStoragePreviewTooltip.SLOT;
        graphics.fill(sx + 1, sy + 1, sx + ClientStoragePreviewTooltip.SLOT - 1, sy + ClientStoragePreviewTooltip.SLOT - 1, HOVER_COLOR);
        graphics.setTooltipForNextFrame(font, stack, (int) Math.floor(mx), (int) Math.floor(my));
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
