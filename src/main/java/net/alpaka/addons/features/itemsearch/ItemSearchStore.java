package net.alpaka.addons.features.itemsearch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaStats;
import net.alpaka.addons.features.slayer.SkyblockProfileTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What Item Search remembers: the last seen contents of every Ender Chest page, backpack, sack,
 * island chest and the other places in {@link ItemSearchSource}, per account and SkyBlock profile.
 *
 * Kept beside the slayer stats ({@link AlpakaStats#directory()}), one file per profile under
 * {@code item-search/<account>/}, so a second launcher sees the same items. The items are saved
 * whole, with their lore, since that is what the search reads and what the tooltip shows.
 *
 * Only touched on the game thread; the file is written on a thread of its own from a copy.
 */
public final class ItemSearchStore {
    private ItemSearchStore() {}

    /** One stack of a container: the item (its count left at one) and how many there are. */
    public record Item(ItemStack stack, long count) {}

    /** One container as last seen. */
    public static final class Container {
        public final ItemSearchSource source;
        public final int page;
        /** The sack's or chest's own name, null for the rest. */
        public final String label;
        /** An island chest's block, and the other half of a large chest; null for the rest. */
        public final BlockPos pos;
        public final BlockPos pos2;
        public final long seenAt;
        public final List<Item> items;

        public Container(ItemSearchSource source, int page, String label, BlockPos pos, BlockPos pos2, long seenAt, List<Item> items) {
            this.source = source;
            this.page = page;
            this.label = label;
            this.pos = pos;
            this.pos2 = pos2;
            this.seenAt = seenAt;
            this.items = items;
        }

        public String key() {
            return ItemSearchStore.key(source, page, label, pos);
        }

        public String place() {
            return source.place(page, label);
        }
    }

    static String key(ItemSearchSource source, int page, String label, BlockPos pos) {
        if (pos != null) return source.name() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return source.name() + ":" + page + (label != null ? ":" + label : "");
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final long SAVE_DELAY_MS = 3_000L;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka Item Search writer");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, Container> containers = new LinkedHashMap<>();
    /** The account and profile {@link #containers} belongs to, "account/profile"; null before loading. */
    private static String loadedFor = null;
    /**
     * The registries the items were read with, kept for writing them: leaving the server can clear
     * the level before the last write.
     */
    private static HolderLookup.Provider loadedRegistries = null;
    private static boolean dirty = false;
    private static long changedAt = 0L;
    /** Showing the screenshot tour's made-up containers, which are never saved. */
    private static boolean preview = false;

    /** Everything remembered for the profile in play. */
    public static Collection<Container> all() {
        ensureLoaded();
        return containers.values();
    }

    /** For the screenshot tour: shows these containers, in memory only, until the game restarts. */
    public static void devPreview(Collection<Container> list) {
        preview = true;
        containers.clear();
        for (Container c : list) containers.put(c.key(), c);
    }

    /** Replaces what a container holds with what was just seen in it. */
    public static void put(Container container) {
        if (!ensureLoaded()) return;
        containers.put(container.key(), container);
        markDirty();
    }

    /** Forgets one container, as when its chest was broken. */
    public static void remove(String key) {
        if (!ensureLoaded()) return;
        if (containers.remove(key) != null) markDirty();
    }

    /** Forgets everything for the profile in play. */
    public static void clear() {
        if (!ensureLoaded()) return;
        containers.clear();
        markDirty();
    }

    static void markDirty() {
        dirty = true;
        changedAt = System.currentTimeMillis();
    }

    /** Called every tick: writes a few seconds after the last change, so paging through a backpack writes once. */
    public static void tick() {
        if (preview) return;
        if (dirty && System.currentTimeMillis() - changedAt >= SAVE_DELAY_MS) flush();
        // The profile changed: write the old one's items before loading the new one's.
        String now = owner();
        if (loadedFor != null && now != null && !now.equals(loadedFor)) {
            flush();
            loadedFor = null;
            containers.clear();
        }
    }

    /** Writes what changed now, as when leaving the server. */
    public static void flush() {
        if (!dirty || loadedFor == null || loadedRegistries == null) return;
        dirty = false;
        String json = GSON.toJson(encode(loadedRegistries));
        File file = fileFor(loadedFor);
        WRITER.execute(() -> write(file, json));
    }

    /** "account/profile" for the player in play, or null when the profile is not known yet. */
    private static String owner() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null || mc.getUser().getProfileId() == null) return null;
        String account = mc.getUser().getProfileId().toString();
        String profile = SkyblockProfileTracker.INSTANCE.currentFor(account);
        if (profile == null) {
            AlpakaStats.Account known = AlpakaStats.instance.accounts.get(account);
            profile = known == null ? null : known.lastProfile;
        }
        return profile == null ? null : account + "/" + profile;
    }

    /** Loads the profile's file the first time it is needed. False while there is nothing to load into. */
    private static boolean ensureLoaded() {
        if (preview) return false;
        String owner = owner();
        if (owner == null) return false;
        if (owner.equals(loadedFor)) return true;
        HolderLookup.Provider registries = registries();
        if (registries == null) return false;
        if (loadedFor != null) flush();
        containers.clear();
        dirty = false;
        loadedFor = owner;
        loadedRegistries = registries;
        File file = fileFor(owner);
        if (!file.isFile()) return true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            decode(root, registries);
        } catch (Exception e) {
            AlpakaAddons.LOGGER.warn("Item Search: could not read {}", file, e);
        }
        return true;
    }

    private static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.registryAccess();
    }

    private static File fileFor(String owner) {
        String[] parts = owner.split("/", 2);
        return new File(new File(new File(AlpakaStats.directory(), "item-search"), safe(parts[0])), safe(parts[1]) + ".json");
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void write(File file, String json) {
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Item Search: could not write {}", file, e);
        }
    }

    // ---- JSON -----------------------------------------------------------------------------------

    private static JsonObject encode(HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        JsonArray list = new JsonArray();
        for (Container c : containers.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("source", c.source.name());
            o.addProperty("page", c.page);
            if (c.label != null) o.addProperty("label", c.label);
            if (c.pos != null) o.addProperty("pos", c.pos.asLong());
            if (c.pos2 != null) o.addProperty("pos2", c.pos2.asLong());
            o.addProperty("seen", c.seenAt);
            JsonArray items = new JsonArray();
            for (Item item : c.items) {
                JsonElement stack = ItemStack.CODEC.encodeStart(ops, item.stack()).result().orElse(null);
                if (stack == null) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("count", item.count());
                entry.add("item", stack);
                items.add(entry);
            }
            o.add("items", items);
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("containers", list);
        return root;
    }

    private static void decode(JsonObject root, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        for (JsonElement element : root.getAsJsonArray("containers")) {
            try {
                JsonObject o = element.getAsJsonObject();
                ItemSearchSource source = ItemSearchSource.valueOf(o.get("source").getAsString());
                List<Item> items = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("items")) {
                    JsonObject entry = e.getAsJsonObject();
                    ItemStack stack = ItemStack.CODEC.parse(ops, entry.get("item")).result().orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) items.add(new Item(stack, entry.get("count").getAsLong()));
                }
                Container c = new Container(source, o.get("page").getAsInt(),
                        o.has("label") ? o.get("label").getAsString() : null,
                        o.has("pos") ? BlockPos.of(o.get("pos").getAsLong()) : null,
                        o.has("pos2") ? BlockPos.of(o.get("pos2").getAsLong()) : null,
                        o.get("seen").getAsLong(), items);
                containers.put(c.key(), c);
            } catch (RuntimeException e) {
                // One container written by a newer version, or an item this game no longer knows:
                // skip it and keep the rest.
                AlpakaAddons.LOGGER.debug("Item Search: skipped a saved container", e);
            }
        }
    }
}
