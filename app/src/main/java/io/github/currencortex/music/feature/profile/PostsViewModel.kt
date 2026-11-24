package io.github.currencortex.music.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class PostsState(val posts: List<CommunityPost> = emptyList(), val total: Int = 0,
    val offset: Int = 0, val done: Boolean = false, val loaded: Boolean = false, val loading: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val failedMore: Boolean = false, val message: String? = null,
    val editor: Boolean = false, val draft: PostDraft = PostDraft(), val draftRestored: Boolean = false,
    val picker: Boolean = false, val attachmentQuery: String = "", val attachmentType: String = "song",
    val attachmentResults: List<PostAttachment> = emptyList(), val attachmentLoading: Boolean = false,
    val attachmentError: String? = null, val attachmentSearched: Boolean = false,
    val attachmentOffset: Int = 0, val attachmentMore: Boolean = false, val attachmentRetryMore: Boolean = false,
    val detailId: Long? = null, val detail: CommunityPost? = null, val detailLoading: Boolean = false,
    val detailShareable: Boolean = false, val linkedPostDeleted: Boolean = false,
    val detailError: String? = null, val focusComments: Boolean = false, val postedCommentId: Long? = null, val commentOwner: String? = null,
    val comments: List<PostComment> = emptyList(), val commentsLoading: Boolean = false,
    val commentsError: String? = null, val nextAfter: Long? = null, val lastLoadedCommentId: Long = 0,
    val commentRetryAfter: Long = 0)

