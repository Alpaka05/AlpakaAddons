package net.alpaka.addons.features.menuhighlight;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks slots in SkyBlock menus the way Item Search marks what it found, for as long as the menu is
 * open: the summoned pet in the Pets menu, and the unfinished tasks of the SkyBlock Leveling guide.
 * Only the menu's own items are read; nothing is clicked or sent.
 */
public final class MenuHighlights {
    private MenuHighlights() {}

    /** "Pets", with a page counter before or after it and Hypixel's search behind it (as SkyHanni reads it). */
    private static final Pattern PETS_TITLE =
            Pattern.compile("(?:\\(\\d+/\\d+\\) )?Pets(?:: \".*\")?(?: \\(\\d+/\\d+\\))? ?");

    /**
     * The SkyBlock Leveling menus: the guide ("Guide ➜ ..."), the task overview ("Tasks ➜ ...") and
     * every task category below it. SkyHanni's guide highlight covers the same titles.
     */
    private static final Pattern LEVELING_TITLE = Pattern.compile(
            "(?:\\(\\d+/\\d+\\) )?(?:.*Guide ➜.*|Tasks ➜ .*|Skill Related Tasks|Essence Shop ➜.*|Complete Dungeons ➜.*"
                    + "|(?:Core|Event|Miscellaneous|Slaying|Mining|Fishing|Story|Dungeon) ➜ .*)");

    /** Hypixel shows these only on a task that is still open; a finished one gets a ✔ instead. */
    private static final Pattern OPEN_TASK_LINE = Pattern.compile(
            "This task can only be completed once!|This task can be completed \\d+ times!|You have not unlocked the content.*");

    /** A category's progress, below 100% (one or two digits) when it isn't finished. */
    private static final Pattern PROGRESS_LINE = Pattern.compile(
            "(?:Total Progress|Progress to Complete Category): (\\d{1,3}(?:\\.\\d+)?)%");

    /** The SkyBlock Leveling menus keep their overview item here; it is never a task. */
    private static final int OVERVIEW_SLOT = 4;

    /** Each stack's mark color (0 for none), worked out once; a stack is replaced, not changed, when a slot changes. */
    private static final Map<ItemStack, Integer> marks = new WeakHashMap<>();
    private static AbstractContainerScreen<?> cachedScreen;
    private static Menu cachedMenu = Menu.NONE;

    private enum Menu { NONE, PETS, LEVELING }

    /** Called for every slot as it is drawn, before its item, so the item and tooltip stay on top. */
    public static void drawSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.menuHighlightsEnabled) return;
        if (slot.container instanceof Inventory) return;
        if (screen != cachedScreen) {
            cachedScreen = screen;
            cachedMenu = menuOf(screen.getTitle().getString());
            marks.clear();
        }
        if (cachedMenu == Menu.NONE) return;
        if (cachedMenu == Menu.PETS && !config.menuHighlightActivePet) return;
        if (cachedMenu == Menu.LEVELING && (!config.menuHighlightUnfinishedTasks || slot.getContainerSlot() == OVERVIEW_SLOT)) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        boolean marked = marks.computeIfAbsent(stack, s -> mark(cachedMenu, s) ? 1 : 0) == 1;
        if (!marked) return;
        int color = cachedMenu == Menu.PETS ? config.menuHighlightActivePetColor : config.menuHighlightUnfinishedTasksColor;
        SlotMark.draw(graphics, slot, color);
    }

    private static Menu menuOf(String title) {
        String clean = SkyblockUtils.cleanColor(title);
        if (PETS_TITLE.matcher(clean).matches()) return Menu.PETS;
        if (LEVELING_TITLE.matcher(clean).matches()) return Menu.LEVELING;
        return Menu.NONE;
    }

    private static boolean mark(Menu menu, ItemStack stack) {
        List<String> lore = loreOf(stack);
        if (menu == Menu.PETS) return isActivePet(petInfo(stack), lore);
        return isUnfinishedTask(SkyblockUtils.cleanColor(stack.getHoverName().getString()), lore);
    }

    static boolean isPetsMenu(String title) {
        return menuOf(title) == Menu.PETS;
    }

    static boolean isLevelingMenu(String title) {
        return menuOf(title) == Menu.LEVELING;
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
