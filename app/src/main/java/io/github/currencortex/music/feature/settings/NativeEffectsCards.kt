package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.effects.NativeEffectTile
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import java.util.Locale

@Composable internal fun EffectsMembership(type: Int, modifier: Modifier = Modifier) {
    if (type == 3 || type == 4) EffectsArtwork(NativeEffectsAssets.local(if (type == 4) "svip_icon_url.png" else "vip_icon_url.png"),
        modifier.size(if (type == 4) 31.dp else 28.dp, 12.dp), if (type == 4) "SVIP" else "VIP", ContentScale.Fit)
}

@Composable internal fun NativeMasterPager(dark: Boolean, moodChosen: Boolean, loading: Boolean,
    moodStatus: String, song: String?, active: Boolean, enabled: Boolean, onMood: () -> Unit,
    masterChosen: Boolean, masterReady: Boolean, masterLoading: Boolean, masterStatus: String, onMaster: () -> Unit,
    speakerChosen: Boolean, speakerReady: Boolean, speakerLoading: Boolean, speakerStatus: String, onSpeaker: () -> Unit,
    moodEmotion: String? = null, songCover: String? = null, playing: Boolean = false,
    speakerScene: io.github.currencortex.music.data.effects.OriginalSpeakerScene = io.github.currencortex.music.data.effects.OriginalSpeakerScene.NORMAL,
    onDetails: (SoundEffectKind) -> Unit = {}) {
    val pager = rememberPagerState(initialPage = 3000) { Int.MAX_VALUE }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(active, dragged, pager.isScrollInProgress, pager.settledPage, moodChosen, masterChosen, speakerChosen) {
        if (active && !dragged && !pager.isScrollInProgress && !moodChosen && !masterChosen && !speakerChosen) {
            delay(6000)
            pager.animateScrollToPage(pager.currentPage + 1)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gutter = maxWidth * .098f
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = gutter), pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth().testTag("sound_master_pager")) { index ->
            val kind = when (index % 3) { 1 -> 2; 2 -> 1; else -> 0 }
            val focused = pager.currentPage == index
            val side = if (dark) "dark" else "light"
            val isUsing = when (kind) { 0 -> moodChosen; 1 -> masterChosen; else -> speakerChosen }
            val isReady = when (kind) { 0 -> moodChosen && !loading; 1 -> masterReady; else -> speakerReady }
            val isLoading = when (kind) { 0 -> loading; 1 -> masterLoading; else -> speakerLoading }
            val emotion = moodEmotion?.takeIf { it in MASTER_CARD_EMOTIONS } ?: "default"
            val scene = speakerScene.wireName
            val poster = when {
                kind == 0 && isUsing && emotion != "default" -> "mood_card_${emotion}_${side}_poster.png"
                kind == 0 && isUsing -> "emotion_default_${side}_poster.png"
                kind == 0 -> "emotion_${side}_unuse_bg_url.${if (dark) "jpg" else "png"}"
                kind == 1 -> "ai_model_${side}_${if (isUsing) "use" else "unuse"}_bg_url.png"
                isUsing && scene != "normal" -> "speakerphone_model_${scene}_used_${if (dark) "dark_" else ""}bg_url.png"
                else -> "speakerphone_model_normal_${if (isUsing) "used" else "unused"}_${if (dark) "dark_" else ""}bg_url.png"
            }
            val video = when {
                kind == 0 && isUsing && emotion != "default" -> "mood_card_${emotion}_${side}_video.mp4"
                kind == 0 && isUsing -> "emotion_video_map_default_${side}_video.mp4"
                kind == 0 -> "emotion_${side}_unuse_video_bg_url.mp4"
                kind == 1 -> "ai_model_${side}_${if (isUsing) "use" else "unuse"}_video_bg_url.mp4"
                isUsing && scene != "normal" -> "speakerphone_model_${scene}_used_${if (dark) "dark_" else ""}video_url.mp4"
                else -> "speakerphone_model_normal_${if (isUsing) "used" else "unused"}_${if (dark) "dark_" else ""}video_url.mp4"
            }
            val title = when (kind) { 0 -> "AI 情绪调音师"; 1 -> "AI 调音大师"; else -> "外放音效" }
            val target = when (kind) { 0 -> SoundEffectKind.MOOD; 1 -> SoundEffectKind.AI_MASTER; else -> SoundEffectKind.SPEAKER }
            val label = when (kind) { 0 -> "mood"; 1 -> "ai"; else -> "speaker" }
            Box(Modifier.fillMaxWidth().aspectRatio(1.37f).graphicsLayer {
                val distance = abs((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                scaleY = 1f - distance * .12f
            }.clip(RoundedCornerShape(12.dp)).background(if (dark) Color(0xFF302B58) else Color(0xFFE0DDF8))
                .clickable(enabled = focused, role = Role.Button) { onDetails(target) }
                .then(if (focused) Modifier.testTag("sound_master_${label}_detail") else Modifier)
                .semantics { contentDescription = title }) {
                EffectsVideo(video, poster, active && focused, Modifier.matchParentSize())
                if (isUsing) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .13f)).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (kind == 2) "$title · ${speakerScene.label}" else title, Modifier.weight(1f), fontSize = 10.sp,
                        color = if (dark || kind == 2) Color.White else Color(0xFF64512F), maxLines = 1)
                    Text(if (kind == 2) "切换环境 ›" else "查看 ›", fontSize = 10.sp,
                        color = if (dark || kind == 2) Color.White.copy(alpha = .85f) else Color(0xFF64512F))
                } else EffectsMembership(4, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp))
                if (kind == 1 && isUsing && !songCover.isNullOrBlank()) Box(Modifier.align(Alignment.Center).offset(y = (-7).dp).size(85.dp)) {
                    EffectsArtwork(NativeEffectsAssets.local("ai_model_black_pan_url.png"),
                        Modifier.size(84.dp).align(Alignment.CenterEnd).offset(x = 16.dp), scale = ContentScale.Fit)
                    EffectsArtwork(songCover, Modifier.size(80.dp).clip(RoundedCornerShape(3.dp)))
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = if (isUsing) 14.dp else 8.dp)
                    .padding(top = 14.dp, bottom = if (isUsing) 14.dp else 20.dp),
                    horizontalAlignment = if (isUsing) Alignment.Start else Alignment.CenterHorizontally) {
                    if (isUsing && isReady) Text(if (kind == 2) "外放 · ${speakerScene.label}" else
                        song?.let { "${if (playing) "正在播放" else "在听"}《$it》" } ?: "已开启 · 播放后自动匹配", fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (dark || kind == 2) Color.White else Color(0xFF64512F))
                    else if (kind == 0) EffectsArtwork(NativeEffectsAssets.local("emotion_title_icon_${side}_url.png"),
                        Modifier.size(137.dp, 20.dp), title, ContentScale.Fit)
                    else if (kind == 1 && !isUsing) EffectsArtwork(NativeEffectsAssets.local("ai_model_title_icon_url.png"),
                        Modifier.fillMaxWidth(.5f).aspectRatio(7.2f), title, ContentScale.Fit)
                    else Text(title, fontSize = if (kind == 2 && !isUsing) 16.sp else 18.sp, fontWeight = FontWeight.Bold,
                        color = if (dark || kind == 2) Color.White else Color(0xFF182B43))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(if (isUsing) 10.dp else 2.dp, Alignment.CenterHorizontally)) {
                        Text(when (kind) { 0 -> moodStatus; 1 -> masterStatus; else -> speakerStatus },
                            Modifier.weight(1f, fill = isUsing || kind != 2).padding(top = if (isUsing) 4.dp else 8.dp),
                            fontSize = if (isUsing) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = if (isUsing) TextAlign.Start else TextAlign.Center,
                            color = if (dark || kind == 2 || kind == 1 && !isUsing) Color.White.copy(alpha = .85f) else Color(0xCC64512F))
                        if (!isUsing) Text("›", fontSize = 16.sp,
                            color = if (dark || kind == 1 || kind == 2) Color.White.copy(alpha = .85f) else Color(0xCC64512F))
                        if (isUsing) NativeEffectsAction(if (isLoading) "取消加载" else "取消", {
                            when (kind) { 0 -> onMood(); 1 -> onMaster(); else -> onSpeaker() }
                        }, Modifier.width(66.dp).then(if (focused) Modifier.testTag(
                            when (kind) { 0 -> "sound_original_mood_action"; 1 -> "sound_master_ai_action"; else -> "sound_master_speaker_action" }) else Modifier),
                            enabled = focused)
                    }
                    if (!isUsing) NativeEffectsAction("立即使用", {
                        when (kind) { 0 -> onMood(); 1 -> onMaster(); else -> onSpeaker() }
                    }, Modifier.padding(top = 14.dp).width(164.dp).then(if (focused) Modifier.testTag(
                        when (kind) { 0 -> "sound_original_mood_action"; 1 -> "sound_master_ai_action"; else -> "sound_master_speaker_action" }) else Modifier),
                        enabled = focused && enabled,
                        background = if (kind == 1) Color(0xFFE8D2A0) else Color.White)
                }
            }
        }
    }
}

