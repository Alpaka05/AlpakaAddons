package net.alpaka.addons.features.itemsearch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;

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
import java.util.concurrent.TimeUnit;

/**
 * How a SkyBlock item is made and where its wiki page is, for the search window's item view. Read
 * from the NotEnoughUpdates item repository, which the item list mods draw their recipes from too:
 * one small file per item, fetched when the player opens that item and kept until the game
 * restarts. The requests name nothing about the player.
 */
public final class ItemRecipes {
    private ItemRecipes() {}

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
    private static final long RETRY_MS = 30_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** An amount of an item, by the repository's id. */
    public record Stack(String id, double count) {}

    public enum Kind {
        CRAFTING("Crafting Table"), FORGE("Forge"), NPC_SHOP("NPC Shop"), TRADE("Trade"), KAT("Kat Upgrade"),
        DROP("Mob Drop"), OTHER("Recipe");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /**
     * One way to get the item. A crafting recipe has its nine slots in {@code grid}, top row first,
     * empty ones null; the others list what goes in. {@code seconds} is how long the Forge or Kat
     * takes. {@code source} names the NPC that sells it or the mob that drops it, {@code note} a
     * drop's chance, and {@code extra} what the repository adds: requirements, how many drop.
     */
    public record Recipe(Kind kind, List<Stack> grid, List<Stack> inputs, Stack output, long seconds, String note,
                         String source, List<String> extra) {}

    /** What the repository says about an item: its recipes, its wiki pages and what it takes to craft. */
    public record Info(String id, List<Recipe> recipes, List<String> wiki, String craftText) {
        static final Info NONE = new Info(null, List.of(), List.of(), "");
    }

    /** The state of one item's file. */
    public enum State { LOADING, READY, MISSING, FAILED }

    private static final Map<String, Info> KNOWN = new ConcurrentHashMap<>();
    private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();

    /**
     * The item's info, or null while it is on its way; asking starts fetching it. From the whole
     * repository once that is read, with the NPCs that sell it and the mobs that drop it; until then
     * from the item's own file.
     */
    public static Info get(String id) {
        if (id == null) return Info.NONE;
        NeuRepo.Index index = NeuRepo.get();
        if (index != null) return index.info(id);
        Info known = KNOWN.get(id);
        if (known != null) return known;
        if (!AlpakaConfig.instance.allowApiCalls) return null;
        Long waitUntil = PENDING.get(id);
        if (waitUntil != null && (waitUntil == Long.MAX_VALUE || System.currentTimeMillis() < waitUntil)) return null;
        PENDING.put(id, Long.MAX_VALUE);
        fetch(id);
        return null;
    }

    /**
     * Whether Hypixel's /viewrecipe opens a recipe menu for the item, from which it can be crafted:
     * what the repository says once it is read, else whether the item has a crafting recipe.
     */
    public static boolean hasHypixelRecipe(String id) {
        if (id == null || id.contains(";")) return false;
        NeuRepo.Index index = NeuRepo.get();
        if (index != null) return index.viewRecipe().contains(id);
        Info info = get(id);
        if (info == null) return false;
        for (Recipe r : info.recipes()) if (r.kind() == Kind.CRAFTING && id.equals(r.output().id())) return true;
        return false;
    }

    /** The recipes the item goes into; null while the repository is read. */
    public static List<Recipe> usedIn(String id) {
        NeuRepo.Index index = NeuRepo.get();
        return index == null ? null : index.used().getOrDefault(id, List.of());
    }

    /** An item's name: Hypixel's, else the repository's, else made from its id. */
    public static String displayName(String id) {
        if (id == null) return "";
        String name = net.alpaka.addons.features.market.ItemIcons.name(id);
        if (name != null) return name;
        NeuRepo.Index index = NeuRepo.get();
        if (index != null && index.names().containsKey(id)) return index.names().get(id);
        return prettyId(id);
    }

    private static final String[] PET_TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    /** An item's rarity: Hypixel's, or for a pet ("ARMADILLO;3") the one its id ends in. */
    public static String tierOf(String id) {
        if (id == null) return null;
        String tier = net.alpaka.addons.features.market.ItemIcons.tier(id);
        if (tier != null) return tier;
        NeuRepo.Index index = NeuRepo.get();
        if (index != null && index.tiers().containsKey(id)) return index.tiers().get(id);
        int semi = id.lastIndexOf(';');
        if (semi > 0 && semi == id.length() - 2 && Character.isDigit(id.charAt(semi + 1))) {
            int i = id.charAt(semi + 1) - '0';
            if (i < PET_TIERS.length) return PET_TIERS[i];
        }
        return null;
    }

