package net.alpaka.addons.features.damagetags;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.regex.Pattern;

public class DamageTagFeature {

    /**
     * A plain damage number: comma-grouped digits ({@code 1,234}) or a compact value with a suffix
     * ({@code 139k}, {@code 1.5M}). A bare decimal without a suffix is not one - that is what Hypixel's
     * timers and counters look like, and the old pattern hid those too.
     */
    private static final Pattern DAMAGE_NUMBER = Pattern.compile("^(?:\\d{1,3}(?:,\\d{3})*|\\d+(?:\\.\\d+)?[kKmMbB])$");

    /** Damage splashes are short-lived; an armor stand older than this is a hologram, not a splash. */
    private static final int MAX_TAG_AGE_TICKS = 300;

    public static boolean shouldHideEntity(Entity entity) {
        if (!AlpakaConfig.instance.onlyCritDamageEnabled) return false;
        if (!SkyblockUtils.isOnSkyblock()) return false;

        return isNonCritSplash(entity);
    }

    public static boolean shouldHideNameTag(Entity entity) {
        if (!AlpakaConfig.instance.onlyCritDamageEnabled) return false;
        if (!SkyblockUtils.isOnSkyblock()) return false;

        return isNonCritSplash(entity);
    }

    /** A young armor stand whose name is a non-crit damage number. Other entities never are. */
    private static boolean isNonCritSplash(Entity entity) {
        if (!(entity instanceof ArmorStand armorStand) || armorStand.tickCount > MAX_TAG_AGE_TICKS) return false;
        return isNonCritDamageTag(armorStand.getCustomName() != null ? armorStand.getCustomName().getString() : null);
    }

    public static boolean isNonCritDamageTag(String customName) {
        if (customName == null || customName.isEmpty()) return false;

        // Almost every name this is handed - players, mobs, holograms - carries a character no
        // damage tag can contain, and rejecting those without stripping the string first is what
        // keeps this allocation-free on the overwhelming majority of entities.
        if (!couldBeDamageTag(customName)) return false;

        // Hypixel draws its fishing timer and similar counters in bold; damage splashes are not.
        if (customName.contains("§l")) return false;

        String clean = SkyblockUtils.cleanColor(customName).trim();
        if (clean.isEmpty()) return false;

        // A crit carries its star (✧ or ✦) and always stays visible.
        if (clean.indexOf('✧') >= 0 || clean.indexOf('✦') >= 0) return false;

        return DAMAGE_NUMBER.matcher(clean).matches();
    }

    /**
     * Cheap pre-filter for {@link #isNonCritDamageTag}, run over the raw name.
     *
     * Accepts exactly the characters a damage tag can contain, so anything rejected here
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
