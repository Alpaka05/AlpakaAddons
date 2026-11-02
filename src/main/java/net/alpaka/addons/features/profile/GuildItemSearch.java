package net.alpaka.addons.features.profile;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.alpaka.addons.config.AtomicJsonFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** One explicit, resumable search. Only the current profile's inventory references stay in memory. */
final class GuildItemSearch {
    interface Source {
        JsonElement get(String path, boolean skyKings) throws Exception;
    }

    static final class Failure extends IOException {
        final int status;
        final long retryAt;

        Failure(int status, String message, long retryAt) {
            super(message);
            this.status = status;
            this.retryAt = retryAt;
        }
    }

    record Guild(int rank, String name) {}
    record Player(String uuid, String name, String guild) {}
    record Profile(String id, String name) {}
    record Inventory(String id, String name) {}
    record Match(String playerUuid, String player, String guild, String profileId, String profile,
                 String inventory, String slot, long updated, JsonObject item) {
        String location() { return locationName(inventory, slot); }
    }
    record Issue(String player, String guild, String profile, String reason) {}
    record View(boolean running, boolean complete, boolean cancelled, String message, long started,
                int guildsLoaded, List<Guild> guilds, int playersDone, int playersTotal,
                int profilesChecked, int inventoriesChecked, List<Match> matches, List<Issue> issues) {}

    static final class Progress {
        int schema = 1;
        long started = System.currentTimeMillis();
        List<Guild> guilds;
        int guildIndex;
        List<Player> players = new ArrayList<>();
        int playerIndex;
        List<Profile> profiles;
        int profileIndex;
        Set<String> checkedInventories = new HashSet<>();
        int profilesChecked;
        int inventoriesChecked;
        List<Match> matches = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        boolean complete;
        boolean cancelled;
    }

