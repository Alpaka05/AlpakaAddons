package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypixelItemsTest {
    /** Writes NBT the way Hypixel's inventories are: a root compound holding the list "i". */
    static final class Nbt {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(bytes);

        Nbt begin(int type, String name) throws IOException {
            out.writeByte(type);
            out.writeUTF(name);
            return this;
        }

        Nbt string(String name, String value) throws IOException {
            begin(8, name).out.writeUTF(value);
            return this;
        }

        Nbt end() throws IOException {
            out.writeByte(0);
            return this;
        }

        String base64() throws IOException {
            ByteArrayOutputStream zipped = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(zipped)) {
                gz.write(bytes.toByteArray());
            }
            return Base64.getEncoder().encodeToString(zipped.toByteArray());
        }
    }

    /** Two slots: the Time Gun with lore and an enchantment, and an empty one. */
    static String riftInventory() throws IOException {
        Nbt n = new Nbt();
        n.begin(10, "");
        n.begin(9, "i");
        n.out.writeByte(10);
        n.out.writeInt(2);
        // Slot 0.
        n.begin(2, "id").out.writeShort(291);
        n.begin(1, "Count").out.writeByte(1);
        n.begin(2, "Damage").out.writeShort(0);
        n.begin(10, "tag");
        n.begin(10, "display");
        n.string("Name", "§6Time Gun");
        n.begin(9, "Lore");
        n.out.writeByte(8);
        n.out.writeInt(2);
        n.out.writeUTF("§7Shoots time.");
        n.out.writeUTF("§6§lLEGENDARY");
        n.end();
        n.begin(10, "ExtraAttributes");
        n.string("id", "TIME_GUN");
        n.string("uuid", "abc-123");
        n.begin(3, "rarity_upgrades").out.writeInt(1);
        n.begin(10, "enchantments");
        n.begin(3, "sharpness").out.writeInt(5);
        n.end();
        n.end();
        n.begin(10, "SkullOwner");
        n.begin(10, "Properties");
        n.begin(9, "textures");
        n.out.writeByte(10);
        n.out.writeInt(1);
        n.string("Value", "eyJ0ZXh0dXJlcyI6e319");
        n.end();
        n.end();
        n.end();
        n.end();
        n.end();
        // Slot 1, empty.
        n.end();
        n.end();
        return n.base64();
    }

    /** An inventory of one slot, holding an item with this SkyBlock id and name. */
    static String oneItem(String id, String name) throws IOException {
        Nbt n = new Nbt();
        n.begin(10, "");
        n.begin(9, "i");
        n.out.writeByte(10);
        n.out.writeInt(1);
        n.begin(2, "id").out.writeShort(1);
        n.begin(1, "Count").out.writeByte(1);
        n.begin(10, "tag");
        n.begin(10, "display");
        n.string("Name", name);
        n.end();
        n.begin(10, "ExtraAttributes");
        n.string("id", id);
        n.end();
        n.end();
        n.end();
        n.end();
        return n.base64();
    }

    /** A tool as the tool kits keep it: its SkyBlock data alone, without the item around it. */
    static String bareTool(String id) throws IOException {
        Nbt n = new Nbt();
        n.begin(10, "");
        n.string("id", id);
        n.begin(3, "rarity_upgrades").out.writeInt(1);
        n.end();
        return n.base64();
    }

    @Test
    void itemsAsThePagesReadThem() throws IOException {
        JsonObject inv = HypixelItems.inventory(riftInventory());
        JsonObject items = Json.obj(inv, "items");
        assertEquals(2, items.size());
        JsonElement gun = items.get("0");
        assertEquals("TIME_GUN", Json.str(gun, "skyblockId"));
        assertEquals("§6Time Gun", Json.str(gun, "name"));
        assertEquals(2, Json.arr(gun, "lore").size());
        assertEquals(1, Json.num(gun, "count", 0));
        assertEquals(5, Json.num(gun, "enchantments.sharpness", 0));
        assertEquals("1", Json.str(gun, "attributes.rarity_upgrades"));
        assertEquals("eyJ0ZXh0dXJlcyI6e319", Json.str(gun, "attributes.skin_texture"));
        assertEquals("abc-123", Json.str(gun, "uuid"));
        // The SkyBlock data whole, nested parts as they are: what Item Value prices the item from.
        assertEquals("TIME_GUN", Json.str(gun, "data.id"));
        assertEquals(5, Json.num(gun, "data.enchantments.sharpness", 0));
        assertEquals(1, Json.num(gun, "data.rarity_upgrades", 0));
        assertTrue(items.get("1").isJsonNull());
        assertTrue(InventoryItems.present(gun));
        assertTrue(!InventoryItems.present(items.get("1")));
    }

    @Test
    void aSingleItemComesWithOrWithoutTheItemAroundIt() throws IOException {
        JsonElement piece = HypixelItems.single(oneItem("WISE_DRAGON_HELMET", "§6Wise Dragon Helmet"));
        assertEquals("WISE_DRAGON_HELMET", Json.str(piece, "skyblockId"));
        assertEquals("§6Wise Dragon Helmet", Json.str(piece, "name"));
        // A tool kit's tool has no name or lore, only its data.
        JsonElement tool = HypixelItems.single(bareTool("CACTUS_KNIFE_3"));
        assertEquals("CACTUS_KNIFE_3", Json.str(tool, "skyblockId"));
        assertEquals(null, Json.str(tool, "name"));
        assertEquals("1", Json.str(tool, "attributes.rarity_upgrades"));
        assertEquals(1, Json.num(tool, "data.rarity_upgrades", 0));
        assertTrue(HypixelItems.single(new Nbt().begin(10, "").end().base64()).isJsonNull());
    }

    @Test
    void brokenDataIsAnErrorNotACrash() {
        assertThrows(IOException.class, () -> HypixelItems.inventory(Base64.getEncoder().encodeToString("not gzip".getBytes())));
    }
}
