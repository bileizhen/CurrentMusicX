package io.github.currencortex.music.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PostsState(val posts: List<CommunityPost> = emptyList(), val total: Int = 0,
    val offset: Int = 0, val done: Boolean = false, val loaded: Boolean = false, val loading: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val message: String? = null,
    val editor: Boolean = false, val draft: String = "", val commentsFor: Long? = null,
    val comments: List<PostComment> = emptyList(), val commentsLoading: Boolean = false,
    val commentsError: String? = null)

class PostsViewModel(val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(PostsState())
    private fun session() = RequestSession(container.accountRepository.server, container.accountRepository.token)
    private val repository = PostsRepository(container.apiClient, ::session)
    private var requested = false
    private var generation = 0L
    private var feedTask: Job? = null
    private var commentTask: Job? = null
    private var actionTask: Job? = null
    init { viewModelScope.launch {
        container.sessionRestored.await()
        combine(container.accountRepository.sessionRevision, container.musicSettings.state.map { it.server }.distinctUntilChanged())
            { revision, server -> revision to server }.collect {
                ++generation; feedTask?.cancel(); commentTask?.cancel(); actionTask?.cancel()
                state.value = PostsState()
                if (requested) reload()
            }
    } }
    fun ensureLoaded() { requested = true; if (!state.value.loaded && !state.value.loading) reload() }
    fun reload(more: Boolean = false) {
        if (more && (state.value.loading || state.value.done)) return
        requested = true
        val before = state.value
        val token = ++generation
        feedTask?.cancel()
        state.update { it.copy(loading = true, error = null) }
        feedTask = viewModelScope.launch {
            container.sessionRestored.await()
            when (val result = appResult { repository.list(if (more) before.offset else 0) }) {
                is AppResult.Success -> if (token == generation) state.update { it.copy(
                    posts = (if (more) before.posts + result.value.posts else result.value.posts).distinctBy(CommunityPost::id),
                    total = result.value.total, offset = (if (more) before.offset else 0) + result.value.posts.size,
                    done = result.value.posts.isEmpty() || (if (more) before.offset else 0) + result.value.posts.size >= result.value.total,
                    loaded = true, loading = false) }
                is AppResult.Failure -> if (token == generation) state.update { it.copy(loading = false, loaded = true, error = result.kind.message) }
            }
        }
    }
    private fun action(success: String, block: suspend () -> Unit, refresh: Boolean = true, after: () -> Unit = {}) {
        if (state.value.busy) return
        if (session().token == null) { state.update { it.copy(message = "登录 CurrentMusic 账户后可操作") }; return }
        val expected = session()
        state.update { it.copy(busy = true, message = null) }
        actionTask = viewModelScope.launch {
            val result = appResult { block() }
            if (expected != session()) return@launch
            state.update { it.copy(busy = false, message = if (result is AppResult.Success) success else (result as AppResult.Failure).kind.message) }
            if (result is AppResult.Success) { after(); if (refresh) reload() }
        }
    }
    fun like(post: CommunityPost) {
        val on = !post.likedByMe
        action(if (on) "已点赞" else "已取消点赞", { repository.like(post.id, on) }, refresh = false) {
            state.update { it.copy(posts = it.posts.map { current -> if (current.id != post.id) current else current.copy(
                likedByMe = on, likeCount = (current.likeCount + if (current.likedByMe == on) 0 else if (on) 1 else -1).coerceAtLeast(0)) }) }
        }
    }
    fun delete(post: CommunityPost) {
        if (post.canDelete) action("帖子已删除", { repository.delete(post.id) }, refresh = false) {
            state.update { it.copy(posts = it.posts.filterNot { current -> current.id == post.id },
                total = (it.total - 1).coerceAtLeast(0), offset = (it.offset - 1).coerceAtLeast(0)) }
        }
    }
    fun editor(open: Boolean) {
        if (state.value.busy) return
        state.update { it.copy(editor = open, message = null) }
    }
    fun draft(value: String) { state.update { it.copy(draft = value) } }
    fun publish(attachments: List<PostAttachment>) {
        val content = state.value.draft
        if (content.isNotBlank()) action("帖子已发布", { repository.create(content, attachments) }) {
            state.update { it.copy(editor = false, draft = "") }
        }
    }
    fun comments(id: Long?) {
        commentTask?.cancel()
        state.update { it.copy(commentsFor = id, comments = emptyList(), commentsLoading = id != null, commentsError = null) }
        if (id == null) return
        val expected = session()
        commentTask = viewModelScope.launch {
            val result = appResult { repository.comments(id) }
            if (expected != session() || state.value.commentsFor != id) return@launch
            state.update { it.copy(commentsLoading = false,
                comments = (result as? AppResult.Success)?.value.orEmpty(),
                commentsError = (result as? AppResult.Failure)?.kind?.message) }
        }
    }
    fun sendComment(content: String) {
        val id = state.value.commentsFor ?: return
        if (content.isNotBlank()) action("评论已发布", { repository.comment(id, content) }, refresh = false) {
            state.update { it.copy(posts = it.posts.map { post -> if (post.id == id) post.copy(commentCount = post.commentCount + 1) else post }) }
            if (state.value.commentsFor == id) comments(id)
        }
    }
    fun delete(comment: PostComment) {
        val id = state.value.commentsFor ?: return
        if (comment.canDelete) action("评论已删除", { repository.deleteComment(comment.id) }, refresh = false) {
            state.update { it.copy(posts = it.posts.map { post -> if (post.id == id) post.copy(commentCount = (post.commentCount - 1).coerceAtLeast(0)) else post }) }
            if (state.value.commentsFor == id) comments(id)
        }
    }
}
