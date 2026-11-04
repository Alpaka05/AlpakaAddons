package net.alpaka.addons.features.deployable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The deployables the timer knows, grouped by family, strongest first within each family.
 *
 * Hypixel applies only one buff of a family at a time, the strongest in range, so the declaration
 * order is the order the timer picks in: Plasmaflux beats an SOS Flare, an SOS Flare beats
 * Overflux, and so on down to Radiant; a Will-o'-wisp beats a Glacite Lantern. The families are
 * shown side by side, one line each, the way the other SkyBlock mods do.
 *
 * Power orbs, the Umberella and the lanterns carry their name and the seconds left in a name tag
 * ("Plasmaflux 52s", "Glacite Lantern 120s"), so they are read from that. Flares have no name tag
 * and are told apart by their head's skin; they last three minutes from the moment they appear.
 * The Totem of Corruption and the Pocket Black Hole are a few stacked tags each and only count for
 * their owner.
 */
public enum Deployable {
    PLASMAFLUX(Family.COMBAT, Kind.TAG, "Plasmaflux", 20, 60,
            "83ed4ce23933e66e04df16070644f7599eeb55307f7eafe8d92f40fb3520863c"),
    SOS_FLARE(Family.COMBAT, Kind.FLARE, "SOS Flare", 40, 180,
            "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b"),
    OVERFLUX(Family.COMBAT, Kind.TAG, "Overflux", 18, 60,
            "20de5e8974940375934d32f71c91ad2d5728d38e51647dcc8f39206c099a54c2"),
    ALERT_FLARE(Family.COMBAT, Kind.FLARE, "Alert Flare", 40, 180,
            "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b"),
    MANA_FLUX(Family.COMBAT, Kind.TAG, "Mana Flux", 18, 60,
            "82ada1c7fcc8cf35defeb944a4f8ffa9a9d260560fc7f5f5826de8085435967c"),
    WARNING_FLARE(Family.COMBAT, Kind.FLARE, "Warning Flare", 40, 180,
            "22e2bf6c1ec330247927ba63479e5872ac66b06903c86c82b52dac9f1c971458"),
    RADIANT(Family.COMBAT, Kind.TAG, "Radiant", 18, 60,
            "c94d0a068ea50a9de2cef23a92bca4b3678d12ca8a1815d13beac94dfd557125"),

    WILL_O_WISP(Family.MINING, Kind.TAG, "Will-o'-wisp", 30, 300,
            "6ec1a6957f02a4258e64dc7c520463d92647888499845d784ef0e7abe8f38402"),
    GLACITE_LANTERN(Family.MINING, Kind.TAG, "Glacite Lantern", 30, 300,
            "e75c66c2a8f92762da538bf349d207f813028cc1fa56527e20a494fc85b30734"),
    TITANIUM_LANTERN(Family.MINING, Kind.TAG, "Titanium Lantern", 30, 300,
            "df46eb02f6860f276f3b0157752a6adca6fc443c28e1e9e41ed2dcf304288210"),
    MITHRIL_LANTERN(Family.MINING, Kind.TAG, "Mithril Lantern", 30, 300,
            "d2a6952d13acf631ce1bebaa0815fcf8a57f66fff69ba51599a936a1160b615b"),
    DWARVEN_LANTERN(Family.MINING, Kind.TAG, "Dwarven Lantern", 30, 300,
            "b7b06b329237e7469d0650ba167aa9611ef1373c3c2c6bbdda7b13773c6aa69f"),

    UMBERELLA(Family.UMBERELLA, Kind.TAG, "Umberella", 30, 300,
            "1e4a17cc5293c1d61a37cc75746ba64ed86a17e342f2e5b40dfc8ff89978a580"),

    /** Buffs only its owner; drawn as a banner, like the item, since it has no head. */
    TOTEM_OF_CORRUPTION(Family.TOTEM, Kind.TOTEM, "Totem of Corruption", 30, 300, null),

    /**
     * Small, Medium and Large all read "Black Hole" in the world, so they are one entry. It pulls
     * mobs rather than buffing anyone, so only the owner's counts, and only while it is nearby.
     */
    BLACK_HOLE(Family.BLACK_HOLE, Kind.BLACK_HOLE, "Black Hole", 30, 180,
            "55b70f094e01679508dd5bca39640ed9ec5c6c6792fbd8f57c03b3a12f9c0a92");

    /** Families whose buffs don't replace each other; the HUD shows one line per family, in this order. */
    public enum Family { COMBAT, MINING, UMBERELLA, TOTEM, BLACK_HOLE }

    /** How the deployable shows up in the world. */
    public enum Kind {
        /** One stand tagged "Name 52s". */
        TAG,
        /** A bare stand wearing a head, no time shown. */
        FLARE,
        /** "Totem of Corruption", "Remaining: 2m 30s" and "Owner: name", stacked. */
        TOTEM,
        /** "Black Hole 52s" and "Spawned by: name", stacked. */
        BLACK_HOLE
    }

    public final Family family;
    public final Kind kind;
    /** The name as Hypixel writes it, in the name tag and in the item's buff line. */
    public final String displayName;
    /** How far the buff reaches, in blocks. */
    public final int range;
    /** How long one lasts, in seconds; a Bubblegum pet item doubles it. */
    public final int durationSeconds;
    /** The id of the head's skin on textures.minecraft.net, or null for one that is no head. */
    public final String skinHash;

    Deployable(Family family, Kind kind, String displayName, int range, int durationSeconds, String skinHash) {
        this.family = family;
        this.kind = kind;
        this.displayName = displayName;
        this.range = range;
        this.durationSeconds = durationSeconds;
        this.skinHash = skinHash;
    }

    /**
     * Whether the buff reaches everyone in a Glacite Mineshaft, not just those in range: Hypixel
     * says so for the two strongest lanterns.
     */
    public boolean wholeMineshaft() {
        return this == WILL_O_WISP || this == GLACITE_LANTERN;
    }

    /** The head's texture value, as a head item carries it, for drawing the icon; null if it is no head. */
    public String textureValue() {
        if (skinHash == null) return null;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + skinHash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Whether this one is stronger than {@code other} of the same family; null counts as nothing. */
    public boolean beats(Deployable other) {
        return other == null || ordinal() < other.ordinal();
    }

    /** The deployable a "Name 52s" tag (color codes removed) belongs to, or null. */
    public static Deployable fromTagName(String name) {
        for (Deployable d : values()) {
            if (d.kind == Kind.TAG && name.equals(d.displayName)) return d;
        }
        return null;
    }

    /** The flare whose skin a decoded head texture uses, or null. */
    public static Deployable flareFromTexture(String decodedTexture) {
        for (Deployable d : values()) {
            if (d.kind == Kind.FLARE && decodedTexture.contains(d.skinHash)) return d;
        }
        return null;
    }

    /** "2m 05s" from a minute on, "45s" below. */
    public static String formatTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        int s = seconds % 60;
        return seconds / 60 + "m " + (s < 10 ? "0" : "") + s + "s";
    }
}
