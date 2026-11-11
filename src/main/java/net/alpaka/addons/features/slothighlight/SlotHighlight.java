package net.alpaka.addons.features.slothighlight;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks slots in SkyBlock menus the way Item Search marks what it found, for as long as the menu is
 * open: what is ready to collect or switched on in one color, what is missing or unfinished in
 * another. Only the open menu's items are read; nothing is clicked or sent.
 *
 * The titles and lore lines are the ones SkyHanni, Skyblocker and Firmament read for the same
 * highlights.
 */
public final class SlotHighlight {
    private SlotHighlight() {}

    /** What a slot's mark means. */
    enum Mark { NONE, READY, MISSING }

    /** The parts of an item the rules read, cleaned of color codes. */
    record Item(int slot, String name, List<String> lore, String petInfo) {
        boolean hasLine(String line) {
            return lore.contains(line);
        }

        boolean hasLine(Pattern pattern) {
            for (String line : lore) if (pattern.matcher(line).matches()) return true;
            return false;
        }

        String lastLine() {
            return lore.isEmpty() ? "" : lore.getLast();
        }
    }

    /** One menu: its title, whether the player wants it, and what each item gets. */
    enum Place {
        ACTIVE_PET("(?:\\(\\d+/\\d+\\) )?Pets(?:: \".*\")?(?: \\(\\d+/\\d+\\))? ?",
                c -> c.slotHighlightActivePet,
                item -> isActivePet(item.petInfo(), item.lore()) ? Mark.READY : Mark.NONE),
        LEVEL_TASKS("(?:\\(\\d+/\\d+\\) )?(?:.*Guide ➜.*|Tasks ➜ .*|Skill Related Tasks|Essence Shop ➜.*|Complete Dungeons ➜.*"
                + "|(?:Core|Event|Miscellaneous|Slaying|Mining|Fishing|Story|Dungeon) ➜ .*)",
                c -> c.slotHighlightUnfinishedTasks,
                // Slot 4 holds the menu's overview, never a task.
                item -> item.slot() != 4 && isUnfinishedTask(item.name(), item.lore()) ? Mark.MISSING : Mark.NONE),
        AUCTIONS("Manage Auctions",
                c -> c.slotHighlightAuctions,
                item -> item.hasLine("Status: Sold!") ? Mark.READY
                        : item.hasLine("Status: Expired!") ? Mark.MISSING : Mark.NONE),
        BAZAAR_ORDERS("(?:Your|Co-op) Bazaar Orders",
                c -> c.slotHighlightBazaarOrders,
                item -> item.hasLine(FILLED_ORDER) ? Mark.READY : Mark.NONE),
        CROESUS("(?:\\(\\d+/\\d+\\) )?(?:Croesus|Vesuvius)",
                c -> c.slotHighlightCroesus,
                item -> isUnopenedRun(item) ? Mark.READY : Mark.NONE),
        JACOB_CONTESTS("Jacob's Farming Contests",
                c -> c.slotHighlightJacobContests,
                item -> item.lastLine().equals("Click to claim reward!") ? Mark.READY : Mark.NONE),
        SLAYER_REWARDS(".*Slayer LVL Rewards",
                c -> c.slotHighlightSlayerRewards,
                item -> item.lastLine().equals("Click to claim rewards!") ? Mark.READY : Mark.NONE),
        COMMISSIONS("Commissions",
                c -> c.slotHighlightCommissions,
                item -> item.lore().stream().anyMatch(line -> line.contains("COMPLETED")) ? Mark.READY : Mark.NONE),
        CHOCOLATE_FACTORY("Chocolate Factory",
                c -> c.slotHighlightChocolateFactory,
                item -> item.hasLine(UNCLAIMED_REWARDS) ? Mark.READY : Mark.NONE),
        COMPOSTER_UPGRADES("Composter Upgrades",
                c -> c.slotHighlightComposterUpgrades,
                item -> item.hasLine("Click to upgrade!") ? Mark.READY : Mark.NONE);

        private final Pattern title;
        private final Predicate<AlpakaConfig> enabled;
        private final Function<Item, Mark> rule;

        Place(String title, Predicate<AlpakaConfig> enabled, Function<Item, Mark> rule) {
            this.title = Pattern.compile(title);
            this.enabled = enabled;
            this.rule = rule;
        }

