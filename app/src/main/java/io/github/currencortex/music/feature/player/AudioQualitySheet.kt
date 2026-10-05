package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.ui.component.MusicDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Quality is a requested level, never a claim about the stream returned by the provider. */
@Composable internal fun AudioQualitySheet(selected: AudioQuality, onSelect: (AudioQuality) -> Unit,
    onDismiss: () -> Unit, enabled: Boolean = true, title: String = "当前歌曲音质", onNetwork: (() -> Unit)? = null) {
    MusicDialog(title, onDismiss, footer = {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("close_quality_sheet")
            .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center) {
            Text("收起", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }) {
        AudioQualityChoices(selected, { quality ->
            if (quality != selected) onSelect(quality)
            onDismiss()
        }, enabled, onNetwork)
    }
}

@Composable internal fun AudioQualityChoices(selected: AudioQuality, onSelect: (AudioQuality) -> Unit,
    enabled: Boolean = true, onNetwork: (() -> Unit)? = null) {
    val colors = MiuixTheme.colorScheme
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .68f).dp
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("quality_choices"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 · ${selected.label}", Modifier.weight(1f), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                onNetwork?.let { action -> Text("网络与播放 ›", Modifier.heightIn(min = 48.dp).testTag("quality_network_settings")
                    .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action)
                    .padding(horizontal = 8.dp, vertical = 14.dp), fontSize = 12.sp, color = colors.primary) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeaturedQuality(AudioQuality.SKY, selected, enabled, onSelect, Modifier.weight(1f))
                FeaturedQuality(AudioQuality.JYMASTER, selected, enabled, onSelect, Modifier.weight(1f))
            }
        }
        items(listOf(AudioQuality.JYEFFECT, AudioQuality.HIRES, AudioQuality.LOSSLESS, AudioQuality.EXHIGH, AudioQuality.STANDARD)) { quality ->
            QualityRow(quality, selected, enabled, onSelect)
        }
        item { QualityRow(AudioQuality.AUTO, selected, enabled, onSelect) }
        item {
            Text(if (enabled) "按所选档位请求音源，实际音质取决于歌曲、音源及账号权限。自动最高会优先请求可用高音质。"
                else "请先退出一起听或结束投屏，再切换音质。",
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp).testTag("quality_note"),
                fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

@Composable private fun FeaturedQuality(quality: AudioQuality, selected: AudioQuality, enabled: Boolean,
    onSelect: (AudioQuality) -> Unit, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    val active = selected == quality
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.qualityClick(quality, active, enabled) { onSelect(quality) }
        .background(colors.primary.copy(alpha = if (active) .16f else .07f), shape)
        .border(1.dp, if (active) colors.primary.copy(alpha = .6f) else Color.Transparent, shape)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (quality == AudioQuality.SKY) "SURROUND" else "MASTER", Modifier.weight(1f), fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, color = colors.primary)
            QualityCheck(active)
        }
        Spacer(Modifier.height(10.dp))
        Text(quality.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(if (quality == AudioQuality.SKY) "多声道环绕音源" else "高解析母带音源", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    }
}

@Composable private fun QualityRow(quality: AudioQuality, selected: AudioQuality, enabled: Boolean, onSelect: (AudioQuality) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val active = quality == selected
    Row(Modifier.fillMaxWidth().qualityClick(quality, active, enabled) { onSelect(quality) }
        .background(if (active) colors.primary.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(18.dp))
        .padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(colors.primary.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
            Text(quality.shortName(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.primary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(quality.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(quality.description(), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        }
        QualityCheck(active)
    }
}

@Composable private fun Modifier.qualityClick(quality: AudioQuality, active: Boolean, enabled: Boolean, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .98f else 1f, tween(120), label = "quality press")
    return this.testTag("quality_${quality.name}").graphicsLayer {
        scaleX = scale.value; scaleY = scaleX; alpha = if (enabled) 1f else .45f
    }.clickable(enabled = enabled, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = onClick)
        .semantics { selected = active }
}

@Composable private fun QualityCheck(active: Boolean) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.size(24.dp).background(if (active) colors.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
        if (active) Image(Icons.Default.Check, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(colors.onPrimary))
    }
}

private fun AudioQuality.shortName() = when (this) {
    AudioQuality.JYEFFECT -> "全景"
    AudioQuality.HIRES -> "HR"
    AudioQuality.LOSSLESS -> "SQ"
    AudioQuality.EXHIGH -> "HQ"
    AudioQuality.STANDARD -> "标"
    else -> "自动"
}
private fun AudioQuality.description() = when (this) {
    AudioQuality.JYEFFECT -> "空间音频，沉浸聆听"
    AudioQuality.HIRES -> "高解析度，保留更多声音细节"
    AudioQuality.LOSSLESS -> "无损音源，还原原始细节"
    AudioQuality.EXHIGH -> "清晰细腻，兼顾音质与流量"
    AudioQuality.STANDARD -> "均衡音质，更省流量"
    else -> "优先请求可用的最高音质"
}
