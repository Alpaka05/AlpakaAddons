package net.alpaka.addons.features.storagepreview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.AtomicJsonFile;
import net.alpaka.addons.features.slayer.SkyblockProfileTracker;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows what is inside a backpack or an ender chest page when its icon is hovered in the Storage
 * menu ({@code /storage}).
 *
 * Hypixel keeps that content on the server and sends it only while the backpack or page itself is
 * open, so the preview is what the player saw there last: every opened page is remembered (per
 * account and SkyBlock profile, and kept on disk), and the Storage menu's icon for it then carries
 * the remembered grid in its tooltip. A page that was never opened with the mod has no preview, and
 * one changed without the mod watching (another client, an auto-pickup into a backpack) shows its
 * old content until it is opened again.
 *
 * Only reads what is on screen; nothing is sent to the server.
 */
public final class StoragePreviewFeature {

    private StoragePreviewFeature() {
    }

    /** "Ender Chest (2/9)" - an open ender chest page. */
    private static final Pattern ENDER_CHEST_TITLE = Pattern.compile("^Ender Chest \\((\\d+)/\\d+\\)");
    /** "Jumbo Backpack (Slot #3)", with or without the enchanted "✦". */
    private static final Pattern BACKPACK_TITLE = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)");

    /** The Storage menu's icons: "Ender Chest Page 2" and "Backpack Slot 3". */
    private static final Pattern ENDER_CHEST_ICON = Pattern.compile("^Ender Chest Page (\\d+)");
    private static final Pattern BACKPACK_ICON = Pattern.compile("^Backpack Slot (\\d+)");

    /** Every page has a navigation row on top; the content is everything below it. */
    private static final int NAV_SLOTS = 9;

    /** Ticks after which an open page counts as loaded even if it is still completely empty. */
    private static final int LOAD_TICKS = 20;

    private static final String FILE_NAME = "alpaka-storage.json";
    private static final Gson GSON = new GsonBuilder().create();
    /** How long closing the game waits for a write still in progress. */
    private static final long WRITER_WAIT_MS = 3_000L;
    /** Writes the file off the client thread; one thread, so two writes never overlap or swap order. */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka Storage Preview writer");
        t.setDaemon(true);
        return t;
    });

    /** account/profile -> page key ("ec1", "bp3") -> the page's content, slot for slot. */
    private static final Map<String, Map<String, List<ItemStack>>> pages = new HashMap<>();
    /**
     * The file's content: as read, then kept current with every page that changes, already encoded.
     * Profiles are decoded from it once a registry is at hand.
     */
    private static JsonObject fileData = null;

    private static AbstractContainerScreen<?> watched = null;
    private static int watchedTicks = 0;
    private static boolean dirty = false;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.storagePreviewEnabled;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> leavePage());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onStopping());
        ClientTooltipComponentCallback.EVENT.register(StoragePreviewFeature::clientComponent);
    }

    private static net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent clientComponent(TooltipComponent component) {
        return component instanceof StoragePreviewTooltip preview ? new ClientStoragePreviewTooltip(preview) : null;
    }

    /**
     * Watches the open page every tick, so the cache is current when the page closes. The page is
     * only copied, encoded and marked for saving when its content differs from what is remembered:
     * an untouched page costs a comparison a tick, and closing it writes nothing.
     */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu menu)
                || !isEnabled() || !SkyblockUtils.isOnSkyblock()) {
            leavePage();
            return;
        }
        String key = titleKey(screen.getTitle());
        if (key == null) {
            leavePage();
            return;
        }
        if (screen != watched) {
            leavePage();
            watched = screen;
            watchedTicks = 0;
        }
        watchedTicks++;

        int size = menu.getRowCount() * 9;
        if (size <= NAV_SLOTS) return;
        // The slots stay empty until the server's content packet arrives; recording before that
        // would show a full backpack as empty. The navigation row is never empty once it has.
        boolean loaded = watchedTicks >= LOAD_TICKS;
        for (int i = 0; i < NAV_SLOTS && !loaded; i++) {
            loaded = menu.getSlot(i).hasItem();
        }
        if (!loaded) return;

        ClientPacketListener connection = mc.getConnection();
        String id = profileId(mc);
        Map<String, List<ItemStack>> profile = currentProfile();
        if (connection == null || id == null || profile == null) return;
        List<ItemStack> known = profile.get(key);
        if (known != null && sameContent(menu, size, known)) return;

        List<ItemStack> content = new ArrayList<>(size - NAV_SLOTS);
        for (int i = NAV_SLOTS; i < size; i++) {
            Slot slot = menu.getSlot(i);
            content.add(slot.getItem().copy());
        }
        profile.put(key, content);
        // Encoded now, while the registries are certainly at hand, and only this page: saving then
        // has no item to encode.
        RegistryOps<JsonElement> ops = connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonArray items = new JsonArray();
        for (ItemStack stack : content) {
            items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(new JsonObject()));
        }
        JsonObject root = file();
        JsonObject stored = root.get(id) instanceof JsonObject existing ? existing : new JsonObject();
        root.add(id, stored);
        stored.add(key, items);
        dirty = true;
    }

    /** Whether the open page's content slots hold exactly what is remembered for it. */
    private static boolean sameContent(ChestMenu menu, int size, List<ItemStack> known) {
        if (known.size() != size - NAV_SLOTS) return false;
        for (int i = NAV_SLOTS; i < size; i++) {
            if (!ItemStack.matches(menu.getSlot(i).getItem(), known.get(i - NAV_SLOTS))) return false;
        }
        return true;
    }

    /** The page just closed, another screen took its place, or the server was left: write what was recorded. */
    private static void leavePage() {
        watched = null;
        if (dirty) {
            dirty = false;
            save();
        }
    }

    /** The last title read and what it said: a menu's title stays the same object while it is open, and is read every tick. */
    private static Component keyTitle = null;
    private static String keyOfTitle = null;
    private static Component plainTitleOf = null;
    private static String plainTitle = "";

    private static String titleKey(Component title) {
        if (title != keyTitle) {
            keyOfTitle = pageKey(SkyblockUtils.cleanColor(title.getString()));
            keyTitle = title;
        }
        return keyOfTitle;
    }

    /** "ec2" for "Ender Chest (2/9)", "bp3" for "Jumbo Backpack (Slot #3)", else null. */
    static String pageKey(String title) {
        Matcher ec = ENDER_CHEST_TITLE.matcher(title);
        if (ec.find()) return "ec" + ec.group(1);
        Matcher bp = BACKPACK_TITLE.matcher(title);
        if (bp.find()) return "bp" + bp.group(1);
        return null;
    }

    /** The page a Storage menu icon stands for, by the icon's name, else null. */
    static String iconKey(String name) {
        Matcher ec = ENDER_CHEST_ICON.matcher(name);
        if (ec.find()) return "ec" + ec.group(1);
        Matcher bp = BACKPACK_ICON.matcher(name);
        if (bp.find()) return "bp" + bp.group(1);
        return null;
    }

    /**
     * The tooltip image for a hovered stack: the remembered page behind a Storage menu icon, or the
     * stack's own image for anything else.
     */
    public static java.util.Optional<TooltipComponent> tooltipImage(AbstractContainerScreen<?> screen, ItemStack stack,
                                                                    java.util.Optional<TooltipComponent> original) {
        if (!isEnabled() || original.isPresent() || !SkyblockUtils.isOnSkyblock()) return original;
        Component title = screen.getTitle();
        if (title != plainTitleOf) {
            plainTitle = SkyblockUtils.cleanColor(title.getString()).trim();
            plainTitleOf = title;
        }
        if (!"Storage".equals(plainTitle)) return original;
        String key = iconKey(SkyblockUtils.cleanColor(stack.getHoverName().getString()).trim());
        if (key == null) return original;
        Map<String, List<ItemStack>> profile = currentProfile();
        List<ItemStack> content = profile == null ? null : profile.get(key);
        if (content == null || content.isEmpty()) return original;
        return java.util.Optional.of(new StoragePreviewTooltip(content));
    }

    /**
     * "account/profile" for the player in play; null outside a world, and before Hypixel has named
     * the profile: pages kept under no name would show in whichever profile is played next.
     */
    private static String profileId(Minecraft mc) {
        if (mc.getConnection() == null || mc.getUser() == null) return null;
        String account = mc.getUser().getProfileId().toString();
        String profileName = SkyblockProfileTracker.INSTANCE.currentFor(account);
        return profileName == null ? null : account + "/" + profileName;
    }

    /** The current account and profile's pages, loaded from the file the first time; null outside a world. */
    private static Map<String, List<ItemStack>> currentProfile() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        String id = profileId(mc);
        if (connection == null || id == null) return null;
        Map<String, List<ItemStack>> profile = pages.get(id);
        if (profile != null) return profile;

        profile = new HashMap<>();
        JsonObject stored = file().get(id) instanceof JsonObject object ? object : null;
        if (stored != null) {
            RegistryOps<JsonElement> ops = connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (Map.Entry<String, JsonElement> page : stored.entrySet()) {
                List<ItemStack> content = new ArrayList<>();
                for (JsonElement item : page.getValue().getAsJsonArray()) {
                    content.add(ItemStack.OPTIONAL_CODEC.parse(ops, item).result().orElse(ItemStack.EMPTY));
                }
                profile.put(page.getKey(), content);
            }
        }
        pages.put(id, profile);
        return profile;
    }

    private static File storageFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME).toFile();
    }

    private static JsonObject file() {
        if (fileData == null) {
            fileData = new JsonObject();
            File f = storageFile();
            if (f.isFile()) {
                try {
                    fileData = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (IOException | RuntimeException e) {
                    // Moved aside, so the next save does not replace what may still be recovered by hand.
                    AlpakaAddons.LOGGER.warn("Could not read {}, starting with no storage previews", FILE_NAME, e);
                    AtomicJsonFile.quarantine(f);
                }
            }
        }
        return fileData;
    }

    /**
     * Writes the file in the background. The items were encoded as each page changed; turning it all
     * into text and writing it happens off the client thread, where it hitched the game every time a
     * page was closed. The writer gets its own copy of each profile's page list, since the client
     * thread goes on putting pages in; a page's encoded items are never changed once put in, only
     * replaced. The file is moved into place whole, so a write cut short leaves the previous file
     * rather than one that cannot be read.
     */
    private static void save() {
        JsonObject snapshot = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : file().entrySet()) {
            if (entry.getValue() instanceof JsonObject profile) {
                JsonObject copy = new JsonObject();
                for (Map.Entry<String, JsonElement> page : profile.entrySet()) copy.add(page.getKey(), page.getValue());
                snapshot.add(entry.getKey(), copy);
            } else {
                snapshot.add(entry.getKey(), entry.getValue());
            }
        }
        File file = storageFile();
        Runnable write = () -> {
            try {
                AtomicJsonFile.write(file, GSON.toJson(snapshot));
            } catch (IOException | RuntimeException e) {
                AlpakaAddons.LOGGER.warn("Could not write {}", FILE_NAME, e);
            }
        };
        try {
            WRITER.execute(write);
        } catch (RejectedExecutionException stopped) {
            // The game is closing and the writer has stopped: write here, so nothing is lost.
            write.run();
        }
    }

    /**
     * The game is closing: writes what the open page recorded and waits a few seconds for the
     * writer, which as a daemon thread would otherwise be cut off mid-write.
     */
    private static void onStopping() {
        leavePage();
        WRITER.shutdown();
        try {
            if (!WRITER.awaitTermination(WRITER_WAIT_MS, TimeUnit.MILLISECONDS)) {
                AlpakaAddons.LOGGER.warn("{} was still being written as the game closed", FILE_NAME);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
