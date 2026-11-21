package net.alpaka.addons.features.viewmodel;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Inspect Animation: on a key press the hand shows off the held item, like the weapon inspects of
 * shooters. Which animations it can play depends on the item (see {@link InspectAnimation.Kind}),
 * and each press picks one of them at random.
 *
 * Purely on the player's screen: it only moves the first-person hand (ItemInHandRendererMixin), and
 * nothing is sent to the server. Like an inspect in those games it gives way to anything the player
 * does with the item - attacking, using it, or switching to another one - by easing back to rest in
 * a moment rather than snapping there.
 */
public final class ItemInspectFeature {
    private ItemInspectFeature() {}

    public static KeyMapping INSPECT_KEY;

    /** How long the hand takes to return to rest when an inspect is cut short. */
    private static final long CANCEL_NANOS = 180_000_000L;

    private static InspectAnimation.Kind kind;
    private static InspectAnimation.Variant variant;
    /** The variant played last, which the next press skips so two in a row differ. */
    private static InspectAnimation.Variant lastVariant;
    private static final java.util.Random RANDOM = new java.util.Random();
    private static long startNanos;
    private static float durationNanos;
    /** When the inspect was cut short (0 while it plays out), and the pose it was cut short in. */
    private static long cancelNanos;
    private static InspectAnimation.Pose cancelPose;
    /** The slot and item the inspect started with; another one in hand ends it. */
    private static int slot;
    private static ItemStack stack = ItemStack.EMPTY;
    /** Whether the arm was already swinging at the start, so only a new swing ends the inspect. */
    private static boolean swingingAtStart;

    /** For the screenshot tour: a fixed point of the animation instead of the clock, or -1. */
    private static float devProgress = -1.0f;
    /** For the screenshot tour: the variant to play instead of a random one, or -1. */
    private static int devVariant = -1;

    public static void register() {
        INSPECT_KEY = AlpakaKeyBinds.register(new KeyMapping(
                "key.alpaka.inspect_item",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                net.alpaka.addons.client.AlpakaKeyCategory.CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(ItemInspectFeature::tick);
    }

    private static void tick(Minecraft mc) {
        boolean pressed = false;
        while (INSPECT_KEY.consumeClick()) pressed = true;
        LocalPlayer player = mc.player;
        if (!AlpakaConfig.instance.itemInspectEnabled || player == null) {
            kind = null;
            return;
        }
        if (pressed && mc.gui.screen() == null && mc.options.getCameraType().isFirstPerson()) {
            start(player);
            return;
        }
        if (kind == null || cancelNanos != 0L) return;
        if (!player.swinging) swingingAtStart = false;
        boolean otherItem = player.getInventory().getSelectedSlot() != slot
                || !ItemStack.isSameItem(player.getMainHandItem(), stack);
        boolean acting = mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()
                || (player.swinging && !swingingAtStart);
        if (otherItem || acting || !mc.options.getCameraType().isFirstPerson()) cancel();
    }

    private static void start(LocalPlayer player) {
        stack = player.getMainHandItem().copy();
        slot = player.getInventory().getSelectedSlot();
        kind = kindOf(stack);
        variant = pickVariant(kind);
        swingingAtStart = player.swinging;
        startNanos = System.nanoTime();
        durationNanos = variant.seconds() * 1.0e9f / Math.max(0.1f, AlpakaConfig.instance.itemInspectSpeed);
        cancelNanos = 0L;
        cancelPose = null;
    }

    /** One of the kind's variants at random, but not the one played last. */
    private static InspectAnimation.Variant pickVariant(InspectAnimation.Kind kind) {
        InspectAnimation.Variant[] variants = kind.variants;
        if (devVariant >= 0) return variants[devVariant % variants.length];
        InspectAnimation.Variant picked = variants[RANDOM.nextInt(variants.length)];
        if (picked == lastVariant && variants.length > 1) {
            picked = variants[(java.util.Arrays.asList(variants).indexOf(picked) + 1 + RANDOM.nextInt(variants.length - 1)) % variants.length];
        }
        lastVariant = picked;
        return picked;
    }

    private static void cancel() {
        long now = System.nanoTime();
        InspectAnimation.Pose current = poseAt(now);
        if (current == null) return;
        cancelPose = current.wrapped();
        cancelNanos = now;
    }

    /**
     * The pose the main hand is drawn in this frame, or null when no inspect is playing. Called from
     * the hand renderer once per frame.
     */
    public static InspectAnimation.Pose currentPose() {
        if (kind == null || !AlpakaConfig.instance.itemInspectEnabled) return null;
        if (devProgress >= 0.0f) return variant.sample(devProgress);
        return poseAt(System.nanoTime());
    }

    /** The kind of the inspect playing now, or null. */
    public static InspectAnimation.Kind currentKind() {
        return kind;
    }

    private static InspectAnimation.Pose poseAt(long now) {
        if (kind == null) return null;
        if (cancelNanos != 0L) {
            float t = (now - cancelNanos) / (float) CANCEL_NANOS;
            if (t >= 1.0f) {
                kind = null;
                return null;
            }
            // Ease-out, so the hand leaves the pose quickly and settles gently.
            float eased = 1.0f - (1.0f - t) * (1.0f - t);
            return cancelPose.lerp(InspectAnimation.Pose.REST, eased);
        }
        float t = (now - startNanos) / durationNanos;
        if (t >= 1.0f) {
            kind = null;
            return null;
        }
        return variant.sample(t);
    }

    /** Which animation an item gets: SkyBlock's own item type where it has one, else the vanilla item. */
    static InspectAnimation.Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return InspectAnimation.Kind.HAND;
        InspectAnimation.Kind skyblock = kindOfSkyblockType(skyblockType(stack));
        if (skyblock != null) return skyblock;
        if (stack.is(ItemTags.SWORDS)) return InspectAnimation.Kind.SWORD;
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.is(Items.MACE) || stack.is(Items.SHEARS)) {
            return InspectAnimation.Kind.TOOL;
        }
        if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return InspectAnimation.Kind.BOW;
        if (stack.is(Items.TRIDENT) || stack.is(ItemTags.SPEARS) || stack.is(Items.FISHING_ROD)
                || stack.is(Items.STICK) || stack.is(Items.BLAZE_ROD) || stack.is(Items.BREEZE_ROD)) {
            return InspectAnimation.Kind.POLE;
        }
        if (stack.is(ItemTags.SKULLS) || stack.getItem() instanceof BlockItem) return InspectAnimation.Kind.BLOCK;
        if (stack.has(DataComponents.FOOD)) return InspectAnimation.Kind.FOOD;
        return InspectAnimation.Kind.ITEM;
    }

