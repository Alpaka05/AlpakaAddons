package net.alpaka.addons.features.deployable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The deployables the timer knows, strongest first.
 *
 * Hypixel applies only one deployable buff at a time, the strongest in range, so the declaration
 * order is the order the timer picks in: Plasmaflux beats an SOS Flare, an SOS Flare beats
 * Overflux, and so on down to Radiant.
 *
 * Power orbs carry their name and the seconds left in a name tag ("Plasmaflux 52s"), so they are
 * read from that. Flares have no name tag and are told apart by their head's skin; they last three
 * minutes from the moment they appear.
 */
public enum Deployable {
    PLASMAFLUX("Plasmaflux", 20, 60,
            "83ed4ce23933e66e04df16070644f7599eeb55307f7eafe8d92f40fb3520863c", false),
    SOS_FLARE("SOS Flare", 40, 180,
            "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b", true),
    OVERFLUX("Overflux", 18, 60,
            "20de5e8974940375934d32f71c91ad2d5728d38e51647dcc8f39206c099a54c2", false),
    ALERT_FLARE("Alert Flare", 40, 180,
            "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b", true),
    MANA_FLUX("Mana Flux", 18, 60,
            "82ada1c7fcc8cf35defeb944a4f8ffa9a9d260560fc7f5f5826de8085435967c", false),
    WARNING_FLARE("Warning Flare", 40, 180,
            "22e2bf6c1ec330247927ba63479e5872ac66b06903c86c82b52dac9f1c971458", true),
    RADIANT("Radiant", 18, 60,
            "c94d0a068ea50a9de2cef23a92bca4b3678d12ca8a1815d13beac94dfd557125", false);

    /** The name as Hypixel writes it, in an orb's name tag and in the item's buff line. */
    public final String displayName;
    /** How far the buff reaches, in blocks. */
    public final int range;
    /** How long one lasts, in seconds. */
    public final int durationSeconds;
    /** The id of the head's skin on textures.minecraft.net. */
    public final String skinHash;
    public final boolean flare;

    Deployable(String displayName, int range, int durationSeconds, String skinHash, boolean flare) {
        this.displayName = displayName;
        this.range = range;
        this.durationSeconds = durationSeconds;
        this.skinHash = skinHash;
        this.flare = flare;
    }

    /** The head's texture value, as a head item carries it, for drawing the icon. */
    public String textureValue() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + skinHash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Whether this one is stronger than {@code other}; null counts as nothing. */
    public boolean beats(Deployable other) {
        return other == null || ordinal() < other.ordinal();
    }

    /** The power orb a name tag (color codes removed) belongs to, or null. */
    public static Deployable orbFromName(String name) {
        for (Deployable d : values()) {
            if (!d.flare && name.equals(d.displayName)) return d;
        }
        return null;
    }

    /** The flare whose skin a decoded head texture uses, or null. */
    public static Deployable flareFromTexture(String decodedTexture) {
        for (Deployable d : values()) {
            if (d.flare && decodedTexture.contains(d.skinHash)) return d;
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
