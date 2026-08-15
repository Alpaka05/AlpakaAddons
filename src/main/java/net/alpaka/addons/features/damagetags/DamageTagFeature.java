package net.alpaka.addons.features.damagetags;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.regex.Pattern;

public class DamageTagFeature {

    // Matches numbers with formatting like 139.9k, 1, 1,554, 25.4k, ✧1,554M✧, etc.
    private static final Pattern DAMAGE_TAG_PATTERN = Pattern.compile("^[✧✦*]?\\s*[0-9]+(?:[,.][0-9]+)*[kKmMbB]?\\s*[✧✦*]?$");

    public static boolean shouldHideEntity(Entity entity) {
        if (!AlpakaConfig.instance.onlyCritDamageEnabled) return false;
        if (!SkyblockUtils.isOnSkyblock()) return false;

        if (entity instanceof ArmorStand armorStand) {
            return isNonCritDamageTag(armorStand.getCustomName() != null ? armorStand.getCustomName().getString() : null);
        }
        return false;
    }

    public static boolean shouldHideNameTag(Entity entity) {
        if (!AlpakaConfig.instance.onlyCritDamageEnabled) return false;
        if (!SkyblockUtils.isOnSkyblock()) return false;

        return isNonCritDamageTag(entity.getCustomName() != null ? entity.getCustomName().getString() : null);
    }

    public static boolean isNonCritDamageTag(String customName) {
        if (customName == null || customName.isEmpty()) return false;

        String clean = cleanColor(customName);
        if (clean.isEmpty()) return false;

        // Check if it matches a damage number indicator
        if (DAMAGE_TAG_PATTERN.matcher(clean).matches()) {
            // If it contains the crit symbol (✧ / \u2727 or ✦ / \u2726), keep it shown!
            boolean isCrit = clean.contains("✧") || clean.contains("\u2727") || clean.contains("✦") || clean.contains("\u2726");
            return !isCrit; // Hide if it is NOT a crit (e.g. fire, poison, normal non-crit hit)
        }

        return false;
    }

    private static String cleanColor(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
