package net.alpaka.addons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

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

    private static String cleanColor(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
