package net.alpaka.addons.features.dungeon.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;

/**
 * The custom terminal window (design.md 5.2), the MarketScreen pattern: Hypixel's chest made
 * exactly as vanilla makes it, shown in a screen of our own instead of the vanilla one.
 *
 * What it will be: a ContainerScreen subclass with an AlpakaStyle panel and header
 * ({@code AlpakaStyle.HEADER_H}) on the configured background, the play area as a fixed grid by
 * slot index, cells colored by their Mark. A press on a cell goes through TerminalClicks and then
 * {@code Outbound.menuClick(menu, slot, button, shift ? QUICK_MOVE : PICKUP, MENU_CLICK)}, and on
 * success {@link TerminalSession#onClickSent}; everything else is swallowed, Esc and the inventory
 * key close. A "Classic View" button falls back to the vanilla chest until restart. It never calls
 * slotClicked, closeContainer or super.mouseClicked.
 *
 * Not built yet: never takes a chest over, so terminals open in the vanilla chest.
 */
public final class TerminalScreen {

    private TerminalScreen() {}

    /**
     * From MenuScreensMixin, for every chest Hypixel opens, after the market screens had their
     * turn; only called while the Terminal Solvers master is on, and after
     * {@link TerminalSession#beforeMenuOpens}. Opens the custom window and returns true for a
     * terminal it handles: Custom Terminal GUI on, the terminal's own switch on
     * ({@link TerminalKind#enabled()}), in a Catacombs run. It then also starts the session with
     * {@link TerminalSession#open}, which carries the session on when Hypixel replaced the same
     * terminal's window. False leaves the chest to vanilla, which then starts the session itself.
     */
    public static boolean tryOpen(MenuType<?> type, Minecraft mc, int containerId, Component title) {
        return false;
    }

    /** The custom window on a locally filled chest, for the screenshot tour; sends nothing. Null while not built. */
    public static Screen preview(TerminalKind kind) {
        return null;
    }
}
