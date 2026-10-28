package net.alpaka.addons.features.commands;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shorten Warp: "/isle" for "/warp isle".
 *
 * A typed command whose name is one of Hypixel's warp destinations, with nothing after it, is sent
 * as a warp to that destination - unless the name is a command the server or a mod already knows,
 * which then keeps its own meaning ("/hub" and "/garden" are Hypixel's own and warp anyway).
 *
 * The names are those the warp menu and Skyblocker's warp list offer, with NEU's aliases.
 */
public final class WarpShortcut {

    private WarpShortcut() {
    }

    static final Set<String> DESTINATIONS = Set.of(
            // Hub
            "hub", "village", "elizabeth", "castle", "crypt", "crypts", "da", "museum", "wizard", "wiz", "tower",
            "stonks", "carnival", "taylor",
            // Private Island, Garden, Rift
            "home", "island", "garden", "rift",
            // Farming, Foraging, Fishing
            "barn", "desert", "trapper", "trap", "farming", "park", "trees", "foraging", "jungle", "howl",
            "galatea", "moonglade", "murkwater", "murk", "bayou", "backwater", "lotus", "atoll", "torrhus",
            "spring", "springs", "canyon", "loch", "marsh", "glowing", "safari", "dragontail", "scarleton",
            // Mining
            "gold", "deep", "deeper", "mines", "dmines", "dwarves", "forge", "crystals", "ch", "cn", "hollows",
            "nucleus", "nuc", "basecamp", "base", "camp", "glacite", "gt", "tunnel", "tunnels",
            // Combat
            "spider", "spiders", "arachne", "nest", "mound", "top", "end", "drag", "dragons", "void", "sepulture",
            "nether", "isle", "crimson", "ci", "kuudra", "smold", "smoldering", "skull", "wasteland",
            // Dungeons, Jerry's Workshop
            "dungeon_hub", "dungeons", "dh", "dhub", "winter", "jerry", "workshop");

    /**
     * Names that mean a spot on the Garden while the player is on it: "/barn" there teleports to the
     * barn ("/plottp barn") to claim visitors, and only elsewhere warps to the Farming Islands' barn.
     */
    static final Set<String> GARDEN_PLOTS = Set.of("barn");

    /**
     * The warp a typed command stands for, without its slash ("warp isle", or "plottp barn" on the
     * Garden), or null to send it as typed. {@code knownCommand} says whether a command name
     * already means something; {@code onGarden} whether the player is on their Garden.
     */
    public static String rewrite(String command, Predicate<String> knownCommand, boolean onGarden) {
        if (command == null) return null;
        String trimmed = command.trim();
        if (trimmed.isEmpty() || trimmed.indexOf(' ') >= 0) return null;
        String name = trimmed.toLowerCase(Locale.ROOT);
        if (!DESTINATIONS.contains(name) || knownCommand.test(name)) return null;
        if (onGarden && GARDEN_PLOTS.contains(name)) return "plottp " + name;
        return "warp " + name;
    }
}
