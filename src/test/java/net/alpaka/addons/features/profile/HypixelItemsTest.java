package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypixelItemsTest {
    /** Writes NBT the way Hypixel's inventories are: a root compound holding the list "i". */
    private static final class Nbt {
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
    private static String riftInventory() throws IOException {
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

    @Test
    void itemsInElitesShape() throws IOException {
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
        assertTrue(items.get("1").isJsonNull());
        assertTrue(InventoryItems.present(gun));
        assertTrue(!InventoryItems.present(items.get("1")));
    }

    @Test
    void theRiftOfTheRightProfileAndMember() throws IOException {
        String data = riftInventory();
        JsonElement answer = JsonParser.parseString("""
                {"success": true, "profiles": [
                  {"profile_id": "11111111-1111-1111-1111-111111111111", "members": {"8f16f5a695d3468392aed51c1a5bb037": {"rift": {"inventory": {
                    "inv_contents": {"type": 0, "data": "%1$s"}}}}}},
                  {"profile_id": "b135fa76-a529-48f8-9fc8-7be8ab8e45b4", "members": {
                    "0123456789abcdef0123456789abcdef": {"rift": {"inventory": {"inv_contents": {"type": 0, "data": "%1$s"}}}},
                    "8f16f5a695d3468392aed51c1a5bb037": {"rift": {"inventory": {
                      "inv_contents": {"type": 0, "data": "%1$s"}, "ender_chest_contents": {"type": 0, "data": "%1$s"}}}}}}]}
                """.formatted(data));
        Map<String, JsonElement> rift = ProfileSession.riftInventories(answer, "b135fa76a52948f89fc87be8ab8e45b4", "8f16f5a695d3468392aed51c1a5bb037");
        assertEquals(java.util.Set.of("rift_inventory", "rift_ender_chest"), rift.keySet());
        assertEquals("TIME_GUN", Json.str(Json.obj(rift.get("rift_inventory"), "items").get("0"), "skyblockId"));
        assertTrue(ProfileSession.riftInventories(answer, "b135fa76a52948f89fc87be8ab8e45b4", "ffffffffffffffffffffffffffffffff").isEmpty());
    }

    @Test
    void brokenDataIsAnErrorNotACrash() {
        assertThrows(IOException.class, () -> HypixelItems.inventory(Base64.getEncoder().encodeToString("not gzip".getBytes())));
    }
}