    private static final Gson GSON = new Gson();
    private final Path file;
    private final Source source;
    private final BooleanSupplier allowed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Alpaka Guild Item Search");
        thread.setDaemon(true);
        return thread;
    });
    private Progress progress;
    private List<Inventory> inventories;
    private long updated;
    private volatile boolean stop;
    private volatile boolean running;
    private volatile boolean cancelRequested;
    private boolean checkpointBlocked;
    private volatile View view;

    GuildItemSearch(Path file, Source source, BooleanSupplier allowed) {
        this.file = file;
        this.source = source;
        this.allowed = allowed;
        progress = new Progress();
        String message = "Ready to search the top 10 Normal guilds on SkyKings.";
        try {
            String saved = AtomicJsonFile.read(file.toFile());
            if (saved != null) {
                Progress loaded = GSON.fromJson(saved, Progress.class);
                if (!valid(loaded)) throw new IllegalArgumentException("Invalid search checkpoint");
                progress = loaded;
                message = loaded.complete ? "Search finished. Results are from the saved run."
                        : loaded.cancelled ? "Search cancelled. Results are saved." : "Saved search loaded. Resume when ready.";
            }
        } catch (IOException | RuntimeException error) {
            // Preserve an unreadable checkpoint before a new search can replace it.
            if (AtomicJsonFile.quarantine(file.toFile()) == null && file.toFile().exists()) {
                message = "Couldn't preserve the unreadable checkpoint. Move it aside before starting.";
                checkpointBlocked = true;
            } else message = "Unreadable checkpoint preserved separately. Start a new search.";
        }
        publish(message);
    }

    private static boolean valid(Progress p) {
        return p != null && p.schema == 1 && p.players != null && p.checkedInventories != null
                && p.matches != null && p.issues != null && p.guildIndex >= 0 && p.playerIndex >= 0
                && p.playerIndex <= p.players.size() && p.profileIndex >= 0
                && (p.guilds == null ? p.guildIndex == 0 : p.guildIndex <= p.guilds.size())
                && (p.profiles == null ? p.profileIndex == 0 : p.profileIndex <= p.profiles.size());
    }

    View view() { return view; }

    synchronized void start(boolean fresh) {
        if (running || checkpointBlocked) return;
        if (!allowed.getAsBoolean()) {
            publish("Turn on Network Features and set your SkyKings API key in the profile settings.");
            return;
        }
        if (fresh) progress = new Progress();
        if (progress.complete || progress.cancelled) return;
        inventories = null;
        stop = false;
        cancelRequested = false;
        running = true;
        publish("Starting search…");
        worker.execute(this::run);
    }

    void pause() { stop = true; }
    synchronized void cancel() {
        cancelRequested = true;
        stop = true;
        if (!running) {
            progress.cancelled = true;
            publish("Search cancelled. Results are saved.");
            worker.execute(() -> {
                synchronized (this) {
                    try { checkpoint(); }
                    catch (IOException error) { publish("Couldn't save the cancelled search. Check disk space."); }
                }
            });
        }
    }

    private void run() {
        String message = "Search paused. Progress is saved.";
        try {
            checkpoint();
            while (!stop && allowed.getAsBoolean() && !progress.complete) {
                step();
                checkpoint();
            }
            if (!allowed.getAsBoolean()) message = "Search paused: Network Features are off or the SkyKings key is missing.";
            else if (progress.complete) message = "Search finished. Check Not Checked for coverage gaps.";
        } catch (InterruptedException interrupted) {
            // Pause is cooperative: keep the worker usable and the checkpoint write uninterrupted.
        } catch (Exception error) {
            // HTTP failures use fixed messages; never surface a request or its Authorization header.
            message = error instanceof Failure ? error.getMessage()
                    : error instanceof IOException ? "Couldn't save the search. Check disk space and resume."
                    : "Search paused after an unexpected data error. Resume to retry.";
        } finally {
            synchronized (this) {
                if (cancelRequested) {
                    progress.cancelled = true;
                    message = "Search cancelled. Results are saved.";
                }
                try { checkpoint(); }
                catch (IOException error) { message = "Couldn't save the search. Check disk space before continuing."; }
                running = false;
                publish(message);
            }
        }
    }

    /** A successful step is the unit written to disk, so an interruption only repeats one request. */
    void step() throws Exception {
        if (progress.guilds == null) {
            publish("Loading SkyKings' Normal guild ranking…");
            JsonElement answer = fetch("/leaderboard/guilds?profile_type=normal", true);
            requireArray(answer, "guilds");
            List<Guild> guilds = new ArrayList<>();
            for (JsonElement guild : Json.arr(answer, "guilds")) {
                int rank = (int) Json.num(guild, "rank", 0);
                String name = Json.str(guild, "name");
                if (rank >= 1 && rank <= 10 && name != null && !name.isBlank()) guilds.add(new Guild(rank, name));
            }
            guilds.sort(Comparator.comparingInt(Guild::rank));
            if (guilds.size() != 10 || guilds.stream().map(Guild::rank).distinct().count() != 10
                    || guilds.stream().map(Guild::name).distinct().count() != 10) {
                throw new Failure(0, "SkyKings didn't return all ten guilds. Resume to retry.", 0);
            }
            progress.guilds = guilds;
            return;
        }
        if (progress.guildIndex < progress.guilds.size()) {
            Guild guild = progress.guilds.get(progress.guildIndex);
            publish("Loading members of #" + guild.rank() + " " + guild.name() + "…");
            JsonElement answer = fetch("/guild/members?name=" + URLEncoder.encode(guild.name(), StandardCharsets.UTF_8), true);
            requireArray(answer, "members");
            if (Json.arr(answer, "members").isEmpty()) {
                throw new Failure(0, "SkyKings returned an empty guild roster. Resume to retry.", 0);
            }
            LinkedHashMap<String, Player> players = new LinkedHashMap<>();
            for (Player player : progress.players) players.put(player.uuid(), player);
            for (JsonElement member : Json.arr(answer, "members")) {
                String uuid = normalizeUuid(Json.str(member, "uuid"));
                String name = Json.str(member, "username");
                if (uuid == null || name == null || name.isBlank()) {
                    throw new Failure(0, "SkyKings returned an incomplete member list. Resume to retry.", 0);
                }
                players.putIfAbsent(uuid, new Player(uuid, name, guild.name()));
            }
            progress.players = new ArrayList<>(players.values());
            progress.guildIndex++;
            return;
        }
        if (progress.playerIndex >= progress.players.size()) {
            progress.complete = true;
            return;
        }
        Player player = progress.players.get(progress.playerIndex);
        if (progress.profiles == null) {
            publish("Loading profiles of " + player.name() + "…");
            JsonElement account;
            try { account = fetch("/account/" + player.uuid(), false); }
            catch (Failure error) {
                if (error.status != 404) throw error;
                issue(player, "", "Player not found by Elite.");
                nextPlayer();
                return;
            }
            requireArray(account, "profiles");
            progress.profiles = profiles(account);
            if (progress.profiles.isEmpty()) issue(player, "", "No accessible SkyBlock profiles.");
            return;
        }
        if (progress.profileIndex >= progress.profiles.size()) {
            nextPlayer();
            return;
        }
        Profile profile = progress.profiles.get(progress.profileIndex);
        String path = "/profile/" + player.uuid() + "/" + profile.id();
        if (inventories == null) {
            publish("Loading " + player.name() + " / " + profile.name() + "…");
            JsonElement member;
            try { member = fetch(path, false); }
            catch (Failure error) {
                if (error.status != 404) throw error;
                issue(player, profile.name(), "Profile no longer accessible.");
                nextProfile();
                return;
            }
            if (!Json.bool(member, "api.inventories")) {
                issue(player, profile.name(), "Inventory API is off or unavailable.");
                nextProfile();
                return;
            }
            requireArray(member, "inventories");
            updated = (long) Json.num(member, "lastUpdated", 0);
            inventories = new ArrayList<>();
            for (JsonElement ref : Json.arr(member, "inventories")) {
                String name = Json.str(ref, "name");
                String id = Json.str(ref, "id");
                if (searchInventory(name)) {
                    if (normalizeUuid(id) == null) throw new Failure(0, "Elite returned an invalid inventory reference.", 0);
                    inventories.add(new Inventory(id, name));
                }
            }
            // Check the cheaper armor and wardrobe requests before the many individual backpacks.
            inventories.sort(Comparator.comparingInt(i -> i.name().startsWith("backpack_") ? 1 : 0));
            return;
        }
        Inventory inventory = inventories.stream().filter(i -> !progress.checkedInventories.contains(i.name())).findFirst().orElse(null);
        if (inventory == null) {
            nextProfile();
            return;
        }
        publish("Checking " + player.name() + " / " + profile.name() + " / " + locationName(inventory.name(), "") + "…");
        JsonElement answer;
        try { answer = fetch(path + "/inventories/" + inventory.id(), false); }
        catch (Failure error) {
            if (error.status != 404) throw error;
            issue(player, profile.name(), locationName(inventory.name(), "") + " is no longer accessible.");
            progress.checkedInventories.add(inventory.name());
            return;
        }
        if (Json.at(answer, "items") == null || !Json.at(answer, "items").isJsonObject()) {
            throw new Failure(0, "Elite returned no inventory contents. Resume to retry.", 0);
        }
        for (var entry : Json.obj(answer, "items").entrySet()) {
            JsonElement item = entry.getValue();
            if (!matches(item)) continue;
            Match match = new Match(player.uuid(), player.name(), player.guild(), profile.id(), profile.name(),
                    inventory.name(), entry.getKey(), updated, item.getAsJsonObject().deepCopy());
            String itemUuid = Json.str(item, "uuid");
            boolean duplicate = progress.matches.stream().anyMatch(m -> m.playerUuid().equals(player.uuid())
                    && m.profileId().equals(profile.id()) && (itemUuid != null ? itemUuid.equals(Json.str(m.item(), "uuid"))
                    : m.inventory().equals(inventory.name()) && m.slot().equals(entry.getKey())));
            if (!duplicate) progress.matches.add(match);
        }
        progress.checkedInventories.add(inventory.name());
        progress.inventoriesChecked++;
    }

    private JsonElement fetch(String path, boolean skyKings) throws Exception {
        for (int attempt = 0; ; attempt++) {
            if (stop || !allowed.getAsBoolean()) throw new InterruptedException();
            try { return source.get(path, skyKings); }
            catch (Failure error) {
                if (error.status == 401 || error.status == 403 || error.status == 404) throw error;
                if (error.status != 429 && attempt >= 2) throw error;
                long until = Math.max(error.retryAt, System.currentTimeMillis() + (error.status == 429 ? 30_000L : 2_000L * (attempt + 1)));
                publish("Waiting for " + (skyKings ? "SkyKings" : "Elite") + ". Retry at "
                        + java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0) + ".");
                while (System.currentTimeMillis() < until) {
                    if (stop || !allowed.getAsBoolean()) throw new InterruptedException();
                    Thread.sleep(Math.min(250L, Math.max(1L, until - System.currentTimeMillis())));
                }
            }
        }
    }

    private void nextProfile() {
        progress.profilesChecked++;
        progress.profileIndex++;
        progress.checkedInventories.clear();
        inventories = null;
    }

    private void nextPlayer() {
        progress.playerIndex++;
        progress.profiles = null;
        progress.profileIndex = 0;
        progress.checkedInventories.clear();
        inventories = null;
    }

    private void issue(Player player, String profile, String reason) {
        progress.issues.add(new Issue(player.name(), player.guild(), profile, reason));
    }

    private void publish(String message) {
        view = new View(running, progress.complete, progress.cancelled, message, progress.started,
                progress.guildIndex, progress.guilds == null ? List.of() : List.copyOf(progress.guilds),
                progress.playerIndex, progress.players.size(), progress.profilesChecked, progress.inventoriesChecked,
                List.copyOf(progress.matches), List.copyOf(progress.issues));
    }

    void checkpoint() throws IOException {
        AtomicJsonFile.write(file.toFile(), GSON.toJson(progress));
    }

    static List<Profile> profiles(JsonElement account) {
        List<Profile> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement profile : Json.arr(account, "profiles")) {
            String id = normalizeUuid(Json.str(profile, "profileId"));
            if (id == null || Json.bool(profile, "deleted") || Json.bool(profile, "wasRemoved") || !seen.add(id)) continue;
            String name = Json.str(profile, "profileName");
            out.add(new Profile(id, name == null ? id : name));
        }
        return out;
    }

    static boolean searchInventory(String name) {
        return name != null && (Set.of("inventory", "ender_chest", "wardrobe", "armor").contains(name)
                || name.matches("backpack_\\d+"));
    }

    static boolean matches(JsonElement item) {
        if (!"DCTR_SPACE_HELM".equals(Json.str(item, "skyblockId"))) return false;
        String sender = Json.str(item, "attributes.sender_name");
        if (sender != null && !sender.isBlank()) return clean(sender).equalsIgnoreCase("[ADMIN] Dctr");
        for (JsonElement line : Json.arr(item, "lore")) {
            if (line.isJsonPrimitive() && clean(line.getAsString()).matches("(?i)From:\\s*\\[ADMIN]\\s+Dctr")) return true;
        }
        return false;
    }

    private static String clean(String text) { return text.replaceAll("(?i)§[0-9A-FK-ORX]", "").trim(); }

    private static String normalizeUuid(String uuid) {
        if (uuid == null) return null;
        String clean = uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        return clean.matches("[0-9a-f]{32}") ? clean : null;
    }

    private static void requireArray(JsonElement root, String field) throws Failure {
        JsonElement element = Json.at(root, field);
        if (element == null || !element.isJsonArray()) throw new Failure(0, "Data source returned no " + field + ". Resume to retry.", 0);
    }

    static String locationName(String inventory, String slot) {
        String name = switch (inventory) {
            case "inventory" -> "Inventory";
            case "ender_chest" -> "Ender Chest";
            case "wardrobe" -> "Wardrobe";
            case "armor" -> "Worn Armor";
            default -> inventory.startsWith("backpack_") ? "Backpack " + (Integer.parseInt(inventory.substring(9)) + 1) : inventory;
        };
        if (slot.isEmpty()) return name;
        if (inventory.equals("wardrobe") && slot.matches("\\d+:[A-Z_]+")) {
            String[] parts = slot.split(":", 2);
            return name + " · Set " + parts[0] + " · " + ProfileData.title(parts[1]);
        }
        try {
            int index = Integer.parseInt(slot);
            if (inventory.equals("ender_chest")) return name + " · Page " + (index / 45 + 1) + " · Slot " + (index % 45 + 1);
            if (inventory.equals("armor") && index >= 0 && index < 4) {
                return name + " · " + List.of("Boots", "Leggings", "Chestplate", "Helmet").get(index);
            }
            return name + " · Slot " + (index + 1);
        } catch (NumberFormatException nonNumericSlot) {
            return name + " · " + slot;
        }
    }

    String csv() {
        StringBuilder csv = new StringBuilder("Player,Guild,Profile,Location,Item UUID,Recipient,Data Updated\r\n");
        for (Match match : view.matches()) {
            csv.append(csvCell(match.player())).append(',').append(csvCell(match.guild())).append(',')
                    .append(csvCell(match.profile())).append(',').append(csvCell(match.location())).append(',')
                    .append(csvCell(Json.str(match.item(), "uuid"))).append(',')
                    .append(csvCell(Json.str(match.item(), "attributes.recipient_name"))).append(',')
                    .append(match.updated()).append("\r\n");
        }
        return csv.toString();
    }

    private static String csvCell(String value) {
        String text = value == null ? "" : clean(value);
        // Player-controlled names must stay text when the CSV is opened in a spreadsheet.
        if (text.matches("^[=+@-].*")) text = "'" + text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
