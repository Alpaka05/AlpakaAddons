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
    ZOMBIE("Zombie", "§2", "Warden Heart", "Hub",
            new String[]{"Graveyard", "Revenant Cave", "Crypts"},
            "Revenant Horror", "Atoned Horror"),
    SPIDER("Spider", "§4", "Primordial Eye", "Spider's Den",
            new String[]{"Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert"},
            "Tarantula Broodfather", "Conjoined Brood"),
    WOLF("Wolf", "§f", "Overflux Capacitor", "The Park",
            new String[]{"Ruins", "Howling Cave", "Soul Cave", "Spirit Cave"},
            "Sven Packmaster"),
    ENDERMAN("Enderman", "§5", "Judgement Core", "The End",
            new String[]{"Void Sepulture", "Zealot Bruiser Hideout", "Dragon's Nest"},
            "Voidgloom Seraph"),
    BLAZE("Blaze", "§e", "High Class Archfiend Dice", "Crimson Isle",
            new String[]{"Smoldering Tomb"},
            "Inferno Demonlord"),
    VAMPIRE("Vampire", "§c", "Unfanged Vampire Part", "The Rift",
            new String[]{"Stillgore Château", "Oubliette"},
            "Bloodfiend", "Riftstalker Bloodfiend"),
    /** No such slayer exists on Hypixel; kept only so older configs still deserialize. */
    GUARDIAN("Guardian", "§3", null, "", new String[0]);

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

    /**
     * The Skyblock island this slayer is fought on, as the player list writes it after "Area:".
     *
     * Distinct from {@link #slayerAreas}, which names the one zone the boss actually spawns in and
     * is deliberately narrow so leaving it can pause the session. This is the whole island, used for
     * the coarser question of whether the HUD belongs on screen at all - anywhere on Crimson Isle
     * counts for Blaze, not just the Smoldering Tomb.
     *
     * Spellings are taken from SkyHanni's IslandType enum, which is the same string the player list
     * carries, so they are exact rather than guessed. Empty means "unknown", which
     * {@link #isOnSlayerIsland} treats as always matching so a missing entry cannot hide the HUD.
     */
    public final String island;

    public final String[] bossNames;

    SlayerType(String display, String colorCode, String rngDropItem, String island, String[] slayerAreas, String... bossNames) {
        this.island = island;
        this.display = display;
        this.colorCode = colorCode;
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

    /**
     * Whether the given island is the one this slayer is fought on.
     *
     * Fails open on an unknown or unreadable island: a HUD that stays hidden because the player list
     * changed shape is a worse failure than one that shows a moment early.
     */
    public boolean isOnSlayerIsland(String currentIsland) {
        if (island == null || island.isEmpty()) return true;
        if (currentIsland == null || currentIsland.isEmpty()) return true;
        // Exact, as SkyHanni matches island names: a substring test would put "Hub" inside
        // "Dungeon Hub" and show the Revenant HUD in the dungeon hub.
        return currentIsland.equalsIgnoreCase(island);
    }
}
