package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.profile.CommunityPost
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.collectAsPageState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val markdownLinks = Regex("""!?\[([^\]\n]*)\]\([^\n)]*\)""")
private val markdownPrefix = Regex("""(?m)^[ \t]*(?:#{1,6}|>|[-+*]|\d+\.)[ \t]+""")
private val markdownCode = Regex("""(?m)^[ \t]*\x60{3}[^ \n]*[ \t]*$""")
private val postWhitespace = Regex("""\s+""")

internal fun postExcerpt(content: String): String {
    val text = content.replace(markdownLinks, "$1").replace(markdownPrefix, "")
        .replace(markdownCode, "").filterNot { it == '*' || it == '_' || it == '\u0060' }
        .replace(postWhitespace, " ").trim()
    val length = text.codePointCount(0, text.length)
    return if (length > 180) text.substring(0, text.offsetByCodePoints(0, 180)) + "…" else text
}

internal fun postTime(seconds: Long, now: Long = System.currentTimeMillis() / 1000): String {
    if (seconds <= 0) return ""
    val age = (now - seconds).coerceAtLeast(0)
    return when {
        age < 60 -> "刚刚"
        age < 3600 -> "${age / 60} 分钟前"
        age < 86400 -> "${age / 3600} 小时前"
        age < 86400 * 30 -> "${age / 86400} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(seconds * 1000))
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun PostsScreen(vm: PostsViewModel, navigate: (String) -> Unit, contentTopInset: Dp = 0.dp) {
    val state by vm.state.collectAsPageState()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val bottom = LocalMusicBottomInset.current
    val clipboard = LocalClipboardManager.current
    var deleting by remember { mutableStateOf<CommunityPost?>(null) }
    var options by remember { mutableStateOf<CommunityPost?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val backdrop = rememberGlassBackdrop()
    LaunchedEffect(account.account?.id) { deleting = null; options = null; menuOpen = false }

    val menuItems = options?.let { post ->
        buildList {
            add(GlassMenuItem("post_menu_copy", "复制正文", MusicIcons.Copy, onClick = {
                clipboard.setText(AnnotatedString(post.content))
                menuOpen = false
            }))
            if (post.canDelete) add(GlassMenuItem("post_delete_" + post.id, "删除帖子", MusicIcons.Trash, danger = true,
                enabled = !state.busy, onClick = { menuOpen = false; deleting = post }))
        }
    }.orEmpty()
    Box(Modifier.fillMaxSize()) {
        GlassMenu(menuOpen, { menuOpen = it }, menuItems, menuAnchor, backdrop,
            GlassMenuTags("post_menu_panel", "post_menu_dismiss", "post_menu_action"), bottomInset = bottom,
            content = {
                Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
                    MusicPullToRefresh(state.loading, { vm.reload() }, Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize().testTag("discover_posts"),
                            contentPadding = PaddingValues(16.dp, contentTopInset + 8.dp, 16.dp, 20.dp + bottom),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            rootMotionItems {
                                if (vm.authorId == null) item {
                                    SettingsGroup {
                                        SettingsPreference(if (account.account == null) "登录后发帖" else "发布帖子",
                                            modifier = Modifier.testTag("post_create"), onClick = {
                                                if (account.account == null) navigate("user/login") else vm.editor(true)
                                            }, leading = { Icon(MusicIcons.Add, null, Modifier.size(24.dp),
                                                tint = COUITheme.colorScheme.primary) })
                                    }
                                }
                                state.message?.let { item { SettingsHint(it) } }
                                state.error?.let { item {
                                    SettingsHint(it, error = true)
                                    MusicTextAction("重试", { vm.reload(state.failedMore) }, Modifier.testTag("post_retry"))
                                } }
                                if (state.loading && state.posts.isEmpty()) item { LoadingPostList() }
                                if (state.loaded && !state.loading && state.posts.isEmpty() && state.error == null) item {
                                    SettingsHint(if (vm.authorId == null) "还没有帖子" else "这位用户还没有发帖")
                                }
                                items(state.posts, key = { it.id }) { post ->
                                    SettingsGroup(Modifier.testTag("post_" + post.id)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                PostAuthor(post, vm.container, navigate, Modifier.weight(1f))
                                                var bounds by remember(post.id) { mutableStateOf<Rect?>(null) }
                                                GlassIconButton({ options = post; menuAnchor = bounds; menuOpen = true }, "帖子操作",
                                                    Modifier.testTag("post_more_" + post.id), onBoundsChanged = { bounds = it },
                                                    opensMenu = true, surface = false) {
                                                    Icon(MusicIcons.More, null, Modifier.size(20.dp))
                                                }
                                            }
                                            Column(Modifier.fillMaxWidth().clickable(onClickLabel = "查看帖子") { vm.detail(post.id) }
                                                .testTag("post_open_" + post.id), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (post.title.isNotBlank()) Text(post.title, fontSize = 18.sp,
                                                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                val excerpt = remember(post.content) { postExcerpt(post.content) }
                                                Text(excerpt.ifEmpty { "查看正文" }, fontSize = 14.sp, maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis, color = COUITheme.colorScheme.labelSecondaryVariant)
                                            }
                                            PostMusic(post, vm.container, navigate)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                PostLikeButton(post, state.busy) {
                                                    if (account.account == null) navigate("user/login") else vm.like(post)
                                                }
                                                PostAction(MusicIcons.Comment, "评论 " + post.commentCount, { vm.detail(post.id, focusComments = true) },
                                                    Modifier.testTag("post_comments_" + post.id), count = post.commentCount)
                                            }
                                        }
                                    }
                                }
                                if (state.posts.isNotEmpty() && !state.done && state.error == null) item {
                                    MusicTextAction(if (state.loading) "加载中…" else "加载更多", { vm.reload(true) },
                                        Modifier.testTag("posts_more"), enabled = !state.loading)
                                }
                            }
                        }
                    }
                }
            }, toolbar = {})
    }
    if (state.editor) PostEditorScreen(vm, state)
    if (state.detailId != null) PostDetailDialog(vm, state, navigate, account.account != null)
    deleting?.let { post -> ConfirmPostDeletion("删除帖子？", { deleting = null }, "post_confirm_delete") {
        deleting = null
        vm.delete(post)
    } }
}

