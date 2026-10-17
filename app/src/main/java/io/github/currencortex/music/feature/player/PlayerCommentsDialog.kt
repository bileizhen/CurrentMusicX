package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.song.NeteaseComment
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicPlaceholder
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun PlayerCommentsDialog(state: PlayerComments, dismiss: () -> Unit, reload: () -> Unit, more: () -> Unit,
    composer: PlayerCommentComposer = PlayerCommentComposer(), edit: (String) -> Unit = {}, reply: (NeteaseComment?) -> Unit = {}, send: () -> Unit = {}) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    MusicDialog("网易云评论" + (state.total?.let { " · $it" } ?: ""), dismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = if (keyboardVisible) 100.dp else 360.dp).testTag("player_comments_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading && state.latest.isEmpty()) items(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicPlaceholder(Modifier.fillMaxWidth(.35f).height(14.dp))
                    MusicPlaceholder(Modifier.fillMaxWidth().height(36.dp))
                }
            }
            if (state.hot.isNotEmpty()) {
                item { Text("热门评论", fontSize = 14.sp) }
                items(state.hot, key = { "hot-${it.commentId}" }) { CommentContent(it) { reply(it) } }
            }
            if (state.latest.isNotEmpty()) {
                item { Text("最新评论", fontSize = 14.sp) }
                items(state.latest, key = { "latest-${it.commentId}" }) { CommentContent(it) { reply(it) } }
            }
            if (!state.loading && state.error == null && state.latest.isEmpty() && state.hot.isEmpty()) item { Text("暂无评论") }
            state.error?.let { error -> item { Text(error); TextButton("重试", reload, modifier = Modifier.testTag("retry_comments")) } }
            if (state.more) item { TextButton(if (state.loading) "正在加载…" else "加载更多评论", more,
                enabled = !state.loading, modifier = Modifier.testTag("more_comments")) }
        }
        composer.replyTo?.let { comment -> Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("回复 ${comment.user.nickname.ifBlank { "网易云用户" }}", Modifier.weight(1f), fontSize = 13.sp)
            TextButton("取消回复", { reply(null) }, enabled = !composer.sending, modifier = Modifier.testTag("cancel_comment_reply"))
        } }
        TextField(composer.draft, edit, Modifier.fillMaxWidth().testTag("player_comment_draft"),
            label = if (composer.replyTo == null) "写下你的评论" else "写下你的回复", enabled = !composer.sending)
        composer.error?.let { Text(it, Modifier.testTag("player_comment_error"), fontSize = 12.sp) }
        composer.message?.let { Text(it, Modifier.testTag("player_comment_result"), fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("关闭", dismiss, Modifier.weight(1f))
            TextButton(if (composer.sending) "发送中…" else "发送", send, Modifier.weight(1f).testTag("player_comment_send"),
                enabled = !composer.sending && composer.draft.isNotBlank())
        }
    }
}

@Composable private fun CommentContent(comment: NeteaseComment, reply: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(comment.user.nickname.ifBlank { "网易云用户" }, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .65f))
        Text(comment.content, fontSize = 15.sp)
        if (comment.likedCount > 0) Text("${comment.likedCount} 人赞同", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
        TextButton("回复", reply, modifier = Modifier.testTag("reply_comment_${comment.commentId}"))
    }
}
