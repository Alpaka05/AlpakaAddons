package net.alpaka.addons.features.market;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * The best speed cap for each Garden crop, offered under the field when Rancher's Boots ask for one.
 *
 * The speeds are the ones the farming community settled on: the speed at which a standard farm
 * design moves exactly one block per crop break. Crops that share a speed share a row.
 */
public final class RancherSpeeds {
    private RancherSpeeds() {}

    /** One speed and the crops it is right for. */
    public record Group(int speed, List<String> crops) {}

    public static final List<Group> GROUPS = List.of(
            new Group(93, List.of("Wheat", "Carrot", "Potato", "Nether Wart")),
            new Group(155, List.of("Pumpkin", "Melon", "Cocoa Beans")),
            new Group(233, List.of("Mushroom")),
            new Group(328, List.of("Sugar Cane", "Sunflower", "Moonflower", "Wild Rose")),
            new Group(464, List.of("Cactus")));

    /** The item a crop is shown as; as the farming mods show them. */
    public static Item icon(String crop) {
        return switch (crop) {
            case "Wheat" -> Items.WHEAT;
            case "Carrot" -> Items.CARROT;
            case "Potato" -> Items.POTATO;
            case "Nether Wart" -> Items.NETHER_WART;
            case "Pumpkin" -> Items.CARVED_PUMPKIN;
            case "Melon" -> Items.MELON_SLICE;
            case "Cocoa Beans" -> Items.COCOA_BEANS;
            case "Mushroom" -> Items.RED_MUSHROOM_BLOCK;
            case "Sugar Cane" -> Items.SUGAR_CANE;
            case "Sunflower" -> Items.SUNFLOWER;
            case "Moonflower" -> Items.BLUE_ORCHID;
            case "Wild Rose" -> Items.ROSE_BUSH;
            case "Cactus" -> Items.CACTUS;
            default -> Items.BARRIER;
        };
    }

    /**
     * Whether the sign asks for the Rancher's Boots speed cap: {@code "", "^^^^^^", "Set your",
     * "speed cap!"}. One of Hypixel's versions says "Set your Garden's", so only the ends are matched.
     */
    public static boolean isSpeedCap(SignPrompt prompt) {
        if (prompt == null) return false;
        String q = prompt.question().toLowerCase(Locale.ROOT);
        return q.startsWith("set your") && q.endsWith("speed cap!");
    }
}
