package net.alpaka.addons.features.slayer

import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats
import net.minecraft.client.Minecraft
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * One line per slayer boss and one per drop, appended to CSV files beside the slayer record.
 *
 * The record in alpaka-stats.json only keeps totals: lifetime kills, the last drop of each item,
 * the best time per tier. That answers what the HUD needs, but not which sessions went well, so this
 * log keeps every boss on its own, to be opened as a table or handed to an analysis tool later.
 *
 * Laid out for a German Excel, which is what the files are opened with: semicolons between fields,
 * a UTF-8 byte order mark so umlauts and the star in item names survive, no decimal numbers (a
 * decimal point would be read as a thousands separator), and times as "2026-10-22 21:04:11", which
 * Excel takes for a date on its own.
 *
 * The files are only ever appended to, never rewritten, so a kill costs one short write instead of
 * the whole record. Each PC writes files of its own, one per month, so two machines sharing a synced
 * folder never write the same file, and nothing has to be merged.
 *
 * Always on, with no setting: it costs nothing in play, and a history that was switched off for a
 * while cannot be filled in afterwards.
 */
object SlayerKillLog {

    /** Columns of the boss file. New ones go at the end; see [LogFiles.resolve]. */
    internal val KILL_HEADER = listOf(
        "Time", "Session", "Account", "Profile", "Slayer", "Tier", "Fight Ms", "Since Last Kill Ms",
        "Personal Best", "XP", "Total Kills", "Drops",
    )

    /** Columns of the drop file. "Boss Time" is the boss line the drop belongs to, empty for none. */
    internal val DROP_HEADER = listOf(
        "Time", "Session", "Account", "Profile", "Slayer", "Tier", "Item", "Amount", "Magic Find", "Boss Time",
    )

    /**
     * How long a boss line waits for its drops. Hypixel prints the drop just before the quest
     * completion and the sidebar can notice the kill either side of it, so a drop is taken as this
     * boss's when the two are this close in either direction.
     */
    internal const val DROP_WINDOW_MS = 5_000L

    /** A drop that no boss claimed within this long is written on its own, grinding loot for one. */
    internal const val LOOSE_DROP_AFTER_MS = 2 * DROP_WINDOW_MS

    /**
     * A pause longer than this starts a new session. Long enough for a trip to the bazaar or a
     * reconnect, short enough that the next evening is a session of its own.
     */
    internal const val SESSION_GAP_MS = 30 * 60_000L

    /** Two kill signals for the same boss closer than this are one kill; see SlayerDropTracker. */
    private const val KILL_DEBOUNCE_MS = 2_000L

    private const val RETRY_PAUSE_MS = 5_000L

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM")

    /** "+305% ✯ Magic Find" after the item, once colour codes are gone. */
    private val MAGIC_FIND = Regex("""\+(\d+)%\s*✯?\s*Magic Find""")

    /** "3x " in front of the item inside the parentheses. */
    private val AMOUNT = Regex("""^(\d+)x\s+""")

    /** Leading noise on the item name; the same as SlayerDropTracker strips. */
    private val ITEM_PREFIX = Regex("""^(?:\d+x\s+)?(?:◆\s*)?""")

    internal class Kill(
        val atMs: Long,
        val account: String,
        val profile: String,
        val type: SlayerType,
        val tier: Int,
        val fightMs: Long?,
        val personalBest: Boolean,
        val xp: Long,
        val totalKills: Int?,
    ) {
        var session = ""
        var sinceLastKillMs: Long? = null
        val drops = ArrayList<Drop>()
    }

    internal class Drop(
        val atMs: Long,
        val type: SlayerType,
        val item: String,
        val amount: Int,
        val magicFind: Int?,
    ) {
        var boss: Kill? = null
    }

    /** A finished line and the file it goes into. */
    internal data class Row(val kind: Kind, val month: String, val line: String)

