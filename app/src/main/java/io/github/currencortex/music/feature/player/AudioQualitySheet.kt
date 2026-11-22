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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.lx.LxPlatform
import io.github.currencortex.music.data.lx.LxSourceInfo
import io.github.currencortex.music.data.settings.AudioProvider
import io.github.currencortex.music.data.settings.AudioProviderState
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.qualityFileSummary
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.ui.component.MusicBottomSheet
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Quality is a requested level, never a claim about the stream returned by the provider. */
@Composable internal fun AudioQualitySheet(selected: AudioQuality, onSelect: (AudioQuality) -> Unit,
    onDismiss: () -> Unit, enabled: Boolean = true, title: String = "当前歌曲音质", onNetwork: (() -> Unit)? = null,
    song: Song? = null, insights: SongInsightsRepository? = null,
    audioInfo: io.github.currencortex.music.core.media.AudioStreamInfo? = null, onRefresh: (() -> Unit)? = null,
    container: AppContainer? = null, onEffects: (() -> Unit)? = null,
    isQq: Boolean = song?.musicSource == MusicSource.QQ_MUSIC,
    available: Collection<AudioQuality>? = null) {
    var visible by remember { mutableStateOf(true) }
    var afterDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dismiss by rememberUpdatedState(onDismiss)
    var songQualities by remember(song?.id, song?.musicSource) { mutableStateOf<Set<AudioQuality>?>(null) }
    val nativeQqQualitiesEnabled = qqNativeQualitiesEnabled(container)
    LaunchedEffect(isQq, song?.id, song?.musicSource, container, nativeQqQualitiesEnabled) {
        songQualities = if (isQq && song != null && container != null && nativeQqQualitiesEnabled)
            (appResult { container.qqMusic.qualities(song) } as? AppResult.Success)?.value?.takeIf { it.isNotEmpty() }
        else null
    }
    val effectiveAvailable = if (isQq) songQualities ?: available ?: qqAudioQualities(container) else null
    LaunchedEffect(effectiveAvailable, selected) {
        if (isQq && effectiveAvailable != null && selected != AudioQuality.AUTO && selected !in effectiveAvailable)
            onSelect(AudioQuality.AUTO)
    }
    MusicBottomSheet(show = visible, title = title,
        modifier = Modifier.testTag("audio_quality_sheet"),
        onDismissRequest = { visible = false },
        onDismissFinished = { dismiss(); afterDismiss?.invoke() }) {
        AudioQualityChoices(selected, { quality ->
            if (visible) {
                if (quality != selected) onSelect(quality)
                visible = false
            }
        }, enabled, onNetwork?.let { action -> { afterDismiss = action; visible = false } },
            available = effectiveAvailable, isQq = isQq, extraContent = {
            if (isQq && onEffects != null) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicTextAction("搭配音效 ›", {
                        afterDismiss = onEffects
                        visible = false
                    }, Modifier.testTag("qq_audio_effects"))
                    container?.let { app ->
                        MusicTextAction("账号与会员 ›", {
                            afterDismiss = { app.errorNotices.showMessage("会员与可用音质以 QQ 音乐账号页的实际状态为准") }
                            visible = false
                        }, Modifier.testTag("qq_audio_membership"))
                    }
                }
            }
            audioInfo?.let { info ->
                Text("实际播放 · ${info.summary}", Modifier.padding(4.dp).testTag("actual_audio_source"), fontSize = 13.sp)
                if (info.matched) Text("官方音源暂不可用，正在播放匹配音源。音质与版本以实际文件为准。",
                    Modifier.padding(4.dp), fontSize = 12.sp)
            }
            onRefresh?.let { refresh -> MusicTextAction("重新获取音源", { afterDismiss = refresh; visible = false }, Modifier.testTag("refresh_audio_source")) }
            if (visible && song != null && insights != null && NeteaseSongActionsRepository.songId(song) != null)
                SongQualityFileDetails(song, insights)
        })
    }
}

@Composable private fun qqNativeQualitiesEnabled(container: AppContainer?): Boolean {
    if (container == null) return false
    val access by container.audioSettings.state.collectAsStateWithLifecycle()
    return access.provider != AudioProvider.LX
}

@Composable private fun qqAudioQualities(container: AppContainer?): Set<AudioQuality> {
    if (container == null) return qqNativeQualities
    val access by container.audioSettings.state.collectAsStateWithLifecycle()
    if (access.provider != AudioProvider.LX) return qqNativeQualities
    val sources by container.lxSources.state.collectAsStateWithLifecycle()
    return qqAudioQualities(access, sources)
}

private val qqQualityOrder = listOf(
    AudioQuality.DOLBY, AudioQuality.LOSSLESS, AudioQuality.EXHIGH, AudioQuality.STANDARD,
    AudioQuality.QQ_NAC, AudioQuality.QQ_MASTER, AudioQuality.QQ_SURROUND, AudioQuality.QQ_STUDIO,
    AudioQuality.HIRES, AudioQuality.QQ_DTS_X,
)
private val qqNativeQualities = qqQualityOrder.toCollection(linkedSetOf())

