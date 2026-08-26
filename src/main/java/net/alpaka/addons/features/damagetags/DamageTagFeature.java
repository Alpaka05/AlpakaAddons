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

        // Almost every name this is handed - players, mobs, holograms - carries a character no
        // damage tag can contain, and rejecting those without stripping the string first is what
        // keeps this allocation-free on the overwhelming majority of entities.
        if (!couldBeDamageTag(customName)) return false;

        String clean = SkyblockUtils.cleanColor(customName);
        if (clean.isEmpty()) return false;

        // Check if it matches a damage number indicator
        if (DAMAGE_TAG_PATTERN.matcher(clean).matches()) {
            // If it contains the crit symbol (✧ / \u2727 or ✦ / \u2726), keep it shown!
            boolean isCrit = clean.contains("✧") || clean.contains("\u2727") || clean.contains("✦") || clean.contains("\u2726");
            return !isCrit; // Hide if it is NOT a crit (e.g. fire, poison, normal non-crit hit)
        }

        return false;
    }

    /**
     * Cheap pre-filter for {@link #isNonCritDamageTag}, run over the raw name.
     *
     * Accepts exactly the alphabet {@link #DAMAGE_TAG_PATTERN} can match, so anything rejected here
     * the pattern would have rejected as well - this only saves the strip and the match, it never
     * changes the verdict. Formatting codes are skipped rather than judged, since stripping would
     * have removed them anyway.
     */
    private static boolean couldBeDamageTag(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '§') {
                i++; // The code's second character belongs to the code, whatever it is.
                continue;
            }
            boolean allowed = (c >= '0' && c <= '9')
                    || c == ',' || c == '.' || c == '*' || Character.isWhitespace(c)
                    || c == 'k' || c == 'K' || c == 'm' || c == 'M' || c == 'b' || c == 'B'
                    || c == '✧' || c == '✦';
            if (!allowed) return false;
        }
        return true;
    }
}
