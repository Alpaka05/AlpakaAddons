package net.alpaka.addons.features.itemsearch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The whole NotEnoughUpdates item repository, for what one item's file cannot say: what an item
 * is used in, which NPC sells it and which mob drops it (those live in the NPC's and the mob's
 * files). Downloaded once as an archive, about 22 MB, kept on disk and fetched again only when the
 * repository has changed, checked at most every few hours. The requests name nothing about the player.
 */
public final class NeuRepo {
    private NeuRepo() {}

    private static final String OWNER_REPO = "NotEnoughUpdates/NotEnoughUpdates-REPO";
    private static final long CHECK_EVERY_MS = 6L * 60 * 60 * 1000;
    private static final long RETRY_MS = 60_000L;
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");

    /** Every recipe of the repository: by what it makes, by what goes into it, and each file's own facts. */
    public record Index(Map<String, List<ItemRecipes.Recipe>> made, Map<String, List<ItemRecipes.Recipe>> used,
                        Map<String, ItemRecipes.Info> infos, Map<String, String> names, Set<String> viewRecipe) {
        /** The item's recipes, wiki links and requirement, as the item view shows them. */
        ItemRecipes.Info info(String id) {
            ItemRecipes.Info own = infos.get(id);
            List<ItemRecipes.Recipe> recipes = made.getOrDefault(id, List.of());
            return new ItemRecipes.Info(id, recipes, own == null ? List.of() : own.wiki(), own == null ? "" : own.craftText());
        }
    }

    public enum State { IDLE, LOADING, READY, FAILED, OFF }

    private static volatile Index index = null;
    private static volatile State state = State.IDLE;
    private static volatile long failedAt = 0L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Alpaka NEU repo");
        t.setDaemon(true);
        return t;
    });

    /** The index, or null while it loads; asking starts loading it. */
    public static Index get() {
        Index known = index;
        if (known != null) return known;
        start();
        return null;
    }

    public static State state() {
        if (index != null) return State.READY;
        if (!AlpakaConfig.instance.allowApiCalls) return State.OFF;
        return state;
    }

    private static synchronized void start() {
        if (!AlpakaConfig.instance.allowApiCalls || state == State.LOADING) return;
        if (state == State.FAILED && System.currentTimeMillis() - failedAt < RETRY_MS) return;
        state = State.LOADING;
        WORKER.execute(() -> {
            try {
                Path zip = fetch();
                long started = System.currentTimeMillis();
                index = read(zip);
                state = State.READY;
                AlpakaAddons.LOGGER.info("NEU repository read: {} items with recipes in {} ms",
                        index.made().size(), System.currentTimeMillis() - started);
            } catch (Exception e) {
                AlpakaAddons.LOGGER.warn("Could not load the NEU repository: {}", e.toString());
                failedAt = System.currentTimeMillis();
                state = State.FAILED;
            }
        });
    }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** The archive on disk, downloaded again when the repository has a newer commit than the one kept. */
    private static Path fetch() throws IOException, InterruptedException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("alpaka-neu-repo");
        Files.createDirectories(dir);
        Path zip = dir.resolve("repo.zip");
        Path shaFile = dir.resolve("commit.txt");
        String saved = Files.isRegularFile(shaFile) ? Files.readString(shaFile).trim() : "";
        boolean have = Files.isRegularFile(zip);
        if (have && Files.isRegularFile(shaFile)
                && System.currentTimeMillis() - Files.getLastModifiedTime(shaFile).toMillis() < CHECK_EVERY_MS) {
            return zip;
        }
        HttpClient client = http();
        String sha;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + OWNER_REPO + "/commits/master"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github.sha")
                    .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            sha = response.body() == null ? "" : response.body().trim();
            if (response.statusCode() != 200 || !SHA.matcher(sha).matches()) throw new IOException("commit check " + response.statusCode());
        } catch (IOException e) {
            // Offline or rate limited: the archive kept on disk will do.
            if (have) return zip;
            throw e;
        }
        if (have && sha.equals(saved)) {
            Files.setLastModifiedTime(shaFile, FileTime.fromMillis(System.currentTimeMillis()));
            return zip;
        }
        Path part = dir.resolve("repo.zip.part");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/" + OWNER_REPO + "/archive/" + sha + ".zip"))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(part));
        if (response.statusCode() != 200 || Files.size(part) > MAX_BYTES) {
            Files.deleteIfExists(part);
            if (have) return zip;
            throw new IOException("download " + response.statusCode());
        }
        Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.writeString(shaFile, sha);
        return zip;
    }

    /** Reads every item file of the archive. */
    private static Index read(Path zipPath) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                int items = name.indexOf("/items/");
                if (entry.isDirectory() || items < 0 || !name.endsWith(".json")) continue;
                String file = name.substring(items + "/items/".length());
                if (file.contains("/")) continue;
                if (entry.getSize() > 4L * 1024 * 1024) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    files.put(file.substring(0, file.length() - ".json".length()), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return index(files);
    }

    /** The index of these item files, by their ids. */
    static Index index(Map<String, String> files) {
        Map<String, List<ItemRecipes.Recipe>> made = new HashMap<>();
        Map<String, List<ItemRecipes.Recipe>> used = new HashMap<>();
        Map<String, ItemRecipes.Info> infos = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        Set<String> viewRecipe = new java.util.HashSet<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            String id = file.getKey();
            JsonObject o;
            try {
                JsonElement root = JsonParser.parseString(file.getValue());
                if (!root.isJsonObject()) continue;
                o = root.getAsJsonObject();
            } catch (RuntimeException e) {
                continue;
            }
            String name = ItemRecipes.cleanName(o.has("displayname") && o.get("displayname").isJsonPrimitive()
                    ? o.get("displayname").getAsString() : "");
            if (!name.isEmpty()) names.put(id, name);
            // Items Hypixel's /viewrecipe knows, which opens its own recipe menu to craft from.
            if (o.has("clickcommand") && o.get("clickcommand").isJsonPrimitive()
                    && "viewrecipe".equals(o.get("clickcommand").getAsString())) viewRecipe.add(id);
            ItemRecipes.Info info = ItemRecipes.read(id, o, name);
            infos.put(id, new ItemRecipes.Info(id, List.of(), info.wiki(), info.craftText()));
            for (ItemRecipes.Recipe recipe : info.recipes()) {
                add(made, recipe.output().id(), recipe);
                Set<String> inputs = new LinkedHashSet<>();
                for (ItemRecipes.Stack s : recipe.grid()) if (s != null) inputs.add(s.id());
                for (ItemRecipes.Stack s : recipe.inputs()) inputs.add(s.id());
                inputs.remove("SKYBLOCK_COIN");
                inputs.remove(recipe.output().id());
                for (String input : inputs) add(used, input, recipe);
            }
        }
        // Crafting and the Forge first, then shops, trades and Kat, the mob drops last.
        for (List<ItemRecipes.Recipe> list : made.values()) list.sort(java.util.Comparator.comparingInt(r -> r.kind().ordinal()));
        return new Index(made, used, infos, names, viewRecipe);
    }

    private static void add(Map<String, List<ItemRecipes.Recipe>> map, String id, ItemRecipes.Recipe recipe) {
        List<ItemRecipes.Recipe> list = map.computeIfAbsent(id, k -> new ArrayList<>());
        if (!list.contains(recipe)) list.add(recipe);
    }
}