@Composable
internal fun ProfilePostsScreen(vm: PostsViewModel, navigate: (String) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(vm) { vm.ensureLoaded() }
    MusicSubpage("用户帖子", "profile_posts", onBack) { topInset -> PostsScreen(vm, navigate, topInset) }
}

@Composable
internal fun PostAuthor(post: CommunityPost, container: AppContainer, navigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clickable(enabled = post.author.id > 0) { navigate("user/profile/" + post.author.id) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UserAvatar(post.author, container, 40.dp, reserveOverlay = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(post.author.nickname.ifBlank { post.author.username }, Modifier.weight(1f, fill = false),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UserRoleBadge(post.author.role)
            }
            UserIdentityDetails(post.author.title, post.author.ipRegion)
            Text(postTime(post.createdAt), fontSize = 11.sp, color = COUITheme.colorScheme.labelSecondaryVariant)
        }
    }
}

@Composable
internal fun PostMusic(post: CommunityPost, container: AppContainer, navigate: (String) -> Unit) {
    post.attachments.forEach { attachment ->
        PostAttachmentTile(attachment, Modifier.testTag("post_attachment_" + post.id + "_" + attachment.type + "_" + attachment.id),
            enabled = attachment.id > 0, onClick = {
                when (attachment.type) {
                    "song" -> container.playerController.playList(listOf(Song(attachment.id, attachment.name,
                        attachment.sub, cover = attachment.pic)), 0)
                    "playlist" -> navigate("lib/ncmplaylist/" + attachment.id)
                    "album" -> navigate("lib/album/" + attachment.id)
                    "artist" -> navigate("lib/artist/" + attachment.id)
                }
            })
    }
}

@Composable
internal fun PostLikeButton(post: CommunityPost, busy: Boolean, onLike: () -> Unit) {
    val label = if (post.likedByMe) "取消点赞" else "点赞"
    PostAction(MusicIcons.Heart, "$label " + post.likeCount, onLike, Modifier.testTag("post_like_" + post.id),
        count = post.likeCount, selected = post.likedByMe, enabled = !busy)
}

@Composable
internal fun ConfirmPostDeletion(title: String, dismiss: () -> Unit, tag: String, confirm: () -> Unit) {
    MusicDialog(title, dismiss) {
        Text("删除后无法恢复。")
        MusicDialogButton("确认删除", confirm, modifier = Modifier.testTag(tag))
    }
}
