package net.alpaka.addons.features.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset

/** The slayer log's pairing of bosses and drops, its sessions and its CSV for a German Excel. */
class SlayerKillLogTest {
    private val log = SlayerKillLog
    private val utc = ZoneOffset.UTC

    /** 2026-10-22 19:00:00 UTC. */
    private val t0 = 1_792_695_600_000L

    private fun kill(atMs: Long, type: SlayerType = SlayerType.BLAZE, fightMs: Long? = 40_000L) =
        SlayerKillLog.Kill(atMs, "Alpaka05", "Kiwi", type, 4, fightMs, false, 500L, 12)

    private fun drop(atMs: Long, item: String = "High Class Archfiend Dice", type: SlayerType = SlayerType.BLAZE) =
        SlayerKillLog.Drop(atMs, type, item, 1, 305)

    private fun fields(row: SlayerKillLog.Row) = row.line.split(";")

    @Test
    fun bossWaitsForItsDropWindow() {
        val book = SlayerKillLog.Book(utc)
        book.kill(kill(t0))
        assertTrue(book.take(t0 + 4_000L).isEmpty())
        assertEquals(1, book.take(t0 + SlayerKillLog.DROP_WINDOW_MS).size)
    }

    @Test
    fun dropJustBeforeTheKillGoesWithThatBoss() {
        val book = SlayerKillLog.Book(utc)
        book.drop(drop(t0 - 1_000L))
        book.kill(kill(t0))
        val rows = book.take(t0 + 6_000L)

        assertEquals(2, rows.size)
        val boss = fields(rows[0])
        assertEquals("2026-10-22 19:00:00", boss[0])
        assertEquals("High Class Archfiend Dice", boss[11])
        val dropRow = fields(rows[1])
        assertEquals(SlayerKillLog.Kind.DROPS, rows[1].kind)
        assertEquals("305", dropRow[8])
        assertEquals("2026-10-22 19:00:00", dropRow[9])
    }

    @Test
    fun dropGoesWithTheNearerOfTwoBosses() {
        val book = SlayerKillLog.Book(utc)
        book.kill(kill(t0))
        book.drop(drop(t0 + 4_000L))
        book.kill(kill(t0 + 5_000L))
        val rows = book.take(t0 + 20_000L)

        val bosses = rows.filter { it.kind == SlayerKillLog.Kind.KILLS }.map { fields(it) }
        assertEquals("", bosses[0][11])
        assertEquals("High Class Archfiend Dice", bosses[1][11])
    }

    @Test
    fun dropOfAnotherSlayerIsNotThisBosses() {
        val book = SlayerKillLog.Book(utc)
        book.kill(kill(t0))
        book.drop(drop(t0, "Warden Heart", SlayerType.ZOMBIE))
        val first = book.take(t0 + 6_000L)
        assertEquals(1, first.size)
        assertEquals("", fields(first[0])[11])

        val loose = book.take(t0 + SlayerKillLog.LOOSE_DROP_AFTER_MS)
        assertEquals(1, loose.size)
        assertEquals("", fields(loose[0])[9])
    }

    @Test
    fun longPauseOrOtherSlayerStartsANewSession() {
        val book = SlayerKillLog.Book(utc)
        book.kill(kill(t0))
        book.kill(kill(t0 + 60_000L))
        book.kill(kill(t0 + 60_000L + SlayerKillLog.SESSION_GAP_MS + 1))
        book.kill(kill(t0 + 2 * SlayerKillLog.SESSION_GAP_MS, SlayerType.ZOMBIE))
        val rows = book.take(t0 + 3 * SlayerKillLog.SESSION_GAP_MS).map { fields(it) }

        assertEquals(rows[0][1], rows[1][1])
        assertEquals("", rows[0][7])
        assertEquals("60000", rows[1][7])
        assertTrue(rows[2][1] != rows[1][1])
        assertEquals("", rows[2][7])
        assertTrue(rows[3][1] != rows[2][1])
    }

    @Test
    fun quitWritesEverythingHeld() {
        val book = SlayerKillLog.Book(utc)
        book.kill(kill(t0))
        book.drop(drop(t0 + 60_000L))
        assertEquals(2, book.take(t0 + 1, all = true).size)
        assertTrue(book.isEmpty())
    }

    @Test
    fun unknownFightTimeAndTierStayEmpty() {
        val book = SlayerKillLog.Book(utc)
        book.kill(SlayerKillLog.Kill(t0, "Alpaka05", "", SlayerType.BLAZE, 0, null, false, 0L, null))
        val row = fields(book.take(t0, all = true)[0])
        assertEquals(listOf("", "", "0", ""), listOf(row[5], row[6], row[9], row[10]))
    }

    @Test
    fun fieldsWithSemicolonsOrQuotesAreQuoted() {
        assertEquals("a;\"b;c\";\"say \"\"hi\"\"\"", log.csvLine(listOf("a", "b;c", "say \"hi\"")))
    }