private val MASTER_CARD_EMOTIONS = setOf("ecstatic", "passionate", "energetic", "joyful", "sweet", "romantic", "confident",
    "relaxed", "healing", "calm", "nostalgic", "lonely", "sad", "bitter", "depressed")

@Composable internal fun NativeSmartBanner(tile: NativeEffectTile?, dark: Boolean, chosen: Boolean, loading: Boolean,
    status: String?, active: Boolean, enabled: Boolean, onApply: () -> Unit) {
    val side = if (dark) "dark" else "light"
    val poster = tile?.image(dark)?.takeIf { it.isNotBlank() }
        ?: NativeEffectsAssets.local("banner-100-$side-poster.png")
    val animation = if (active) tile?.animation(dark)?.takeIf { it.isNotBlank() && !it.contains(".mp4") }
        ?: NativeEffectsAssets.local("banner-100-$side-animation.webp") else null
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1f / .309f).clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF3789F7))) {
        EffectsArtwork(animation ?: poster, Modifier.matchParentSize(), animated = active)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tile?.name ?: "智能音效", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    EffectsMembership(tile?.membership ?: 3)
                }
                Text(status ?: tile?.description ?: "基于曲风自动匹配最佳音效参数，听感更丰富",
                    Modifier.testTag("sound_original_smart_status"), fontSize = 12.sp, color = Color.White.copy(alpha = .8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NativeEffectsAction(if (chosen) (if (loading) "取消加载" else "取消") else "使用", onApply,
                Modifier.width(60.dp).testTag("sound_original_smart_enabled"), enabled)
        }
    }
}

