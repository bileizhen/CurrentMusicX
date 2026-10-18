package io.github.currencortex.music.feature.player

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlayerAiTurn(val question: String, val answer: String = "", val cards: List<PostAttachment> = emptyList(), val complete: Boolean = false)
data class PlayerAiState(val song: PostAttachment? = null, val turns: List<PlayerAiTurn> = emptyList(),
    val draft: String = "", val busy: Boolean = false, val error: String? = null)

class PlayerAiController(private val repository: PlayerAiRepository, private val scope: CoroutineScope,
    private val session: () -> RequestSession) {
    val state = MutableStateFlow(PlayerAiState())
    private var job: Job? = null
    private var epoch = 0L
    private var owner: RequestSession? = null
    private var reset = true
    fun close() { epoch++; job?.cancel(); owner = null; state.value = PlayerAiState(); reset = true }
    fun open(song: Song) {
        close()
        val id = NeteaseSongActionsRepository.songId(song) ?: return
        owner = session()
        state.value = PlayerAiState(song = PostAttachment("song", id, song.name, song.artists, song.cover))
    }
    fun draft(text: String) { state.update { it.copy(draft = text.take(500)) } }
    fun ask() {
        val value = state.value; val song = value.song ?: return
        if (value.busy || value.draft.isBlank()) return
        val expected = owner ?: return
        if (expected != session() || expected.token.isNullOrBlank()) {
            state.update { it.copy(error = "登录 CurrentMusic 后可向 AI 提问") }; return
        }
        val generation = epoch
        state.value = value.copy(turns = value.turns.takeLast(5) + PlayerAiTurn(value.draft), draft = "", busy = true, error = null)
        job = scope.launch {
            try {
                repository.ask(value.draft, song, reset, expected).collect { event ->
                    if (generation != epoch || expected != session()) return@collect
                    if (event.type == "delta" || event.type == "done") state.update { latest -> latest.copy(turns = latest.turns.dropLast(1) +
                        latest.turns.last().copy(answer = event.text, cards = event.cards, complete = event.type == "done")) }
                    if (event.type == "done") reset = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == epoch && expected == session()) state.update { it.copy(
                error = if (failure is ApiException) failure.kind.message else failure.message ?: "AI 暂时不可用",
                draft = it.draft.ifBlank { value.draft }) } }
            finally { if (generation == epoch) state.update { it.copy(busy = false) } }
        }
    }
}