internal fun qqAudioQualities(access: AudioProviderState, sources: List<LxSourceInfo>): Set<AudioQuality> {
    if (access.provider != AudioProvider.LX) return qqNativeQualities
    val supported = sources.asSequence().filter { access.lxSmartSwitch || it.id == access.lxSourceId }
        .flatMap { it.qualities(LxPlatform.QQ_MUSIC).asSequence() }
        .mapNotNull { quality -> when (quality) {
            "128k" -> AudioQuality.STANDARD
            "320k" -> AudioQuality.EXHIGH
            "flac" -> AudioQuality.LOSSLESS
            "flac24bit", "hires" -> AudioQuality.HIRES
            "atmos" -> AudioQuality.QQ_STUDIO
            "atmos_plus" -> AudioQuality.QQ_SURROUND
            "master" -> AudioQuality.QQ_MASTER
            "nac" -> AudioQuality.QQ_NAC
            "dtsx" -> AudioQuality.QQ_DTS_X
            "dolby" -> AudioQuality.DOLBY
            else -> null
        } }.toSet()
    val available = linkedSetOf<AudioQuality>()
    qqQualityOrder.forEach { if (it in supported) available += it }
    return available
}

@Composable internal fun AudioQualityChoices(selected: AudioQuality, onSelect: (AudioQuality) -> Unit,
    enabled: Boolean = true, onNetwork: (() -> Unit)? = null, modifier: Modifier = Modifier,
    available: Collection<AudioQuality>? = null, isQq: Boolean = false,
    extraContent: @Composable () -> Unit = {}) {
    val colors = COUITheme.colorScheme
    val regularOptions = (available ?: listOf(AudioQuality.VIVID, AudioQuality.DOLBY, AudioQuality.JYEFFECT,
        AudioQuality.HIRES, AudioQuality.LOSSLESS, AudioQuality.EXHIGH, AudioQuality.HIGHER, AudioQuality.STANDARD))
        .filter { it != AudioQuality.AUTO }
    val qqOptions = qqQualityOrder.filter { it in regularOptions }
    val qqFeaturedOptions = listOf(AudioQuality.QQ_SURROUND, AudioQuality.QQ_MASTER)
        .filter { it in regularOptions }
    // This is a fixed, short list. Compose it before the native entrance starts instead of
    // subcomposing every text row during the first animated layout pass.
    // Let the native sheet supply the available screen height. Keep the safe-area spacing
    // inside the scroll content rather than reserving a fixed footer below its viewport.
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("quality_choices")
        .navigationBarsPadding().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        run {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 · ${selected.qualityLabel(isQq)}", Modifier.weight(1f), fontSize = 13.sp, color = colors.labelSecondaryVariant)
                onNetwork?.let { action -> Text("网络与播放 ›", Modifier.heightIn(min = 48.dp).testTag("quality_network_settings")
                    .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action)
                    .padding(horizontal = 8.dp, vertical = 14.dp), fontSize = 12.sp, color = colors.primary) }
            }
        }
        if (available == null) run {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeaturedQuality(AudioQuality.SKY, selected, enabled, onSelect, false, Modifier.weight(1f))
                FeaturedQuality(AudioQuality.JYMASTER, selected, enabled, onSelect, false, Modifier.weight(1f))
            }
        } else if (isQq) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                qqFeaturedOptions.forEach { quality ->
                    FeaturedQuality(quality, selected, enabled, onSelect, true, Modifier.weight(1f))
                }
            }
        }
        (if (isQq) qqOptions.filter { it !in qqFeaturedOptions } else regularOptions).forEach { quality ->
            QualityRow(quality, selected, enabled, onSelect, isQq)
        }
        QualityRow(AudioQuality.AUTO, selected, enabled, onSelect, isQq)
        run {
            Text(when {
                !enabled -> "请先退出一起听，再切换音质。"
                isQq -> "可用音质取决于歌曲、账号权限和设备解码能力。"
                else -> "按所选档位请求音源，实际音质取决于歌曲、音源及账号权限。自动最高会优先请求可用高音质。"
            },
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp).testTag("quality_note"),
                fontSize = 12.sp, color = colors.labelSecondaryVariant)
        }
        extraContent()
    }
}

