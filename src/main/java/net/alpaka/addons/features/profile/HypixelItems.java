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
 * base64 NBT in the game's old 1.8 item format; this turns it into items with a name, lore,
 * SkyBlock id and attributes: {"items": {"0": item, "1": null, ...}}.
 *
 * A reader of its own rather than the game's NbtIo: it is small, and it keeps this testable
 * without a running game.
 */
final class HypixelItems {
    private HypixelItems() {}

    /** An inventory's {@code data} as Hypixel sends it, slot by slot. */
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

    /** One slot: null for an empty one. */
    static JsonElement item(Object nbt) {
        if (!(nbt instanceof Map<?, ?> compound) || !compound.containsKey("id")) return JsonNull.INSTANCE;
        JsonObject item = new JsonObject();
        Map<?, ?> tag = map(compound.get("tag"));
        Map<?, ?> display = map(tag.get("display"));
        if (display.get("Name") instanceof String name) item.addProperty("name", name);
        JsonArray lore = new JsonArray();
        if (display.get("Lore") instanceof Object[] lines) {
            for (Object line : lines) if (line instanceof String s) lore.add(s);
        }
        item.add("lore", lore);
        item.add("count", number(compound.get("Count"), 1));
        item.add("damage", number(compound.get("Damage"), 0));
        skyblock(item, map(tag.get("ExtraAttributes")));
        String texture = skinTexture(tag);
        if (texture != null) item.getAsJsonObject("attributes").addProperty("skin_texture", texture);
        return item;
    }

    /**
     * The one item of packed NBT that holds a single thing: a wardrobe piece, a backpack's icon, a
     * tool of a tool kit. Null (JSON's) when it holds nothing. Most are an inventory of one slot;
     * the tool kits keep only the item's SkyBlock data, without a name or lore, so such an item
     * comes with its id alone and is named by whoever draws it.
     */
    static JsonElement single(String base64) throws IOException {
        if (!(read(base64) instanceof Map<?, ?> root)) return JsonNull.INSTANCE;
        if (root.get("i") instanceof Object[] list) {
            for (Object slot : list) {
                JsonElement item = item(slot);
                if (item.isJsonObject()) return item;
            }
            return JsonNull.INSTANCE;
        }
        if (!(root.get("id") instanceof String)) return JsonNull.INSTANCE;
        JsonObject item = new JsonObject();
        item.add("lore", new JsonArray());
        item.addProperty("count", 1);
        item.addProperty("damage", 0);
        skyblock(item, root);
        return item;
    }

    /** What Hypixel keeps on an item beyond its look, read from its {@code ExtraAttributes}. */
    private static void skyblock(JsonObject item, Map<?, ?> extra) {
        if (extra.get("id") instanceof String id) item.addProperty("skyblockId", id);
        if (extra.get("uuid") instanceof String uuid) item.addProperty("uuid", uuid);

        JsonObject enchantments = new JsonObject();
        for (var e : map(extra.get("enchantments")).entrySet()) {
            if (e.getValue() instanceof Number n) enchantments.addProperty(String.valueOf(e.getKey()), n);
        }
        item.add("enchantments", enchantments);

        // The plain ones as strings, for the pages that look one up; nested ones are in "data".
        JsonObject attributes = new JsonObject();
        for (var e : extra.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.equals("enchantments") || key.equals("id")) continue;
            if (e.getValue() instanceof String || e.getValue() instanceof Number) attributes.addProperty(key, String.valueOf(e.getValue()));
        }
        item.add("attributes", attributes);
        // The SkyBlock data whole, nested parts and all, as the game keeps it on an item: what
        // Item Value prices an item from.
        item.add("data", json(extra));
    }

    /** NBT as JSON: compounds as objects, lists as arrays. Packed arrays, a backpack's contents say, are left out. */
    private static JsonElement json(Object nbt) {
        if (nbt instanceof Map<?, ?> compound) {
            JsonObject out = new JsonObject();
            for (var e : compound.entrySet()) {
                JsonElement value = json(e.getValue());
                if (value != null) out.add(String.valueOf(e.getKey()), value);
            }
            return out;
        }
        if (nbt instanceof Object[] list) {
            JsonArray out = new JsonArray();
            for (Object o : list) {
                JsonElement value = json(o);
                if (value != null) out.add(value);
            }
            return out;
        }
        if (nbt instanceof String s) return new JsonPrimitive(s);
        if (nbt instanceof Number n) return new JsonPrimitive(n);
        return null;
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
