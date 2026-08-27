package net.alpaka.addons.features.slayer;

/**
 * The Skyblock slayers.
 *
 * Enum constant names are persisted as keys of {@code AlpakaConfig.slayerBossMap}, so they must not
 * be renamed. {@link #display} is the mob-flavoured name shown in the mod's own output; {@link
 * #bossNames} are the boss names Hypixel actually writes on the sidebar, which is how an active
 * quest is identified - the sidebar says "Inferno Demonlord IV", never "Blaze Slayer".
 */
public enum SlayerType {
    ZOMBIE("Zombie", "§2", "Warden Heart", new String[]{"Graveyard", "Revenant Cave", "Crypts"},
            new String[]{"Graveyard", "Revenant Cave", "Crypts"},
            "Revenant Horror", "Atoned Horror"),
    SPIDER("Spider", "§4", "Primordial Eye", new String[]{"Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert"},
            new String[]{"Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert"},
            "Tarantula Broodfather", "Conjoined Brood"),
    WOLF("Wolf", "§f", "Overflux Capacitor", new String[]{"Ruins", "Howling Cave", "Soul Cave", "Spirit Cave"},
            new String[]{"Ruins", "Howling Cave", "Soul Cave", "Spirit Cave"},
            "Sven Packmaster"),
    ENDERMAN("Enderman", "§5", "Judgement Core", new String[]{"Void Sepulture", "Zealot Bruiser Hideout", "Dragon's Nest"},
            new String[]{"Void Sepulture", "Zealot Bruiser Hideout", "Dragon's Nest"},
            "Voidgloom Seraph"),
    BLAZE("Blaze", "§e", "High Class Archfiend Dice", new String[]{"Stronghold", "The Wasteland", "Smoldering Tomb"},
            new String[]{"Smoldering Tomb"},
            "Inferno Demonlord"),
    VAMPIRE("Vampire", "§c", "Unfanged Vampire Part", new String[]{"Stillgore Château", "Oubliette"},
            new String[]{"Stillgore Château", "Oubliette"},
            "Bloodfiend", "Riftstalker Bloodfiend"),
    /** No such slayer exists on Hypixel; kept only so older configs still deserialize. */
    GUARDIAN("Guardian", "§3", null, new String[0], new String[0]);

    public final String display;
    public final String colorCode;

    /**
     * The slayer's headline RNG drop - the valuable one worth counting a dry streak against.
     *
     * Taken as the highest-value <em>tradeable</em> drop per slayer, which is the sense in which
     * "High Class Archfiend Dice" is Inferno's headline drop. By strict RNG-meter rarity each
     * slayer's rarest drop is actually its cosmetic dye - Flame Dye for Inferno, Matcha Dye for
     * Revenant and so on, all scoring 75,000,000 against High Class Archfiend Dice's 194,939 - but
     * dyes are a separate ultra-rare cosmetic tier that nobody grinds a dry streak against, so they
     * are deliberately not used here.
     *
     * A null omits the "bosses since" line for that slayer rather than counting against a guess.
     */
    public final String rngDropItem;

    /**
     * Zone names, as written on the sidebar's own area line, where this slayer is fought.
     *
     * These must name only the zone the boss is actually fought in, not the wider island around it.
     * Listing a surrounding zone defeats the whole point: with "The Wasteland" and "Stronghold" also
     * listed for Blaze, walking out of the Smoldering Tomb still counted as being in the area, so the
     * session never paused. Blaze is the one confirmed from live play; if another slayer's zone turns
     * out to be named differently on the sidebar, correct it here rather than widening the list.
     *
     * Empty means "nowhere known", which {@link #isSlayerArea} treats as never matching.
     */
    public final String[] slayerAreas;

    /** [slayerAreas] lowercased once, so the per-line area test allocates nothing. */
    private final String[] slayerAreasLower;

    /**
     * Every zone this slayer is normally fought in, as the sidebar writes the area name.
     *
     * Wider than {@link #slayerAreas}, and answering a different question. That list names only the
     * one zone the boss spawns in, and is deliberately narrow so that walking out of it can pause
     * the session clock. This one decides whether the HUD belongs on screen at all, so it covers the
     * whole stretch of ground a slayer is actually run over - Blaze is fought across the Stronghold
     * and the Wasteland as well as the Smoldering Tomb.
     *
     * Taken from SkyHanni's own area-to-slayer table (SlayerApi.checkTypeForCurrentArea), so that
     * the HUD appears exactly where its slayer profit tracker does. Guessing at this was the
     * original mistake: the HUD used to be gated on a single *island* per slayer, which hid it for
     * any slayer run anywhere but its home island.
     */
    public final String[] trackerAreas;