    /** A repository name without its color codes and a pet's level placeholder: "§6[Lvl {LVL}] Armadillo" as "Armadillo". */
    static String cleanName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("§.", "").replace("[Lvl {LVL}] ", "").trim();
    }

    /** Where the item's file stands, for the item view's message. */
    public static State state(String id) {
        Info known = id == null ? Info.NONE : KNOWN.get(id);
        if (known != null) return known.id() == null ? State.MISSING : State.READY;
        Long waitUntil = PENDING.get(id);
        return waitUntil != null && waitUntil != Long.MAX_VALUE ? State.FAILED : State.LOADING;
    }

    private static void fetch(String id) {
        String file = URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(URI.create(REPO + file + ".json"))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET()
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || (response.statusCode() != 200 && response.statusCode() != 404)) {
                            PENDING.put(id, System.currentTimeMillis() + RETRY_MS);
                            return;
                        }
                        KNOWN.put(id, response.statusCode() == 404 ? Info.NONE : own(parse(id, response.body())));
                        PENDING.remove(id);
                    } catch (RuntimeException e) {
                        AlpakaAddons.LOGGER.debug("Could not read the NEU recipe of {}", id, e);
                        KNOWN.put(id, Info.NONE);
                        PENDING.remove(id);
                    }
                });
    }

    /** Only the recipes of a file that make its own item: an item's file alone knows no more. */
    private static Info own(Info info) {
        if (info.id() == null) return info;
        List<Recipe> mine = new ArrayList<>();
        for (Recipe r : info.recipes()) if (r.output().id().equals(info.id())) mine.add(r);
        return new Info(info.id(), mine, info.wiki(), info.craftText());
    }

    /** Reads a repository file: the old single crafting recipe, the newer list of recipes, and the wiki links. */
    static Info parse(String id, String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return Info.NONE;
        JsonObject o = root.getAsJsonObject();
        return read(id, o, cleanName(string(o, "displayname")));
    }

    /**
     * Every recipe in a file, whatever it makes: an NPC's file lists what the NPC sells, a mob's
     * what it drops. {@code name} is the file's own name, the NPC's or the mob's.
     */
    static Info read(String id, JsonObject o, String name) {
        List<Recipe> recipes = new ArrayList<>();
        if (o.has("recipe") && o.get("recipe").isJsonObject()) {
            Recipe crafting = crafting(id, o.getAsJsonObject("recipe"));
            if (crafting != null) recipes.add(crafting);
        }
        if (o.has("recipes") && o.get("recipes").isJsonArray()) {
            for (JsonElement element : o.getAsJsonArray("recipes")) {
                if (!element.isJsonObject()) continue;
                for (Recipe recipe : recipes(id, element.getAsJsonObject(), name)) {
                    if (!recipes.contains(recipe)) recipes.add(recipe);
                }
            }
        }
        List<String> wiki = new ArrayList<>();
        if (o.has("info") && o.get("info").isJsonArray()) {
            for (JsonElement element : o.getAsJsonArray("info")) {
                if (!element.isJsonPrimitive()) continue;
                String url = element.getAsString();
                if (url.startsWith("https://")) wiki.add(url);
            }
        }
        String craftText = o.has("crafttext") && o.get("crafttext").isJsonPrimitive() ? o.get("crafttext").getAsString() : "";
        return new Info(id, recipes, wiki, craftText);
    }

    private static List<Recipe> recipes(String id, JsonObject o, String fileName) {
        String type = string(o, "type");
        Stack output = new Stack(o.has("overrideOutputId") ? string(o, "overrideOutputId") : id,
                o.has("count") ? number(o.get("count")) : 1);
        switch (type) {
            case "", "crafting" -> {
                Recipe crafting = crafting(id, o);
                return crafting == null ? List.of() : List.of(crafting);
            }
            case "forge" -> {
                long seconds = o.has("duration") ? (long) number(o.get("duration")) : 0;
                return List.of(new Recipe(Kind.FORGE, List.of(), stacks(o, "inputs"), output, seconds, "", null, List.of()));
            }
            case "npc_shop" -> {
                Stack result = o.has("result") ? stack(string(o, "result")) : output;
                return List.of(new Recipe(Kind.NPC_SHOP, List.of(), stacks(o, "cost"), result == null ? output : result, 0, "",
                        fileName.isEmpty() ? null : fileName, List.of()));
            }
            case "trade" -> {
                Stack cost = stack(string(o, "cost"));
                Stack result = o.has("result") ? stack(string(o, "result")) : output;
                return List.of(new Recipe(Kind.TRADE, List.of(), cost == null ? List.of() : List.of(cost),
                        result == null ? output : result, 0, "", null, List.of()));
            }
            case "katgrade" -> {
                List<Stack> inputs = new ArrayList<>();
                Stack pet = stack(string(o, "input"));
                if (pet != null) inputs.add(pet);
                inputs.addAll(stacks(o, "items"));
                double coins = o.has("coins") ? number(o.get("coins")) : 0;
                if (coins > 0) inputs.add(new Stack("SKYBLOCK_COIN", coins));
                Stack result = stack(string(o, "output"));
                long seconds = o.has("time") ? (long) number(o.get("time")) : 0;
                return List.of(new Recipe(Kind.KAT, List.of(), inputs, result == null ? output : result, seconds, "", null, List.of()));
            }
            case "drops" -> {
                // A mob's file: one way to get each of its drops.
                String mob = cleanName(string(o, "name"));
                if (mob.isEmpty()) mob = fileName;
                if (o.has("level") && o.get("level").isJsonPrimitive()) mob += " (Lv " + (long) number(o.get("level")) + ")";
                List<String> mobExtra = lines(o, "extra");
                List<Recipe> out = new ArrayList<>();
                if (!o.has("drops") || !o.get("drops").isJsonArray()) return out;
                for (JsonElement element : o.getAsJsonArray("drops")) {
                    if (!element.isJsonObject()) continue;
                    JsonObject drop = element.getAsJsonObject();
                    Stack item = stack(string(drop, "id"));
                    if (item == null) continue;
                    List<String> extra = new ArrayList<>(lines(drop, "extra"));
                    extra.addAll(mobExtra);
                    out.add(new Recipe(Kind.DROP, List.of(), List.of(), item, 0, string(drop, "chance"), mob, extra));
                }
                return out;
            }
            default -> {
                return List.of();
            }
        }
    }

    /** A list of text lines, color codes taken out. */
    private static List<String> lines(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (JsonElement element : o.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) continue;
            String line = cleanName(element.getAsString());
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    /** A crafting grid, A1 to C3 with the letter the row. Null when every slot is empty. */
    private static Recipe crafting(String id, JsonObject o) {
        List<Stack> grid = new ArrayList<>(9);
        boolean any = false;
        for (char row = 'A'; row <= 'C'; row++) {
            for (int col = 1; col <= 3; col++) {
                Stack s = stack(string(o, "" + row + col));
                grid.add(s);
                any |= s != null;
            }
        }
        if (!any) return null;
        int count = o.has("count") ? (int) number(o.get("count")) : 1;
        String out = o.has("overrideOutputId") ? string(o, "overrideOutputId") : id;
        return new Recipe(Kind.CRAFTING, grid, List.of(), new Stack(out, Math.max(1, count)), 0, "", null, List.of());
    }

    private static List<Stack> stacks(JsonObject o, String key) {
        List<Stack> out = new ArrayList<>();
        if (!o.has(key) || !o.get(key).isJsonArray()) return out;
        for (JsonElement element : o.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) continue;
            Stack s = stack(element.getAsString());
            if (s != null) out.add(s);
        }
        return out;
    }

    /** "ENCHANTED_DIAMOND:32" as an item and an amount; null for an empty slot. */
    static Stack stack(String text) {
        if (text == null || text.isBlank()) return null;
        int colon = text.lastIndexOf(':');
        if (colon <= 0) return new Stack(text.trim(), 1);
        try {
            return new Stack(text.substring(0, colon).trim(), Double.parseDouble(text.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return new Stack(text.trim(), 1);
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    private static double number(JsonElement e) {
        try {
            return e.getAsDouble();
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    /** "6h", "1d 4h", "30m": how long the Forge takes. */
    public static String duration(long seconds) {
        long d = seconds / 86400, h = seconds / 3600 % 24, m = seconds / 60 % 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append("d ");
        if (h > 0) out.append(h).append("h ");
        if (m > 0 && d == 0) out.append(m).append("m ");
        if (out.isEmpty()) out.append(seconds).append("s");
        return out.toString().trim();
    }

    /** The wiki page to open: the repository's first link, else the official wiki's page by the item's name. */
    public static String wikiUrl(Info info, String name) {
        if (info != null && !info.wiki().isEmpty()) return info.wiki().get(0);
        String page = name == null ? "" : name.trim().replace(' ', '_');
        return "https://hypixelskyblock.minecraft.wiki/w/Special:Search?search="
                + URLEncoder.encode(page.replace('_', ' '), StandardCharsets.UTF_8);
    }

    /** An id as words, for an ingredient Hypixel's list does not name: ENCHANTED_DIAMOND as "Enchanted Diamond". */
    public static String prettyId(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("[_;]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