    /**
     * The animation for a SkyBlock item type as its rarity line names it ("LEGENDARY DUNGEON SWORD"),
     * or null for a type without one of its own. SkyBlock dresses many weapons up as other items -
     * the Aspect of the End is a diamond shovel, drills are prismarine shards - so the type it gives
     * an item says better what the player holds than the vanilla item does.
     */
    static InspectAnimation.Kind kindOfSkyblockType(String rarityLine) {
        if (rarityLine.isEmpty()) return null;
        List<String> words = List.of(rarityLine.toUpperCase(Locale.ROOT).split("[^A-Z]+"));
        if (words.contains("SWORD") || words.contains("LONGSWORD")) return InspectAnimation.Kind.SWORD;
        if (words.contains("BOW") || words.contains("SHORTBOW")) return InspectAnimation.Kind.BOW;
        if (words.contains("ROD") || words.contains("WAND") || words.contains("STAFF")) return InspectAnimation.Kind.POLE;
        if (words.contains("PICKAXE") || words.contains("DRILL") || words.contains("AXE") || words.contains("HOE")
                || words.contains("SHOVEL") || words.contains("GAUNTLET") || words.contains("SHEARS")) {
            return InspectAnimation.Kind.TOOL;
        }
        return null;
    }

    /** The last lore line, where SkyBlock names rarity and type; "" without lore. */
    private static String skyblockType(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return "";
        Component last = lore.lines().get(lore.lines().size() - 1);
        String text = ChatFormatting.stripFormatting(last.getString());
        return text == null ? "" : text;
    }

    /**
     * For the screenshot tour: starts the held item's variant with that index, frozen at a point
     * (0..1), or with progress -1 stops it.
     */
    public static void devShow(int variantIndex, float progress) {
        LocalPlayer player = Minecraft.getInstance().player;
        devProgress = progress;
        devVariant = progress < 0.0f ? -1 : variantIndex;
        if (progress < 0.0f) {
            kind = null;
        } else if (player != null) {
            start(player);
        }
    }
}
