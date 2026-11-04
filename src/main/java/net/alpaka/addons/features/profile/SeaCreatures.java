package net.alpaka.addons.features.profile;

import com.google.gson.JsonObject;

/** The sea creatures the Fishing page counts, and how their kills are read from the profile. */
final class SeaCreatures {
    private SeaCreatures() {}

    /**
     * The sea creatures worth counting: the rare ones of each kind of water, as {id, name}. Plain
     * catches like Squid run into the thousands and would push these out of the card.
     */
    static final String[][] RARE_WATER = {
            {"guardian_emperor", "The Loch Emperor"}, {"water_hydra", "Water Hydra"}, {"carrot_king", "Carrot King"},
            {"blue_ringed_octopus", "Blue Ringed Octopus"}, {"wiki_tiki", "Wiki Tiki"}, {"zombie_miner", "Abyssal Miner"},
            {"alligator", "Alligator"}, {"titanoboa", "Titanoboa"}, {"phantom_fisherman", "Phantom Fisherman"},
            {"grim_reaper", "Grim Reaper"}, {"yeti", "Yeti"}, {"reindrake", "Reindrake"}, {"great_white_shark", "Great White Shark"}};
    static final String[][] RARE_LAVA = {
            {"fire_eel", "Fire Eel"}, {"pig_rider", "Taurus"}, {"pond_squid_300", "Plhlegblast"}, {"thunder", "Thunder"},
            {"lord_jawbus", "Lord Jawbus"}, {"fiery_scuttler", "Fiery Scuttler"}, {"ragnarok", "Ragnarok"}};

    /**
     * Kills of one sea creature. Hypixel counts them twice, under the bare id and under the id with
     * the creature's level ({@code lord_jawbus_600}, the Bestiary's count); the bare one stopped
     * counting for some creatures long ago, so the larger of the two is the true one.
     */
    static double kills(JsonObject kills, String id) {
        double best = 0;
        for (var e : kills.entrySet()) {
            String key = e.getKey();
            boolean match = key.equals(id) || (key.startsWith(id + "_") && key.substring(id.length() + 1).chars().allMatch(Character::isDigit));
            if (match && e.getValue().isJsonPrimitive()) best = Math.max(best, e.getValue().getAsDouble());
        }
        return best;
    }
}
