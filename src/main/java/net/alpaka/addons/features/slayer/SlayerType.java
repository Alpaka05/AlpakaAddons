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
    ZOMBIE("Zombie", "Revenant Horror", "Atoned Horror"),
    SPIDER("Spider", "Tarantula Broodfather", "Conjoined Brood"),
    WOLF("Wolf", "Sven Packmaster"),
    ENDERMAN("Enderman", "Voidgloom Seraph"),
    BLAZE("Blaze", "Inferno Demonlord"),
    VAMPIRE("Vampire", "Bloodfiend", "Riftstalker Bloodfiend"),
    /** No such slayer exists on Hypixel; kept only so older configs still deserialize. */
    GUARDIAN("Guardian");

    public final String display;
    public final String[] bossNames;

    SlayerType(String display, String... bossNames) {
        this.display = display;
        this.bossNames = bossNames;
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
