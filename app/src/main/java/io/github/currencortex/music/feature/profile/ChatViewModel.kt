package io.github.currencortex.music.feature.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class ChatState(val messages: List<ChatMessage> = emptyList(), val members: List<ProfileUser> = emptyList(),
    val loaded: Boolean = false, val loading: Boolean = false, val loadingOlder: Boolean = false,
    val hasOlder: Boolean = false, val connected: Boolean = false, val seq: Long = 0,
    val draft: String = "", val image: String = "", val sending: Boolean = false, val uploading: Boolean = false,
    val error: String? = null, val actionError: String? = null, val deleting: Long? = null,
    val scrollRequest: Long = 0, val attachments: List<PostAttachment> = emptyList(),
    val suggestions: List<ProfileUser> = emptyList(), val references: List<PostAttachment> = emptyList(), val referenceBusy: Boolean = false)

class ChatViewModel(val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(ChatState())
    private fun session() = container.audioSession()
    val repository = ChatRepository(container.apiClient, ::session)
    private val events = ChatEventClient()
    private var identity: RequestSession? = null
    private var action: Job? = null
    private var upload: Job? = null
    private var older: Job? = null
    private var memberSearch: Job? = null
    private var referenceSearch: Job? = null
    private val refresh = MutableStateFlow(0)
    init { viewModelScope.launch {
        container.sessionRestored.await()
        combine(container.accountRepository.sessionRevision, container.musicSettings.state.map { it.server }.distinctUntilChanged())
            { _, _ -> session() }.distinctUntilChanged().collect { resetFor(it) }
    } }
    private fun resetFor(expected: RequestSession) {
        if (identity == expected) return
        identity = expected; action?.cancel(); upload?.cancel(); older?.cancel(); memberSearch?.cancel(); referenceSearch?.cancel()
        state.value = ChatState()
    }
    private fun current(expected: RequestSession) = expected == session() && identity == expected

    /** Screen lifecycle owns this collection. Reconnect replays the last acknowledged sequence. */
    suspend fun observe() {
        container.sessionRestored.await()
        try {
            combine(container.accountRepository.sessionRevision,
                container.musicSettings.state.map { it.server }.distinctUntilChanged(), refresh) { _, _, _ -> session() }
                .collectLatest { expected ->
                    resetFor(expected)
                    try {
                        if (!state.value.loaded) {
                            state.update { it.copy(loading = true, error = null) }
                            val checkpoint = repository.live(0, expected).latestSeq
                            val page = repository.messages(expected = expected)
                            val members = appResult { repository.members(expected) }
                            if (!current(expected)) return@collectLatest
                            state.update { it.copy(messages = page.distinctBy(ChatMessage::id).sortedBy(ChatMessage::id),
                                members = (members as? AppResult.Success)?.value.orEmpty(), loaded = true, loading = false,
                                hasOlder = page.size >= 30, seq = checkpoint) }
                        }
                        var backoff = 1_000L
                        while (currentCoroutineContext().isActive && current(expected)) {
                            try {
                                val replay = repository.live(state.value.seq, expected)
                                replay.events.sortedBy { it.seq }.forEach { accept(it, expected) }
                                if (replay.events.isEmpty() && replay.latestSeq > state.value.seq) {
                                    // A retention gap needs a fresh snapshot, not a silent cursor jump.
                                    val page = repository.messages(expected = expected)
                                    if (current(expected)) state.update { it.copy(messages = page.distinctBy(ChatMessage::id)
                                        .sortedBy(ChatMessage::id), hasOlder = page.size >= 30, seq = replay.latestSeq) }
                                }
                                events.events(expected, state.value.seq, onOpen = {
                                    if (current(expected)) state.update { it.copy(connected = true, error = null) }
                                }).collect { event -> accept(event, expected); backoff = 1_000 }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                if (!current(expected)) break
                                state.update { it.copy(connected = false, error = "连接中断，正在重连") }
                                // Polling also covers servers/proxies that cannot keep an SSE stream open.
                                delay(backoff); backoff = (backoff * 2).coerceAtMost(15_000)
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (current(expected)) state.update { it.copy(loading = false, error = "聊天记录加载失败，请重试") }
                    } finally {
                        if (current(expected)) state.update { it.copy(connected = false) }
                    }
                }
        } finally { state.update { it.copy(connected = false) } }
    }
    private fun accept(event: ChatLiveEvent, expected: RequestSession) {
        if (!current(expected) || event.seq <= state.value.seq) return
        val messages = applyChatEvent(state.value.messages, event)
        state.update { it.copy(messages = messages, seq = event.seq, error = null) }
    }
    fun retry() { if (!state.value.loaded) state.update { it.copy(error = null) }; refresh.update { it + 1 } }
    fun loadOlder() {
        if (!state.value.hasOlder || state.value.loadingOlder) return
        val before = state.value.messages.minOfOrNull(ChatMessage::id) ?: return
        val expected = session()
        state.update { it.copy(loadingOlder = true, actionError = null) }
        older = viewModelScope.launch {
            when (val result = appResult { repository.messages(before, expected) }) {
                is AppResult.Success -> if (current(expected)) state.update { it.copy(loadingOlder = false,
                    messages = (result.value + it.messages).distinctBy(ChatMessage::id).sortedBy(ChatMessage::id), hasOlder = result.value.size >= 30) }
                is AppResult.Failure -> if (current(expected)) state.update { it.copy(loadingOlder = false, actionError = result.kind.message) }
            }
        }
    }
    fun draft(value: String) {
        state.update { it.copy(draft = value.take(500), actionError = null) }
        memberSearch?.cancel()
        val fragment = value.substringAfterLast('@', "").takeIf { '@' in value && it.length <= 40 && it.none(Char::isWhitespace) }
        if (fragment == null) { state.update { it.copy(suggestions = emptyList()) }; return }
        val expected = session()
        memberSearch = viewModelScope.launch {
            delay(250)
            val result = appResult { repository.members(expected, fragment) }
            if (current(expected) && result is AppResult.Success) state.update { it.copy(suggestions = result.value) }
        }
    }
    fun attachCurrent() {
        val song = container.playbackQueue.state.value.current ?: return
        val id = io.github.currencortex.music.data.song.NeteaseSongActionsRepository.songId(song) ?: return
        attach(PostAttachment("song", id, song.name, song.artists, song.cover))
    }
    fun searchReferences(query: String, type: String) {
        referenceSearch?.cancel()
        state.update { it.copy(references = emptyList(), referenceBusy = query.isNotBlank()) }
        if (query.isBlank()) return
        val expected = session()
        referenceSearch = viewModelScope.launch {
            delay(250)
            val result = appResult { repository.references(query, type, expected) }
            if (current(expected)) state.update { it.copy(referenceBusy = false, references = (result as? AppResult.Success)?.value.orEmpty(),
                actionError = (result as? AppResult.Failure)?.kind?.message) }
        }
    }
    fun attach(card: PostAttachment) { state.update { it.copy(attachments = validMusicAttachments(it.attachments + card)) } }
    fun removeAttachment(card: PostAttachment) { if (!state.value.sending) state.update { it.copy(attachments = it.attachments - card) } }
    fun mention(user: ProfileUser) {
        if (user.nickname.isBlank()) return
        state.update {
            val prefix = it.draft.replace(Regex("@[^\\s@]*$"), "")
            it.copy(draft = (prefix + (if (prefix.isNotEmpty() && !prefix.last().isWhitespace()) " " else "") + "@${user.nickname} ").take(500),
                suggestions = emptyList(), members = (it.members + user).distinctBy(ProfileUser::id))
        }
    }
    fun removeImage() { if (!state.value.sending) state.update { it.copy(image = "") } }
    fun upload(resolver: ContentResolver, uri: Uri) {
        if (state.value.uploading || state.value.sending) return
        val expected = session()
        if (expected.token == null) { state.update { it.copy(actionError = "登录 CurrentMusic 后可发送图片") }; return }
        state.update { it.copy(uploading = true, actionError = null) }
        upload = viewModelScope.launch {
            val result = appResult { repository.upload(withContext(Dispatchers.IO) { ProfileViewModel.avatarBytes(resolver, uri) }, expected) }
            if (!current(expected)) return@launch
            state.update { it.copy(uploading = false, image = (result as? AppResult.Success)?.value ?: it.image,
                actionError = (result as? AppResult.Failure)?.kind?.message) }
        }
    }
    fun send() {
        val value = state.value
        if (value.sending || value.uploading || (value.draft.isBlank() && value.image.isBlank() && value.attachments.isEmpty())) return
        val expected = session()
        if (expected.token == null) { state.update { it.copy(actionError = "登录 CurrentMusic 后即可聊天") }; return }
        val mentions = chatMentions(value.draft, value.members)
        state.update { it.copy(sending = true, actionError = null) }
        action = viewModelScope.launch {
            when (val result = appResult { repository.send(value.draft, value.image, mentions, expected, value.attachments) }) {
                is AppResult.Success -> if (current(expected)) state.update { it.copy(sending = false,
                    messages = (it.messages + result.value).distinctBy(ChatMessage::id).sortedBy(ChatMessage::id),
                    draft = if (it.draft == value.draft) "" else it.draft, image = if (it.image == value.image) "" else it.image,
                    attachments = if (it.attachments == value.attachments) emptyList() else it.attachments,
                    scrollRequest = it.scrollRequest + 1) }
                is AppResult.Failure -> if (current(expected)) state.update { it.copy(sending = false,
                    actionError = "${result.kind.message}，草稿已保留，请确认后重试") }
            }
        }
    }
    fun delete(message: ChatMessage) {
        if (state.value.deleting != null || message.userId != container.accountRepository.state.value.account?.id) return
        val expected = session()
        state.update { it.copy(deleting = message.id, actionError = null) }
        viewModelScope.launch {
            when (val result = appResult { repository.delete(message.id, expected) }) {
                is AppResult.Success -> if (current(expected)) state.update { it.copy(deleting = null, messages = it.messages.filterNot { m -> m.id == message.id }) }
                is AppResult.Failure -> if (current(expected)) state.update { it.copy(deleting = null, actionError = result.kind.message) }
            }
        }
    }
}

internal fun applyChatEvent(messages: List<ChatMessage>, event: ChatLiveEvent): List<ChatMessage> = when (event.type) {
    "message" -> ApiJson.decodeFromJsonElement<ChatMessage>(event.payload).let { message ->
        (messages.filterNot { it.id == message.id } + message).sortedBy(ChatMessage::id)
    }
    "delete" -> event.payload.jsonObject["id"]?.jsonPrimitive?.longOrNull?.let { id -> messages.filterNot { it.id == id } } ?: messages
    "edit" -> {
        val patch = event.payload as? JsonObject
        val id = patch?.get("id")?.jsonPrimitive?.longOrNull
        messages.map { message -> if (message.id != id) message else message.copy(
            content = (patch["content"] as? JsonPrimitive)?.contentOrNull ?: message.content,
            atts = patch["atts"]?.let { validMusicAttachments(ApiJson.decodeFromJsonElement<List<PostAttachment>>(it)) } ?: message.atts,
            streaming = (patch["streaming"] as? JsonPrimitive)?.booleanOrNull ?: message.streaming) }
    }
    else -> messages
}
internal fun chatMentions(content: String, members: List<ProfileUser>) = members.filter {
    it.id > 0 && it.nickname.isNotBlank() && Regex("@${Regex.escape(it.nickname)}(?=\\s|$)").containsMatchIn(content)
}.map { it.id }.distinct().take(10)
