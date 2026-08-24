package net.alpaka.addons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

public class SkyblockUtils {

    /**
     * The Skyblock area marker Hypixel prefixes the zone line with, e.g. "⏣ Torrhus Canyon".
     * Present on effectively every Skyblock sidebar and, unlike the title, never renamed.
     */
    private static final String AREA_MARKER = "⏣";

    /**
     * Whether the player is on Hypixel Skyblock.
     *
     * Detection is deliberately not based on the sidebar title alone. Hypixel renames that title
     * during events - it reads "BLAZE SIMULATOR" during one - which used to make this return false in
     * the middle of normal play and silently switch off every feature gated on it. The zone marker is
     * checked as well, which survives those renames.
     */
    public static boolean isOnSkyblock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        var serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            String ip = serverData.ip.toLowerCase();
            if (!ip.contains("hypixel.net") && !ip.contains("localhost")) {
                return false;
            }
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return false;

        String title = cleanColor(objective.getDisplayName().getString()).toLowerCase();
        if (title.contains("skyblock") || title.contains("rift")) return true;

        for (String line : getSidebarLines()) {
            if (line.contains(AREA_MARKER)) return true;
        }
        return false;
    }

    /**
     * The sidebar's lines with colour codes stripped, title first.
     *
     * Hypixel writes the player's current zone onto one of these lines, which makes this the
     * client-visible, server-provided answer to "where am I" - no world or chunk scanning involved.
     *
     * Callers should cache the result: assembling this walks the scoreboard and allocates, so it is
     * not suited to per-entity or per-frame use.
     */
    public static List<String> getSidebarLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();

        try {
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective == null) return List.of();

            List<String> lines = new ArrayList<>();
            lines.add(cleanColor(objective.getDisplayName().getString()));

            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                lines.add(cleanColor(lineText(scoreboard, entry)));
            }
            return lines;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Reassembles one sidebar row. Hypixel splits the visible text across the entry's team prefix
     * and suffix, so reading only the entry name would miss most of the line.
     */
    private static String lineText(Scoreboard scoreboard, PlayerScoreEntry entry) {
        PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
        if (team != null) {
            String prefix = team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
            String name = entry.ownerName() != null ? entry.ownerName().getString() : "";
            String suffix = team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
            return prefix + name + suffix;
        }
        return entry.ownerName() != null ? entry.ownerName().getString() : entry.owner();
    }

    /**
     * Strips every legacy formatting code, not just the ones Minecraft itself defines.
     *
     * Scoreboard entries have to be unique strings, so Hypixel pads each sidebar row with otherwise
     * meaningless codes drawn from outside the standard set - {@code §g}-{@code §j}, {@code §p}-{@code
     * §z} - and drops them anywhere in the line, including mid-word. A real captured row reads
     * {@code "Inferno Demonl§jord IV"}. Matching only {@code §[0-9A-FK-OR]} left those in place and
     * silently broke every substring test done against a sidebar line.
     */
    public static String cleanColor(String input) {
        if (input == null) return "";
        return input.replaceAll("§.", "").trim();
    }

    /**
     * Prefixes Hypixel writes in front of the island name in the player list.
     *
     * Both forms taken from SkyHanni's tab-list widget, which matches
     * {@code (Area|Dungeon): (?<island>.*)} - the dungeon variant is used inside the Catacombs,
     * where the line reads "Dungeon: Catacombs" instead.
     */
    private static final String[] AREA_PREFIXES = {"Area:", "Dungeon:"};

    /**
     * The Skyblock island the player is on, or null when it cannot be read.
     *
     * The sidebar only ever names the current <em>zone</em> - "Smoldering Tomb", "Blazing Volcano" -
     * so it cannot answer which island those belong to. Hypixel puts the island itself in the player
     * list instead, which is why this reads from there.
     *
     * Purely a read of a list the client is already showing; nothing is requested from the server.
     * Callers should cache: this walks every tab-list entry.
     */
    public static String getCurrentIsland() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return null;

        try {
            for (var info : mc.player.connection.getListedOnlinePlayers()) {
                Component name = info.getTabListDisplayName();
                if (name == null) continue;

                String line = cleanColor(name.getString());
                for (String prefix : AREA_PREFIXES) {
                    int at = line.indexOf(prefix);
                    if (at < 0) continue;

                    String island = line.substring(at + prefix.length()).trim();
                    if (!island.isEmpty()) return island;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
