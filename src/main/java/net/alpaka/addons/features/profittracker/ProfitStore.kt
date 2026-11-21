package net.alpaka.addons.features.profittracker

import com.google.gson.GsonBuilder
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AtomicJsonFile
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Where the tracker keeps its figures: all-time per profile and activity in `data.json`, the
 * session in memory, and both as CSV next to them (`profit-log.csv` grows with every gain,
 * `profit-summary.csv` is rewritten on every save) in `config/alpaka-profit-tracker/`.
 *
 * Writing happens on one background thread, so a save never stalls a frame; the JSON is written
 * atomically like the config. Saves are batched: a gain marks the store dirty and the tick writes
 * at most every few seconds.
 */
object ProfitStore {

    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private const val SAVE_EVERY_MS = 10_000L

    val dir: File by lazy { FabricLoader.getInstance().configDir.resolve("alpaka-profit-tracker").toFile() }
    private val dataFile: File get() = File(dir, "data.json")
    val logFile: File get() = File(dir, "profit-log.csv")
    val summaryFile: File get() = File(dir, "profit-summary.csv")

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Alpaka Profit Tracker").apply { isDaemon = true }
    }

    private var data: ProfitFile = ProfitFile()
    private var loaded = false

    /** This game's figures, per profile and activity. Not saved: a restart starts a new session. */
    private val session = LinkedHashMap<String, LinkedHashMap<String, ProfitBucket>>()

    private var dirty = false
    private var lastSaveMs = 0L

    /** Log rows not written yet; drained by the writer thread. */
    private val pendingLog = ArrayList<String>()

    fun load() {
        if (loaded) return
        loaded = true
        try {
            val json = AtomicJsonFile.read(dataFile) ?: return
            data = GSON.fromJson(json, ProfitFile::class.java) ?: ProfitFile()
        } catch (e: Exception) {
            AlpakaAddons.LOGGER.warn("Profit Tracker: could not read {}", dataFile, e)
            try {
                AtomicJsonFile.quarantine(dataFile)
            } catch (_: Exception) {
            }
            data = ProfitFile()
        }
    }

    fun total(profile: String, activity: String): ProfitBucket? = load().let { data.profiles[profile]?.activities?.get(activity) }

    fun session(profile: String, activity: String): ProfitBucket? = session[profile]?.get(activity)

    /** Both buckets of an activity, created on first use: all time first, then the session. */
    fun buckets(profile: String, activity: String): Pair<ProfitBucket, ProfitBucket> {
        load()
        val total = data.profiles.getOrPut(profile) { ProfitProfile() }.activities.getOrPut(activity) { ProfitBucket() }
        val current = session.getOrPut(profile) { LinkedHashMap() }.getOrPut(activity) { ProfitBucket() }
        return total to current
    }

    /** The activities of a profile that have anything in the scope asked for, most recent first is up to the caller. */
    fun activities(profile: String, sessionOnly: Boolean): List<String> {
        load()
        val source = if (sessionOnly) session[profile] else data.profiles[profile]?.activities
        return source?.filterValues { !it.isEmpty() }?.keys?.toList() ?: emptyList()
    }

    fun log(row: String) {
        synchronized(pendingLog) { pendingLog += row }
        dirty = true
    }

    fun markDirty() {
        dirty = true
    }

    /** Clears an activity, or every activity of the profile for null; the session, or all time as well. */
    fun reset(profile: String, activity: String?, allTime: Boolean) {
        load()
        if (activity == null) {
            session.remove(profile)
            if (allTime) data.profiles.remove(profile)
        } else {
            session[profile]?.remove(activity)
            if (allTime) data.profiles[profile]?.activities?.remove(activity)
        }
        dirty = true
        saveNow()
    }

    fun toggleHidden(profile: String, activity: String, key: String) {
        val (total, current) = buckets(profile, activity)
        val hidden = !(total.items[key]?.hidden ?: current.items[key]?.hidden ?: false)
        total.items[key]?.hidden = hidden
        current.items[key]?.hidden = hidden
        dirty = true
    }

    /** Called every tick: writes when something changed and the last write is a while ago. */
    fun tick(now: Long) {
        if (dirty && now - lastSaveMs >= SAVE_EVERY_MS) saveNow()
    }

    /** Writes now, in the background; [blocking] waits for it, for when the game is closing. */
    fun saveNow(blocking: Boolean = false) {
        if (!loaded || !dirty) return
        dirty = false
        lastSaveMs = System.currentTimeMillis()
        val json = GSON.toJson(data)
        val rows: List<String>
        synchronized(pendingLog) {
            rows = ArrayList(pendingLog)
            pendingLog.clear()
        }
        // Built here, on the game thread, where the prices and the data can't change underneath.
        val summary = summaryText()
        val task = Runnable {
            try {
                AtomicJsonFile.write(dataFile, json)
                if (rows.isNotEmpty()) appendLog(rows)
                AtomicJsonFile.write(summaryFile, summary)
            } catch (e: Exception) {
                AlpakaAddons.LOGGER.warn("Profit Tracker: could not save", e)
            }
        }
        if (blocking) {
            // After whatever is still queued, so the log keeps its order.
            writer.submit(task).get()
        } else {
            writer.execute(task)
        }
    }

    private fun appendLog(rows: List<String>) {
        val file = logFile
        val fresh = !file.exists() || file.length() == 0L
        val text = buildString {
            if (fresh) append(ProfitCsv.LOG_HEADER).append('\n')
            rows.forEach { append(it).append('\n') }
        }
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun summaryText(): String = buildString {
        append(ProfitCsv.SUMMARY_HEADER).append('\n')
        for ((profile, p) in data.profiles) {
            for ((activity, bucket) in p.activities) {
                if (bucket.isEmpty()) continue
                val title = ProfitActivity.of(activity).title
                ProfitCsv.summaryRows(profile, title, "all time", bucket, ProfitItems::price).forEach { append(it).append('\n') }
                val s = session[profile]?.get(activity)
                if (s != null && !s.isEmpty()) {
                    ProfitCsv.summaryRows(profile, title, "session", s, ProfitItems::price).forEach { append(it).append('\n') }
                }
            }
        }
    }

    /** A row for the log, in the local time zone. */
    fun logRow(now: Long, profile: String, activity: ProfitActivity, key: String, name: String, amount: Long, each: Double): String =
        ProfitCsv.logRow(now, ZoneId.systemDefault(), profile, activity.title, key, name, amount, each)
}