@Composable internal fun NativeEffectsAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, background: Color = Color.White) {
    Box(modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(32.dp))
        .background(background.copy(alpha = if (enabled) 1f else .5f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF0F0F24).copy(alpha = if (enabled) 1f else .5f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun NativeMoreEffectRow(tile: NativeEffectTile, chosen: Boolean, loading: Boolean,
    enabled: Boolean, onDetails: () -> Unit, onApply: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 84.dp)
        .clip(RoundedCornerShape(6.dp)).background(colors.surface)
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).testTag("sound_more_${tile.id}").clickable(role = Role.Button, onClick = onDetails),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tile.name, Modifier.weight(1f, fill = false), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                EffectsMembership(tile.membership)
            }
            if (tile.description.isNotBlank()) Text(tile.description, fontSize = 12.sp, color = colors.onSurfaceVariantSummary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            fun number(value: Long) = if (value < 10000) value.toString() else String.format(Locale.ROOT, "%.1f万", value / 10000.0)
            tile.useCount?.let { count -> Text("${number(count)}人使用" + (tile.trackCount?.let { " | ${number(it)}条动态" } ?: ""),
                fontSize = 11.sp, color = colors.onSurfaceVariantSummary) }
        }
        Box(Modifier.widthIn(min = 60.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(24.dp))
            .background((if (chosen) colors.onSurfaceVariantSummary else colors.primary).copy(alpha = .08f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onApply).padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center) {
            Text(if (chosen) (if (loading) "取消加载" else "取消") else "使用", fontSize = 13.sp,
                color = if (enabled) colors.primary else colors.onSurfaceVariantSummary)
        }
    }
}
