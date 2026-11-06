package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import io.github.currencortex.music.ui.component.nativeui.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun postTime(seconds: Long): String {
    if (seconds <= 0) return ""
    val age = (System.currentTimeMillis() / 1000 - seconds).coerceAtLeast(0)
    return when {
        age < 60 -> "刚刚"
        age < 3600 -> "${age / 60} 分钟前"
        age < 86400 -> "${age / 3600} 小时前"
        age < 86400 * 30 -> "${age / 86400} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(seconds * 1000))
    }
}

@Suppress("DEPRECATION")
@Composable internal fun PostsScreen(vm: PostsViewModel, navigate: (String) -> Unit, contentTopInset: androidx.compose.ui.unit.Dp = 0.dp) {
    val state by vm.state.collectAsPageState()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val bottom = LocalMusicBottomInset.current
    val clipboard = LocalClipboardManager.current
    var deleting by remember { mutableStateOf<CommunityPost?>(null) }
    var options by remember { mutableStateOf<CommunityPost?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val menuBackdrop = rememberGlassBackdrop()
    val menuItems = options?.let { post -> buildList {
        add(GlassMenuItem("post_menu_copy", "复制正文", MusicIcons.Copy, onClick = {
            clipboard.setText(AnnotatedString(post.content)); menuOpen = false
        }))
        if (post.canDelete) add(GlassMenuItem("post_delete_${post.id}", "删除帖子", MusicIcons.Trash, danger = true,
            enabled = !state.busy, onClick = { menuOpen = false; deleting = post }))
    } }.orEmpty()
    var deletingComment by remember { mutableStateOf<PostComment?>(null) }
    LaunchedEffect(account.account?.id) { deleting = null; deletingComment = null; options = null; menuOpen = false }
    Box(Modifier.fillMaxSize()) {
    GlassMenu(menuOpen, { menuOpen = it }, menuItems, menuAnchor, menuBackdrop,
        GlassMenuTags("post_menu_panel", "post_menu_dismiss", "post_menu_action"), bottomInset = bottom,
        content = {
    Box(Modifier.fillMaxSize().then(if (menuBackdrop != null) Modifier.layerBackdrop(menuBackdrop) else Modifier)) {
    MusicPullToRefresh(state.loading, { vm.reload() }, Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("discover_posts"),
        contentPadding = PaddingValues(start = 16.dp, top = contentTopInset + 8.dp, end = 16.dp, bottom = 20.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rootMotionItems {
        item {
            SettingsGroup {
                SettingsPreference(if (account.account == null) "登录后分享" else "分享此刻",
                    "聊聊音乐，记录你的日常", Modifier.testTag("post_create"), onClick = {
                        if (account.account == null) navigate("user/login") else vm.editor(true)
                    }, leading = { Icon(MusicIcons.Add, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary) },
                    trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) })
            }
        }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("社区动态", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(if (state.loaded) "${state.total} 条" else "", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } }
        state.message?.let { item { Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary) } }
        state.error?.let { item { Text(it); MusicTextAction("重试", { vm.reload() }, Modifier.testTag("post_retry")) } }
        if (state.loading && state.posts.isEmpty()) item { LoadingPostList() }
        if (state.loaded && !state.loading && state.posts.isEmpty() && state.error == null) item {
            SettingsGroup {
                Column(Modifier.fillMaxWidth().padding(24.dp).testTag("posts_empty"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(MusicIcons.Comment, null, Modifier.size(32.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("还没有动态", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("分享一首歌，开启一段交流", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        items(state.posts, key = { it.id }) { post ->
            SettingsGroup(Modifier.testTag("post_${post.id}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { navigate("user/profile/${post.author.id}") },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UserAvatar(post.author, vm.container, 40.dp, reserveOverlay = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(post.author.nickname.ifBlank { post.author.username }, Modifier.weight(1f, fill = false), fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                UserRoleBadge(post.author.role)
                            }
                            UserIdentityDetails(post.author.title, post.author.ipRegion)
                            Text(postTime(post.createdAt), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                    var bounds by remember(post.id) { mutableStateOf<Rect?>(null) }
                    GlassIconButton({ options = post; menuAnchor = bounds; menuOpen = true }, "帖子操作",
                        Modifier.testTag("post_more_${post.id}"), onBoundsChanged = { bounds = it }, opensMenu = true, surface = false) {
                        Icon(MusicIcons.More, null, Modifier.size(20.dp))
                    }
                }
                var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
                SelectionContainer {
                    MarkdownText(if (!expanded && post.content.length > 600) post.content.take(600) + "…" else post.content, compact = true)
                }
                if (post.content.length > 600) MusicTextAction(if (expanded) "收起" else "展开全文", { expanded = !expanded })
                post.attachments.forEach { attachment ->
                    PostAttachmentTile(attachment, Modifier.testTag("post_attachment_${post.id}_${attachment.type}_${attachment.id}"),
                        enabled = attachment.id > 0, onClick = {
                            when (attachment.type) {
                                "song" -> vm.container.playerController.playList(listOf(Song(attachment.id, attachment.name,
                                    attachment.sub, cover = attachment.pic)), 0)
                                "playlist" -> navigate("lib/ncmplaylist/${attachment.id}")
                                "album" -> navigate("lib/album/${attachment.id}")
                                "artist" -> navigate("lib/artist/${attachment.id}")
                            }
                        })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PostAction(MusicIcons.Heart, "${if (post.likedByMe) "取消点赞" else "点赞"} ${post.likeCount}", {
                            if (account.account == null) navigate("user/login") else vm.like(post)
                        }, Modifier.testTag("post_like_${post.id}"), count = post.likeCount, selected = post.likedByMe, enabled = !state.busy)
                        PostAction(MusicIcons.Comment, "评论 ${post.commentCount}", { vm.comments(post.id) },
                            Modifier.testTag("post_comments_${post.id}"), count = post.commentCount)
                        Spacer(Modifier.weight(1f))
                        PostAction(MusicIcons.Copy, "复制正文", { clipboard.setText(AnnotatedString(post.content)) },
                            Modifier.testTag("post_copy_${post.id}"))
                    }
                }
            }
        }
        if (state.posts.isNotEmpty() && !state.done) item { MusicTextAction(if (state.loading) "加载中…" else "加载更多", { vm.reload(true) },
            Modifier.testTag("posts_more"), enabled = !state.loading) }
    }
    }
    }
    }
    }, toolbar = {})
    }
    if (state.editor) PostEditorScreen(vm, state)
    if (state.commentsFor != null && deletingComment == null) {
        val reply = rememberSaveable(state.commentsFor, account.account?.id, saver = TextFieldState.Saver) { TextFieldState() }
        LaunchedEffect(state.message) { if (state.message == "评论已发布") reply.clearText() }
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
                TextField(state = reply, modifier = Modifier.fillMaxWidth().testTag("post_reply"), readOnly = state.busy)
                MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MusicDialogButton("关闭", { vm.comments(null) }, modifier = Modifier.weight(1f))
                    MusicDialogButton("发送", { vm.sendComment(reply.text.toString()) }, enabled = !state.busy && reply.text.isNotBlank(),
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