        Mark mark(Item item) {
            return rule.apply(item);
        }

        static Place of(String title) {
            String clean = SkyblockUtils.cleanColor(title);
            for (Place place : values()) {
                if (place.title.matcher(clean).matches()) return place;
            }
            return null;
        }
    }

    /** Hypixel shows these only on a task that is still open; a finished one gets a ✔ instead. */
    private static final Pattern OPEN_TASK_LINE = Pattern.compile(
            "This task can only be completed once!|This task can be completed \\d+ times!|You have not unlocked the content.*");

    /** A category's progress, below 100% when it isn't finished. */
    private static final Pattern PROGRESS_LINE = Pattern.compile(
            "(?:Total Progress|Progress to Complete Category): (\\d{1,3}(?:\\.\\d+)?)%");

    /** A bazaar order or offer that is completely filled. */
    private static final Pattern FILLED_ORDER = Pattern.compile("Filled: [\\d,.k]+/[\\d,.k]+ 100%!");

    private static final Pattern UNCLAIMED_REWARDS = Pattern.compile("You have \\d+ unclaimed rewards?!");

    /** A Kuudra run in Vesuvius, which has no "not opened" line, only its tier. */
    private static final Pattern KUUDRA_TIER = Pattern.compile("(?:Basic|Hot|Burning|Fiery|Infernal) Tier");

    /** Each stack's mark, worked out once; a stack is replaced, not changed, when a slot changes. */
    private static final Map<ItemStack, Mark> marks = new WeakHashMap<>();
    private static AbstractContainerScreen<?> cachedScreen;
    private static Place cachedPlace;

    /** Called for every slot as it is drawn, before its item, so the item and tooltip stay on top. */
    public static void drawSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.slotHighlightEnabled) return;
        if (slot.container instanceof Inventory) return;
        if (screen != cachedScreen) {
            cachedScreen = screen;
            cachedPlace = Place.of(screen.getTitle().getString());
            marks.clear();
        }
        Place place = cachedPlace;
        if (place == null || !place.enabled.test(config)) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        Mark mark = marks.computeIfAbsent(stack, s -> place.mark(itemOf(slot.getContainerSlot(), s)));
        if (mark == Mark.NONE) return;
        SlotMark.draw(graphics, slot, mark == Mark.READY ? config.slotHighlightReadyColor : config.slotHighlightMissingColor);
    }

    /**
     * The summoned pet: Hypixel's pet data says "active":true (what Firmament reads), and its lore
     * offers to despawn it (what Skyblocker and SkyHanni read). Either is enough.
     */
    static boolean isActivePet(String petInfo, List<String> lore) {
        if (petInfo != null && petInfo.replace(" ", "").contains("\"active\":true")) return true;
        for (String line : lore) {
            if (line.contains("Click to despawn")) return true;
        }
        return false;
    }

    /** A task or category not done yet: a ✖ in front, a line only open tasks have, or progress below 100%. */
    static boolean isUnfinishedTask(String name, List<String> lore) {
        if (name.startsWith("✖")) return true;
        for (String line : lore) {
            if (line.startsWith("✖") || OPEN_TASK_LINE.matcher(line).matches()) return true;
            Matcher progress = PROGRESS_LINE.matcher(line);
            if (progress.find() && Double.parseDouble(progress.group(1)) < 100) return true;
        }
        return false;
    }

    /** A Croesus or Vesuvius run whose chests are all still closed. */
    private static boolean isUnopenedRun(Item item) {
        if (item.hasLine("No chests opened yet!")) return true;
        if (item.hasLine("No more chests to open!")) return false;
        for (String line : item.lore()) {
            if (line.startsWith("Opened Chest:") || line.startsWith("Opened chest:")) return false;
        }
        return item.hasLine(KUUDRA_TIER);
    }

    static Place placeOf(String title) {
        return Place.of(title);
    }

    private static Item itemOf(int slot, ItemStack stack) {
        return new Item(slot, SkyblockUtils.cleanColor(stack.getHoverName().getString()), loreOf(stack), petInfo(stack));
    }

    private static String petInfo(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String info = data.copyTag().getStringOr("petInfo", "");
        return info.isEmpty() ? null : info;
    }

    private static List<String> loreOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(SkyblockUtils.cleanColor(line.getString()));
        return lines;
    }
}