    internal enum class Kind(val prefix: String, val header: List<String>) {
        KILLS("kills", KILL_HEADER),
        DROPS("drops", DROP_HEADER),
    }

    /**
     * Pairs bosses with their drops and decides sessions. Holds no game state, so it can be fed
     * events directly in a test.
     */
    internal class Book(private val zone: ZoneId) {
        private val kills = ArrayList<Kill>()
        private val drops = ArrayList<Drop>()

        private var lastKill: Kill? = null
        private var session = ""

        fun kill(kill: Kill) {
            val previous = lastKill
            // A new slayer is a new session too: a run of Revenants and a run of Blazes are
            // different things to compare, even on the same evening.
            val sameRun = previous != null && previous.type == kill.type
                    && previous.account == kill.account && previous.profile == kill.profile
                    && kill.atMs - previous.atMs <= SESSION_GAP_MS
            if (!sameRun) session = time(kill.atMs)
            kill.session = session
            kill.sinceLastKillMs = if (sameRun) kill.atMs - previous!!.atMs else null
            lastKill = kill
            kills.add(kill)
        }

        fun drop(drop: Drop) {
            drops.add(drop)
        }

        /**
         * Lines whose wait is over at [nowMs]; with [all], every line still held, for quitting.
         *
         * A boss is finished once its drop window has passed. A drop goes with the nearest boss of
         * its slayer inside the window, and is written with it; one that no boss claims is written
         * on its own later, after every boss that could have claimed it is finished.
         */
        fun take(nowMs: Long, all: Boolean = false): List<Row> {
            val rows = ArrayList<Row>()

            val due = kills.filter { all || nowMs - it.atMs >= DROP_WINDOW_MS }
            for (kill in due) {
                for (drop in drops) {
                    if (drop.boss != null || drop.type != kill.type) continue
                    if (kotlin.math.abs(drop.atMs - kill.atMs) > DROP_WINDOW_MS) continue
                    val other = kills.firstOrNull {
                        it !== kill && it.type == drop.type
                                && kotlin.math.abs(drop.atMs - it.atMs) < kotlin.math.abs(drop.atMs - kill.atMs)
                    }
                    if (other == null) drop.boss = kill
                }
                kill.drops.addAll(drops.filter { it.boss === kill })
                rows.add(Row(Kind.KILLS, month(kill.atMs), killLine(kill)))
                for (drop in kill.drops) rows.add(Row(Kind.DROPS, month(drop.atMs), dropLine(drop, kill)))
                drops.removeAll(kill.drops.toSet())
            }
            kills.removeAll(due.toSet())

            val loose = drops.filter { all || nowMs - it.atMs >= LOOSE_DROP_AFTER_MS }
            for (drop in loose) rows.add(Row(Kind.DROPS, month(drop.atMs), dropLine(drop, null)))
            drops.removeAll(loose.toSet())
            return rows
        }

        fun isEmpty() = kills.isEmpty() && drops.isEmpty()

        private fun killLine(kill: Kill) = csvLine(listOf(
            time(kill.atMs),
            kill.session,
            kill.account,
            kill.profile,
            kill.type.display,
            tier(kill.tier),
            kill.fightMs?.toString() ?: "",
            kill.sinceLastKillMs?.toString() ?: "",
            if (kill.personalBest) "Yes" else "",
            kill.xp.toString(),
            kill.totalKills?.toString() ?: "",
            kill.drops.joinToString(", ") { if (it.amount > 1) "${it.amount}x ${it.item}" else it.item },
        ))

        private fun dropLine(drop: Drop, kill: Kill?) = csvLine(listOf(
            time(drop.atMs),
            kill?.session ?: lastKill?.takeIf { drop.atMs - it.atMs <= SESSION_GAP_MS }?.session ?: "",
            kill?.account ?: lastKill?.account ?: "",
            kill?.profile ?: lastKill?.profile ?: "",
            drop.type.display,
            kill?.let { tier(it.tier) } ?: "",
            drop.item,
            drop.amount.toString(),
            drop.magicFind?.toString() ?: "",
            kill?.let { time(it.atMs) } ?: "",
        ))

