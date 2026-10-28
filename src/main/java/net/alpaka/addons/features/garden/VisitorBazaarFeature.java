package net.alpaka.addons.features.garden;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor Bazaar Shortcut: clicking a garden visitor's Accept Offer while items are missing opens
 * the bazaar on the first required item instead of sending a click the server would only refuse,
 * and copies the amount so it can be pasted into the bazaar's custom amount sign.
 */
public final class VisitorBazaarFeature {
    static final String ACCEPT_OFFER = "Accept Offer";
    static final String ITEMS_REQUIRED = "Items Required:";
    static final String MISSING = "Missing items to accept!";

    private static final Pattern FORMATTING = Pattern.compile("§.");
    /** The amount Hypixel writes after the item, such as " x13" or " x1,280". */
    private static final Pattern AMOUNT = Pattern.compile("\\s+x([\\d,]+)$");

    /** The first required item of an offer that lacks items, and how many of it the visitor wants. */
    record Missing(String item, int amount) {}

    private VisitorBazaarFeature() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!AlpakaConfig.instance.visitorBazaarEnabled) return;
            Missing missing = missing(stack);
            if (missing == null) return;
            lines.add(Component.literal("Click to Open Bazaar Page").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" (copies " + missing.amount() + ")").withStyle(ChatFormatting.DARK_GRAY)));
        });
    }

    /**
     * Called for every slot click in a container. Returns whether the click was taken over, in
     * which case it must not reach the server.
     */
    public static boolean onSlotClicked(Slot slot) {
        if (!AlpakaConfig.instance.visitorBazaarEnabled || slot == null || !slot.hasItem()) return false;
        Missing missing = missing(slot.getItem());
        if (missing == null) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(Integer.toString(missing.amount()));
        Outbound.command("bz " + missing.item(), Outbound.Cause.MENU_CLICK);
        return true;
    }

    private static Missing missing(ItemStack stack) {
        if (stack.isEmpty() || !ACCEPT_OFFER.equals(strip(stack.getHoverName().getString()))) return null;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(line.getString());
        return missing(stack.getHoverName().getString(), lines);
    }

    /**
     * The item to look up in the bazaar, or null if this is not an Accept Offer that lacks items.
     * Hypixel doesn't say which of several required items is missing, so it is the first one.
     */
    static Missing missing(String name, List<String> lore) {
        if (!ACCEPT_OFFER.equals(strip(name))) return null;
        boolean lacking = false;
        String first = null;
        boolean inRequired = false;
        for (String raw : lore) {
            String line = strip(raw);
            if (line.equals(MISSING)) lacking = true;
            if (line.equals(ITEMS_REQUIRED)) {
                inRequired = true;
            } else if (inRequired) {
                if (line.isEmpty()) inRequired = false;
                else if (first == null) first = line;
            }
        }
        if (!lacking || first == null) return null;
        int amount = 1;
        Matcher m = AMOUNT.matcher(first);
        if (m.find()) {
            try {
                amount = Integer.parseInt(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                // An amount too large for an int is no amount anyone types into the bazaar.
            }
            first = first.substring(0, m.start());
        }
        return first.isEmpty() ? null : new Missing(first, amount);
    }

    private static String strip(String text) {
        return FORMATTING.matcher(text).replaceAll("").trim();
    }
}
