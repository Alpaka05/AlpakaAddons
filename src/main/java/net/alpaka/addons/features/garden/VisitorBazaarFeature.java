package net.alpaka.addons.features.garden;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Visitor Bazaar Shortcut: clicking a garden visitor's Accept Offer while items are missing opens
 * the bazaar on the first required item instead of sending a click the server would only refuse.
 */
public final class VisitorBazaarFeature {
    static final String ACCEPT_OFFER = "Accept Offer";
    static final String ITEMS_REQUIRED = "Items Required:";
    static final String MISSING = "Missing items to accept!";

    private static final Pattern FORMATTING = Pattern.compile("§.");
    /** The amount Hypixel writes after the item, such as " x13" or " x1,280". */
    private static final Pattern AMOUNT = Pattern.compile("\\s+x[\\d,.]+[kKmM]?$");

    private VisitorBazaarFeature() {}

    /**
     * Called for every slot click in a container. Returns whether the click was taken over, in
     * which case it must not reach the server.
     */
    public static boolean onSlotClicked(Slot slot) {
        if (!AlpakaConfig.instance.visitorBazaarEnabled || slot == null || !slot.hasItem()) return false;
        ItemStack stack = slot.getItem();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(line.getString());
        String item = missingItem(stack.getHoverName().getString(), lines);
        if (item == null) return false;
        Outbound.command("bz " + item, Outbound.Cause.MENU_CLICK);
        return true;
    }

    /**
     * The item to look up in the bazaar, or null if this is not an Accept Offer that lacks items.
     * Hypixel doesn't say which of several required items is missing, so it is the first one.
     */
    static String missingItem(String name, List<String> lore) {
        if (!ACCEPT_OFFER.equals(strip(name))) return null;
        boolean missing = false;
        String first = null;
        boolean inRequired = false;
        for (String raw : lore) {
            String line = strip(raw);
            if (line.equals(MISSING)) missing = true;
            if (line.equals(ITEMS_REQUIRED)) {
                inRequired = true;
            } else if (inRequired) {
                if (line.isEmpty()) inRequired = false;
                else if (first == null) first = AMOUNT.matcher(line).replaceFirst("");
            }
        }
        return missing && first != null && !first.isEmpty() ? first : null;
    }

    private static String strip(String text) {
        return FORMATTING.matcher(text).replaceAll("").trim();
    }
}
