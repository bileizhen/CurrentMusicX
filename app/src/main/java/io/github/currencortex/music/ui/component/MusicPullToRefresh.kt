package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.RefreshState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Loading data on entry must not start the refresh header's viewport-resizing spring.
 * Only a user's pull gesture owns that header; initial loading uses the page placeholders.
 */
@Composable
fun MusicPullToRefresh(loading: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier,
    indicatorColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f),
    showIndicator: Boolean = true,
    content: @Composable () -> Unit) {
    if (!showIndicator) {
        val currentLoading = rememberUpdatedState(loading)
        val currentRefresh = rememberUpdatedState(onRefresh)
        val threshold = with(LocalDensity.current) { 80.dp.toPx() }
        val connection = remember(threshold) {
            object : NestedScrollConnection {
                private var pulled = 0f
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (currentLoading.value) pulled = 0f
                    if (source != NestedScrollSource.UserInput || available.y >= 0 || pulled <= 0) return Offset.Zero
                    val consumed = maxOf(available.y, -pulled)
                    pulled += consumed
                    return Offset(0f, consumed)
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (currentLoading.value || source != NestedScrollSource.UserInput || available.y <= 0) return Offset.Zero
                    pulled = (pulled + available.y).coerceAtMost(threshold * 2)
                    return Offset(0f, available.y)
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    val refresh = pulled >= threshold && !currentLoading.value
                    val wasPulling = pulled > 0
                    pulled = 0f
                    if (refresh) currentRefresh.value()
                    return if (wasPulling && available.y > 0) Velocity(0f, available.y) else Velocity.Zero
                }
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    pulled = 0f
                    return Velocity.Zero
                }
            }
        }
        Box(modifier.nestedScroll(connection)) { content() }
        return
    }
    val pull = rememberPullToRefreshState()
    PullToRefresh(isRefreshing = loading && pull.refreshState != RefreshState.Idle,
        onRefresh = onRefresh, modifier = modifier, pullToRefreshState = pull,
        color = indicatorColor, circleSize = 18.dp,
        refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新结束"),
        refreshTextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = indicatorColor),
        content = content)
}
