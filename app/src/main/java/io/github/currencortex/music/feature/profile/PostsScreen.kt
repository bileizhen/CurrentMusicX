package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun postTime(seconds: Long) = if (seconds <= 0) "" else
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))

@Suppress("DEPRECATION")
@Composable internal fun PostsScreen(vm: PostsViewModel, navigate: (String) -> Unit) {
    val state by vm.state.collectAsPageState()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val songFlow = remember(vm) { vm.container.playerController.state.map { it.song }.distinctUntilChanged() }
    val playingSong by songFlow.collectAsStateWithLifecycle(initialValue = null)
    val bottom = LocalMusicBottomInset.current
    val clipboard = LocalClipboardManager.current
    var deleting by remember { mutableStateOf<CommunityPost?>(null) }
    var deletingComment by remember { mutableStateOf<PostComment?>(null) }
    LaunchedEffect(account.account?.id) { deleting = null; deletingComment = null }
    MusicPullToRefresh(state.loading, { vm.reload() }, Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("discover_posts"),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.loaded) "${state.total} 条帖子" else "音乐与生活", Modifier.weight(1f), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
            MusicTextAction(if (account.account == null) "登录后发帖" else "发帖", {
                if (account.account == null) navigate("user/login") else vm.editor(true)
            }, Modifier.testTag("post_create"))
        } }
        state.message?.let { item { Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary) } }
        state.error?.let { item { Text(it); MusicTextAction("重试", { vm.reload() }, Modifier.testTag("post_retry")) } }
        if (state.loading && state.posts.isEmpty()) item { LoadingSongList(3) }
        if (state.loaded && !state.loading && state.posts.isEmpty() && state.error == null) item {
            Text("还没有帖子，分享你的音乐生活吧", Modifier.testTag("posts_empty"), fontSize = 14.sp)
        }
        items(state.posts, key = { it.id }) { post ->
            Card(Modifier.fillMaxWidth().testTag("post_${post.id}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { navigate("user/profile/${post.author.id}") },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UserAvatar(post.author, vm.container, 36.dp, reserveOverlay = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(post.author.nickname.ifBlank { post.author.username }, Modifier.weight(1f), fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                UserRoleBadge(post.author.role)
                            }
                            UserIdentityDetails(post.author.title, post.author.ipRegion)
                            Text(postTime(post.createdAt), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f))
                        }
                    }
                    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
                    MarkdownText(if (!expanded && post.content.length > 600) post.content.take(600) + "…" else post.content, compact = true)
                    if (post.content.length > 600) MusicTextAction(if (expanded) "收起" else "展开全文", { expanded = !expanded })
                    post.attachments.forEach { attachment ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = attachment.id > 0) {
                            when (attachment.type) {
                                "song" -> vm.container.playerController.playList(listOf(Song(attachment.id, attachment.name,
                                    attachment.sub, cover = attachment.pic)), 0)
                                "playlist" -> navigate("lib/ncmplaylist/${attachment.id}")
                                "album" -> navigate("lib/album/${attachment.id}")
                                "artist" -> navigate("lib/artist/${attachment.id}")
                            }
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AsyncImage(attachment.pic, null, Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(attachment.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(attachment.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MusicTextAction("${if (post.likedByMe) "已赞" else "赞"} ${post.likeCount}", { vm.like(post) },
                            Modifier.testTag("post_like_${post.id}"), enabled = !state.busy)
                        MusicTextAction("评论 ${post.commentCount}", { vm.comments(post.id) }, Modifier.testTag("post_comments_${post.id}"))
                        Spacer(Modifier.weight(1f))
                        MusicTextAction("复制", { clipboard.setText(AnnotatedString(post.content)) })
                        if (post.canDelete) MusicTextAction("删除", { deleting = post }, Modifier.testTag("post_delete_${post.id}"), enabled = !state.busy)
                    }
                }
            }
        }
        if (state.posts.isNotEmpty() && !state.done) item { MusicTextAction(if (state.loading) "加载中…" else "加载更多", { vm.reload(true) },
            Modifier.testTag("posts_more"), enabled = !state.loading) }
    }
    }
    if (state.editor) {
        var attachCurrent by rememberSaveable(account.account?.id) { mutableStateOf(false) }
        val current = playingSong?.takeIf { it.musicSource == MusicSource.NETEASE }
        MusicDialog("发帖", { vm.editor(false) }) {
            TextField(state.draft, vm::draft, Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("post_draft"))
            Text("支持 Markdown 格式", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
            current?.let { SettingsSwitch("附带当前歌曲", attachCurrent, { attachCurrent = it }, summary = it.name) }
            state.message?.let { Text(it, fontSize = 12.sp) }
            MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicDialogButton("取消", { vm.editor(false) }, enabled = !state.busy, modifier = Modifier.weight(1f))
            MusicDialogButton(if (state.busy) "发布中…" else "发布", {
                vm.publish(if (attachCurrent && current != null) listOf(PostAttachment("song", current.id, current.name, current.artists, current.cover)) else emptyList())
            }, enabled = !state.busy && state.draft.isNotBlank(), modifier = Modifier.weight(1f).testTag("post_publish"))
            }
        }
    }
    if (state.commentsFor != null && deletingComment == null) {
        var reply by rememberSaveable(state.commentsFor, account.account?.id) { mutableStateOf("") }
        LaunchedEffect(state.message) { if (state.message == "评论已发布") reply = "" }
        MusicDialog("评论", { vm.comments(null) }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).testTag("post_comment_list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.commentsLoading) item { Text("正在加载评论…") }
                state.commentsError?.let { item { Text(it); MusicTextAction("重试", { vm.comments(state.commentsFor) }) } }
                if (!state.commentsLoading && state.commentsError == null && state.comments.isEmpty()) item { Text("还没有评论") }
                items(state.comments, key = { it.id }) { comment ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(comment.author.nickname, Modifier.clickable { vm.comments(null); navigate("user/profile/${comment.author.id}") },
                            fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        UserIdentityDetails(comment.author.title, comment.author.ipRegion)
                        Text(comment.content, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(postTime(comment.createdAt), Modifier.weight(1f), fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                            if (comment.canDelete) MusicTextAction("删除", { deletingComment = comment },
                                Modifier.testTag("post_comment_delete_${comment.id}"), enabled = !state.busy)
                        }
                    }
                }
            }
            state.message?.let { Text(it, fontSize = 12.sp) }
            if (account.account != null) {
                TextField(reply, { reply = it }, Modifier.fillMaxWidth().testTag("post_reply"))
                MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MusicDialogButton("关闭", { vm.comments(null) }, modifier = Modifier.weight(1f))
                    MusicDialogButton("发送", { vm.sendComment(reply) }, enabled = !state.busy && reply.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("post_reply_send"))
                }
            } else MusicTextAction("登录后评论", { vm.comments(null); navigate("user/login") })
        }
    }
    deleting?.let { post -> MusicDialog("删除帖子？", { deleting = null }) {
        Text("删除后无法恢复。")
        MusicDialogButton("确认删除", { deleting = null; vm.delete(post) }, modifier = Modifier.testTag("post_confirm_delete"))
    } }
    deletingComment?.let { comment -> MusicDialog("删除评论？", { deletingComment = null }) {
        Text("删除后无法恢复。")
        MusicDialogButton("确认删除", { deletingComment = null; vm.delete(comment) }, modifier = Modifier.testTag("post_comment_confirm_delete"))
    } }
}
