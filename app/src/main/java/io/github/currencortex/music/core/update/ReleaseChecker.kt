package io.github.currencortex.music.core.update

fun interface ReleaseChecker {
    fun check(channel: UpdateChannel): AppRelease?
}
