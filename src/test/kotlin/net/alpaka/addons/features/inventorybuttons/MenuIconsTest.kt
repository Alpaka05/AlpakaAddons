package net.alpaka.addons.features.inventorybuttons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

/** The heads of Hypixel's menus as button icons: reading their skin, learning them, finding them. */
class MenuIconsTest {
    private val fastTravel = "a".repeat(64)
    private val day = 24 * 3_600_000L

    @BeforeEach
    fun forget() = MenuIcons.clear()

    private fun encode(json: String) = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

    @Test
    fun `the skin is read from a head's texture value`() {
        val sacks = "80a077e248d142772ea800864f8c578b9d36885b29daf836b64a706882b6ec10"
        val signed = encode(
            """{"timestamp":1591310585609,"profileId":"41d3abc2d749400c9090d5434d03831b","profileName":"Megakloon",""" +
                """"signatureRequired":true,"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/$sacks"}}}"""
        )
        assertEquals(sacks, MenuIcons.hashOf(signed))
        // Hypixel sends some without quotes around the keys.
        assertEquals(sacks, MenuIcons.hashOf(encode("""{textures:{SKIN:{url:"http://textures.minecraft.net/texture/$sacks"}}}""")))
        assertEquals(sacks, MenuIcons.hashOf(MenuIcons.textureValue(sacks)))
    }

    @Test
    fun `a texture value without a skin gives nothing`() {
        assertNull(MenuIcons.hashOf(null))
        assertNull(MenuIcons.hashOf("not base64 !"))
        assertNull(MenuIcons.hashOf(encode("""{"textures":{}}""")))
        // A cape alone is no head.
        assertNull(MenuIcons.hashOf(encode("""{"textures":{"CAPE":{"url":"http://textures.minecraft.net/texture/$fastTravel"}}}""")))
    }

    @Test
    fun `only a head with a usable skin is an icon`() {
        assertEquals(fastTravel, MenuIcons.hash("head:$fastTravel"))
        assertNull(MenuIcons.hash("head:../../etc"))
        assertNull(MenuIcons.hash("minecraft:player_head"))
        assertNull(MenuIcons.hash(null))
    }

    @Test
    fun `a head seen in a menu is listed under its name after the built-in ones`() {
        assertTrue(MenuIcons.see(fastTravel, "§aFast Travel ", 1_000))
        val all = MenuIcons.all()
        assertEquals("Fast Travel", all["head:$fastTravel"])
        assertEquals("Fast Travel", MenuIcons.name("head:$fastTravel"))
        assertEquals(MenuIcons.BUILT_IN.size + 1, all.size)
        assertEquals("head:$fastTravel", all.keys.last())
        assertEquals("Accessory Bag", all.values.first())
    }

    @Test
    fun `seeing a head again is only news after a while or under a new name`() {
        val before = MenuIcons.revision
        assertTrue(MenuIcons.see(fastTravel, "Fast Travel", 1_000))
        assertFalse(MenuIcons.see(fastTravel, "Fast Travel", 2_000))
        assertEquals(before + 1, MenuIcons.revision)
        assertTrue(MenuIcons.see(fastTravel, "Fast Travel", 1_000 + day))
        assertEquals(before + 1, MenuIcons.revision)
        assertTrue(MenuIcons.see(fastTravel, "Warps", 1_000 + day))
        assertEquals("Warps", MenuIcons.name("head:$fastTravel"))
        assertEquals(before + 2, MenuIcons.revision)
    }

    @Test
    fun `nameless heads and the built-in ones are not learned`() {
        assertFalse(MenuIcons.see(fastTravel, "§7 ", 1_000))
        assertFalse(MenuIcons.see("not a skin", "Fast Travel", 1_000))
        val quiver = MenuIcons.BUILT_IN.entries.first { it.value == "Quiver" }.key
        assertFalse(MenuIcons.see(quiver, "Quiver (Arrows)", 1_000))
        assertEquals("Quiver", MenuIcons.name("head:$quiver"))
        assertEquals(MenuIcons.BUILT_IN.size, MenuIcons.all().size)
    }

