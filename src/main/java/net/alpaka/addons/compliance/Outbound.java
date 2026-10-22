package net.alpaka.addons.compliance;

import net.alpaka.addons.AlpakaAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/**
 * The one place the mod sends anything to the server.
 *
 * Every message, command or menu click the mod sends is the direct result of the player pressing a
 * key, clicking, or pressing Enter, and says which through its {@link Cause}. Nothing reacts to
 * other players' chat, timers or game events by sending; two features once did - a party-chat
 * auto-reply and a guild announcement fired from a timer - and were removed. A source scan in the
 * tests fails the build if anything outside this class calls the network directly.
 */
public final class Outbound {
    /** What the player did that sends the command. */
    public enum Cause {
        /** Pressed Enter on a line typed into the chat box. */
        CHAT_INPUT,
        /** Pressed a key, such as Y on a party invite. */
        KEY_PRESS,
        /** Released or clicked a command wheel segment. */
        WHEEL,
        /** Clicked the mod's drawing of a slot in an open menu, such as a card in the Auction House. */
        MENU_CLICK
    }

    /** The same command again this soon is taken as a double input and dropped. */
    private static final long REPEAT_MS = 500L;

    private static String lastCommand = null;
    private static long lastSentAtMs = 0L;

    /**
     * The same slot clicked again this soon is one press that arrived twice - some input mods on
     * macOS deliver events twice - and is dropped. Far below how fast anyone clicks on purpose.
     */
    private static final long SAME_CLICK_MS = 60L;

    private static int lastClickContainer = -1;
    private static int lastClickSlot = -1;
    private static long lastClickAtMs = 0L;

    private Outbound() {}

    /**
     * Sends a command, without its leading slash, for an action the player just took. Returns
     * whether it went out. Characters chat does not allow - formatting codes, control characters -
     * are removed first, so a hand-edited config cannot slip them in.
     */
    public static boolean command(String command, Cause cause) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null || command == null) return false;
        String cleaned = StringUtil.filterText(command).trim();
        if (cleaned.isEmpty()) return false;

        long now = System.currentTimeMillis();
        if (cleaned.equals(lastCommand) && now - lastSentAtMs < REPEAT_MS) return false;
        lastCommand = cleaned;
        lastSentAtMs = now;

        AlpakaAddons.LOGGER.debug("Sending /{} ({})", cleaned, cause);
        mc.player.connection.sendCommand(cleaned);
        return true;
    }

    /**
     * Clicks a slot of the open menu, for a mouse click the player just made on the mod's own
     * drawing of that slot. Returns whether the click went out.
     *
     * It is exactly the click vanilla sends when that slot itself is clicked - the same call, the
     * same container, slot, button and kind - and one per press: a left or right click, or a shift
     * click. Never a drag, a double-click collect, a number-key swap or a drop, and never for a menu
     * that is no longer the open one. The market screens only change how a menu looks; what reaches
     * Hypixel is what the vanilla chest would have sent for the same click.
     */
    public static boolean menuClick(AbstractContainerMenu menu, int slotIndex, int button, ContainerInput input, Cause cause) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || menu == null || cause != Cause.MENU_CLICK) return false;
        if (player.containerMenu != menu) return false;
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
        if (button != 0 && button != 1) return false;
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) return false;

        long now = System.currentTimeMillis();
        if (menu.containerId == lastClickContainer && slotIndex == lastClickSlot && now - lastClickAtMs < SAME_CLICK_MS) {
            return false;
        }
        lastClickContainer = menu.containerId;
        lastClickSlot = slotIndex;
        lastClickAtMs = now;

        AlpakaAddons.LOGGER.debug("Clicking slot {} of menu {} ({} {}, {})", slotIndex, menu.containerId, input, button, cause);
        mc.gameMode.handleContainerInput(menu.containerId, slotIndex, button, input, player);
        return true;
    }
}
