package net.alpaka.addons.features.slayer;

public enum SlayerType {
    ZOMBIE("Zombie"),
    SPIDER("Spider"),
    WOLF("Wolf"),
    ENDERMAN("Enderman"),
    VAMPIRE("Vampire"),
    BLAZE("Blaze"),
    GUARDIAN("Guardian");

    public final String display;

    SlayerType(String display) {
        this.display = display;
    }
}
