package io.github.currencortex.music.core.update

/** SemVer subset used by GitHub release tags: 1.2.3[-alpha.N|-beta.N|-rc.N]. */
internal data class Version(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val stage: Int,
    val stageNumber: Long,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        listOf(major to other.major, minor to other.minor, patch to other.patch).forEach { (a, b) ->
            if (a != b) return a.compareTo(b)
        }
        if (stage != other.stage) return stage.compareTo(other.stage)
        return stageNumber.compareTo(other.stageNumber)
    }

    companion object {
        private const val ALPHA = 0
        private const val BETA = 1
        private const val RC = 2
        private const val FINAL = 3
        private val regex = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-(alpha|beta|rc)\\.(0|[1-9][0-9]*))?")

        fun parse(raw: String): Version? {
            val match = regex.matchEntire(raw.trim()) ?: return null
            val stage = when (match.groupValues[4]) {
                "alpha" -> ALPHA
                "beta" -> BETA
                "rc" -> RC
                else -> FINAL
            }
            return Version(
                major = match.groupValues[1].toLongOrNull() ?: return null,
                minor = match.groupValues[2].toLongOrNull() ?: return null,
                patch = match.groupValues[3].toLongOrNull() ?: return null,
                stage = stage,
                stageNumber = if (match.groupValues[5].isEmpty()) 0L
                    else match.groupValues[5].toLongOrNull() ?: return null,
            )
        }
    }
}
