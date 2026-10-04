package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.RefreshState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

/** Loading data on entry must not start the refresh header's viewport-resizing spring.
 * Only a user's pull gesture owns that header; initial loading uses the page placeholders.
 */
@Composable
fun MusicPullToRefresh(loading: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val pull = rememberPullToRefreshState()
    PullToRefresh(isRefreshing = loading && pull.refreshState != RefreshState.Idle,
        onRefresh = onRefresh, modifier = modifier, pullToRefreshState = pull, content = content)
}
