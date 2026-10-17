package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.SoundEffectNotice
import io.github.currencortex.music.core.media.SoundEffectNoticePhase

/** Fixed geometry across progress updates; no continuous shimmer or repeatedly entering content. */
@Composable internal fun UpdateIslandPill(notice: SoundEffectNotice, modifier: Modifier = Modifier,
    material: Boolean = true, height: Dp = 36.dp, font: Float = DESKTOP_ISLAND_FONT_SIZE,
    onInstall: () -> Unit = {}) {
    val downloading = notice.phase == SoundEffectNoticePhase.APPLYING
    val failed = notice.phase == SoundEffectNoticePhase.FAILED
    val ready = notice.phase == SoundEffectNoticePhase.READY
    val motion = rememberDesktopMotion()
    val glow = remember { Animatable(0f) }
    LaunchedEffect(ready, motion) {
        if (ready) glow.animateTo(1f, tween(if (motion) 720 else 0, easing = FastOutSlowInEasing))
        else glow.snapTo(0f)
    }
    val tint = if (failed) Color(0xFFFF9B91) else Color(0xFF8DBFFF)
    val shape = RoundedCornerShape(50)
    val iconSize = 23.dp.coerceAtMost(height)
    val iconInset = (height - iconSize) / 2
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = TextStyle(color = Color.White, fontSize = font.sp, fontWeight = FontWeight.Medium)
    val statusStyle = TextStyle(color = tint, fontSize = (font - 1).sp,
        fontFeatureSettings = "tnum", textAlign = TextAlign.End)
    val status = if (downloading) notice.progress?.let { "$it%" } ?: "连接中"
        else if (ready) "点击安装" else ""
    // Percentages share one compact slot; terminal notices have no status slot at all.
    val titleWidth = with(LocalDensity.current) {
        textMeasurer.measure(notice.name, titleStyle, maxLines = 1, softWrap = false).size.width.toDp() + 2.dp
    }
    val statusWidth = with(LocalDensity.current) {
        if (status.isEmpty()) 0.dp else textMeasurer.measure(
            if (downloading && notice.progress != null) "100%" else status,
            statusStyle, maxLines = 1, softWrap = false).size.width.toDp() + 2.dp
    }
    val gaps = if (status.isEmpty()) 9.dp else 18.dp
    val pillWidth = (iconInset + iconSize + gaps + titleWidth + statusWidth + 12.dp).coerceAtMost(340.dp)
    Row(modifier.animateContentSize(tween(if (motion) 360 else 0, easing = FastOutSlowInEasing))
        .width(pillWidth).height(height).then(if (material) Modifier.clip(shape)
        .background(Color(0xFF11141B)).border(.8.dp, Color.White.copy(alpha = .2f), shape) else Modifier)
        .drawWithCache {
            val brush = Brush.radialGradient(
                listOf(tint.copy(alpha = .40f), tint.copy(alpha = .16f), Color.Transparent),
                center = Offset(size.width * .5f, size.height * 1.15f),
                radius = size.width * .65f)
            onDrawBehind { if (ready) drawRect(brush, alpha = glow.value) }
        }
        .semantics(mergeDescendants = true) {
            stateDescription = notice.message
            if (downloading) progressBarRangeInfo = notice.progress?.let { ProgressBarRangeInfo(it / 100f, 0f..1f) }
                ?: ProgressBarRangeInfo.Indeterminate
            else liveRegion = LiveRegionMode.Polite
        }.then(if (ready) Modifier.clickable(role = Role.Button, onClickLabel = "点击安装", onClick = onInstall) else Modifier)
        .testTag("update_island_notice").padding(start = iconInset, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (ready) {
            val context = LocalContext.current
            val icon = remember(context) { context.applicationInfo.loadIcon(context.packageManager).toBitmap(96, 96).asImageBitmap() }
            Image(BitmapPainter(icon), "CurrentMusic", Modifier.size(iconSize).clip(CircleShape))
        } else Canvas(Modifier.size(iconSize)) {
            val stroke = 1.8.dp.toPx()
            if (downloading) {
                drawCircle(tint.copy(alpha = .22f), style = Stroke(stroke))
                notice.progress?.let { drawArc(tint, -90f, 360f * it / 100, false,
                    style = Stroke(stroke, cap = StrokeCap.Round)) }
                drawLine(tint, Offset(size.width * .5f, size.height * .28f), Offset(size.width * .5f, size.height * .68f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .34f, size.height * .52f), Offset(size.width * .5f, size.height * .68f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .66f, size.height * .52f), Offset(size.width * .5f, size.height * .68f), stroke, StrokeCap.Round)
            } else {
                drawLine(tint, Offset(size.width * .3f, size.height * .3f), Offset(size.width * .7f, size.height * .7f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .7f, size.height * .3f), Offset(size.width * .3f, size.height * .7f), stroke, StrokeCap.Round)
            }
        }
        BasicText(notice.name, Modifier.weight(1f), style = titleStyle,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (status.isNotEmpty()) BasicText(status,
            Modifier.width(statusWidth), style = statusStyle, maxLines = 1, softWrap = false)
    }
}