@Composable private fun SongQualityFileDetails(song: Song, repository: SongInsightsRepository) {
    val owner = song to repository.session()
    var response by remember(owner) { mutableStateOf<AppResult<List<SongQualityInfo>>?>(null) }
    var retry by remember(owner) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(owner, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            response = null
            response = appResult { repository.qualities(song) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(4.dp).testTag("song_quality_files"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("歌曲文件资料", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        when (val result = response) {
            null -> Text("正在加载…", fontSize = 13.sp)
            is AppResult.Failure -> MusicTextAction("${if (result.status == 404) "当前服务器暂不支持" else result.message} · 重试",
                { retry++ }, Modifier.testTag("song_quality_files_retry"))
            is AppResult.Success -> {
                if (result.value.isEmpty()) Text("暂无文件资料", fontSize = 13.sp)
                result.value.forEach { file ->
                    Text(file.label.ifBlank { file.level }, fontSize = 14.sp)
                    Text(qualityFileSummary(file.br, file.size, file.sr), fontSize = 12.sp,
                        color = COUITheme.colorScheme.labelSecondaryVariant)
                    file.variants.forEach { variant -> Text("${variant.immerseType.ifBlank { "音质变体" }} · ${qualityFileSummary(variant.br, variant.size, variant.sr)}",
                        fontSize = 12.sp, color = COUITheme.colorScheme.labelSecondaryVariant) }
                }
            }
        }
    }
}

@Composable private fun FeaturedQuality(quality: AudioQuality, selected: AudioQuality, enabled: Boolean,
    onSelect: (AudioQuality) -> Unit, isQq: Boolean, modifier: Modifier) {
    val colors = COUITheme.colorScheme
    val active = selected == quality
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.qualityClick(quality, active, enabled) { onSelect(quality) }
        .background(colors.primary.copy(alpha = if (active) .16f else .07f), shape)
        .border(1.dp, if (active) colors.primary.copy(alpha = .6f) else Color.Transparent, shape)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (quality == AudioQuality.SKY || quality == AudioQuality.QQ_SURROUND) "SURROUND" else "MASTER",
                Modifier.weight(1f), fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, color = colors.primary)
            QualityCheck(active)
        }
        Spacer(Modifier.height(10.dp))
        Text(quality.qualityLabel(isQq), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.labelPrimary)
        Text(quality.description(isQq), fontSize = 12.sp, color = colors.labelSecondaryVariant)
    }
}

@Composable private fun QualityRow(quality: AudioQuality, selected: AudioQuality, enabled: Boolean,
    onSelect: (AudioQuality) -> Unit, isQq: Boolean) {
    val colors = COUITheme.colorScheme
    val active = quality == selected
    Row(Modifier.fillMaxWidth().qualityClick(quality, active, enabled) { onSelect(quality) }
        .background(if (active) colors.primary.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(18.dp))
        .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(colors.primary.copy(alpha = .08f), CircleShape),
            contentAlignment = Alignment.Center) {
            Text(quality.shortName(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.primary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(quality.qualityLabel(isQq), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.labelPrimary)
            Text(quality.description(isQq), fontSize = 12.sp, color = colors.labelSecondaryVariant)
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
    val colors = COUITheme.colorScheme
    Box(Modifier.size(24.dp).background(if (active) colors.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
        if (active) Image(Icons.Default.Check, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(colors.labelOnColor))
    }
}

private fun AudioQuality.shortName() = when (this) {
    AudioQuality.QQ_MASTER -> "母带"
    AudioQuality.QQ_SURROUND -> "全景"
    AudioQuality.QQ_STUDIO -> "臻品"
    AudioQuality.QQ_DTS_X -> "DTS:X"
    AudioQuality.QQ_NAC -> "NAC"
    AudioQuality.VIVID -> "全景"
    AudioQuality.DOLBY -> "杜比"
    AudioQuality.JYEFFECT -> "臻音"
    AudioQuality.HIRES -> "HR"
    AudioQuality.LOSSLESS -> "SQ"
    AudioQuality.EXHIGH -> "HQ"
    AudioQuality.HIGHER -> "高"
    AudioQuality.STANDARD -> "标"
    else -> "自动"
}
internal fun AudioQuality.qualityLabel(qqMusic: Boolean): String = if (qqMusic) when (this) {
    AudioQuality.LOSSLESS -> "SQ 无损品质"
    AudioQuality.EXHIGH -> "HQ 高品质"
    AudioQuality.STANDARD -> "标准品质"
    else -> label
} else label

private fun AudioQuality.description(qqMusic: Boolean) = when (this) {
    AudioQuality.QQ_MASTER -> "QQ 音乐高解析母带音源"
    AudioQuality.QQ_SURROUND -> "QQ 音乐全景声音源"
    AudioQuality.QQ_STUDIO -> "QQ 音乐高品质音源"
    AudioQuality.QQ_DTS_X -> "需设备支持 DTS:X 解码与输出"
    AudioQuality.QQ_NAC -> "QQ 音乐 AI 编码音源"
    AudioQuality.VIVID -> "臻音全景声档位，需歌曲与账号支持"
    AudioQuality.DOLBY -> if (qqMusic) "QQ 音乐杜比全景声，需歌曲、账号和设备支持" else "杜比全景声档位，需音源、账号与设备支持"
    AudioQuality.JYEFFECT -> "网易云高清臻音档位，与 Hi-Res 分开选择"
    AudioQuality.HIRES -> "请求高解析度音源，不代表设备已高解析输出"
    AudioQuality.LOSSLESS -> if (qqMusic) "FLAC 无损音源" else "无损音源，还原原始细节"
    AudioQuality.EXHIGH -> if (qqMusic) "高码率音源" else "清晰细腻，兼顾音质与流量"
    AudioQuality.HIGHER -> "较高码率，兼顾清晰度与流量"
    AudioQuality.STANDARD -> "均衡音质，更省流量"
    else -> "优先请求可用的最高音质"
}
