package net.alpaka.addons.features.profittracker

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The tracker's two CSV files, written in plain RFC 4180 with a comma and a dot for decimals, so a
 * spreadsheet in any language reads them the same (and "1,234" never becomes 1.234).
 *
 *  - The log gets one row per gain or cost as it happens and is only ever appended to: the full
 *    history, also of what a reset clears.
 *  - The summary is rewritten on every save: per profile, activity and scope (all time, session)
 *    one row per item and a total row, at the prices of the moment.
 */
object ProfitCsv {

    const val LOG_HEADER = "time,profile,activity,item_id,item,amount,price_each,value"
    const val SUMMARY_HEADER = "profile,activity,scope,item_id,item,amount,price_each,value,actions,hours,profit_per_hour"

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /** One gain or cost, as a row of the log. [priceEach] is NaN for an item without a price. */
    fun logRow(timeMs: Long, zone: ZoneId, profile: String, activity: String, itemId: String, item: String,
               amount: Long, priceEach: Double): String {
        val value = if (priceEach.isNaN()) Double.NaN else priceEach * amount
        return listOf(
            TIME.format(Instant.ofEpochMilli(timeMs).atZone(zone)),
            profile, activity, itemId, item, amount.toString(), number(priceEach), number(value)
        ).joinToString(",") { cell(it) }
    }

    /** Rows for one bucket of the summary: every item, then a COINS row, then a TOTAL row. */
    fun summaryRows(profile: String, activity: String, scope: String, bucket: ProfitBucket,
                    price: (String) -> Double): List<String> {
        val rows = ArrayList<String>()
        var total = 0.0
        for ((key, item) in bucket.items) {
            val each = price(key)
            val value = if (each.isNaN()) Double.NaN else each * item.amount
            if (!value.isNaN() && !item.hidden) total += value
            rows += row(profile, activity, scope, key, item.name, item.amount.toString(), number(each), number(value), "", "", "")
        }
        if (bucket.coins != 0L) {
            total += bucket.coins
            rows += row(profile, activity, scope, ProfitItems.COINS, "Coins", bucket.coins.toString(), "1", bucket.coins.toString(), "", "", "")
        }
        if (bucket.costs != 0L) {
            total -= bucket.costs
            rows += row(profile, activity, scope, "COSTS", "Costs", "", "", (-bucket.costs).toString(), "", "", "")
        }
        val hours = bucket.activeMs / 3_600_000.0
        val perHour = if (hours > 0.0) total / hours else Double.NaN
        rows += row(profile, activity, scope, "TOTAL", "Total Profit", "", "", number(total),
            bucket.actions.toString(), number(hours), number(perHour))
        return rows
    }

    private fun row(vararg cells: String): String = cells.joinToString(",") { cell(it) }

    /** Whole coins where the value is whole, two decimals otherwise; empty for no price. */
    fun number(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> ""
        value == Math.rint(value) && kotlin.math.abs(value) < 1e15 -> value.toLong().toString()
        else -> String.format(Locale.ROOT, "%.2f", value)
    }

    /** A cell, quoted when it holds a comma, quote or line break. */
    fun cell(text: String): String {
        if (text.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }
}
