package io.github.currencortex.music.feature.player

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.song.NeteaseComment
import io.github.currencortex.music.ui.component.DecoratedAvatar
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import io.github.currencortex.music.ui.component.MusicPlaceholder
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun PlayerCommentsDialog(state: PlayerComments, dismiss: () -> Unit, reload: () -> Unit, more: () -> Unit,
    composer: PlayerCommentComposer = PlayerCommentComposer(), edit: (String) -> Unit = {}, reply: (NeteaseComment?) -> Unit = {}, send: () -> Unit = {}) {
    val colors = MiuixTheme.colorScheme
    val indication = LocalIndication.current
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val ime = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val safeTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val maxHeight = (windowHeight - ime - safeTop - 72.dp).coerceIn(180.dp, 680.dp)
    var hotSelected by rememberSaveable(state.songId) { mutableStateOf(true) }
    val showHot = hotSelected && state.hot.isNotEmpty()
    val hotScroll = rememberLazyListState()
    val latestScroll = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focusReply by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusReply) { if (focusReply > 0) { focus.requestFocus(); keyboard?.show() } }
    LaunchedEffect(composer.message) {
        if (composer.message != null) { focusManager.clearFocus(); keyboard?.hide(); hotSelected = false; latestScroll.scrollToItem(0) }
    }
    val close = { focusManager.clearFocus(); keyboard?.hide(); dismiss() }
    MusicOverlayDialog(show = true, onDismissRequest = close, insideMargin = DpSize(20.dp, 16.dp)) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, LocalIndication provides indication) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("网易云评论", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                state.total?.let { Text(" $it", Modifier.weight(1f), fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
                    ?: Spacer(Modifier.weight(1f))
                CommentAction(if (state.loading) "刷新中…" else "刷新", "refresh_comments", !state.loading, reload)
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = close)
                    .semantics { contentDescription = "关闭评论"; role = Role.Button }.testTag("close_comments"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, Modifier.size(20.dp), tint = colors.onSurfaceVariantSummary)
                }
            }
            if (state.hot.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CommentTab("热门", showHot, "comments_hot") { hotSelected = true }
                CommentTab("最新", !showHot, "comments_latest") { hotSelected = false }
            }
            val rows = if (showHot) state.hot else state.latest
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("player_comments_list"),
                state = if (showHot) hotScroll else latestScroll, contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.loading && rows.isEmpty()) items(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicPlaceholder(Modifier.size(36.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MusicPlaceholder(Modifier.fillMaxWidth(.35f).height(12.dp))
                            MusicPlaceholder(Modifier.fillMaxWidth().height(40.dp))
                        }
                    }
                }
                items(rows, key = { it.commentId }) { comment ->
                    CommentContent(comment, enabled = !composer.sending) { reply(comment); focusReply++ }
                }
                if (!state.loading && state.error == null && rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("还没有评论", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text("写下你的感受，成为第一位听友", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
                state.error?.let { error -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(error, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        CommentAction("重试", "retry_comments", !state.loading, reload)
                    }
                } }
                if (!showHot && state.more && state.error == null) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CommentAction(if (state.loading) "正在加载…" else "加载更多评论", "more_comments", !state.loading, more)
                    }
                }
                if (!showHot && rows.isNotEmpty() && !state.more && !state.loading && state.error == null) item {
                    Text("已显示全部评论", Modifier.fillMaxWidth().padding(vertical = 8.dp), fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = colors.onSurfaceVariantSummary)
                }
            }
            HorizontalDivider(color = colors.onSurface.copy(alpha = .08f))
            composer.replyTo?.let { comment ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.onSurface.copy(alpha = .04f)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("回复 ${comment.user.nickname.ifBlank { "网易云用户" }}", fontSize = 12.sp, color = colors.primary)
                        Text(comment.content, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
                    }
                    CommentAction("取消", "cancel_comment_reply", !composer.sending) { reply(null) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                TextField(composer.draft, edit, Modifier.weight(1f).focusRequester(focus).testTag("player_comment_draft"),
                    label = if (composer.replyTo == null) "写下你的评论" else "写下你的回复", useLabelAsPlaceholder = true,
                    insideMargin = DpSize(14.dp, 12.dp), cornerRadius = 16.dp, maxLines = 3,
                    textStyle = MiuixTheme.textStyles.main.copy(fontSize = 15.sp), readOnly = composer.sending)
                TextButton(if (composer.sending) "发送中…" else "发送", send, Modifier.testTag("player_comment_send"),
                    enabled = !composer.sending && composer.draft.isNotBlank())
            }
            val feedback = composer.error ?: composer.message
            if (feedback != null || composer.draft.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(feedback ?: "", Modifier.weight(1f).testTag(if (composer.error != null) "player_comment_error" else "player_comment_result")
                    .semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                if (composer.draft.isNotEmpty()) Text("${composer.draft.length}/1000", Modifier.padding(start = 8.dp), fontSize = 11.sp,
                    color = if (composer.draft.length >= 1000) colors.primary else colors.onSurfaceVariantSummary)
            }
        }
        }
    }
}

@Composable private fun CommentTab(title: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).semantics { this.selected = selected; role = Role.Tab }
        .testTag(tag).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, Modifier.padding(top = 4.dp, bottom = 10.dp), fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSurface else colors.onSurfaceVariantSummary)
        Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) colors.primary else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable private fun CommentAction(title: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(title, Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag(tag).padding(horizontal = 10.dp, vertical = 12.dp), fontSize = 13.sp,
        color = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .45f))
}

@Composable private fun CommentContent(comment: NeteaseComment, enabled: Boolean, reply: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    var expanded by rememberSaveable(comment.commentId) { mutableStateOf(false) }
    var overflow by remember(comment.content) { mutableStateOf(false) }
    val time = remember(comment.time) {
        if (comment.time > 0) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(comment.time)) else ""
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DecoratedAvatar(comment.user.avatarUrl.takeIf { it.isNotBlank() }, null, size = 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(comment.user.nickname.ifBlank { "网易云用户" }, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (comment.likedCount > 0) Text("${comment.likedCount} 赞", Modifier.padding(start = 8.dp), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
            SelectionContainer {
                Text(comment.content, fontSize = 15.sp, lineHeight = 23.sp, maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
            }
            if (overflow || expanded) CommentAction(if (expanded) "收起" else "展开全文", "expand_comment_${comment.commentId}") { expanded = !expanded }
            comment.beReplied.firstOrNull()?.let { quote ->
                Text("${quote.user?.nickname?.ifBlank { "网易云用户" } ?: "网易云用户"}：${quote.content ?: "原评论已删除"}",
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.onSurface.copy(alpha = .04f)).padding(10.dp),
                    fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(time, Modifier.weight(1f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                CommentAction("回复", "reply_comment_${comment.commentId}", enabled, reply)
            }
        }
    }
}
