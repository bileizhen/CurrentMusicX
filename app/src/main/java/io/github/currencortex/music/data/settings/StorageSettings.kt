package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.currencortex.music.core.media.AudioCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class AudioCacheLimit(val storedBytes: Long, val label: String) {
    GB_1(1024L * 1024 * 1024, "1 GB"),
    GB_2(2L * 1024 * 1024 * 1024, "2 GB"),
    GB_5(5L * 1024 * 1024 * 1024, "5 GB"),
    GB_10(10L * 1024 * 1024 * 1024, "10 GB"),
    GB_20(20L * 1024 * 1024 * 1024, "20 GB"),
    UNLIMITED(0L, "不限制");

    val effectiveBytes: Long get() = if (storedBytes == 0L) AudioCache.UNLIMITED_BYTES else storedBytes

    companion object {
        val DEFAULT = GB_1
        fun fromStoredBytes(value: Long) = entries.firstOrNull { it.storedBytes == value } ?: DEFAULT
    }
}

class StorageSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val cacheLimitBytes = longPreferencesKey("storage.audioCacheLimitBytes")

    val limitBytes = store.data.map {
        AudioCacheLimit.fromStoredBytes(it[cacheLimitBytes] ?: AudioCacheLimit.DEFAULT.storedBytes).effectiveBytes
    }.catch { emit(AudioCacheLimit.DEFAULT.effectiveBytes) }
        .distinctUntilChanged()
    val state: StateFlow<AudioCacheLimit?> = limitBytes.map { effectiveLimit(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun set(value: AudioCacheLimit) {
        store.edit { it[cacheLimitBytes] = value.storedBytes }
    }

    private fun effectiveLimit(value: Long) =
        AudioCacheLimit.entries.firstOrNull { it.effectiveBytes == value } ?: AudioCacheLimit.DEFAULT
}
