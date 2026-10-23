package net.alpaka.addons.features.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.utils.ModVersion;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cheapest Buy It Now of the item being put up for auction, from SkyCofl, for the price
 * suggestions on the Auction House's price sign.
 *
 * The item is the one in the middle of "Create BIN Auction" or "Create Auction", noted while that
 * menu is open: by the time Hypixel opens the price sign the menu may be gone. The request names
 * the item and, for a pet, its rarity - nothing about the player. Answers are kept for a couple of
 * minutes, and only asked for while Network Features is on.
 */
public final class LowestBin {
    private LowestBin() {}

    private static final String BASE = "https://sky.coflnet.com/api/auctions/tag/";
    /** Where the item sits in Hypixel's auction creation menus. */
    private static final int ITEM_SLOT = 13;
    /** How long after the creation menu was last seen its item still counts as the one being sold. */
    private static final long ITEM_FOR_MS = 30_000L;
    private static final long FRESH_MS = 2 * 60_000L;
    private static final long RETRY_MS = 30_000L;

    /** The item being sold: its SkyCofl tag, a pet's rarity, how many, and its name. */
    public record Item(String tag, String rarity, int count, String name) {}

    public enum State { LOADING, READY, NONE, FAILED }

    /** The cheapest BIN for one of the item, in coins; NaN unless READY. */
    public record Result(State state, double each) {}

    private static volatile Item selling = null;
    private static volatile long sellingSeenMs = 0L;

