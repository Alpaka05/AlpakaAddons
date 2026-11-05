package net.alpaka.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Profile Dashboard gets from Hypixel's {@code /v2/skyblock/profiles}: for each profile
 * of the player, its id, name, mode and whether it is selected, and of the player's own member data
 * only the fields the dashboard shows. Co-op members' data and everything else is left out.
 *
 * This is what keeps the server the dashboard's backend rather than a copy of Hypixel's API: it
 * answers with what one screen of the mod draws, nothing more. A new dashboard page that needs
 * more of Hypixel's data adds its fields to {@link #MEMBER_FIELDS}.
 */
final class Dashboard {
    private Dashboard() {}

    /** Kept of each profile. */
    static final List<String> PROFILE_FIELDS = List.of("profile_id", "cute_name", "selected", "game_mode");

    /** Kept of the player's member data, as dotted paths into it. */
    static final List<String> MEMBER_FIELDS = List.of(
            "rift.inventory.inv_contents",
            "rift.inventory.inv_armor",
            "rift.inventory.equipment_contents",
            "rift.inventory.ender_chest_contents");

    /** Hypixel's answer for {@code player}, cut down to the dashboard's fields, as JSON. */
    static String of(String hypixelBody, String player) {
        Object root = Json.parse(hypixelBody);
        List<Object> profiles = new ArrayList<>();
        if (root instanceof Map<?, ?> r && r.get("profiles") instanceof List<?> list) {
            for (Object p : list) {
                if (!(p instanceof Map<?, ?> profile)) continue;
                Object member = profile.get("members") instanceof Map<?, ?> members ? members.get(player) : null;
                if (!(member instanceof Map<?, ?>)) continue;
                Map<String, Object> out = new LinkedHashMap<>();
                for (String field : PROFILE_FIELDS) {
                    if (profile.containsKey(field)) out.put(field, profile.get(field));
                }
                out.put("members", Map.of(player, keep(member, MEMBER_FIELDS)));
                profiles.add(out);
            }
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("success", true);
        answer.put("profiles", profiles);
        return Json.write(answer);
    }

    /** Only the given paths of {@code source}, in the same nesting; paths that aren't there are skipped. */
    static Map<String, Object> keep(Object source, List<String> paths) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String path : paths) {
            String[] parts = path.split("\\.");
            Object value = source;
            for (String part : parts) {
                value = value instanceof Map<?, ?> m ? m.get(part) : null;
                if (value == null) break;
            }
            if (value == null) continue;
            Map<String, Object> at = out;
            for (int i = 0; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) at.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
                at = next;
            }
            at.put(parts[parts.length - 1], value);
        }
        return out;
    }
}
