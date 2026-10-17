package net.alpaka.addons.utils

/**
 * A version as the builds and release tags write it, ordered the way semantic versioning orders
 * them: `1.3.0 < 2.0.0-alpha < 2.0.0-beta.1 < 2.0.0-beta.2 < 2.0.0-rc.1 < 2.0.0`.
 *
 * The build part after a `+` (`+134.0de331d`) names the commit and plays no part in the order, so a
 * test build counts as the version it is a build of.
 */
class ReleaseVersion private constructor(
    private val numbers: List<Int>,
    /** What follows the `-`, split at the dots: `beta.1` as ["beta", "1"]. Empty on a full release. */
    private val preRelease: List<String>,
) : Comparable<ReleaseVersion> {

    /** Whether this is a version ahead of its release - an alpha, a beta - rather than the release. */
    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: ReleaseVersion): Int {
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val diff = numbers.getOrElse(i) { 0 } - other.numbers.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        // The release itself comes after everything leading up to it.
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return other.preRelease.size.coerceAtMost(1) - preRelease.size.coerceAtMost(1)
        }
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val diff = compareIdentifier(preRelease[i], other.preRelease[i])
            if (diff != 0) return diff
        }
        return preRelease.size - other.preRelease.size
    }

    companion object {
        /** "2.0.0-beta.1+3.a1b2c3d" read as 2.0.0 beta 1; null for anything not shaped like a version. */
        @JvmStatic
        fun parse(version: String): ReleaseVersion? {
            val withoutBuild = version.removePrefix("v").substringBefore('+')
            val numbers = withoutBuild.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }
            val preRelease = if ('-' in withoutBuild) withoutBuild.substringAfter('-').split('.') else emptyList()
            if (preRelease.any { it.isEmpty() }) return null
            return ReleaseVersion(numbers, preRelease)
        }

        /** Numbers by value, below any word; words alphabetically, so alpha < beta < rc. */
        private fun compareIdentifier(a: String, b: String): Int {
            val an = a.toIntOrNull()
            val bn = b.toIntOrNull()
            return when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
