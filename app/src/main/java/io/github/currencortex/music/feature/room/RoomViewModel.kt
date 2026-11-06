package io.github.currencortex.music.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RoomBrowserState(val rooms: List<RoomCard> = emptyList(), val loading: Boolean = false,
    val more: Boolean = false, val error: String? = null, val query: String = "", val scope: RoomScope = RoomScope.PUBLIC)
data class RoomBansState(val bans: List<RoomBan> = emptyList(), val loading: Boolean = false, val error: String? = null)
class RoomViewModel(val container: AppContainer) : ViewModel() {
    val browser = MutableStateFlow(RoomBrowserState())
    val session = container.roomSession
    val busy = MutableStateFlow(false)
    val passwordRoom = MutableStateFlow<RoomInfo?>(null)
    val bans = MutableStateFlow(RoomBansState())
    private var query = ""
    private var loading: Job? = null
    private var writing: Job? = null
    private var bansJob: Job? = null
    init { viewModelScope.launch {
        container.sessionRestored.await()
        combine(container.accountRepository.sessionRevision, container.musicSettings.state.map { it.server }.distinctUntilChanged()) { revision, server -> revision to server }
            .collect { loading?.cancel(); writing?.cancel(); bansJob?.cancel(); bans.value = RoomBansState(); busy.value = false; passwordRoom.value = null; browser.value = RoomBrowserState(); load() }
    } }
    init { viewModelScope.launch {
        session.state.map { it.detail?.room?.id to it.detail?.role(container.accountRepository.state.value.account?.id ?: 0) }
            .distinctUntilChanged().collect { bansJob?.cancel(); bans.value = RoomBansState() }
    } }
    fun load(value: String = query, more: Boolean = false, roomScope: RoomScope = browser.value.scope) {
        if (container.accountRepository.state.value.account == null) { browser.value = RoomBrowserState(error = "登录后可创建或加入听歌房"); return }
        if (more && (browser.value.loading || !browser.value.more)) return
        val nextQuery = value.trim()
        val previous = browser.value
        val retained = if (nextQuery == previous.query && roomScope == previous.scope) previous.rooms else emptyList()
        val offset = if (more) retained.size else 0
        loading?.cancel()
        loading = viewModelScope.launch {
            query = nextQuery
            browser.value = previous.copy(rooms = retained, loading = true, query = query, error = null, scope = roomScope)
            when (val result = appResult { container.roomRepository.list(query, offset, roomScope) }) {
                is AppResult.Success -> {
                    val rooms = ((if (more) retained else emptyList()) + result.value.rooms).distinctBy { it.id }
                    val hasMore = if (result.value.total > 0) rooms.size < result.value.total else result.value.rooms.size == 20
                    browser.value = RoomBrowserState(rooms, more = hasMore && result.value.rooms.isNotEmpty(), query = query, scope = roomScope)
                }
                is AppResult.Failure -> browser.value = previous.copy(rooms = retained, loading = false, error = result.kind.message, query = query, scope = roomScope)
            }
        }
    }
    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        browser.update { it.copy(error = null) }
        writing = viewModelScope.launch {
            try { when (val result = appResult(block)) {
                is AppResult.Failure -> browser.update { it.copy(error = result.kind.message) }; else -> Unit }
            } finally { busy.value = false }
        }
    }
    fun search(value: String) { if (browser.value.scope == RoomScope.PUBLIC && value.matches(Regex("\\d{6}"))) find(value) else load(value) }
    fun find(code: String) = run {
        val expected = container.roomRepository.session()
        val room = container.roomRepository.find(code)
        if (expected != container.roomRepository.session()) throw ApiException(ErrorKind.Unauthorized)
        if (room.hasPassword) passwordRoom.value = room else session.join(room, expected = expected)
    }
    fun join(room: RoomInfo, password: String) = run { session.join(room, password); passwordRoom.value = null }
    fun create(name: String, password: String, public: Boolean, free: Boolean, needApproval: Boolean = !free, joinLocked: Boolean = false) = run {
        session.requireCanJoin()
        val expected = container.roomRepository.session()
        val room = container.roomRepository.create(name, password, public, free, expected, needApproval, joinLocked)
        session.join(room, created = true, expected = expected)
    }
    fun leave() = run { session.leave(); load() }
    fun enterOwned(room: RoomCard) = run {
        session.requireCanJoin()
        val expected = container.roomRepository.session()
        val info = if (room.archived) container.roomRepository.reopen(room.id, expected)
            else container.roomRepository.detail(room.id, expected).room
        session.join(info, created = room.archived, expected = expected)
    }
    fun deleteOwned(room: RoomCard, expected: RequestSession) = run {
        check(browser.value.scope == RoomScope.MINE && !session.active)
        if (expected != container.roomRepository.session()) throw ApiException(ErrorKind.Unauthorized)
        container.roomRepository.delete(room.id, expected)
        if (expected == container.roomRepository.session()) load()
    }
    fun loadBans() = manageBans(null)
    fun unban(userId: Long) = manageBans(userId)
    private fun manageBans(userId: Long?) {
        if (bans.value.loading) return
        val detail = session.state.value.detail ?: return
        val expected = container.roomRepository.session()
        fun valid() = expected == container.roomRepository.session() &&
            session.state.value.detail?.room?.id == detail.room.id &&
            session.state.value.detail?.role(container.accountRepository.state.value.account?.id ?: 0) == RoomRole.OWNER
        if (!valid()) return
        bans.value = RoomBansState(loading = true)
        bansJob = viewModelScope.launch {
            val result = appResult {
                if (userId != null) container.roomRepository.unban(detail.room.id, userId, expected)
                container.roomRepository.bans(detail.room.id, expected)
            }
            if (!valid()) { bans.value = RoomBansState(); return@launch }
            bans.value = when (result) {
                is AppResult.Success -> RoomBansState(result.value)
                is AppResult.Failure -> RoomBansState(error = result.kind.message)
            }
        }
    }
}
