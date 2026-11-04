package io.github.currencortex.music.core.update

fun interface ReleaseChecker {
    fun check(channel: UpdateChannel): AppRelease?

    /** Exact installed version, independent of the selected update channel and upgrade eligibility. */
    fun installedRelease(): AppRelease? = null
}
