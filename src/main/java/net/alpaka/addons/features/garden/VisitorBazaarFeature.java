package net.alpaka.addons.features.garden;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor Bazaar Shortcut: clicking a garden visitor's Accept Offer while items are missing opens
 * the bazaar on a missing item instead of sending a click the server would only refuse, and copies
 * the amount still needed so it can be pasted into the bazaar's custom amount sign.
 *
 * Hypixel doesn't mark which of several required items is missing, so the inventory is counted:
 * the click opens the first item the inventory doesn't hold enough of, and only moves on to the next
 * once that one is complete.
 *
 * An Ironman profile can't use the bazaar, so there the click opens the item's recipe instead.
 */
public final class VisitorBazaarFeature {
    static final String ACCEPT_OFFER = "Accept Offer";
    static final String ITEMS_REQUIRED = "Items Required:";
    static final String MISSING = "Missing items to accept!";

    /** The symbol Hypixel puts after an Ironman profile's name and the player's own in the tab list. */
    static final String IRONMAN = "♲";

    private static final Pattern FORMATTING = Pattern.compile("§.");
    /** The amount Hypixel writes after the item, such as " x13" or " x1,280". */
    private static final Pattern AMOUNT = Pattern.compile("\\s+x([\\d,]+)$");

    /** One required item of an offer and how many of it the visitor wants. */
    record Required(String item, int amount) {}

    /** The item a click opens, and how many of it to buy. */
    record Pick(String item, int amount) {}

    private VisitorBazaarFeature() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!AlpakaConfig.instance.visitorBazaarEnabled) return;
            List<Required> offer = offer(stack);
            if (offer == null) return;
            Pick pick = pick(offer, VisitorBazaarFeature::inInventory);
            // With several items the line says which one the click opens.
            String target = offer.size() > 1 ? ": " + pick.item() : "";
            if (onIronman()) {
                lines.add(Component.literal("Click to Open Recipe" + target).withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" (" + pick.amount() + " needed)").withStyle(ChatFormatting.DARK_GRAY)));
            } else {
                lines.add(Component.literal("Click to Open Bazaar Page" + target).withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" (copies " + pick.amount() + ")").withStyle(ChatFormatting.DARK_GRAY)));
            }
        });
    }

    /**
     * Called for every slot click in a container. Returns whether the click was taken over, in
     * which case it must not reach the server.
     */
    public static boolean onSlotClicked(Slot slot) {
        if (!AlpakaConfig.instance.visitorBazaarEnabled || slot == null || !slot.hasItem()) return false;
        List<Required> offer = offer(slot.getItem());
        if (offer == null) return false;
        Pick pick = pick(offer, VisitorBazaarFeature::inInventory);
        if (onIronman()) {
            Outbound.command("recipe " + pick.item(), Outbound.Cause.MENU_CLICK);
        } else {
            Minecraft.getInstance().keyboardHandler.setClipboard(Integer.toString(pick.amount()));
            Outbound.command("bz " + pick.item(), Outbound.Cause.MENU_CLICK);
        }
        return true;
    }

    private static List<Required> offer(ItemStack stack) {
        if (stack.isEmpty() || !ACCEPT_OFFER.equals(strip(stack.getHoverName().getString()))) return null;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(line.getString());
        return offer(stack.getHoverName().getString(), lines);
    }

    private static boolean onIronman() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && isIronman(SkyblockUtils.getTabListLines(), player.getGameProfile().name());
    }

    /**
     * Whether the tab list shows an Ironman profile: its "Profile:" line, or the player's own entry
     * ("[312] Name ♲"), carries the Ironman symbol.
     */
    static boolean isIronman(List<String> tabLines, String playerName) {
        for (String line : tabLines) {
            if (!line.contains(IRONMAN)) continue;
            if (line.startsWith("Profile:")) return true;
            int at = line.indexOf(playerName);
            int end = at + playerName.length();
            // The whole name, not the start of a longer one ("Ab" in "Abc").
            boolean whole = at >= 0 && (end == line.length() || !isNameChar(line.charAt(end)));
            if (whole && line.indexOf(IRONMAN, end) >= end) return true;
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** How many of an item, by its plain name, the player's inventory holds. */
    private static int inInventory(String item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && item.equals(strip(stack.getHoverName().getString()))) count += stack.getCount();
        }
        return count;
    }

    /** The required items of an Accept Offer that lacks items, or null for any other item. */
    static List<Required> offer(String name, List<String> lore) {
        if (!ACCEPT_OFFER.equals(strip(name))) return null;
        boolean lacking = false;
        boolean inRequired = false;
        List<Required> items = new ArrayList<>();
        for (String raw : lore) {
            String line = strip(raw);
            if (line.equals(MISSING)) lacking = true;
            if (line.equals(ITEMS_REQUIRED)) {
                inRequired = true;
            } else if (inRequired) {
                if (line.isEmpty()) inRequired = false;
                else items.add(required(line));
            }
        }
        items.removeIf(r -> r.item().isEmpty());
        return lacking && !items.isEmpty() ? List.copyOf(items) : null;
    }

    private static Required required(String line) {
        Matcher m = AMOUNT.matcher(line);
        if (!m.find()) return new Required(line, 1);
        int amount = 1;
        try {
            amount = Integer.parseInt(m.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            // An amount too large for an int is no amount anyone types into the bazaar.
        }
        return new Required(line.substring(0, m.start()), amount);
    }

    /**
     * The item to open: the first one the inventory doesn't hold enough of, with the amount still
     * needed. If it holds enough of every one, the items are somewhere Hypixel counts but the
     * inventory doesn't show, and the first item is opened with its full amount.
     */
    static Pick pick(List<Required> offer, ToIntFunction<String> have) {
        for (Required r : offer) {
            int held = have.applyAsInt(r.item());
            if (held < r.amount()) return new Pick(r.item(), r.amount() - held);
        }
        Required first = offer.get(0);
        return new Pick(first.item(), first.amount());
    }

    private static String strip(String text) {
        return FORMATTING.matcher(text).replaceAll("").trim();
    }
}
