package net.alpaka.addons.features.profittracker

/**
 * What one activity earned: items by their price key, coins (costs are negative coins), how many
 * times the activity's own action happened (a boss slain, a sea creature caught, a burrow dug) and
 * how long it was played.
 *
 * Plain fields only, so Gson writes and reads it as it is.
 */
class ProfitBucket {
    /** Items by price key (see [ProfitItems.key]), in the order they first dropped. */
    var items: LinkedHashMap<String, ProfitItem> = LinkedHashMap()
    var coins: Long = 0L
    /** What the activity cost, in coins, as a positive number: slayer quests, summoning eyes. */
    var costs: Long = 0L
    var actions: Long = 0L
    /** Time spent in the activity, in milliseconds; breaks longer than a few minutes are left out. */
    var activeMs: Long = 0L
    /** When anything was first counted, epoch ms; 0 for an empty bucket. */
    var startedAt: Long = 0L

    fun isEmpty(): Boolean = items.isEmpty() && coins == 0L && costs == 0L && actions == 0L

    fun addItem(key: String, name: String, amount: Long, now: Long) {
        if (amount == 0L) return
        val item = items.getOrPut(key) { ProfitItem(name, 0L) }
        // A name read off the real item beats a placeholder taken from the key.
        if (item.name == key && name != key) item.name = name
        item.amount += amount
        if (item.amount <= 0L) items.remove(key)
        touch(now)
    }

    fun addCoins(amount: Long, now: Long) {
        if (amount == 0L) return
        coins += amount
        touch(now)
    }

    fun addCost(amount: Long, now: Long) {
        if (amount == 0L) return
        costs += amount
        touch(now)
    }

    fun addAction(now: Long) {
        actions++
        touch(now)
    }

    private fun touch(now: Long) {
        if (startedAt == 0L) startedAt = now
    }

    fun copy(): ProfitBucket = ProfitBucket().also { c ->
        items.forEach { (k, v) -> c.items[k] = ProfitItem(v.name, v.amount, v.hidden) }
        c.coins = coins
        c.costs = costs
        c.actions = actions
        c.activeMs = activeMs
        c.startedAt = startedAt
    }
}

class ProfitItem(var name: String, var amount: Long, var hidden: Boolean = false)

/** Every activity of one SkyBlock profile, by [ProfitActivity.id]. */
class ProfitProfile {
    var activities: LinkedHashMap<String, ProfitBucket> = LinkedHashMap()
}

/** The file: every profile's all-time figures. Sessions live in memory only. */
class ProfitFile {
    var version: Int = 1
    var profiles: LinkedHashMap<String, ProfitProfile> = LinkedHashMap()
}
