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
import net.alpaka.addons.features.slayer.SkyblockProfileTracker;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
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

    /** account/profile -> page key ("ec1", "bp3") -> the page's content, slot for slot. */
    private static final Map<String, Map<String, List<ItemStack>>> pages = new HashMap<>();
    /** The file's content as read, decoded per account/profile once a registry is at hand. */
    private static JsonObject fileData = null;

    private static AbstractContainerScreen<?> watched = null;
    private static int watchedTicks = 0;
    private static boolean dirty = false;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.storagePreviewEnabled;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientTooltipComponentCallback.EVENT.register(StoragePreviewFeature::clientComponent);
    }

    private static net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent clientComponent(TooltipComponent component) {
        return component instanceof StoragePreviewTooltip preview ? new ClientStoragePreviewTooltip(preview) : null;
    }

    /** Copies the open page into the cache every tick, so it is current when the page closes. */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu menu)
                || !isEnabled() || !SkyblockUtils.isOnSkyblock()) {
            leavePage();
            return;
        }
        String key = pageKey(SkyblockUtils.cleanColor(screen.getTitle().getString()));
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

        List<ItemStack> content = new ArrayList<>(size - NAV_SLOTS);
        for (int i = NAV_SLOTS; i < size; i++) {
            Slot slot = menu.getSlot(i);
            content.add(slot.getItem().copy());
        }
        Map<String, List<ItemStack>> profile = currentProfile();
        if (profile == null) return;
        profile.put(key, content);
        dirty = true;
    }

    /** The page just closed, or another screen took its place: write what was recorded. */
    private static void leavePage() {
        watched = null;
        if (dirty) {
            dirty = false;
            save();
        }
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
        if (!"Storage".equals(SkyblockUtils.cleanColor(screen.getTitle().getString()).trim())) return original;
        String key = iconKey(SkyblockUtils.cleanColor(stack.getHoverName().getString()).trim());
        if (key == null) return original;
        Map<String, List<ItemStack>> profile = currentProfile();
        List<ItemStack> content = profile == null ? null : profile.get(key);
        if (content == null || content.isEmpty()) return original;
        return java.util.Optional.of(new StoragePreviewTooltip(content));
    }

    /** The current account and profile's pages, loaded from the file the first time; null outside a world. */
    private static Map<String, List<ItemStack>> currentProfile() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.getUser() == null) return null;
        String account = mc.getUser().getProfileId().toString();
        String profileName = SkyblockProfileTracker.INSTANCE.currentFor(account);
        String id = account + "/" + (profileName != null ? profileName : "unknown-profile");
        Map<String, List<ItemStack>> profile = pages.get(id);
        if (profile != null) return profile;

        profile = new HashMap<>();
        JsonObject stored = file().has(id) ? file().getAsJsonObject(id) : null;
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
                    AlpakaAddons.LOGGER.warn("Could not read {}, starting with no storage previews", FILE_NAME, e);
                }
            }
        }
        return fileData;
    }

    /** Writes every profile loaded this session over its stored copy; others stay as they were. */
    private static void save() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        HolderLookup.Provider registries = connection.registryAccess();
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonObject root = file();
        for (Map.Entry<String, Map<String, List<ItemStack>>> profile : pages.entrySet()) {
            JsonObject stored = new JsonObject();
            for (Map.Entry<String, List<ItemStack>> page : profile.getValue().entrySet()) {
                JsonArray items = new JsonArray();
                for (ItemStack stack : page.getValue()) {
                    items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(new JsonObject()));
                }
                stored.add(page.getKey(), items);
            }
            root.add(profile.getKey(), stored);
        }
        try {
            Files.writeString(storageFile().toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("Could not write {}", FILE_NAME, e);
        }
    }
}
