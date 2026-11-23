package io.github.currencortex.music.feature.announcement

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.util.showMusicError
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.MusicDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.suqi8.coui.kmp.basic.PageIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.currencortex.music.ui.component.MusicDialogButton as TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun AnnouncementDialog(items: List<Announcement>, suppressByDefault: Boolean = false,
    onOpenLink: ((String) -> Unit)? = null, initialIndex: Int = 0, showSuppressionAction: Boolean = true,
    onDismiss: (Boolean) -> Unit) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(items.indices), pageCount = { items.size })
    val heights = remember(items) { mutableStateMapOf<Int, Int>() }
    var footerMeasured by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // The dialog panel is capped by the design system, so a fixed pager height squeezes the footer
    // off the panel. Reserve everything below the pager; the measurement only grows the estimate,
    // because a squeezed footer reports a smaller size than it needs.
    val footerContent = maxOf(if (items.size > 1) 72.dp else 48.dp,
        with(density) { footerMeasured.toDp() })
    val footerReserve = 12.dp + 20.dp + footerContent
    val dismiss = { onDismiss(suppressByDefault) }
    val detailUrl = items[pager.currentPage.coerceIn(items.indices)].detailUrl
    val openDetails: (String) -> Unit = onOpenLink ?: { url ->
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) {
            context.showMusicError("未找到可以打开链接的应用")
        }
    }
    MusicDialog(if (items.size == 1) "公告" else "公告（${items.size}）", dismiss, alignContent = false,
        footer = {
            Column(Modifier.fillMaxWidth().onSizeChanged { footerMeasured = it.height }) {
                if (items.size > 1) Row(Modifier.fillMaxWidth().heightIn(min = 24.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    PageIndicator(pagerState = pager, modifier = Modifier
                        .semantics { contentDescription = "第${pager.currentPage + 1}条公告，共${items.size}条" }
                        .testTag("announcement_indicator"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val style = TextStyle(fontSize = 15.sp)
                    val margin = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    if (showSuppressionAction) TextButton("不再显示", onClick = { onDismiss(true) }, textStyle = style, insideMargin = margin,
                        minHeight = 48.dp, modifier = Modifier.weight(1f).testTag("announcement_suppress"))
                    TextButton("知道了", onClick = dismiss, textStyle = style, insideMargin = margin,
                        minHeight = 48.dp, modifier = Modifier.weight(1f).testTag("announcement_dismiss"))
                    if (detailUrl != null) TextButton("查看详情", onClick = { openDetails(detailUrl) },
                        textStyle = style, insideMargin = margin, minHeight = 48.dp,
                        modifier = Modifier.weight(1f).testTag("announcement_details"))
                }
            }
        }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxContent = if (maxHeight == Dp.Infinity) 460.dp
                else (maxHeight - footerReserve).coerceIn(64.dp, 460.dp)
            val currentHeight = with(density) { heights[pager.currentPage]?.toDp() } ?: maxContent
            val height by animateDpAsState(currentHeight.coerceAtMost(maxContent), tween(240), label = "announcement_height")
            HorizontalPager(state = pager, pageSpacing = 16.dp, modifier = Modifier.fillMaxWidth()
                .height(height)
                .testTag("announcements")) { index ->
                val item = items[index]
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).onSizeChanged {
                    if (heights[index] != it.height) heights[index] = it.height
                },
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.pinned) Text("置顶", fontSize = 12.sp, color = COUITheme.colorScheme.primary,
                        modifier = Modifier.background(COUITheme.colorScheme.primary.copy(alpha = .1f),
                            RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    val date = remember(item.createdAt) {
                        if (item.createdAt <= 0) "" else runCatching {
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                                .format(Instant.ofEpochSecond(item.createdAt))
                        }.getOrDefault("")
                    }
                    Text(listOf(item.author.ifBlank { "CurrentStation" }, date).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp, color = COUITheme.colorScheme.labelSecondaryVariant)
                    if (item.body.isNotBlank()) MarkdownText(item.body, compact = true)
                }
            }
        }
    }
}
