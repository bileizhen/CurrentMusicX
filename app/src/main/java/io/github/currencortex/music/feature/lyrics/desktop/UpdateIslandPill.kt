package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
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
    material: Boolean = true, height: Dp = 36.dp, font: Float = DESKTOP_ISLAND_FONT_SIZE) {
    val downloading = notice.phase == SoundEffectNoticePhase.APPLYING
    val failed = notice.phase == SoundEffectNoticePhase.FAILED
    val ready = notice.phase == SoundEffectNoticePhase.READY
    val tint = if (failed) Color(0xFFFF9B91) else Color(0xFF8DBFFF)
    val shape = RoundedCornerShape(50)
    val iconSize = 23.dp.coerceAtMost(height)
    val iconInset = (height - iconSize) / 2
    Row(modifier.width(216.dp).height(height).then(if (material) Modifier.clip(shape)
        .background(Color(0xFF11141B)).border(.8.dp, Color.White.copy(alpha = .2f), shape) else Modifier)
        .semantics(mergeDescendants = true) {
            stateDescription = notice.message
            if (downloading) progressBarRangeInfo = notice.progress?.let { ProgressBarRangeInfo(it / 100f, 0f..1f) }
                ?: ProgressBarRangeInfo.Indeterminate
            else liveRegion = LiveRegionMode.Polite
        }.testTag("update_island_notice").padding(start = iconInset, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Canvas(Modifier.size(iconSize)) {
            val stroke = 1.8.dp.toPx()
            if (downloading) {
                drawCircle(tint.copy(alpha = .22f), style = Stroke(stroke))
                notice.progress?.let { drawArc(tint, -90f, 360f * it / 100, false,
                    style = Stroke(stroke, cap = StrokeCap.Round)) }
                drawLine(tint, Offset(size.width * .5f, size.height * .28f), Offset(size.width * .5f, size.height * .68f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .34f, size.height * .52f), Offset(size.width * .5f, size.height * .68f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .66f, size.height * .52f), Offset(size.width * .5f, size.height * .68f), stroke, StrokeCap.Round)
            } else if (ready) {
                drawLine(tint, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .42f, size.height * .72f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .42f, size.height * .72f), Offset(size.width * .82f, size.height * .28f), stroke, StrokeCap.Round)
            } else {
                drawLine(tint, Offset(size.width * .3f, size.height * .3f), Offset(size.width * .7f, size.height * .7f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .7f, size.height * .3f), Offset(size.width * .3f, size.height * .7f), stroke, StrokeCap.Round)
            }
        }
        BasicText(notice.name, Modifier.weight(1f), style = TextStyle(color = Color.White,
            fontSize = font.sp, fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        BasicText(if (downloading) notice.progress?.let { "$it%" } ?: "连接中" else if (ready)
            if (io.github.currencortex.music.BuildConfig.UPDATE_DOWNLOAD_EXPERIENCE) "再下载" else "待安装" else "",
            Modifier.width(44.dp), style = TextStyle(color = tint, fontSize = (font - 1).sp,
                fontFeatureSettings = "tnum", textAlign = TextAlign.End), maxLines = 1, softWrap = false)
    }
}
