package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

@Serializable enum class PlaybackMode(val label: String) { LIST("列表循环"), ONE("单曲循环"), SHUFFLE("随机播放") }
@Serializable data class QueueSnapshot(val songs: List<Song> = emptyList(), val index: Int = -1,
    val positionMs: Long = 0, val mode: PlaybackMode = PlaybackMode.LIST, val quality: AudioQuality = AudioQuality.AUTO) {
    val current get() = songs.getOrNull(index)
}
class PlaybackQueue {
    val state = MutableStateFlow(QueueSnapshot())
    fun replace(songs: List<Song>, index: Int) {
        state.value = state.value.copy(songs = songs.toList(), index = if (songs.isEmpty()) -1 else index.coerceIn(songs.indices), positionMs = 0)
    }
    fun restore(value: QueueSnapshot) {
        state.value = value.copy(index = if (value.songs.isEmpty()) -1 else value.index.coerceIn(value.songs.indices), positionMs = value.positionMs.coerceAtLeast(0))
    }
    fun next(automatic: Boolean = false, random: Random = Random.Default) {
        val s = state.value
        if (s.songs.isEmpty()) return
        val next = when {
            automatic && s.mode == PlaybackMode.ONE -> s.index
            s.mode == PlaybackMode.SHUFFLE && s.songs.size > 1 -> (s.index + random.nextInt(1, s.songs.size)) % s.songs.size
            else -> (s.index + 1) % s.songs.size
        }
        state.value = s.copy(index = next, positionMs = 0)
    }
    fun previous() { val s = state.value; if (s.songs.isNotEmpty()) state.value = s.copy(index = (s.index - 1 + s.songs.size) % s.songs.size, positionMs = 0) }
    fun select(index: Int) { if (index in state.value.songs.indices) state.value = state.value.copy(index = index, positionMs = 0) }
    fun add(song: Song, next: Boolean = false) {
        val s = state.value
        val songs = s.songs.toMutableList().apply { add(if (next) (s.index + 1).coerceAtLeast(0) else size, song) }
        state.value = s.copy(songs = songs, index = if (s.index < 0) 0 else s.index)
    }
    fun remove(index: Int): Boolean {
        val s = state.value
        if (index !in s.songs.indices) return false
        val songs = s.songs.toMutableList().apply { removeAt(index) }
        val changed = index == s.index
        state.value = s.copy(songs = songs, index = when {
            songs.isEmpty() -> -1; index < s.index -> s.index - 1; else -> s.index.coerceAtMost(songs.lastIndex)
        }, positionMs = if (changed) 0 else s.positionMs)
        return changed
    }
    fun clear() { state.value = state.value.copy(songs = emptyList(), index = -1, positionMs = 0) }
}