    @Test
    fun dropLineGivesItemAmountAndMagicFind() {
        val d = log.parseDrop(SlayerType.BLAZE, "2x ◆ Flawed Opal Gemstone",
            "RARE DROP!  (2x ◆ Flawed Opal Gemstone) +305% ✯ Magic Find", t0)!!
        assertEquals("Flawed Opal Gemstone", d.item)
        assertEquals(2, d.amount)
        assertEquals(305, d.magicFind)

        val plain = log.parseDrop(SlayerType.BLAZE, "Warden Heart", "CRAZY RARE DROP!  (Warden Heart)", t0)!!
        assertEquals(1, plain.amount)
        assertEquals(null, plain.magicFind)
    }

    @Test
    fun newFileStartsWithBomAndHeaderAndIsAppendedTo(@TempDir dir: File) {
        val row = SlayerKillLog.Row(SlayerKillLog.Kind.KILLS, "2026-10", "x;y")
        SlayerKillLog.LogFiles.append(dir, "mac", listOf(row))
        SlayerKillLog.LogFiles.append(dir, "mac", listOf(row))

        val file = File(dir, "kills-2026-10-mac.csv")
        val text = file.readText(StandardCharsets.UTF_8)
        assertTrue(text.startsWith("\uFEFFTime;Session;"))
        assertEquals(3, text.trimEnd().split("\r\n").size)
    }

    @Test
    fun fileWithAnOlderHeaderIsLeftAlone(@TempDir dir: File) {
        File(dir, "kills-2026-10-mac.csv").writeText("\uFEFFTime;Slayer\r\n1;2\r\n", StandardCharsets.UTF_8)
        val file = SlayerKillLog.LogFiles.resolve(dir, SlayerKillLog.Kind.KILLS, "2026-10", "mac")
        assertEquals("kills-2026-10-mac-2.csv", file.name)
    }

    private fun row(line: String) = SlayerKillLog.Row(SlayerKillLog.Kind.KILLS, "2026-10", line)

    private fun lines(file: File) = file.readText(StandardCharsets.UTF_8).trimEnd().split("\r\n")

    @Test
    fun switchingFolderCopiesTheLogOver(@TempDir root: File) {
        val old = File(root, "old")
        val new = File(root, "new")
        SlayerKillLog.LogFiles.append(old, "mac", listOf(row("a"), row("b")))

        assertEquals(1, SlayerKillLog.LogFiles.adopt(old, new))
        assertEquals(lines(File(old, "kills-2026-10-mac.csv")), lines(File(new, "kills-2026-10-mac.csv")))
        assertTrue(File(old, "kills-2026-10-mac.csv").exists())
    }

    @Test
    fun switchingBackAddsOnlyTheMissingLines(@TempDir root: File) {
        val a = File(root, "a")
        val b = File(root, "b")
        SlayerKillLog.LogFiles.append(a, "mac", listOf(row("1")))
        SlayerKillLog.LogFiles.adopt(a, b)
        SlayerKillLog.LogFiles.append(b, "mac", listOf(row("2")))

        assertEquals(1, SlayerKillLog.LogFiles.adopt(b, a))
        assertEquals(listOf("1", "2"), lines(File(a, "kills-2026-10-mac.csv")).drop(1))
        assertEquals(0, SlayerKillLog.LogFiles.adopt(b, a))
        assertEquals(0, SlayerKillLog.LogFiles.adopt(a, b))
        assertEquals(listOf("1", "2"), lines(File(b, "kills-2026-10-mac.csv")).drop(1))
    }

    @Test
    fun fileWithAnotherHeaderIsCopiedBesideIt(@TempDir root: File) {
        val old = File(root, "old").apply { mkdirs() }
        val new = File(root, "new")
        File(old, "kills-2026-10-mac.csv").writeText("\uFEFFTime;Slayer\r\n1;2\r\n", StandardCharsets.UTF_8)
        SlayerKillLog.LogFiles.append(new, "mac", listOf(row("x")))

        assertEquals(1, SlayerKillLog.LogFiles.adopt(old, new))
        assertEquals(listOf("x"), lines(File(new, "kills-2026-10-mac.csv")).drop(1))
        assertTrue(File(new, "kills-2026-10-mac-from.csv").exists())
    }

    @Test
    fun otherFilesInTheFolderAreNotTouched(@TempDir root: File) {
        val old = File(root, "old").apply { mkdirs() }
        File(old, "notes.csv").writeText("hi")
        assertEquals(0, SlayerKillLog.LogFiles.adopt(old, File(root, "new")))
    }

    @Test
    fun machineNameIsSafeForAFileName() {
        assertEquals("Colins-MacBook-Pro", SlayerKillLog.LogFiles.sanitize("Colin's MacBook Pro"))
    }
}
