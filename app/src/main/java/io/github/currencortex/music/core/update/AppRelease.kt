package io.github.currencortex.music.core.update

data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val assetName: String?,
    val prerelease: Boolean,
    val size: Long = 0L,
    val sha256: String = "",
)

enum class UpdateChannel { STABLE, PRERELEASE }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Failed(val reason: String) : UpdateState
}