internal class PostsViewModel(
    val container: AppContainer,
    val authorId: Long? = null,
    private val linkedPostId: Long? = null,
) : ViewModel() {
    val state = MutableStateFlow(PostsState(detailId = linkedPostId, detailLoading = linkedPostId != null))
    private fun session() = container.projectSession()
    private val repository = container.postsRepository
    private val commentSource = Any()
    private var draftOwner: PostAccount? = null
    private var publication: Publication? = null
    private var activeSession: RequestSession? = null
    private var activeAccount: PostAccount? = null
    private var draftLoadTask: Job? = null
    private data class Publication(val postId: Long, val authorId: Long)
    private data class PostAccount(val server: String, val accountId: Long) {
        val key get() = "$server|$accountId"
    }

    private fun owner(): PostAccount? {
        val current = session()
        val id = container.accountRepository.state.value.account?.id ?: return null
        return if (current.token.isNullOrBlank() || id <= 0) null else PostAccount(ServerUrl.normalize(current.server), id)
    }

    private var requested = false
    private var generation = 0L
    private var feedTask: Job? = null
    private var detailTask: Job? = null
    private var commentTask: Job? = null
    private var actionTask: Job? = null
    private var attachmentTask: Job? = null
    private var attachmentGeneration = 0L
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(container.accountRepository.sessionRevision, container.musicSettings.state.map { it.server }.distinctUntilChanged())
                { revision, server -> revision to server }.collect {
                    val previous = state.value
                    val account = owner()
                    val editingSameAccount = previous.editor && account != null && account == draftOwner
                    ++generation; ++attachmentGeneration
                    feedTask?.cancel(); detailTask?.cancel(); commentTask?.cancel(); actionTask?.cancel(); attachmentTask?.cancel()
                    activeSession = session()
                    activeAccount = account
                    repository.resetForCurrentSession()
                    if (!editingSameAccount) {
                        draftOwner?.let { container.postDraftStore.flush(it.key) }
                        draftOwner = null
                    }
                    publication = null
                    state.value = PostsState(
                        commentOwner = account?.key,
                        linkedPostDeleted = previous.linkedPostDeleted,
                        editor = editingSameAccount,
                        draft = if (editingSameAccount) previous.draft else PostDraft(),
                        draftRestored = editingSameAccount && previous.draftRestored,
                        message = when {
                            previous.editor && !editingSameAccount -> "登录状态已变化，草稿保留在原账号下"
                            editingSameAccount && previous.busy -> "登录状态已更新，请先确认帖子是否已发布"
                            else -> null
                        },
                    )
                    if (requested) reload()
                    if (linkedPostId != null && !previous.linkedPostDeleted) detail(linkedPostId)
                }
        }
        var observed = repository.changes.value.revision
        viewModelScope.launch {
            repository.changes.collect { changes ->
                val previous = observed
                observed = changes.revision
                val expected = activeSession?.takeIf { it == session() } ?: return@collect
                if (previous < observed) applyPostChanges(changes, expected, previous)
            }
        }
    }

    private fun applyPostChanges(changes: PostChanges, expected: RequestSession, since: Long) {
        val deleted = changes.deletedAfter(expected, since)
        val editedThreads = changes.threadsEditedElsewhere(expected, since, commentSource)
        val before = state.value
        val feedChanged = deleted.isNotEmpty() || changes.createdAfter(expected, since, authorId) ||
            (editedThreads.isNotEmpty() && (before.loading || before.posts.any { it.id in editedThreads }))
        val detailDeleted = before.detailId?.let(deleted::contains) == true
        if (detailDeleted) {
            detailTask?.cancel()
            commentTask?.cancel()
        }
        state.update { current ->
            val posts = current.posts.filterNot { it.id in deleted }.map { changes.applyTo(it, expected, since) }
            val updated = current.copy(
                posts = posts,
                detail = current.detail?.let { changes.applyTo(it, expected, since) },
                total = (current.total - (current.posts.size - posts.size)).coerceAtLeast(0),
                offset = if (feedChanged) 0 else current.offset,
                done = current.done || feedChanged,
            )
            if (detailDeleted) updated.copy(detail = null, detailLoading = false, detailShareable = false,
                detailError = ErrorKind.NotFound.message, comments = emptyList(), commentsLoading = false,
                commentsError = null, nextAfter = null) else updated
        }
        if (requested && feedChanged) reload()
        before.detailId?.takeIf { !detailDeleted && it in editedThreads }?.let {
            loadDetail(it, before.focusComments, refreshing = true)
        }
    }
    fun ensureLoaded() { requested = true; if (!state.value.loaded && !state.value.loading) reload() }
    fun postLink(id: Long): String = repository.link(id)
    fun reload(more: Boolean = false) {
        if (more && (state.value.loading || state.value.done)) return
        requested = true
        val before = state.value
        val token = ++generation
        feedTask?.cancel()
        state.update { it.copy(loading = true, error = null) }
        feedTask = viewModelScope.launch {
            container.sessionRestored.await()
            when (val result = appResult { repository.list(if (more) before.offset else 0, authorId) }) {
                is AppResult.Success -> if (token == generation) state.update { current ->
                    current.copy(
                        posts = (if (more) current.posts + result.value.posts else result.value.posts).distinctBy(CommunityPost::id),
                        detail = current.detail?.let { detail ->
                            val listPost = result.value.posts.find { it.id == detail.id }
                            if (current.detailShareable || listPost == null) detail else detail.copy(commentCount = listPost.commentCount)
                        },
                        total = result.value.total, offset = (if (more) before.offset else 0) + result.value.posts.size,
                        done = result.value.posts.isEmpty() || (if (more) before.offset else 0) + result.value.posts.size >= result.value.total,
                        loaded = true, loading = false,
                    )
                }
                is AppResult.Failure -> if (token == generation) state.update {
                    it.copy(loading = false, loaded = true, error = result.kind.message, failedMore = more)
                }
            }
        }
    }
    private fun <T> action(success: String, block: suspend (RequestSession) -> T, after: (T) -> Unit) {
        if (state.value.busy) return
        val expected = session()
        val account = owner()
        if (account == null || account != activeAccount) {
            state.update { it.copy(message = ErrorKind.Unauthorized.message) }
            return
        }
        state.update { it.copy(busy = true, message = null) }
        actionTask = viewModelScope.launch {
            val result = appResult { block(expected) }
            if (expected != session()) return@launch
            state.update { it.copy(busy = false, message = if (result is AppResult.Success) success else (result as AppResult.Failure).message) }
            if (result is AppResult.Success) after(result.value)
        }
    }
    fun like(post: CommunityPost) {
        val current = state.value.detail?.takeIf { it.id == post.id }
            ?: state.value.posts.find { it.id == post.id }
            ?: post
        val on = !current.likedByMe
        action(if (on) "已点赞" else "已取消点赞", { repository.like(post.id, on, it) }) { count ->
            updatePost(post.id) { it.copy(likedByMe = on, likeCount = count) }
        }
    }
    fun delete(post: CommunityPost) {
        if (post.canDelete) action("帖子已删除", { repository.delete(post.id, it) }) {
            if (linkedPostId == post.id) {
                state.update { it.copy(linkedPostDeleted = true) }
            } else if (state.value.detailId == post.id) {
                detail(null)
            }
        }
    }
    fun editor(open: Boolean) {
        if (state.value.busy) return
        if (open) {
            val current = owner()
            if (current == null) {
                state.update { it.copy(message = ErrorKind.Unauthorized.message) }
                return
            }
            draftLoadTask?.cancel()
            draftLoadTask = viewModelScope.launch {
                container.sessionRestored.await()
                val draft = container.postDraftStore.load(current.key)
                if (current != owner()) {
                    state.update { it.copy(message = "登录状态已变化，请重新打开发帖编辑器") }
                    return@launch
                }
                draftOwner = current
                state.update { it.copy(editor = true, draft = draft, draftRestored = !draft.isEmpty, message = null) }
            }
        } else {
            draftLoadTask?.cancel()
            picker(false)
            draftOwner?.let { container.postDraftStore.flush(it.key) }
            draftOwner = null
            state.update { it.copy(editor = false, draft = PostDraft(), draftRestored = false, message = null) }
        }
    }
    private fun editorOwner(): PostAccount? {
        val current = draftOwner
        if (current != null && current == owner()) return current
        state.update { it.copy(message = "登录状态已变化，请重新打开发帖编辑器") }
        return null
    }

    private fun editDraft(change: (PostDraft) -> PostDraft) {
        if (state.value.busy || !state.value.editor) return
        val current = editorOwner() ?: return
        val updated = change(state.value.draft)
        container.postDraftStore.save(current.key, updated)
        state.update { it.copy(draft = updated, message = null) }
    }
    fun draft(value: String) = editDraft { it.copy(content = value) }
    fun draftTitle(value: String) = editDraft { it.copy(title = value) }
    fun discardDraft() {
        editDraft { PostDraft() }
        editor(false)
    }
    fun publish() {
        val current = editorOwner() ?: return
        val draft = state.value.draft
        if (draft.canPublish) action("帖子已发布", { expected ->
            if (current != owner()) throw ApiException(ErrorKind.Unauthorized)
            val id = repository.create(draft.content, draft.attachments, draft.title, current.accountId, expected)
            withContext(NonCancellable) { container.postDraftStore.clearPublished(current.key, draft) }
            id
        }) { id ->
            publication = Publication(id, current.accountId)
            editor(false)
            detail(id)
        }
    }
    fun picker(open: Boolean) {
        if (state.value.busy) return
        ++attachmentGeneration; attachmentTask?.cancel()
        state.update { it.copy(picker = open, attachmentLoading = false, message = null) }
    }
    fun attachmentQuery(value: String) {
        ++attachmentGeneration; attachmentTask?.cancel()
        state.update { it.copy(attachmentQuery = value, attachmentResults = emptyList(), attachmentLoading = false,
            attachmentSearched = false, attachmentError = null, attachmentMore = false, attachmentOffset = 0, attachmentRetryMore = false) }
    }
    fun attachmentType(value: String) {
        if (value !in setOf("song", "album", "artist", "playlist")) return
        state.update { it.copy(attachmentType = value) }
        attachmentQuery(state.value.attachmentQuery)
        searchAttachments()
    }
    fun retryAttachments() = searchAttachments(state.value.attachmentRetryMore)
    fun searchAttachments(more: Boolean = false) {
        val before = state.value
        if (before.attachmentQuery.isBlank() || (more && (!before.attachmentMore || before.attachmentLoading))) return
        attachmentTask?.cancel()
        val request = ++attachmentGeneration
        val expected = session()
        state.update { it.copy(attachmentLoading = true, attachmentError = null, attachmentSearched = true, attachmentRetryMore = more) }
        attachmentTask = viewModelScope.launch {
            val result = appResult { repository.searchAttachments(before.attachmentQuery, before.attachmentType,
                if (more) before.attachmentOffset else 0) }
            if (request != attachmentGeneration || expected != session()) return@launch
            state.update { current -> when (result) {
                is AppResult.Success -> current.copy(attachmentLoading = false,
                    attachmentResults = ((if (more) before.attachmentResults else emptyList()) + result.value.items).distinctBy { it.type to it.id },
                    attachmentOffset = result.value.nextOffset, attachmentMore = result.value.hasMore)
                is AppResult.Failure -> current.copy(attachmentLoading = false, attachmentError = result.kind.message)
            } }
        }
    }
    fun attach(card: PostAttachment) {
        if (state.value.busy) return
        if (state.value.draft.attachments.size >= 5) {
            state.update { it.copy(message = "最多添加 5 项内容") }
        } else editDraft { draft ->
            draft.copy(attachments = validMusicAttachments(draft.attachments + card).distinctBy { it.type to it.id })
        }
    }
    fun removeAttachment(card: PostAttachment) = editDraft { draft ->
        draft.copy(attachments = draft.attachments.filterNot { it.type == card.type && it.id == card.id })
    }

    fun detail(id: Long?, focusComments: Boolean = false) = loadDetail(id, focusComments)

    private fun loadDetail(id: Long?, focusComments: Boolean, refreshing: Boolean = false) {
        detailTask?.cancel()
        commentTask?.cancel()
        state.update { it.copy(detailId = id, detail = null, detailLoading = id != null, detailShareable = false, detailError = null, focusComments = focusComments,
            comments = emptyList(), commentsLoading = false, commentsError = null, nextAfter = null, lastLoadedCommentId = 0, commentRetryAfter = 0,
            postedCommentId = if (refreshing) it.postedCommentId else null,
            message = if (refreshing) it.message else null) }
        if (id == null) return
        val expected = session()
        if (linkedPostId != null && !CurrentMusicServer.builtIn(expected.server)) {
            state.update { it.copy(detailLoading = false, detailError = "此链接指向 CurrentMusic 公共服务器") }
            return
        }
        val fromFeed = state.value.posts.find { it.id == id }
        val publishedBy = publication?.takeIf { it.postId == id }?.authorId
        detailTask = viewModelScope.launch {
            val result = appResult { repository.detail(id, fromFeed, publishedBy) }
            if (expected != session() || state.value.detailId != id) return@launch
            when (result) {
                is AppResult.Success -> {
                    state.update { current ->
                        val detail = result.value
                        val currentCount = current.posts.find { it.id == id }?.commentCount
                        val post = if (refreshing && !detail.shareable && currentCount != null) {
                            detail.post.copy(commentCount = currentCount)
                        } else detail.post
                        current.copy(detail = post, detailLoading = false, detailShareable = result.value.shareable,
                            posts = current.posts.map { if (it.id == id) post else it })
                    }
                    loadComments()
                }
                is AppResult.Failure -> state.update { it.copy(detailLoading = false, detailError = result.message) }
            }
        }
    }

    fun loadComments(after: Long = 0) {
        val id = state.value.detail?.id ?: return
        if (state.value.commentsLoading) return
        val expected = session()
        state.update { it.copy(commentsLoading = true, commentsError = null, commentRetryAfter = after) }
        commentTask = viewModelScope.launch {
            val result = appResult { repository.comments(id, after) }
            if (expected != session() || state.value.detailId != id) return@launch
            state.update { current -> when (result) {
                is AppResult.Success -> current.copy(commentsLoading = false,
                    comments = (result.value.comments + current.comments).distinctBy { it.id }.sortedBy { it.id },
                    lastLoadedCommentId = result.value.comments.lastOrNull()?.id ?: current.lastLoadedCommentId,
                    nextAfter = result.value.nextAfter?.takeIf { it > after })
                is AppResult.Failure -> current.copy(commentsLoading = false, commentsError = result.message)
            } }
        }
    }
    fun sendComment(id: Long, content: String, replyTo: PostReplyTarget?, commentOwner: String?) {
        if (commentOwner == null || commentOwner != owner()?.key || state.value.detail?.id != id) return
        val account = container.accountRepository.state.value.account ?: return
        if (!state.value.commentsLoading && content.postCharacterCount() in 1..POST_COMMENT_LIMIT) {
            action("评论已发布", { repository.comment(id, content, replyTo?.id, it, commentSource) }) { commentId ->
                updatePost(id) { post -> post.copy(commentCount = post.commentCount + 1) }
                if (state.value.detail?.id == id) {
                    val comment = PostComment(commentId, content.trim(), ProfileUser(
                        id = account.id, username = account.username, nickname = account.nickname, avatar = account.avatar,
                        title = account.title, isAdmin = account.isAdmin, isSuper = account.isSuper, isOwner = account.isOwner,
                    ), createdAt = System.currentTimeMillis() / 1000, canDelete = true, replyTo = replyTo)
                    state.update { current ->
                        current.copy(comments = (current.comments + comment).distinctBy { it.id }.sortedBy { it.id },
                            postedCommentId = commentId)
                    }
                    // Reconcile after the prior page boundary without using the newly inserted ID as a cursor.
                    if (state.value.nextAfter == null) {
                        loadComments(state.value.lastLoadedCommentId)
                    }
                }
            }
        }
    }
    fun canDelete(comment: PostComment): Boolean {
        val account = container.accountRepository.state.value.account ?: return false
        return comment.canDelete || account.id == comment.author.id || account.isAdmin || account.isSuper
    }
    fun delete(comment: PostComment) {
        val id = state.value.detail?.id ?: return
        if (!state.value.commentsLoading && canDelete(comment)) action("评论已删除", { repository.deleteComment(id, comment.id, it, commentSource) }) {
            updatePost(id) { post -> post.copy(commentCount = (post.commentCount - 1).coerceAtLeast(0)) }
            if (state.value.detailId == id) state.update { current ->
                current.copy(comments = current.comments.filterNot { it.id == comment.id }
                    .map { if (it.replyTo?.id == comment.id) it.copy(replyTo = null) else it })
            }
        }
    }

    private fun updatePost(id: Long, change: (CommunityPost) -> CommunityPost) {
        state.update { current -> current.copy(
            posts = current.posts.map { if (it.id == id) change(it) else it },
            detail = current.detail?.let { if (it.id == id) change(it) else it },
        ) }
    }
}
