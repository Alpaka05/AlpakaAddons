package net.alpaka.addons.features.resourcepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.AtomicJsonFile;
import net.alpaka.addons.mixin.DownloadedPackSourceAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.client.resources.server.ServerPackManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cache Resource Pack: keeps Hypixel's server resource pack loaded so joining SkyBlock does not
 * reload every resource each time.
 *
 * Vanilla already keeps the downloaded file (downloads/&lt;pack id&gt;/&lt;sha1&gt;), but it still unloads
 * the pack on every disconnect and reloads all resources when the server sends it again, which is
 * the loading screen. With this on:
 * <ul>
 *   <li>a pack Hypixel sends that is already loaded with the same hash is answered as applied
 *       without touching the resources;</li>
 *   <li>Hypixel's packs stay loaded after leaving, and Hypixel's own "remove pack" packets are
 *       ignored, so lobby hops don't unload them either;</li>
 *   <li>the packs that were loaded when the game closed are loaded with the game's own first
 *       resource load on the next start, so even the first join is instant.</li>
 * </ul>
 * Joining another server or a singleplayer world unloads them again, so nothing from Hypixel leaks
 * into other worlds. The same idea as the ResourcePackCached mod, built on vanilla's pack manager.
 */
public final class ServerPackCache {
    private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");

    /** Hash of every server pack currently loaded, by pack id; filled after each successful reload. */
    private static final Map<UUID, String> loaded = new LinkedHashMap<>();
    /** Whether the packs in {@link #loaded} came from Hypixel, i.e. are the ones this feature keeps. */
    private static volatile boolean loadedAreHypixel;
    /** Packs Hypixel sent on the current connection; see {@link #onUncachedPush}. */
    private static final Set<UUID> sentThisConnection = new HashSet<>();
    /** Noted on the main thread when a connection starts; the pack source learns of it on another. */
    private static volatile boolean connectingToHypixel;
    private static volatile boolean connectedToHypixel;
    private static volatile boolean packsDeclined;

    /** Set while the startup restore runs the pack manager, so it doesn't start a reload of its own. */
    private static boolean suppressReload;
    /** The restored packs ride along with the game's first resource load, which finishes later. */
    private static boolean startupRestorePending;

    private ServerPackCache() {}

    private static boolean enabled() {
        return AlpakaConfig.instance.cacheResourcePackEnabled;
    }

    private static boolean isHypixel(ServerData server) {
        return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel.net");
    }

    /**
     * Called from the client entrypoint, which Fabric runs inside Minecraft's constructor after the
     * pack source exists but before the resource repository is first scanned. The cached packs are
     * pushed as local packs and the manager is ticked once, which hands them to the pack source; the
     * reload it would start is suppressed, because the game's own first load picks them up anyway.
     */
    public static void restoreAtStartup() {
        if (!enabled()) return;
        Map<UUID, Path> packs = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> entry : readFile().entrySet()) {
            Path path = packPath(entry.getKey(), entry.getValue());
            if (Files.isRegularFile(path)) packs.put(entry.getKey(), path);
        }
        if (packs.isEmpty()) return;

