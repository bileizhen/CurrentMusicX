package io.github.currencortex.music.feature.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.profile.POST_COMMENT_LIMIT
import io.github.currencortex.music.data.profile.PostComment
import io.github.currencortex.music.data.profile.postCharacterCount
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.collectAsPageState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PostDetailDialog(vm: PostsViewModel, state: PostsState, navigate: (String) -> Unit, signedIn: Boolean) {
    val density = LocalDensity.current
    val close = { if (!state.busy) vm.detail(null) }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = !state.busy, dismissOnClickOutside = false)) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, LocalMusicBottomInset provides 0.dp, LocalDensity provides density) {
            PostDetailPage(vm, state, { route -> vm.detail(null); navigate(route) }, signedIn, close)
        }
    }
}

@Composable
internal fun LinkedPostScreen(vm: PostsViewModel, navigate: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsPageState()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val close = { if (!state.busy) onBack() }
    PostDetailPage(vm, state, navigate, account.account != null, close, onDeleted = onBack)
}

@Suppress("DEPRECATION")
@Composable
private fun PostDetailPage(
    vm: PostsViewModel,
    state: PostsState,
    navigate: (String) -> Unit,
    signedIn: Boolean,
    onBack: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val id = state.detailId ?: return
    val post = state.detail
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = MiuixTheme.colorScheme
    val list = rememberLazyListState()
    val bottomInset = LocalMusicBottomInset.current
    val openRoute: (String) -> Unit = { route -> if (!state.busy) navigate(route) }
    var deletingPost by remember(id) { mutableStateOf(false) }
    var deletingComment by remember(id) { mutableStateOf<PostComment?>(null) }
    var replyTo by remember(id) { mutableStateOf<PostComment?>(null) }
    val input = rememberSaveable(id, saver = TextFieldState.Saver) { TextFieldState() }

    // The first composition for a thread must not clear its restored input.
    var lastSent by remember(id) { mutableLongStateOf(state.commentSent) }
    LaunchedEffect(state.commentSent) {
        if (lastSent != state.commentSent) {
            input.clearText()
            replyTo = null
            lastSent = state.commentSent
        }
    }
    LaunchedEffect(post?.id) {
        if (post != null && state.focusComments) list.scrollToItem(1)
    }

    MusicSubpage("帖子详情", "post_detail", onBack, modifier = Modifier.background(colors.background).imePadding(),
        actions = {
            if (post != null) PostAction(MusicIcons.Share, "分享帖子", {
                val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, vm.postLink(post.id))
                context.startActivity(Intent.createChooser(share, "分享帖子"))
            })
        }) { topInset ->
        Column(Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.detailLoading) item { LoadingPostList() }
                state.detailError?.let { error -> item {
                    SettingsHint(error, error = true)
                    MusicTextAction("重试", { vm.detail(id, state.focusComments) })
                } }
                if (post != null) {
                    item {
                        SettingsGroup {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PostAuthor(post, vm.container, openRoute, Modifier.fillMaxWidth())
                                if (post.title.isNotBlank()) Text(post.title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                                SelectionContainer { MarkdownText(post.content, allowImages = false) }
                                PostMusic(post, vm.container, openRoute)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PostLikeButton(post, state.busy) {
                                        if (signedIn) vm.like(post) else openRoute("user/login")
                                    }
                                    PostAction(MusicIcons.Copy, "复制正文", { clipboard.setText(AnnotatedString(post.content)) })
                                    if (post.canDelete) PostAction(MusicIcons.Trash, "删除帖子",
                                        { deletingPost = true }, enabled = !state.busy)
                                }
                            }
                        }
                    }
                    item { Text("评论 " + post.commentCount, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    items(state.comments, key = { it.id }) { comment ->
                        SettingsGroup {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(comment.author.nickname.ifBlank { comment.author.username },
                                    Modifier.clickable(enabled = comment.author.id > 0) { openRoute("user/profile/" + comment.author.id) },
                                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                UserIdentityDetails(comment.author.title, comment.author.ipRegion)
                                comment.replyTo?.let { target ->
                                    val name = target.nickname.ifBlank { "用户" + target.userId }
                                    Text("回复 @$name", fontSize = 12.sp, color = colors.primary)
                                }
                                SelectionContainer { Text(comment.content, fontSize = 14.sp) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(postTime(comment.createdAt), Modifier.weight(1f), fontSize = 11.sp,
                                        color = colors.onSurfaceVariantSummary)
                                    if (signedIn) MusicTextAction("回复", { replyTo = comment }, enabled = !state.busy)
                                    if (vm.canDelete(comment)) MusicTextAction("删除", { deletingComment = comment },
                                        Modifier.testTag("post_comment_delete_" + comment.id),
                                        enabled = !state.busy && !state.commentsLoading)
                                }
                            }
                        }
                    }
                    if (state.commentsLoading) item { LoadingTextList(2) }
                    state.commentsError?.let { error -> item {
                        SettingsHint(error, error = true)
                        MusicTextAction("重试", { vm.loadComments(state.commentRetryAfter) }, enabled = !state.busy)
                    } }
                    if (!state.commentsLoading && state.commentsError == null && state.comments.isEmpty()) item {
                        SettingsHint("还没有评论")
                    }
                    state.nextAfter?.takeIf { !state.commentsLoading && state.commentsError == null }?.let { cursor ->
                        item { MusicTextAction("加载更多评论", { vm.loadComments(cursor) }, enabled = !state.busy) }
                    }
                }
            }
            if (post != null) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.message?.let { SettingsHint(it) }
                if (signedIn) {
                    replyTo?.let { comment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("回复 @" + comment.author.nickname.ifBlank { comment.author.username }, Modifier.weight(1f),
                                fontSize = 13.sp, color = colors.primary)
                            MusicTextAction("取消回复", { replyTo = null }, enabled = !state.busy)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(state = input, modifier = Modifier.weight(1f).testTag("post_reply"),
                            label = "写评论", readOnly = state.busy)
                        GlassActionButton("发送", { vm.sendComment(input.text.toString(), replyTo?.id) },
                            Modifier.testTag("post_reply_send"), primary = true, fillWidth = false,
                            enabled = !state.busy && !state.commentsLoading &&
                                input.text.toString().postCharacterCount() in 1..POST_COMMENT_LIMIT)
                    }
                } else MusicTextAction("登录后评论", { openRoute("user/login") })
            }
        }
    }
    if (deletingPost && post != null) ConfirmPostDeletion("删除帖子？", { deletingPost = false }, "post_confirm_delete") {
        deletingPost = false
        vm.delete(post, onDeleted)
    }
    deletingComment?.let { comment ->
        ConfirmPostDeletion("删除评论？", { deletingComment = null }, "post_comment_confirm_delete") {
            deletingComment = null
            if (replyTo?.id == comment.id) replyTo = null
            vm.delete(comment)
        }
    }
}
