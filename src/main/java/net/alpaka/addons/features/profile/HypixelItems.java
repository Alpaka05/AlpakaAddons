package net.alpaka.addons.features.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Hypixel's inventories as the dashboard draws them. Hypixel hands each inventory out as gzipped,
 * base64 NBT in the game's old 1.8 item format; Elite, where the other inventories come from, has
 * already turned them into items with a name, lore, SkyBlock id and attributes. This makes the same
 * shape out of Hypixel's, so one drawing code serves both: {"items": {"0": item, "1": null, ...}}.
 *
 * A reader of its own rather than the game's NbtIo: it is small, and it keeps this testable
 * without a running game.
 */
final class HypixelItems {
    private HypixelItems() {}

    /** An inventory's {@code data} as Hypixel sends it, as Elite's inventory answer would look. */
    static JsonObject inventory(String base64) throws IOException {
        Object root = read(base64);
        JsonObject items = new JsonObject();
        if (root instanceof Map<?, ?> compound && compound.get("i") instanceof Object[] list) {
            for (int i = 0; i < list.length; i++) items.add(String.valueOf(i), item(list[i]));
        }
        JsonObject out = new JsonObject();
        out.add("items", items);
        return out;
    }

    /** One slot: null for an empty one, as Elite lists them. */
    static JsonElement item(Object nbt) {
        if (!(nbt instanceof Map<?, ?> compound) || !compound.containsKey("id")) return JsonNull.INSTANCE;
        JsonObject item = new JsonObject();
        Map<?, ?> tag = map(compound.get("tag"));
        Map<?, ?> display = map(tag.get("display"));
        Map<?, ?> extra = map(tag.get("ExtraAttributes"));
        if (extra.get("id") instanceof String id) item.addProperty("skyblockId", id);
        if (display.get("Name") instanceof String name) item.addProperty("name", name);
        JsonArray lore = new JsonArray();
        if (display.get("Lore") instanceof Object[] lines) {
            for (Object line : lines) if (line instanceof String s) lore.add(s);
        }
        item.add("lore", lore);
        item.add("count", number(compound.get("Count"), 1));
        item.add("damage", number(compound.get("Damage"), 0));
        if (extra.get("uuid") instanceof String uuid) item.addProperty("uuid", uuid);

        JsonObject enchantments = new JsonObject();
        for (var e : map(extra.get("enchantments")).entrySet()) {
            if (e.getValue() instanceof Number n) enchantments.addProperty(String.valueOf(e.getKey()), n);
        }
        item.add("enchantments", enchantments);

        // Elite writes the attributes as plain strings; nested ones are left out, as they are there.
        JsonObject attributes = new JsonObject();
        for (var e : extra.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.equals("enchantments") || key.equals("id")) continue;
            if (e.getValue() instanceof String || e.getValue() instanceof Number) attributes.addProperty(key, String.valueOf(e.getValue()));
        }
        String texture = skinTexture(tag);
        if (texture != null) attributes.addProperty("skin_texture", texture);
        item.add("attributes", attributes);
        return item;
    }

    /** A head's skin: {@code SkullOwner.Properties.textures[0].Value}. */
    private static String skinTexture(Map<?, ?> tag) {
        Map<?, ?> properties = map(map(tag.get("SkullOwner")).get("Properties"));
        if (properties.get("textures") instanceof Object[] textures && textures.length > 0) {
            return map(textures[0]).get("Value") instanceof String value ? value : null;
        }
        return null;
    }

    private static Map<?, ?> map(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    private static JsonPrimitive number(Object o, int fallback) {
        return new JsonPrimitive(o instanceof Number n ? n.intValue() : fallback);
    }

    // ---- NBT ------------------------------------------------------------------------------------

    /** The root compound of gzipped, base64 NBT: compounds as maps, lists as arrays. */
    static Object read(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            int type = in.readUnsignedByte();
            if (type == 0) return Map.of();
            in.readUTF();
            return tag(in, type, 0);
        }
    }

    /** Deeper than this is no item, and only a broken or hostile answer. */
    private static final int MAX_DEPTH = 64;

    private static Object tag(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nested too deep");
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] b = new byte[length(in)];
                in.readFully(b);
                yield b;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int element = in.readUnsignedByte();
                int n = length(in);
                Object[] list = new Object[n];
                for (int i = 0; i < n; i++) list[i] = tag(in, element, depth + 1);
                yield list;
            }
            case 10 -> {
                Map<String, Object> compound = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) break;
                    String name = in.readUTF();
                    compound.put(name, tag(in, t, depth + 1));
                }
                yield compound;
            }
            case 11 -> {
                int n = length(in);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                yield a;
            }
            case 12 -> {
                int n = length(in);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                yield a;
            }
            default -> throw new IOException("Unknown NBT tag " + type);
        };
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 1 << 20) throw new IOException("NBT length out of range");
        return n;
    }
}
