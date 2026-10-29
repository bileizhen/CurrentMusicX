package io.github.currencortex.music.feature.playlistimport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun ImportStep(number: Int, label: String) {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number.toString().padStart(2, '0'), color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.background(colors.primary.copy(alpha = .1f), RoundedCornerShape(8.dp)).padding(7.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The record motif shares the player's visual language and follows the user's theme. */
@Composable internal fun PlaylistImportHero(mode: PlaylistImportMode) {
    val colors = MiuixTheme.colorScheme
    val title = when (mode) {
        PlaylistImportMode.LINKS -> "把歌单带过来"
        PlaylistImportMode.TEXT -> "把歌名变成歌单"
        PlaylistImportMode.IMAGES -> "从一张截图开始"
        PlaylistImportMode.LOCAL -> "导入本地收藏"
    }
    val summary = when (mode) {
        PlaylistImportMode.LINKS -> "复制链接，继续听你的收藏。"
        PlaylistImportMode.TEXT -> "用歌名和歌手匹配歌曲。"
        PlaylistImportMode.IMAGES -> "识别截图，收藏喜欢的歌曲。"
        PlaylistImportMode.LOCAL -> "只匹配信息，音频留在本机。"
    }
    Row(Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CurrentMusic", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariantSummary)
            Text(title, fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, fontSize = 12.sp, lineHeight = 19.sp, color = colors.onSurfaceVariantSummary)
        }
        Canvas(Modifier.size(76.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            drawCircle(Color(0xFF26272C), radius, center)
            for (ring in 1..4) drawCircle(Color.White.copy(alpha = .09f), radius * (.43f + ring * .105f), center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            drawCircle(colors.primary, radius * .34f, center)
            drawCircle(colors.surfaceContainer, radius * .065f, center)
        }
    }
}

@Composable internal fun PlaylistImportGuide() {
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GuideRow("↗", "在原应用里分享歌单", "复制歌单、个人主页或文章的链接。")
        Box(Modifier.padding(start = 19.dp).width(1.dp).height(12.dp).background(colors.onSurfaceVariantSummary.copy(alpha = .2f)))
        GuideRow("↙", "回到这里粘贴", "支持整段分享文字，一次最多 10 个链接。")
    }
}

@Composable private fun GuideRow(symbol: String, title: String, detail: String) {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(colors.surfaceContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 21.sp, color = colors.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}