        try {
            DownloadedPackSource source = Minecraft.getInstance().getDownloadedPackSource();
            ServerPackManager manager = ((DownloadedPackSourceAccessor) source).alpaka$manager();
            source.allowServerPacks();
            packs.forEach(source::pushLocalPack);
            suppressReload = true;
            try {
                manager.tick();
            } finally {
                suppressReload = false;
            }
            startupRestorePending = true;
            AlpakaAddons.LOGGER.info("Loading {} cached server resource pack(s) with the game", packs.size());
        } catch (RuntimeException e) {
            AlpakaAddons.LOGGER.error("Failed to restore cached server resource packs", e);
        }
    }

    public static boolean suppressReload() {
        return suppressReload;
    }

    /**
     * The game's first resource load does not report back to the pack source the way later reloads
     * do, so this finishes the restore once it is done: the restored packs become active.
     */
    public static void onInitialLoadFinished() {
        if (!startupRestorePending) return;
        Minecraft.getInstance().getDownloadedPackSource().onReloadSuccess();
        startupRestorePending = false;
    }

    /** After every successful reload of server packs: these are now the loaded ones. */
    public static void onPacksApplied(List<PackReloadConfig.IdAndPath> packs) {
        loaded.clear();
        for (PackReloadConfig.IdAndPath pack : packs) {
            String hash = hashOf(pack.path());
            if (hash != null) loaded.put(pack.id(), hash);
        }
        loadedAreHypixel = !loaded.isEmpty() && (connectedToHypixel || startupRestorePending);
        if (loadedAreHypixel) {
            writeFile(loaded);
        } else if (file().exists() && !file().delete()) {
            AlpakaAddons.LOGGER.warn("Could not delete {}", file());
        }
    }

    /**
     * The server a connection is about to open to. Minecraft only knows its current server once the
     * connection is up, too late for the pack source, which is set up on the connector thread.
     */
    public static void onConnecting(ServerData server) {
        connectingToHypixel = isHypixel(server);
    }

    /**
     * The pack source is set up for a new connection (on the connector thread): decides whether the
     * kept Hypixel packs have to go.
     */
    public static boolean onConnect(ServerPackManager.PackPromptStatus status) {
        connectedToHypixel = connectingToHypixel;
        packsDeclined = status == ServerPackManager.PackPromptStatus.DECLINED;
        sentThisConnection.clear();
        return unloadKept(!(enabled() && connectedToHypixel && !packsDeclined));
    }

    /**
     * A singleplayer world: Hypixel's packs don't belong there. Called both when a world brings its
     * own pack and on joining it; whichever comes first unloads them, so the second one doesn't
     * remove the world's pack along with them.
     */
    public static boolean onLocalWorld() {
        connectedToHypixel = false;
        sentThisConnection.clear();
        return unloadKept(true);
    }

    private static boolean unloadKept(boolean unload) {
        if (!unload || !loadedAreHypixel) return false;
        loadedAreHypixel = false;
        return true;
    }

    /** On disconnect; true keeps the packs loaded instead of unloading them like vanilla does. */
    public static boolean keepOnDisconnect() {
        boolean keep = enabled() && connectedToHypixel && loadedAreHypixel;
        connectedToHypixel = false;
        return keep;
    }

    /** Hypixel's "remove pack" packets are ignored, otherwise every lobby hop would unload the pack. */
    public static boolean ignorePop() {
        return enabled() && connectedToHypixel && !packsDeclined;
    }

    /** True if the pack Hypixel sent is already loaded with this exact hash, so nothing needs to load. */
    public static boolean isAlreadyLoaded(UUID id, String hash) {
        if (!enabled() || !connectedToHypixel || packsDeclined || hash == null) return false;
        if (!hash.toLowerCase(Locale.ROOT).equals(loaded.get(id))) return false;
        sentThisConnection.add(id);
        return true;
    }

    /**
     * Hypixel sent a pack that has to be downloaded or reloaded: a new version, most likely under a
     * new id. Since pops are ignored, the old version would otherwise stay loaded under it forever, so
     * every kept pack Hypixel hasn't sent again on this connection is removed in the same reload.
     */
    public static void onUncachedPush(UUID id, ServerPackManager manager) {
        if (!enabled() || !connectedToHypixel) return;
        sentThisConnection.add(id);
        for (UUID kept : new ArrayList<>(loaded.keySet())) {
            if (!sentThisConnection.contains(kept)) manager.popPack(kept);
        }
    }

    /** Vanilla's download cache names each file after its SHA-1, which is what the server sends. */
    static String hashOf(Path path) {
        if (path == null || path.getFileName() == null) return null;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SHA1.matcher(name).matches() ? name : null;
    }

    private static Path packPath(UUID id, String hash) {
        return FabricLoader.getInstance().getGameDir().resolve("downloads").resolve(id.toString()).resolve(hash);
    }

    static String toJson(Map<UUID, String> packs) {
        JsonArray array = new JsonArray();
        packs.forEach((id, hash) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("hash", hash);
            array.add(entry);
        });
        JsonObject root = new JsonObject();
        root.add("packs", array);
        return root.toString();
    }

    /** Entries that don't parse or whose hash isn't a SHA-1 are dropped rather than failing the rest. */
    static Map<UUID, String> fromJson(String json) {
        Map<UUID, String> packs = new LinkedHashMap<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("packs")) return packs;
            for (JsonElement element : root.getAsJsonObject().getAsJsonArray("packs")) {
                try {
                    JsonObject entry = element.getAsJsonObject();
                    UUID id = UUID.fromString(entry.get("id").getAsString());
                    String hash = entry.get("hash").getAsString().toLowerCase(Locale.ROOT);
                    if (SHA1.matcher(hash).matches()) packs.put(id, hash);
                } catch (RuntimeException ignored) {
                }
            }
        } catch (RuntimeException ignored) {
        }
        return packs;
    }

    /** A method rather than a constant so the JSON helpers can be tested without a Fabric loader. */
    private static File file() {
        return FabricLoader.getInstance().getConfigDir().resolve("alpaka-server-packs.json").toFile();
    }

    private static Map<UUID, String> readFile() {
        if (!file().exists()) return Map.of();
        try {
            return fromJson(AtomicJsonFile.read(file()));
        } catch (Exception e) {
            AlpakaAddons.LOGGER.warn("Could not read {}", file(), e);
            return Map.of();
        }
    }

    private static void writeFile(Map<UUID, String> packs) {
        try {
            AtomicJsonFile.write(file(), toJson(packs));
        } catch (Exception e) {
            AlpakaAddons.LOGGER.warn("Could not write {}", file(), e);
        }
    }
}
