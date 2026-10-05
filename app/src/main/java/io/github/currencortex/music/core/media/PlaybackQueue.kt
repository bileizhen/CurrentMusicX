package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

@Serializable enum class PlaybackMode(val label: String) { LIST("列表循环"), ONE("单曲循环"), SHUFFLE("随机播放"), HEART("心动模式") }
@Serializable data class QueueSnapshot(val songs: List<Song> = emptyList(), val index: Int = -1,
    val positionMs: Long = 0, val mode: PlaybackMode = PlaybackMode.LIST, val quality: AudioQuality = AudioQuality.AUTO,
    val shuffleHistory: List<Int> = emptyList(), val shuffleHistoryIndex: Int = -1,
    val heartPlaylistId: Long = 0) {
    val current get() = songs.getOrNull(index)
}
class PlaybackQueue {
    val state = MutableStateFlow(QueueSnapshot())
    private data class NextChoice(val songs: List<Song>, val index: Int, val history: List<Int>, val historyIndex: Int, val target: Int)
    private var plannedShuffle: NextChoice? = null
    private fun QueueSnapshot.hasHistory() = shuffleHistoryIndex in shuffleHistory.indices &&
        shuffleHistory[shuffleHistoryIndex] == index && shuffleHistory.all { it in songs.indices }
    private fun resetHistory(s: QueueSnapshot): QueueSnapshot {
        plannedShuffle = null
        return s.copy(shuffleHistory = emptyList(), shuffleHistoryIndex = -1)
    }
    private fun nextIndex(s: QueueSnapshot, automatic: Boolean, random: Random): Int = when {
        s.songs.isEmpty() -> -1
        automatic && s.mode == PlaybackMode.ONE -> s.index
        s.mode == PlaybackMode.SHUFFLE && s.songs.size > 1 -> {
            if (s.hasHistory() && s.shuffleHistoryIndex < s.shuffleHistory.lastIndex)
                s.shuffleHistory[s.shuffleHistoryIndex + 1]
            else plannedShuffle?.takeIf { it.songs == s.songs && it.index == s.index &&
                it.history == s.shuffleHistory && it.historyIndex == s.shuffleHistoryIndex }?.target
                ?: ((s.index + random.nextInt(1, s.songs.size)) % s.songs.size).also {
                    plannedShuffle = NextChoice(s.songs, s.index, s.shuffleHistory, s.shuffleHistoryIndex, it)
                }
        }
        else -> (s.index + 1) % s.songs.size
    }
    /** Planning must use the same shuffle choice as the eventual transport command. */
    fun previewNext(random: Random = Random.Default): Song? {
        val s = state.value
        return s.songs.getOrNull(nextIndex(s, automatic = true, random = random))?.takeIf { it.id != s.current?.id && !it.video }
    }
    fun replace(songs: List<Song>, index: Int) {
        state.value = resetHistory(state.value.copy(songs = songs.toList(), index = if (songs.isEmpty()) -1 else index.coerceIn(songs.indices), positionMs = 0,
            mode = if (state.value.mode == PlaybackMode.HEART) PlaybackMode.LIST else state.value.mode, heartPlaylistId = 0))
    }
    fun restore(value: QueueSnapshot) {
        plannedShuffle = null
        val restored = value.copy(index = if (value.songs.isEmpty()) -1 else value.index.coerceIn(value.songs.indices), positionMs = value.positionMs.coerceAtLeast(0))
        state.value = if (restored.mode == PlaybackMode.SHUFFLE && restored.hasHistory()) restored else resetHistory(restored)
    }
    fun next(automatic: Boolean = false, random: Random = Random.Default) {
        val s = state.value
        if (s.songs.isEmpty()) return
        val next = nextIndex(s, automatic, random)
        plannedShuffle = null
        state.value = if (s.mode == PlaybackMode.SHUFFLE && s.songs.size > 1) {
            val history = if (s.hasHistory()) s.shuffleHistory else listOf(s.index)
            val cursor = if (s.hasHistory()) s.shuffleHistoryIndex else 0
            if (cursor < history.lastIndex) s.copy(index = next, positionMs = 0, shuffleHistoryIndex = cursor + 1)
            else {
                val extended = (history + next).takeLast(1000)
                s.copy(index = next, positionMs = 0, shuffleHistory = extended, shuffleHistoryIndex = extended.lastIndex)
            }
        } else resetHistory(s.copy(index = next, positionMs = 0))
    }
    fun previous() {
        val s = state.value
        if (s.songs.isEmpty()) return
        plannedShuffle = null
        state.value = if (s.mode == PlaybackMode.SHUFFLE) {
            if (s.hasHistory()) {
                val cursor = (s.shuffleHistoryIndex - 1).coerceAtLeast(0)
                s.copy(index = s.shuffleHistory[cursor], positionMs = 0, shuffleHistoryIndex = cursor)
            } else s.copy(positionMs = 0, shuffleHistory = listOf(s.index), shuffleHistoryIndex = 0)
        } else resetHistory(s.copy(index = (s.index - 1 + s.songs.size) % s.songs.size, positionMs = 0))
    }
    fun select(index: Int) { if (index in state.value.songs.indices) state.value = resetHistory(state.value.copy(index = index, positionMs = 0)) }
    fun setMode(mode: PlaybackMode) {
        if (state.value.mode != mode) state.value = resetHistory(state.value.copy(mode = mode, heartPlaylistId = 0))
    }
    fun startHeart(songs: List<Song>, playlistId: Long, positionMs: Long) {
        val current = state.value.current ?: return
        require(playlistId > 0)
        state.value = resetHistory(state.value.copy(songs = (listOf(current) + songs).distinctBy { it.id },
            index = 0, positionMs = positionMs.coerceAtLeast(0), mode = PlaybackMode.HEART, heartPlaylistId = playlistId))
    }
    fun appendHeart(songs: List<Song>, playlistId: Long) {
        val s = state.value
        if (s.mode != PlaybackMode.HEART || s.heartPlaylistId != playlistId) return
        val existing = s.songs.map { it.id }.toSet()
        val additions = songs.distinctBy { it.id }.filter { it.id !in existing }
        if (additions.isNotEmpty()) state.value = s.copy(songs = s.songs + additions)
    }
    fun add(song: Song, next: Boolean = false) {
        val s = state.value
        val songs = s.songs.toMutableList().apply { add(if (next) (s.index + 1).coerceAtLeast(0) else size, song) }
        state.value = resetHistory(s.copy(songs = songs, index = if (s.index < 0) 0 else s.index))
    }
    fun remove(index: Int): Boolean {
        val s = state.value
        if (index !in s.songs.indices) return false
        val songs = s.songs.toMutableList().apply { removeAt(index) }
        val changed = index == s.index
        state.value = resetHistory(s.copy(songs = songs, index = when {
            songs.isEmpty() -> -1; index < s.index -> s.index - 1; else -> s.index.coerceAtMost(songs.lastIndex)
        }, positionMs = if (changed) 0 else s.positionMs))
        return changed
    }
    fun clear() { state.value = resetHistory(state.value.copy(songs = emptyList(), index = -1, positionMs = 0)) }
}
