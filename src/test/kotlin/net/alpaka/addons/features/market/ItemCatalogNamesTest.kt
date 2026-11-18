package net.alpaka.addons.features.market

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hypixel's item list read for the Profit Tracker: ids by name and NPC prices. */
class ItemCatalogNamesTest {

    @Test
    fun idsByNameAndNpcPrices() {
        val catalog = ItemIcons.index(JsonParser.parseString("""
            {"items":[
              {"id":"CACTUS","name":"Cactus","material":"CACTUS","npc_sell_price":1},
              {"id":"CACTUS_HEAD","name":"Cactus","material":"SKULL_ITEM","durability":3,"npc_sell_price":5},
              {"id":"REVENANT_FLESH","name":"Revenant Flesh","material":"ROTTEN_FLESH","npc_sell_price":1},
              {"id":"SCYTHE_BLADE","name":"§6Scythe Blade","material":"DIAMOND"}
            ]}
        """.trimIndent()))
        assertEquals("REVENANT_FLESH", catalog.idsByName()["revenant flesh"])
        assertEquals("SCYTHE_BLADE", catalog.idsByName()["scythe blade"])
        assertEquals(1.0, catalog.npcPrices()["REVENANT_FLESH"])
        assertTrue("SCYTHE_BLADE" !in catalog.npcPrices())
        // Of two items sharing a name, the SkyBlock one wins over the plain Minecraft one.
        val cactus = catalog.idsByName()["cactus"]
        assertEquals(if ("CACTUS" in catalog.vanillaIds()) "CACTUS_HEAD" else "CACTUS", cactus)
    }
}