    /** [trackerAreas] lowercased once, for the same reason as [slayerAreasLower]. */
    private final String[] trackerAreasLower;

    public final String[] bossNames;

    SlayerType(String display, String colorCode, String rngDropItem, String[] trackerAreas, String[] slayerAreas, String... bossNames) {
        this.trackerAreas = trackerAreas;
        this.trackerAreasLower = new String[trackerAreas.length];
        for (int i = 0; i < trackerAreas.length; i++) {
            this.trackerAreasLower[i] = trackerAreas[i].toLowerCase();
        }
        this.display = display;
        this.colorCode = colorCode;
        this.rngDropItem = rngDropItem;
        this.slayerAreas = slayerAreas;
        this.slayerAreasLower = new String[slayerAreas.length];
        for (int i = 0; i < slayerAreas.length; i++) {
            this.slayerAreasLower[i] = slayerAreas[i].toLowerCase();
        }
        this.bossNames = bossNames;
    }

    /**
     * Whether a sidebar area name is one of this slayer's zones.
     *
     * Compared by substring in both directions because the sidebar sometimes decorates the name
     * (with a tier or a qualifier) around the part that identifies the zone.
     */
    public boolean isSlayerArea(String area) {
        if (area == null || area.isEmpty() || slayerAreas.length == 0) return false;
        // Lowercased once for the whole loop; the zone names are pre-lowercased at construction.
        String lowerArea = area.toLowerCase();
        for (int i = 0; i < slayerAreas.length; i++) {
            String knownLower = slayerAreasLower[i];
            if (lowerArea.equals(knownLower)
                    || lowerArea.contains(knownLower)
                    || knownLower.contains(lowerArea)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #values()} without the array copy it makes on every call. This is reached per sidebar
     * line on every refresh, and the enum cannot change at runtime.
     */
    private static final SlayerType[] VALUES = values();

    /**
     * The slayer whose boss name appears in the given sidebar line, or null.
     *
     * The line is expected to be colour-stripped and trimmed, e.g. {@code "Inferno Demonlord IV"}.
     */
    public static SlayerType fromScoreboardLine(String line) {
        if (line == null || line.isEmpty()) return null;
        for (SlayerType type : VALUES) {
            for (String bossName : type.bossNames) {
                if (line.contains(bossName)) return type;
            }
        }
        return null;
    }

    /**
     * The slayer a player typed on the command line, or null.
     *
     * Matches the mob-flavoured name the mod prints ("blaze", "wolf"), the enum's own name, and any
     * part of a boss name. That last one is why "sven" and "inferno" work: those are what the
     * community actually calls these slayers, even though nothing in this mod's output says them.
     *
     * The boss-name match needs three characters so a stray letter cannot land on a slayer, and it
     * is tried last so an exact mob name always wins over a partial boss name.
     */
    public static SlayerType fromUserInput(String input) {
        if (input == null || input.isBlank()) return null;
        String needle = input.trim();

        for (SlayerType type : VALUES) {
            if (type.display.equalsIgnoreCase(needle) || type.name().equalsIgnoreCase(needle)) {
                return type;
            }
        }

        if (needle.length() < 3) return null;
        String lower = needle.toLowerCase();
        for (SlayerType type : VALUES) {
            for (String bossName : type.bossNames) {
                if (bossName.toLowerCase().contains(lower)) return type;
            }
        }
        return null;
    }

    /** The accepted spellings, for the error message when one is not recognised. */
    public static String userInputNames() {
        StringBuilder out = new StringBuilder();
        for (SlayerType type : VALUES) {
            if (type == GUARDIAN) continue;
            if (out.length() > 0) out.append(", ");
            out.append(type.display.toLowerCase());
        }
        return out.toString();
    }

    /**
     * Whether a sidebar line names a zone this slayer is run in.
     *
     * Matched against every sidebar line rather than against the one area line. Finding that line
     * means recognising Hypixel's zone marker, and depending on that glyph is fragile - one
     * unexpected marker and the area reads as empty, which silently switches the HUD off.
     */
    public boolean isTrackerArea(String line) {
        if (line == null || line.isEmpty() || trackerAreasLower.length == 0) return false;
        String lower = line.toLowerCase();
        for (String known : trackerAreasLower) {
            if (lower.contains(known)) return true;
        }
        return false;
    }
}