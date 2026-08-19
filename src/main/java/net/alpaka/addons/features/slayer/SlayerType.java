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
    ZOMBIE("Zombie", "Warden Heart",
            new String[]{"Graveyard", "Revenant Cave", "Crypts"},
            "Revenant Horror", "Atoned Horror"),
    SPIDER("Spider", "Primordial Eye",
            new String[]{"Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert"},
            "Tarantula Broodfather", "Conjoined Brood"),
    WOLF("Wolf", "Overflux Capacitor",
            new String[]{"Ruins", "Howling Cave", "Soul Cave", "Spirit Cave"},
            "Sven Packmaster"),
    ENDERMAN("Enderman", "Judgement Core",
            new String[]{"Void Sepulture", "Zealot Bruiser Hideout", "Dragon's Nest"},
            "Voidgloom Seraph"),
    BLAZE("Blaze", "High Class Archfiend Dice",
            new String[]{"Smoldering Tomb"},
            "Inferno Demonlord"),
    VAMPIRE("Vampire", "Unfanged Vampire Part",
            new String[]{"Stillgore Château", "Oubliette"},
            "Bloodfiend", "Riftstalker Bloodfiend"),
    /** No such slayer exists on Hypixel; kept only so older configs still deserialize. */
    GUARDIAN("Guardian", null, new String[0]);

    public final String display;

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

    public final String[] bossNames;

    SlayerType(String display, String rngDropItem, String[] slayerAreas, String... bossNames) {
        this.display = display;
        this.rngDropItem = rngDropItem;
        this.slayerAreas = slayerAreas;
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
        for (String known : slayerAreas) {
            if (area.equalsIgnoreCase(known)
                    || area.toLowerCase().contains(known.toLowerCase())
                    || known.toLowerCase().contains(area.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The slayer whose boss name appears in the given sidebar line, or null.
     *
     * The line is expected to be colour-stripped and trimmed, e.g. {@code "Inferno Demonlord IV"}.
     */
    public static SlayerType fromScoreboardLine(String line) {
        if (line == null || line.isEmpty()) return null;
        for (SlayerType type : values()) {
            for (String bossName : type.bossNames) {
                if (line.contains(bossName)) return type;
            }
        }
        return null;
    }
}
