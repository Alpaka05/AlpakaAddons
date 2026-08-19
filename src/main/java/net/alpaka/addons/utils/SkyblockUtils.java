package net.alpaka.addons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

public class SkyblockUtils {

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
        if (objective != null) {
            String displayName = cleanColor(objective.getDisplayName().getString()).toLowerCase();
            return displayName.contains("skyblock") || displayName.contains("rift");
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

    private static String cleanColor(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
