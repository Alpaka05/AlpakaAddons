package net.alpaka.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hypixel's Bazaar as the mod prices items with it: every product's instant buy and sell price.
 * Hypixel's answer is 3.5 MB, almost all of it the order books; this is the few dozen KB the mod
 * reads of it, in Hypixel's own shape, so the mod reads either the same way.
 */
final class Bazaar {
    private Bazaar() {}

    /** Hypixel's Bazaar answer cut down to the quick status prices, as JSON; null when it holds no products. */
    static String prices(String hypixelBody) {
        Object root = Json.parse(hypixelBody);
        if (!(root instanceof Map<?, ?> r) || !(r.get("products") instanceof Map<?, ?> products)) return null;
        Map<String, Object> kept = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : products.entrySet()) {
            if (!(e.getKey() instanceof String id) || !(e.getValue() instanceof Map<?, ?> product)) continue;
            if (!(product.get("quick_status") instanceof Map<?, ?> status)) continue;
            Map<String, Object> quick = new LinkedHashMap<>();
            if (status.get("buyPrice") != null) quick.put("buyPrice", status.get("buyPrice"));
            if (status.get("sellPrice") != null) quick.put("sellPrice", status.get("sellPrice"));
            kept.put(id, Map.of("quick_status", quick));
        }
        if (kept.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        if (r.get("lastUpdated") != null) out.put("lastUpdated", r.get("lastUpdated"));
        out.put("products", kept);
        return Json.write(out);
    }
}
