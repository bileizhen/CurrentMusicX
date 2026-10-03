package io.github.currencortex.music.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RoomBrowserState(val rooms: List<RoomCard> = emptyList(), val loading: Boolean = false,
    val more: Boolean = false, val error: String? = null)
class RoomViewModel(val container: AppContainer) : ViewModel() {
    val browser = MutableStateFlow(RoomBrowserState())
    val session = container.roomSession
    val busy = MutableStateFlow(false)
    val passwordRoom = MutableStateFlow<RoomInfo?>(null)
    private var query = ""
    private var loading: Job? = null
    private var writing: Job? = null
    init { viewModelScope.launch {
        container.sessionRestored.await()
        container.accountRepository.sessionRevision.collect { loading?.cancel(); writing?.cancel(); passwordRoom.value = null; browser.value = RoomBrowserState(); load() }
    } }
    fun load(value: String = query, more: Boolean = false) {
        if (container.accountRepository.state.value.account == null) { browser.value = RoomBrowserState(error = "登录后可创建或加入听歌房"); return }
        loading?.cancel()
        loading = viewModelScope.launch {
            query = value.trim(); val old = if (more) browser.value.rooms else emptyList()
            browser.value = RoomBrowserState(old, loading = true)
            when (val result = appResult { container.roomRepository.list(query, old.size) }) {
                is AppResult.Success -> browser.value = RoomBrowserState((old + result.value.rooms).distinctBy { it.id }, more = result.value.rooms.size == 20)
                is AppResult.Failure -> browser.value = RoomBrowserState(old, error = result.kind.message)
            }
        }
    }
    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        writing = viewModelScope.launch { busy.value = true
            try { when (val result = appResult(block)) {
                is AppResult.Failure -> browser.update { it.copy(error = result.kind.message) }; else -> Unit }
            } finally { busy.value = false }
        }
    }
    fun search(value: String) { if (value.matches(Regex("\\d{6}"))) find(value) else load(value) }
    fun find(code: String) = run {
        val expected = container.roomRepository.session()
        val room = container.roomRepository.find(code)
        if (expected != container.roomRepository.session()) throw ApiException(ErrorKind.Unauthorized)
        if (room.hasPassword) passwordRoom.value = room else session.join(room, expected = expected)
    }
    fun join(room: RoomInfo, password: String) = run { session.join(room, password); passwordRoom.value = null }
    fun create(name: String, password: String, public: Boolean, free: Boolean) = run {
        val expected = container.roomRepository.session()
        val room = container.roomRepository.create(name, password, public, free, expected)
        session.join(room, created = true, expected = expected)
    }
    fun leave() = run { session.leave(); load() }
}
