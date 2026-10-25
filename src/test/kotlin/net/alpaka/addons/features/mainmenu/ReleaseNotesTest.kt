package net.alpaka.addons.features.mainmenu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReleaseNotesTest {

    @Test
    fun readsBoldSectionsAndBullets() {
        val sections = ReleaseNotes.parse("""
            **New**
            - Ragnarock: cast countdown under the crosshair
            - Old Master Stars

            **Changed**
            - Mod messages use a gradient `[Alpaka]` prefix

            **Fixed**
            - Command wheel labels no longer overlap
        """.trimIndent())
        assertEquals(listOf("New", "Changed", "Fixed"), sections.map { it.title })
        assertEquals(listOf("Ragnarock: cast countdown under the crosshair", "Old Master Stars"), sections[0].items)
        assertEquals(listOf("Mod messages use a gradient [Alpaka] prefix"), sections[1].items)
    }

    @Test
    fun takesHeadingsAndLooseBullets() {
        val sections = ReleaseNotes.parse("- First\n## Fixed\n* **Second**\n\n## Empty\n")
        assertEquals(listOf("Changes", "Fixed"), sections.map { it.title })
        assertEquals(listOf("Second"), sections[1].items)
    }
}
