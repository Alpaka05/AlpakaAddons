package net.alpaka.addons.features.itemvalue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.serialization.JsonOps;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.AveragePrice;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.LowestBin;
import net.alpaka.addons.features.market.MarketKind;
import net.alpaka.addons.utils.ItemData;
import net.alpaka.addons.utils.TextInputFocus;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Price Tooltip: what a SkyBlock item costs, written under its tooltip - its lowest BIN and average
 * sale on the Auction House, its instant buy and sell price on the Bazaar, and its Item Value where
 * enchantments, stars and the like add to it. A stack shows what the whole stack comes to as well.
 *
 * The lines show always, while the Price Tooltip key is held, or after the key switched them on,
 * as the settings say. The prices are the ones Item Value uses ({@link ItemPrices}), plus SkyCofl's
 * average per item ({@link AveragePrice}); nothing here sends anything to the server.
 */
public final class PriceTooltipFeature {
    private PriceTooltipFeature() {}

    /** The config's choices for when the lines show, by {@link AlpakaConfig#priceTooltipMode}. */
    public static final String[] MODES = {"Always", "While Key Held", "Toggle With Key"};
    private static final int ALWAYS = 0;
    private static final int HOLD = 1;
    private static final int TOGGLE = 2;

    public static PriceKeyMapping KEY;

