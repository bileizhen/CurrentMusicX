package io.github.currencortex.music.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.OriginalSpeakerState
import io.github.currencortex.music.data.effects.OriginalSpeakerScene
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/** Native presentation of the original outside detail. Browsing a scene never applies its DSP. */
@Composable internal fun SpeakerEffectsDetail(
    state: OriginalSpeakerState,
    song: Song?,
    onBack: () -> Unit,
    onEnable: (OriginalSpeakerScene) -> Unit,
    onDisable: () -> Unit,
    previewing: Boolean = false,
    previewScene: OriginalSpeakerScene? = null,
    onPreviewStart: (OriginalSpeakerScene) -> Unit = {},
    onPreviewStop: () -> Unit = {},
) {
    var browsing by rememberSaveable { mutableStateOf(state.scene.name) }
    val scene = OriginalSpeakerScene.entries.firstOrNull { it.name == browsing } ?: OriginalSpeakerScene.NORMAL
    val art = remember(scene) { speakerDetailArt(scene) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var information by rememberSaveable { mutableStateOf(false) }
    val active = LocalMusicPageActive.current
    val currentPreviewing by rememberUpdatedState(previewing)
    val stopPreview by rememberUpdatedState(onPreviewStop)
    DisposableEffect(Unit) { onDispose { if (currentPreviewing) stopPreview() } }
    fun leave() { if (previewing) onPreviewStop(); onBack() }
    BackHandler(enabled = active && !information) { leave() }
    val currentChoice = state.requestedEnabled && state.scene == scene
    val currentReady = state.enabled && state.scene == scene
    val previewHere = previewing && previewScene == scene

    BoxWithConstraints(Modifier.fillMaxSize().testTag("sound_speaker_detail"), contentAlignment = Alignment.TopCenter) {
        val width = maxWidth.coerceAtMost(680.dp)
        val heroHeight = (width * .9067f).coerceIn(240.dp, 390.dp)
        Box(Modifier.widthIn(max = 680.dp).fillMaxSize().background(art.base)) {
            Crossfade(art, animationSpec = tween(600), label = "speaker-environment-backdrop") { backdrop ->
                Box(Modifier.fillMaxSize()) {
                    EffectsArtwork(NativeEffectsAssets.local(backdrop.file("background.png")), Modifier.matchParentSize())
                    Box(Modifier.fillMaxWidth().aspectRatio(750f / 1624f).graphicsLayer {
                        translationY = if (scroll.firstVisibleItemIndex > 0) -size.height else -scroll.firstVisibleItemScrollOffset.toFloat()
                    }) {
                        EffectsVideo(backdrop.file(if (currentReady) "active.mp4" else "ambient.mp4"),
                            backdrop.file("background.png"), active && !information && scroll.firstVisibleItemIndex == 0,
                            Modifier.matchParentSize())
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("sound_speaker_detail_scroll"), state = scroll,
                contentPadding = musicScrollPadding(PaddingValues(bottom = 140.dp))) {
                item(key = "speaker-hero") { Spacer(Modifier.height(heroHeight)) }
                item(key = "speaker-heading") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(art.title + if (currentReady) " · 增强中" else "", color = Color.White,
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("sound_speaker_detail_title"))
                            EffectsMembership(4)
                        }
                        Text(art.description, Modifier.padding(top = 10.dp), color = Color.White.copy(alpha = .65f),
                            fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    }
                }
                item(key = "speaker-scenes") {
                    Row(Modifier.padding(horizontal = 28.dp, vertical = 24.dp).fillMaxWidth().height(64.dp)
                        .clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = .13f)).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OriginalSpeakerScene.entries.forEach { candidate ->
                            val candidateArt = speakerDetailArt(candidate)
                            val selected = candidate == scene
                            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                                .background(if (selected) Color.White.copy(alpha = .20f) else Color.Transparent)
                                .clickable(role = Role.Tab) {
                                    if (candidate != scene) {
                                        if (previewing) onPreviewStop()
                                        browsing = candidate.name
                                        scope.launch { scroll.animateScrollToItem(0) }
                                    }
                                }.semantics { this.selected = selected }
                                .testTag("sound_speaker_detail_scene_${candidate.wireName}"),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                EffectsArtwork(NativeEffectsAssets.local(candidateArt.file("icon.png")), Modifier.size(27.dp), scale = ContentScale.Fit)
                                Text(if (state.enabled && state.scene == candidate) "使用中" else candidateArt.shortTitle,
                                    color = Color.White.copy(alpha = if (selected) 1f else .62f), fontSize = 10.sp)
                            }
                        }
                    }
                }
                item(key = "speaker-tips-$browsing") {
                    EffectsArtwork(NativeEffectsAssets.local(art.file("tips.png")),
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1029f / art.tipsHeight)
                            .testTag("sound_speaker_detail_tips"), art.accessibleTips, ContentScale.Fit)
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).background(
                Brush.verticalGradient(listOf(art.base.copy(alpha = .70f), art.base.copy(alpha = 0f)))),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clickable(role = Role.Button) { leave() }
                    .semantics { contentDescription = "返回音效" }.testTag("sound_speaker_detail_back"), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(20.dp)) {
                        drawLine(Color.White, Offset(size.width * .65f, size.height * .15f),
                            Offset(size.width * .30f, size.height * .5f), 1.8.dp.toPx())
                        drawLine(Color.White, Offset(size.width * .30f, size.height * .5f),
                            Offset(size.width * .65f, size.height * .85f), 1.8.dp.toPx())
                    }
                }
                Text("外放音效", Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center,
                    fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.size(52.dp).clickable(role = Role.Button) { information = true }
                    .semantics { contentDescription = "外放音效说明" }.testTag("sound_speaker_detail_info"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(25.dp).background(Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("i", color = Color.White.copy(alpha = .8f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = LocalMusicBottomInset.current).background(Brush.verticalGradient(
                listOf(art.base.copy(alpha = 0f), art.base.copy(alpha = .95f), art.base)))
                .navigationBarsPadding().padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (currentChoice && !currentReady) Text(state.message, Modifier.padding(bottom = 10.dp)
                    .testTag("sound_speaker_detail_status"), color = Color.White.copy(alpha = .75f),
                    fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (currentChoice) Color.White.copy(alpha = .13f) else Color(0xFFE7CC97))
                    .clickable(role = Role.Button) {
                        if (previewing) onPreviewStop()
                        if (currentChoice) onDisable() else onEnable(scene)
                    }.testTag("sound_speaker_detail_apply"), contentAlignment = Alignment.Center) {
                    Text(if (currentChoice) (if (state.loading) "取消加载" else "取消使用")
                        else if (state.requestedEnabled) "切换环境使用" else "立即使用",
                        color = if (currentChoice) Color.White else Color(0xFF2C2519), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Text(if (previewHere) "结束试听" else "试听音效 ›", Modifier.padding(top = 12.dp)
                    .heightIn(min = 28.dp).clickable(role = Role.Button) {
                        if (previewHere) onPreviewStop() else onPreviewStart(scene)
                    }.testTag("sound_speaker_detail_preview"), color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
                if (previewHere && song != null) Text(song.name, color = Color.White.copy(alpha = .5f), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (information) MusicDialog("外放音效说明", onDismiss = { information = false }) {
        Column(Modifier.heightIn(max = 420.dp).testTag("sound_speaker_detail_information")) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { SpeakerInformationSection("音效介绍", "针对手机扬声器的声音特点，提供通用、浴室和睡前三种环境模式。浏览环境不会改变正在使用的音效，点击底部按钮后才会正式切换。") }
                item { SpeakerInformationSection("通用模式", "适合大部分空间，按横竖屏与系统音量选择原版音效素材，让声音更饱满通透。") }
                item { SpeakerInformationSection("浴室声场", "自然混响与中高频调整，帮助声音在浴室等空间中更清晰。") }
                item { SpeakerInformationSection("睡前陪伴", "弱化重低音和强刺激频段，营造安静放松的听感。") }
                item { SpeakerInformationSection("使用范围", "需要当前网易云账号的有效 SVIP 权益及原版音效引擎。仅在手机扬声器播放时处理音频；连接耳机后暂停外放处理。试听结束会恢复试听前的选择。") }
            }
        }
    }
}

@Composable private fun SpeakerInformationSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

private data class SpeakerDetailArt(val key: String, val shortTitle: String, val title: String,
    val description: String, val base: Color, val tipsHeight: Float, val accessibleTips: String) {
    fun file(kind: String) = "speaker-detail-$key-$kind"
}

private fun speakerDetailArt(scene: OriginalSpeakerScene): SpeakerDetailArt = when (scene) {
    OriginalSpeakerScene.NORMAL -> SpeakerDetailArt("normal", "通用", "通用模式", "横竖屏智能调节，人声清晰，声量饱满",
        Color(0xFF4F8FD6), 1650f, "通用模式 · 音效使用小贴士。横竖屏智能调节，适配手机播放姿态；声音更饱满通透。")
    OriginalSpeakerScene.SHOWER -> SpeakerDetailArt("shower", "浴室", "浴室声场", "混响加持，无惧水声干扰，快乐洗澡",
        Color(0xFF302FA8), 2241f, "浴室声场 · 音效使用小贴士。自然混响，提亮中高频，让歌声更清晰。")
    OriginalSpeakerScene.SLEEP -> SpeakerDetailArt("sleep", "睡前", "睡前陪伴", "弱化重低音与强刺激频段，伴你安心入眠",
        Color(0xFF08548D), 2379f, "睡前陪伴 · 音效使用小贴士。枕边安心听；科学调音，更懂睡眠。")
}
