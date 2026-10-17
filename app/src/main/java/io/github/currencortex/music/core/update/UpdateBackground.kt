package io.github.currencortex.music.core.update

import kotlinx.coroutines.flow.Flow

data class UpdateBackgroundEvent(val id: String, val release: AppRelease, val transfer: UpdateTransferState)

/** A durable task owns network work; UI collectors only observe it. */
interface UpdateBackground {
    val events: Flow<UpdateBackgroundEvent>
    fun start(release: AppRelease, source: UpdateSource): String
    fun cancel()
}

internal fun AppRelease.sameAsset(other: AppRelease) = version == other.version && apkUrl == other.apkUrl &&
    size == other.size && sha256 == other.sha256