    private static final class Entry {
        volatile State state = State.LOADING;
        volatile double each = Double.NaN;
        volatile long fetchedAt = 0L;
        volatile long retryAt = 0L;
        volatile boolean fetching = false;
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static HttpClient client;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LowestBin::tick);
    }

    /** Notes the item in an auction creation menu while one is open. */
    private static void tick(Minecraft mc) {
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!(screen.getMenu() instanceof ChestMenu menu) || menu.slots.size() <= ITEM_SLOT) return;
        String title = SkyblockUtils.cleanColor(screen.getTitle().getString()).trim();
        if (!title.equals("Create BIN Auction") && !title.equals("Create Auction")) return;
        Item item = itemOf(menu.getSlot(ITEM_SLOT).getItem());
        // Without an item in the slot yet, Hypixel shows a button there that asks for one.
        selling = item;
        sellingSeenMs = System.currentTimeMillis();
    }

    /** The item being put up for auction, or null if no auction is being created. */
    public static Item selling() {
        return System.currentTimeMillis() - sellingSeenMs < ITEM_FOR_MS ? selling : null;
    }

    static Item itemOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        net.minecraft.nbt.CompoundTag nbt = data.copyTag();
        String id = nbt.getStringOr("id", "");
        if (id.isEmpty()) return null;
        String petInfo = nbt.getStringOr("petInfo", "");
        String tag = ItemTags.expand(id, petInfo);
        if (tag == null) return null;
        String rarity = id.equals("PET") ? petRarity(petInfo) : null;
        return new Item(tag, rarity, stack.getCount(), SkyblockUtils.cleanColor(stack.getHoverName().getString()));
    }

    /** "LEGENDARY" from a pet's info, {@code {"tier":"LEGENDARY",...}}: a pet's price hangs on it. */
    static String petRarity(String petInfo) {
        try {
            JsonElement root = JsonParser.parseString(petInfo);
            if (!root.isJsonObject()) return null;
            JsonElement tier = root.getAsJsonObject().get("tier");
            return tier == null || !tier.isJsonPrimitive() ? null : tier.getAsString().toUpperCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The cheapest BIN of this item, asking SkyCofl in the background if it is not known or has gone
     * stale. Never waits. Auctions of the player's own (by their UUID without dashes) are left out,
     * so the player is not offered to undercut themselves.
     */
    public static Result get(Item item, String ownUuid) {
        String key = item.tag() + "|" + item.rarity() + "|" + ownUuid;
        Entry entry = CACHE.computeIfAbsent(key, k -> new Entry());
        long now = System.currentTimeMillis();
        boolean due = entry.fetchedAt == 0L || entry.state == State.FAILED ? now >= entry.retryAt : now - entry.fetchedAt > FRESH_MS;
        if (due && !entry.fetching) fetch(entry, url(item), ownUuid);
        return new Result(entry.state, entry.each);
    }

    static String url(Item item) {
        String url = BASE + URLEncoder.encode(item.tag(), StandardCharsets.UTF_8) + "/active/bin";
        return item.rarity() == null ? url : url + "?Rarity=" + URLEncoder.encode(item.rarity(), StandardCharsets.UTF_8);
    }

    private static synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    private static void fetch(Entry entry, String url, String ownUuid) {
        entry.fetching = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            long now = System.currentTimeMillis();
            try {
                if (error != null || response == null) {
                    fail(entry, now, error != null ? error.toString() : "no response", url);
                    return;
                }
                int status = response.statusCode();
                if (status == 404 || status == 400) {
                    answer(entry, Double.NaN, now);
                    return;
                }
                if (status != 200) {
                    fail(entry, now, "HTTP " + status, url);
                    return;
                }
                answer(entry, cheapestEach(response.body(), ownUuid), now);
            } catch (RuntimeException e) {
                fail(entry, now, e.toString(), url);
            } finally {
                entry.fetching = false;
            }
        });
    }

    private static void answer(Entry entry, double each, long now) {
        entry.each = each;
        entry.state = Double.isNaN(each) ? State.NONE : State.READY;
        entry.fetchedAt = now;
    }

    private static void fail(Entry entry, long now, String why, String url) {
        AlpakaAddons.LOGGER.warn("Could not load the lowest BIN from {} ({})", url, why);
        // A price already shown stays; only without one does the bar say it failed.
        if (entry.fetchedAt == 0L) entry.state = State.FAILED;
        entry.retryAt = now + RETRY_MS;
    }

    /** The lowest price for one item among SkyCofl's active BINs, a stack's price split by its size; NaN for none. */
    static double cheapestEach(String json, String ownUuid) {
        double best = Double.NaN;
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) return best;
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            JsonElement bin = o.get("bin");
            if (bin != null && bin.isJsonPrimitive() && !bin.getAsBoolean()) continue;
            JsonElement seller = o.get("auctioneerId");
            if (ownUuid != null && seller != null && seller.isJsonPrimitive() && ownUuid.equalsIgnoreCase(seller.getAsString())) continue;
            JsonElement price = o.get("startingBid");
            if (price == null || !price.isJsonPrimitive() || !price.getAsJsonPrimitive().isNumber()) continue;
            JsonElement countEl = o.get("count");
            int count = countEl != null && countEl.isJsonPrimitive() ? Math.max(1, countEl.getAsInt()) : 1;
            double each = price.getAsDouble() / count;
            if (each > 0 && (Double.isNaN(best) || each < best)) best = each;
        }
        return best;
    }

    // ---- Suggestions ----------------------------------------------------------------------------

    /** One price offered under the field: its chip's text and the coins it fills in. */
    public record Suggestion(String label, long coins) {}

    /**
     * What to offer for a lowest BIN of {@code lowest} coins: that price, and a little under it -
     * 1% and 5% less, cut to three significant digits so the price reads as a round number.
     */
    static List<Suggestion> suggestions(double lowest) {
        List<Suggestion> out = new ArrayList<>();
        long match = Math.round(lowest);
        if (match <= 0) return out;
        out.add(new Suggestion(Coins.compact(match), match));
        for (int percent : new int[] {1, 5}) {
            long under = roundDown(lowest * (100 - percent) / 100.0);
            if (under <= 0 || under >= match) under = match - 1;
            if (under <= 0 || out.get(out.size() - 1).coins() <= under) continue;
            out.add(new Suggestion("-" + percent + "% " + Coins.compact(under), under));
        }
        return out;
    }

    /** Down to three significant digits: 439,560,000 to 439,000,000, 178,199 to 178,000. */
    static long roundDown(double value) {
        if (value < 1000) return (long) Math.floor(value);
        double step = Math.pow(10, Math.floor(Math.log10(value)) - 2);
        return (long) (Math.floor(value / step) * step);
    }

    // ---- For the screenshot tour ----------------------------------------------------------------

    /** Puts an item on sale with a known lowest BIN, as if the menu and SkyCofl had said so. */
    static void devSelling(Item item, double each) {
        selling = item;
        sellingSeenMs = System.currentTimeMillis() + 10 * 60_000L;
        Entry entry = new Entry();
        answer(entry, each, System.currentTimeMillis() + 10 * 60_000L);
        CACHE.put(item.tag() + "|" + item.rarity() + "|" + ownUuid(), entry);
    }

    /** The player's UUID as SkyCofl writes sellers, without dashes. */
    public static String ownUuid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID().toString().replace("-", "");
    }
}
