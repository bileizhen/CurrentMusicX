package io.github.currencortex.music.core.update

@kotlinx.serialization.Serializable data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val assetName: String?,
    val prerelease: Boolean,
    val size: Long = 0L,
    val sha256: String = "",
) {
    val isCi: Boolean get() = Regex("[0-9]+\\.[0-9]+\\.[0-9]+-ci\\.[1-9][0-9]*").matches(version)
}

enum class UpdateChannel { STABLE, CI;
    companion object {
        fun fromStored(value: String?): UpdateChannel = when (value) {
            "CI", "PRERELEASE" -> CI
            else -> STABLE
        }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Failed(val reason: String) : UpdateState
}