        private fun tier(tier: Int) = if (tier in 1..5) tier.toString() else ""

        private fun time(ms: Long) = TIME.format(Instant.ofEpochMilli(ms).atZone(zone))

        private fun month(ms: Long) = MONTH.format(Instant.ofEpochMilli(ms).atZone(zone))
    }

    /** One CSV line for a German Excel: semicolons, quotes only where a field needs them. */
    internal fun csvLine(fields: List<String>): String = fields.joinToString(";") { field ->
        if (field.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    /** The item, its amount and the magic find out of a drop line's parts. */
    internal fun parseDrop(type: SlayerType, rawItem: String, line: String, atMs: Long): Drop? {
        val trimmed = rawItem.trim()
        val item = ITEM_PREFIX.replaceFirst(trimmed, "").trim()
        if (item.isEmpty()) return null
        val amount = AMOUNT.find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val magicFind = MAGIC_FIND.find(line)?.groupValues?.get(1)?.toIntOrNull()
        return Drop(atMs, type, item, amount, magicFind)
    }

    private val book = Book(ZoneId.systemDefault())

    /** Lines finished but not yet on disk, oldest first. Kept until a write of them succeeds. */
    private val unwritten = ArrayList<Row>()

    private var pending: Future<*>? = null
    private var pendingCount = 0
    private var nextAttemptMs = 0L
    private var lastKillAtMs = 0L
    private var lastKillType: SlayerType? = null

    private val WRITER: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AlpakaAddons slayer log writer").apply { isDaemon = true }
    }

    /**
     * A boss died. Called after the timer and the session have taken the kill, so the fight time
     * and the XP are already settled. [xp]: what the session credited for it.
     */
    fun onBossKilled(type: SlayerType, killedAtMs: Long, xp: Long) {
        if (type == lastKillType && killedAtMs - lastKillAtMs < KILL_DEBOUNCE_MS) return
        lastKillType = type
        lastKillAtMs = killedAtMs

        val fight = SlayerTimer.lastFight?.takeIf { it.type == type && it.sidebarKillMs == killedAtMs }
        val totalKills = if (AlpakaConfig.instance.slayerDropTrackerEnabled) AlpakaStats.slayerBossMap()[type]?.kills else null
        book.kill(Kill(
            atMs = killedAtMs,
            account = Minecraft.getInstance().user?.name ?: "",
            profile = AlpakaStats.currentProfileLabel(),
            type = type,
            tier = SlayerQuestDetector.lastSeenTier,
            fightMs = fight?.reportedMs,
            personalBest = fight != null && SlayerTimer.lastResultWasBest,
            xp = xp,
            totalKills = totalKills,
        ))
    }

    /** A drop line was seen while a slayer was running. [rawItem]: what stood in the parentheses. */
    fun onDrop(type: SlayerType, rawItem: String, line: String) {
        parseDrop(type, rawItem, line, System.currentTimeMillis())?.let(book::drop)
    }

    /** Every client tick: finishes what is due and starts a background write. */
    fun tick() {
        unwritten.addAll(book.take(System.currentTimeMillis()))
        collect(false)
        startWrite()
    }

    /** For disconnecting and quitting: writes everything held, waiting for it. */
    fun flushNow() {
        unwritten.addAll(book.take(System.currentTimeMillis(), all = true))
        collect(true)
        nextAttemptMs = 0L
        startWrite()
        collect(true)
    }

    /** The log's own folder inside a stats folder. */
    fun folderIn(statsDirectory: File): File = File(statsDirectory, "slayer-log")

    /** The log files in [statsDirectory], for /alpakastats; 0 when there are none yet. */
    fun fileCount(statsDirectory: File): Int = LogFiles.logFiles(folderIn(statsDirectory)).size

    /**
     * The stats folder was switched with /alpakastats folder: brings the log files along, so the
     * history does not end up split across two folders. Copied rather than moved, like the record;
     * see [LogFiles.adopt]. Runs on the writer, behind any write still aimed at the old folder.
     */
    fun onFolderChanged(from: File, to: File) {
        val source = folderIn(from)
        val target = folderIn(to)
        if (source.absoluteFile.normalize() == target.absoluteFile.normalize()) return
        WRITER.submit {
            try {
                val copied = LogFiles.adopt(source, target)
                if (copied > 0) {
                    Minecraft.getInstance().execute {
                        SlayerDropTracker.sendModMessage("§7Brought §f$copied§7 slayer log file"
                                + (if (copied != 1) "s" else "") + " over from §f" + source.absolutePath)
                    }
                }
            } catch (e: Exception) {
                AlpakaAddons.LOGGER.warn("Could not bring the slayer log over from {}", source.absolutePath, e)
                Minecraft.getInstance().execute {
                    SlayerDropTracker.sendModMessage("§eThe slayer log files stayed in §f" + source.absolutePath
                            + "§e; copy them over by hand")
                }
            }
        }
    }

    private fun startWrite() {
        if (pending != null || unwritten.isEmpty()) return
        if (System.currentTimeMillis() < nextAttemptMs) return
        // A chosen folder that is not there yet is a drive still coming up; lines wait in memory
        // rather than going somewhere that would hide them. See AlpakaStats#isAwaitingStore.
        if (!AlpakaStats.storeAvailable()) return

        val rows = ArrayList(unwritten)
        val folder = folderIn(AlpakaStats.directory())
        pendingCount = rows.size
        pending = WRITER.submit { LogFiles.append(folder, LogFiles.machine(), rows) }
    }

    private fun collect(wait: Boolean) {
        val job = pending ?: return
        if (!wait && !job.isDone) return
        pending = null
        try {
            job.get()
            repeat(pendingCount) { unwritten.removeAt(0) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            AlpakaAddons.LOGGER.warn("Could not write the slayer log; {} lines are kept for the next try",
                unwritten.size, e.cause ?: e)
            nextAttemptMs = System.currentTimeMillis() + RETRY_PAUSE_MS
        }
        pendingCount = 0
    }

    /** The files themselves. Touches nothing but its arguments and the disk, so it runs off-thread. */
    internal object LogFiles {
        private const val BOM = "\uFEFF"

        @Volatile
        private var machineName: String? = null

        /**
         * This PC's name for the file names, so every machine writing to a synced folder has files of
         * its own. Asked on the writer thread: the lookup can take seconds on a Mac.
         */
        fun machine(): String {
            machineName?.let { return it }
            val raw = System.getenv("COMPUTERNAME")
                ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
                ?: ""
            val name = sanitize(raw.substringBefore('.')).ifEmpty { "pc" }
            machineName = name
            return name
        }

        internal fun sanitize(name: String): String =
            name.replace(Regex("['’]"), "").replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').take(40)

        /** The log's CSV files in [folder], by name. */
        fun logFiles(folder: File): List<File> =
            folder.listFiles { f -> f.isFile && f.name.endsWith(".csv") && Kind.entries.any { f.name.startsWith(it.prefix + "-") } }
                ?.sortedBy { it.name } ?: emptyList()

        /**
         * Copies the log files of [from] into [to]; returns how many files it changed in [to].
         *
         * A file [to] does not have is copied as it is. One it has under the same name - the same
         * PC and month, after switching back and forth - gets the lines it is missing appended, so
         * nothing is overwritten and nothing is counted twice: every line carries its own time, so a
         * line already there is the same boss or drop. With a different header the lines would land
         * in the wrong columns, so such a file is copied beside it under a numbered name instead.
         */
        fun adopt(from: File, to: File): Int {
            val files = logFiles(from)
            if (files.isEmpty()) return 0
            if (!to.isDirectory) java.nio.file.Files.createDirectories(to.toPath())

            var changed = 0
            for (file in files) {
                val target = File(to, file.name)
                if (!target.exists() || target.length() == 0L) {
                    java.nio.file.Files.copy(file.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    changed++
                    continue
                }

                val theirs = readLines(file)
                val ours = readLines(target)
                if (theirs.firstOrNull() != ours.firstOrNull()) {
                    val aside = freeName(to, file.name)
                    java.nio.file.Files.copy(file.toPath(), aside.toPath())
                    changed++
                    continue
                }

                val known = ours.toHashSet()
                val missing = theirs.drop(1).filter { it.isNotEmpty() && it !in known }
                if (missing.isEmpty()) continue
                FileChannel.open(target.toPath(), StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
                    val lock = runCatching { channel.lock() }.getOrNull()
                    try {
                        val buffer = ByteBuffer.wrap(missing.joinToString("") { it + "\r\n" }.toByteArray(StandardCharsets.UTF_8))
                        while (buffer.hasRemaining()) channel.write(buffer)
                    } finally {
                        runCatching { lock?.release() }
                    }
                }
                changed++
            }
            return changed
        }

        /** A file's lines without the byte order mark, header first. */
        private fun readLines(file: File): List<String> {
            val lines = file.readText(StandardCharsets.UTF_8).removePrefix(BOM).split("\r\n", "\n")
            return if (lines.lastOrNull()?.isEmpty() == true) lines.dropLast(1) else lines
        }

        /** "kills-2026-10-mac-from-2.csv" and so on, the first name not taken in [folder]. */
        private fun freeName(folder: File, name: String): File {
            val base = name.removeSuffix(".csv")
            var n = 1
            while (true) {
                val file = File(folder, "$base-from${if (n == 1) "" else "-$n"}.csv")
                if (!file.exists()) return file
                n++
            }
        }

        /** Appends the rows to their month's file of their kind, one write per file. */
        fun append(folder: File, machine: String, rows: List<Row>) {
            if (!folder.isDirectory) java.nio.file.Files.createDirectories(folder.toPath())
            for ((key, group) in rows.groupBy { it.kind to it.month }) {
                val (kind, month) = key
                val file = resolve(folder, kind, month, machine)
                write(file, kind, group.joinToString("") { it.line + "\r\n" })
            }
        }

        /**
         * The file for this kind, month and machine. A file whose header is not the current one was
         * written by another version of the mod, so the lines go into a numbered sibling instead;
         * mixing two layouts in one file would shift every column after the change.
         */
        internal fun resolve(folder: File, kind: Kind, month: String, machine: String): File {
            val header = csvLine(kind.header)
            var n = 1
            while (true) {
                val suffix = if (n == 1) "" else "-$n"
                val file = File(folder, "${kind.prefix}-$month-$machine$suffix.csv")
                if (!file.exists() || file.length() == 0L) return file
                val first = file.bufferedReader(StandardCharsets.UTF_8).use { it.readLine() }?.removePrefix(BOM)
                if (first == header) return file
                n++
            }
        }

        /**
         * Appends under a file lock, which keeps a second instance on this PC from interleaving its
         * lines with these. A new file gets the byte order mark and the header first.
         */
        private fun write(file: File, kind: Kind, text: String) {
            FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
                val lock = runCatching { channel.lock() }.getOrNull()
                try {
                    val out = if (channel.size() == 0L) BOM + csvLine(kind.header) + "\r\n" + text else text
                    val buffer = ByteBuffer.wrap(out.toByteArray(StandardCharsets.UTF_8))
                    while (buffer.hasRemaining()) channel.write(buffer)
                } finally {
                    runCatching { lock?.release() }
                }
            }
        }
    }
}