    public static void register() {
        KEY = AlpakaKeyBinds.register(new PriceKeyMapping());
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            // Vanilla also builds tooltips on background threads (search trees); prices are for the eye only.
            if (!Minecraft.getInstance().isSameThread()) return;
            append(stack, lines);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) ->
                    !(KEY.matches(event) && toggle(s)));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    !(KEY.matchesMouse(event) && toggle(s)));
        });
    }

    /** The key in toggle mode: switches the lines on or off. True when it did, so the menu does not get it too. */
    private static boolean toggle(net.minecraft.client.gui.screens.Screen screen) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.priceTooltipEnabled || config.priceTooltipMode != TOGGLE || TextInputFocus.isTyping(screen)) return false;
        config.priceTooltipShown = !config.priceTooltipShown;
        AlpakaConfig.save();
        return true;
    }

    private static boolean shown() {
        AlpakaConfig config = AlpakaConfig.instance;
        return switch (config.priceTooltipMode) {
            case HOLD -> KEY != null && KEY.isHeld(Minecraft.getInstance().getWindow());
            case TOGGLE -> config.priceTooltipShown;
            default -> true;
        };
    }

    // ---- The lines ------------------------------------------------------------------------------

    private static void append(ItemStack stack, List<Component> lines) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.priceTooltipEnabled || !config.allowApiCalls || stack.isEmpty()) return;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !shown()) return;
        Info info = info(custom, stack);
        if (info == null) return;
        ItemPrices.refresh();

        int count = stack.getCount();
        int before = lines.size();
        if (info.base != null && info.base.market() == MarketKind.BAZAAR) {
            ItemPrices.Product product = ItemPrices.bazaar(info.base.id());
            if (config.priceTooltipBazaar && product != null) {
                line(lines, "Bazaar Buy", product.buy(), count, false);
                line(lines, "Bazaar Sell", product.sell(), count, false);
            }
        } else {
            if (config.priceTooltipLowestBin && info.base != null && info.base.priced()) {
                line(lines, "Lowest BIN", info.base.unit(), count, false);
            }
            if (config.priceTooltipAverage && info.cofl != null) {
                AveragePrice.Result avg = AveragePrice.get(info.cofl);
                if (avg.state() == AveragePrice.State.READY) line(lines, "Average Price", avg.average(), count, false);
                else if (avg.state() == AveragePrice.State.LOADING) loading(lines, "Average Price");
            }
        }
        if (config.priceTooltipItemValue && info.value > 0 && info.addsToBase) {
            line(lines, "Item Value", info.value, count, info.incomplete);
        }
        if (lines.size() == before && ItemPrices.loading() && info.base == null) {
            loading(lines, "Prices");
        }
        // One empty line between the item's own lore and the prices.
        if (lines.size() > before) lines.add(before, Component.empty());
    }

    private static void line(List<Component> lines, String label, double each, int count, boolean atLeast) {
        if (Double.isNaN(each) || each <= 0) return;
        MutableComponent line = Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Coins.full(each) + (atLeast ? "+" : "") + " coins").withStyle(ChatFormatting.GOLD));
        if (count > 1) {
            line.append(Component.literal(" (" + Coins.compact(each * count) + " for " + count + ")").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(line);
    }

    private static void loading(List<Component> lines, String label) {
        lines.add(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Loading…").withStyle(ChatFormatting.DARK_GRAY)));
    }

    // ---- What the item is -----------------------------------------------------------------------

    /**
     * What the tooltip needs to know about an item, worked out once per item: the part the item
     * itself is priced as, SkyCofl's name for it, and its Item Value.
     */
    private record Info(ItemValue.Part base, LowestBin.Item cofl, double value, boolean incomplete, boolean addsToBase) {}

    private static final Info NONE = new Info(null, null, Double.NaN, false, false);

    /**
     * The last answers, by the item's data object: the hovered item asks every frame, and its data
     * stays the same object until the server sends the item again. Thrown away when prices change.
     */
    private static final Map<CustomData, Info> INFOS = new IdentityHashMap<>();
    private static long infosKey = Long.MIN_VALUE;

    private static final ItemValue.Prices PRICES = ItemPrices::quote;
    private static final ItemValue.Catalog CATALOG = new ItemValue.Catalog() {
        @Override
        public String name(String id) {
            return ItemIcons.name(id);
        }

        @Override
        public ItemIcons.Upgrades upgrades(String id) {
            return ItemIcons.upgrades(id);
        }
    };

    private static Info info(CustomData custom, ItemStack stack) {
        long key = ((long) ItemPrices.version() << 8) | (AlpakaConfig.instance.itemValueBazaarPrice << 1) | (ItemIcons.ready() ? 1 : 0);
        if (key != infosKey || INFOS.size() > 512) {
            infosKey = key;
            INFOS.clear();
        }
        Info cached = INFOS.get(custom);
        if (cached == null) {
            cached = compute(custom, stack);
            INFOS.put(custom, cached);
        }
        return cached == NONE ? null : cached;
    }

    private static Info compute(CustomData custom, ItemStack stack) {
        JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, ItemData.view(custom));
        // Other servers can store anything under "id"; only a SkyBlock id (a plain string) is priced.
        if (!json.isJsonObject() || !(json.getAsJsonObject().get("id") instanceof JsonPrimitive id) || !id.isString()) return NONE;
        if (id.getAsString().equals("SKYBLOCK_MENU")) return NONE;
        JsonObject data = json.getAsJsonObject();
        ItemValue.Breakdown breakdown = ItemValue.of(data, PRICES, CATALOG);
        ItemValue.Part base = null;
        for (ItemValue.Part part : breakdown.parts()) {
            if (part.group() == ItemValue.Group.BASE) {
                base = part;
                break;
            }
        }
        double value = breakdown.total();
        // Item Value only says something new when the parts add up to more than the item alone.
        boolean adds = false;
        for (ItemValue.Part part : breakdown.parts()) {
            if (part != base && part.priced() && part.total() > 0) {
                adds = true;
                break;
            }
        }
        LowestBin.Item cofl = base != null && base.market() == MarketKind.BAZAAR ? null : LowestBin.itemOf(stack);
        return new Info(base, cofl, value, breakdown.incomplete(), adds);
    }

    // ---- The key --------------------------------------------------------------------------------

    /** The Price Tooltip key. A subclass only so the bound key, which KeyMapping keeps protected, can be read for holding. */
    public static final class PriceKeyMapping extends KeyMapping {
        PriceKeyMapping() {
            super("key.alpaka.price_tooltip", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY);
        }

        /** Whether the bound key is physically down right now: a key mapping hears nothing while a menu is open. */
        boolean isHeld(Window window) {
            InputConstants.Key bound = this.key;
            return switch (bound.getType()) {
                case KEYSYM -> bound.getValue() != GLFW.GLFW_KEY_UNKNOWN
                        && InputConstants.isKeyDown(window, bound.getValue());
                case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), bound.getValue()) == GLFW.GLFW_PRESS;
                // GLFW cannot be asked about a raw scan code; the mapping's own state is all there is.
                case SCANCODE -> this.isDown();
            };
        }
    }
}
