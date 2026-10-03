package io.github.currencortex.music.feature.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

data class ProfileState(val loading: Boolean = false, val profile: ProfileDto? = null, val error: String? = null,
    val sectionErrors: List<String> = emptyList())
class ProfileViewModel(val container: AppContainer, private val userId: Long? = null) : ViewModel() {
    val state = MutableStateFlow(ProfileState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val dialog = MutableStateFlow<String?>(null)
    val avatar = MutableStateFlow<Uri?>(null)
    private val refresh = MutableStateFlow(0)
    private fun session() = RequestSession(container.accountRepository.server, container.accountRepository.token)
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged(), container.profileRepository.revision, refresh)
                { id, server, revision, refresh -> listOf(id, server, revision, refresh) }
                .collectLatest { state.value = ProfileState(); load() }
        }
    }
    fun reload() { refresh.update { it + 1 } }
    private suspend fun load() = coroutineScope {
        if (userId == null && container.accountRepository.token == null) return@coroutineScope
        state.value = ProfileState(loading = true)
        launch { appResult { container.profileRepository.loadScales() } }
        when (val result = appResult {
            if (userId != null) container.profileRepository.profile(userId) else {
                val user = container.profileRepository.me()
                val lists = async { appResult { container.libraryRepository.playlists() } }
                val recent = async { appResult { container.libraryRepository.recent() } }
                val p = lists.await(); val r = recent.await()
                val errors = listOfNotNull(if (p is AppResult.Failure) "歌单：${p.kind.message}" else null,
                    if (r is AppResult.Failure) "最近播放：${r.kind.message}" else null)
                state.update { it.copy(sectionErrors = errors) }
                ProfileDto(user, user.stat,
                    recent = (r as? AppResult.Success)?.value.orEmpty().map { it.dto() },
                    playlists = (p as? AppResult.Success)?.value.orEmpty().map {
                        io.github.currencortex.music.data.library.PlaylistDto(it.id, it.name, it.description, it.cover,
                            it.source, it.ownerId, it.count)
                    })
            }
        }) {
            is AppResult.Success -> state.update { it.copy(loading = false, profile = result.value) }
            is AppResult.Failure -> state.update { it.copy(loading = false, error = result.kind.message) }
        }
    }
    private fun io.github.currencortex.music.data.song.Song.dto() = io.github.currencortex.music.data.song.SongDto(
        id, name, artists, album, cover, durationMs, mv = mv, artistIds = artistIds)
    fun action(success: String, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { when (val r = appResult { block() }) {
                is AppResult.Success -> message.value = success
                is AppResult.Failure -> message.value = r.kind.message
            } } finally { busy.value = false }
        }
    }
    private suspend fun applyUser(user: ProfileUser, expected: RequestSession) {
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        container.authRepository.updateAccount(container.profileRepository.account(user), expected)
        dialog.value = null; avatar.value = null; reload()
    }
    fun update(nickname: String, bio: String, visible: Boolean?) = action("资料已更新") {
        val expected = session(); applyUser(container.profileRepository.update(nickname, bio, visible, expected), expected)
    }
    fun upload(resolver: ContentResolver) = action("头像已更新") {
        val uri = avatar.value ?: return@action
        val expected = session()
        val bytes = withContext(Dispatchers.IO) { avatarBytes(resolver, uri) }
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        applyUser(container.profileRepository.avatar(bytes, expected), expected)
    }
    fun password(old: String, new: String) = action("密码已更新，请确认当前会话") {
        container.profileRepository.password(old, new); dialog.value = null
        container.authRepository.restore(container.accountRepository.server); reload()
    }
    companion object {
        fun avatarBytes(resolver: ContentResolver, uri: Uri): ByteArray {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth > 0 && options.outHeight > 0)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 1024) sample *= 2
            val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Invalid image")
            try { return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray().also { require(it.size <= 1024 * 1024) }
            } } finally { bitmap.recycle() }
        }
    }
}

data class DiscoverState(val query: String = "", val submitted: String = "", val sort: String = "reg",
    val listening: Boolean = false, val loading: Boolean = false, val users: List<SquareUser> = emptyList(),
    val total: Int = 0, val stats: SquareStats = SquareStats(), val error: String? = null)
class DiscoverViewModel(val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(DiscoverState())
    private var task: Job? = null
    init {
        viewModelScope.launch { container.ready.await(); submit() }
        viewModelScope.launch { container.musicSettings.state.map { it.server }.distinctUntilChanged().drop(1).collect {
            task?.cancel(); state.value = DiscoverState(); submit()
        } }
    }
    fun query(value: String) { state.update { it.copy(query = value) } }
    fun sort(value: String) { state.update { it.copy(sort = value) }; submit() }
    fun filter(value: Boolean) { state.update { it.copy(listening = value) }; submit() }
    fun submit(more: Boolean = false) {
        if (more && state.value.loading) return
        task?.cancel()
        val request = state.value.copy(submitted = if (more) state.value.submitted else state.value.query.trim())
        state.value = request.copy(loading = true, error = null)
        task = viewModelScope.launch {
            appResult { container.profileRepository.loadScales() }
            when (val r = appResult { container.profileRepository.square(request.submitted, request.sort,
                if (more) request.users.size else 0, request.listening) }) {
                is AppResult.Success -> state.update { it.copy(loading = false, users = if (more)
                    (request.users + r.value.users).distinctBy(SquareUser::id) else r.value.users, total = r.value.total, stats = r.value.stats) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = r.kind.message) }
            }
        }
    }
    suspend fun pollStats() {
        while (currentCoroutineContext().isActive) {
            delay(30_000)
            val before = state.value
            val server = container.accountRepository.server
            when (val r = appResult { container.profileRepository.square(before.submitted, before.sort, 0, before.listening) }) {
                is AppResult.Success -> if (server == container.accountRepository.server &&
                    before.submitted == state.value.submitted && before.listening == state.value.listening)
                    state.update { it.copy(stats = r.value.stats) }
                is AppResult.Failure -> Unit
            }
        }
    }
}

data class DecorationState(val loading: Boolean = false, val catalog: DecorationsDto? = null, val query: String = "",
    val preview: Decoration? = null, val error: String? = null)
class DecorationViewModel(val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(DecorationState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var task: Job? = null
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged()) { id, server -> id to server }
                .collect { state.value = DecorationState(); reload() }
        }
    }
    fun reload() {
        task?.cancel(); task = viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            when (val r = appResult { container.profileRepository.decorations() }) {
                is AppResult.Success -> state.update { it.copy(loading = false, catalog = r.value) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = r.kind.message) }
            }
        }
    }
    fun query(value: String) { state.update { it.copy(query = value) } }
    fun preview(item: Decoration?) { state.update { it.copy(preview = item) } }
    fun set(id: String) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { when (val r = appResult { container.profileRepository.decorate(id) }) {
                is AppResult.Success -> { message.value = if (id.isEmpty()) "已取消佩戴" else "挂件已设置"; state.update { it.copy(preview = null) }; reload() }
                is AppResult.Failure -> message.value = r.kind.message
            } } finally { busy.value = false }
        }
    }
    override fun onCleared() { task?.cancel() }
}