    @Test
    fun `a head named after the player it shows is left out`() {
        assertTrue(MenuIcons.showsPlayer("Alpakaa", "Alpakaa's Party"))
        assertTrue(MenuIcons.showsPlayer("Alpakaa", "[MVP+] alpakaa"))
        // The player's own head stands for a menu entry there.
        assertFalse(MenuIcons.showsPlayer("Alpakaa", "Your SkyBlock Profile"))
        // What Hypixel's own heads carry for a name.
        assertFalse(MenuIcons.showsPlayer("", "Fast Travel"))
        assertFalse(MenuIcons.showsPlayer(null, "Fast Travel"))
        assertFalse(MenuIcons.showsPlayer("5da6bec64bd942bc", "Accessory Bag"))
    }

    @Test
    fun `only the newest heads are kept`() {
        val total = MenuIcons.MAX_SEEN + 150
        for (i in 0 until total) MenuIcons.see(skin(i), "Head $i", i.toLong())
        val learned = MenuIcons.all().size - MenuIcons.BUILT_IN.size
        assertTrue(learned <= MenuIcons.MAX_SEEN + 100, "kept $learned")
        assertNull(MenuIcons.name("head:${skin(0)}"))
        assertEquals("Head ${total - 1}", MenuIcons.name("head:${skin(total - 1)}"))
    }

    @Test
    fun `learned heads come back from the file`() {
        MenuIcons.see(fastTravel, "Fast Travel", 5_000)
        val json = MenuIcons.json()
        MenuIcons.clear()
        assertNull(MenuIcons.name("head:$fastTravel"))
        MenuIcons.read(json)
        assertEquals("Fast Travel", MenuIcons.name("head:$fastTravel"))
        // Still last seen then: seeing it right after is no news.
        assertFalse(MenuIcons.see(fastTravel, "Fast Travel", 6_000))
    }

    @Test
    fun `a file that is no list of heads is ignored`() {
        MenuIcons.read("[]")
        MenuIcons.read("""{"icons":{"zz":{"name":"x","at":1},"$fastTravel":{"name":"  ","at":1},"${skin(1)}":"nope","${skin(2)}":{"name":"No Time"}}}""")
        assertEquals(MenuIcons.BUILT_IN.size, MenuIcons.all().size)
    }

    @Test
    fun `the search finds a head by its name, not by its skin`() {
        // "bed" and "face" are hex digits, and this skin is full of them.
        val skin = "bedface0".repeat(8)
        MenuIcons.see(skin, "Fast Travel", 1_000)
        val all = listOf("head:$skin", "minecraft:red_bed", "skyblock:FAST_TRAVEL_SCROLL")
        assertEquals(listOf("minecraft:red_bed"), InventoryButtonIcons.search(all, "bed"))
        assertEquals(listOf("head:$skin"), InventoryButtonIcons.search(all, "travel fast").filter { it.startsWith("head:") })
        assertEquals("Fast Travel", InventoryButtonIcons.name("head:$skin"))
        assertEquals("F", InventoryButtonIcons.letter("head:$skin"))
        assertEquals("Head", InventoryButtonIcons.name("head:${skin(7)}"))
        assertEquals("E", InventoryButtonIcons.letter("minecraft:ender_chest"))
    }

    @Test
    fun `the built-in heads are skins with distinct names`() {
        assertEquals(MenuIcons.BUILT_IN.size, MenuIcons.BUILT_IN.values.toSet().size)
        for (hash in MenuIcons.BUILT_IN.keys) assertEquals(hash, MenuIcons.hash("head:$hash"))
        val all = MenuIcons.all().keys.toList()
        assertEquals(1, InventoryButtonIcons.search(all, "sack").size)
    }

    private fun skin(i: Int) = i.toString(16).padStart(32, '0')
}
