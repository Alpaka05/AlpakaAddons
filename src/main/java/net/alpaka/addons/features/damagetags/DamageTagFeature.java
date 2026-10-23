package net.alpaka.addons.features.damagetags;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Optional;
import java.util.regex.Pattern;

public class DamageTagFeature {

    /**
     * A plain damage number: comma-grouped digits ({@code 1,234}) or a compact value with a suffix
     * ({@code 139k}, {@code 1.5M}). A bare decimal without a suffix is not one - that is what Hypixel's
     * timers and counters look like, and the old pattern hid those too.
     */
    private static final Pattern DAMAGE_NUMBER = Pattern.compile("^(?:\\d{1,3}(?:,\\d{3})*|\\d+(?:\\.\\d+)?[kKmMbB])$");

    /**
     * The heart Hypixel puts after a hit the Ring of Love accessory doubled. Matched in both forms
     * so a resource pack or font swap between the two does not lose the tag.
     */
    private static final char LOVE_HEART = '❤';
    private static final char LOVE_HEART_ALT = '♥';

    /** Damage splashes are short-lived; an armor stand older than this is a hologram, not a splash. */
    private static final int MAX_TAG_AGE_TICKS = 300;

    /** What a stand's cached verdict holds for a name that is no splash, so that is remembered too. */
    private static final Splash NO_SPLASH = new Splash("", false, false);

    public static boolean shouldHideEntity(Entity entity) {
        return shouldHideNameTag(entity);
    }

    /**
     * Hypixel's own splash goes either way when the custom tags are on, crit or not:
     * {@link CustomDamageTagFeature} draws its replacement. Otherwise Only Show Crit Damage hides the
     * splashes that did not crit.
     */
    public static boolean shouldHideNameTag(Entity entity) {
        // Cheapest first. This runs for every loaded entity on every frame, before culling, and
        // nearly none of them is a young armor stand.
        if (!(entity instanceof ArmorStand stand) || stand.tickCount > MAX_TAG_AGE_TICKS) return false;
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.onlyCritDamageEnabled && !cfg.customDamageTagsEnabled) return false;
        if (!SkyblockUtils.isOnSkyblock()) return false;

        Splash splash = splashOf(stand);
        return splash != null && (cfg.customDamageTagsEnabled || !splash.crit());
    }

    /**
     * A damage splash read off its armor stand: the number as Hypixel wrote it, whether it crit, and
     * whether Ring of Love doubled it (the heart after the number).
     */
    public record Splash(String number, boolean crit, boolean love) {}

    /** The splash this entity shows, or null when it is not a young armor stand named like one. */
    public static Splash parseSplash(Entity entity) {
        if (!(entity instanceof ArmorStand stand) || stand.tickCount > MAX_TAG_AGE_TICKS) return null;
        return splashOf(stand);
    }

    /**
     * The splash a young armor stand shows, or null. Kept on the stand with the name it was read
     * from ({@link SplashVerdict}) and read again only when the name is a different object: the hooks
     * ask about the same stand on every frame, and a splash's name does not change once it is up.
     */
    private static Splash splashOf(ArmorStand stand) {
        Component name = stand.getCustomName();
        if (name == null) return null;
        SplashVerdict verdict = (SplashVerdict) stand;
        if (verdict.alpaka$splashName() == name) {
            Splash cached = verdict.alpaka$splash();
            return cached == NO_SPLASH ? null : cached;
        }
        Splash splash = couldBeDamageTag(name) ? parseDamageTag(name.getString()) : null;
        verdict.alpaka$setSplash(name, splash == null ? NO_SPLASH : splash);
        return splash;
    }

    /**
     * Any damage tag, crit or not, or null for a name that is none. The one parser: Only Show Crit
     * Damage and the custom tags read a splash the same way.
     */
    public static Splash parseDamageTag(String customName) {
        if (customName == null || customName.isEmpty() || !couldBeDamageTag(customName)) return null;
        // Hypixel draws its fishing timer and similar counters in bold; damage splashes are not.
        if (customName.contains("§l")) return null;

        // One pass instead of stripping the colors, trimming and replacing the decoration: formatting
        // codes, the crit stars, Ring of Love's heart, the asterisks some splashes carry and every
        // space are set aside, and what is left is the number.
        StringBuilder number = new StringBuilder(customName.length());
        boolean crit = false;
        boolean love = false;
        for (int i = 0; i < customName.length(); i++) {
            char c = customName.charAt(i);
            if (c == '§') {
                i++; // The code's second character belongs to the code, whatever it is.
            } else if (c == '✧' || c == '✦') {
                crit = true;
            } else if (c == LOVE_HEART || c == LOVE_HEART_ALT) {
                love = true;
            } else if (c != '*' && !Character.isWhitespace(c)) {
                number.append(c);
            }
        }
        if (number.isEmpty() || !DAMAGE_NUMBER.matcher(number).matches()) return null;
        return new Splash(number.toString(), crit, love);
    }

    /** A damage tag that did not crit: what Only Show Crit Damage hides. A crit always stays. */
    public static boolean isNonCritDamageTag(String customName) {
        Splash splash = parseDamageTag(customName);
        return splash != null && !splash.crit();
    }

    /**
     * Cheap pre-filter for {@link #parseDamageTag}, run over the raw name.
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
            if (!isTagChar(c)) return false;
        }
        return true;
    }

    /**
     * {@link #couldBeDamageTag(String)} run over the name's text segments before it is flattened.
     *
     * {@code getString()} builds a StringBuilder and a String, while walking the segments stops at the
     * first character no damage tag can contain - the second or third for nearly every nametag and
     * hologram, so a stand that is no splash costs no string at all. The skip flag crosses segment
     * boundaries because {@code getString()} concatenates the segments, so a code split across two
     * parts is skipped exactly as it would be in the flat string.
     */
    private static boolean couldBeDamageTag(Component name) {
        boolean[] skipNext = {false};
        return name.<Unit>visit(segment -> {
            for (int i = 0; i < segment.length(); i++) {
                if (skipNext[0]) {
                    skipNext[0] = false;
                    continue;
                }
                char c = segment.charAt(i);
                if (c == '§') {
                    skipNext[0] = true;
                    continue;
                }
                if (!isTagChar(c)) return FormattedText.STOP_ITERATION;
            }
            return Optional.empty();
        }).isEmpty();
    }

    /** A character a damage tag can contain, once its formatting codes are set aside. Both pre-filters share it. */
    private static boolean isTagChar(char c) {
        return (c >= '0' && c <= '9')
                || c == ',' || c == '.' || c == '*' || Character.isWhitespace(c)
                || c == 'k' || c == 'K' || c == 'm' || c == 'M' || c == 'b' || c == 'B'
                || c == '✧' || c == '✦' || c == LOVE_HEART || c == LOVE_HEART_ALT;
    }
}
