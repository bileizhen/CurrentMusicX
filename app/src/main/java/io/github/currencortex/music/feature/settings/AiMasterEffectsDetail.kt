package io.github.currencortex.music.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.OriginalAiMasterState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

private val MasterGold = Color(0xFFE4CC9F)
private data class MasterDiyOption(val key: String, val title: String, val subtitle: String, val icon: String)
private val masterDiyOptions = listOf(
    MasterDiyOption("en_bass", "低频增强", "深沉有力，营造沉浸感", "bass"),
    MasterDiyOption("en_treble", "高频增强", "透亮不刺耳，提升穿透力", "treble"),
    MasterDiyOption("en_vocal", "人声增强", "人声清晰，增强细节", "vocal"),
    MasterDiyOption("en_vibe", "提升氛围感", "柔化边界，烘托情绪", "vibe"),
    MasterDiyOption("en_live", "提升现场感", "立体定位，如临舞台", "live"),
)

/** Native reconstruction of the original black/gold master page and its transactional DIY sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AiMasterEffectsDetail(
    state: OriginalAiMasterState,
    song: Song?,
    playing: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPlayback: () -> Unit,
    onSaveDiy: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (Boolean) -> Unit = {},
    previewing: Boolean = false,
    previewProcessed: Boolean = false,
    onPreviewStop: () -> Unit = {},
) {
    var information by rememberSaveable { mutableStateOf(false) }
    var preferences by rememberSaveable { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val animated = LocalMusicPageActive.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !information && !preferences
    val using = state.enabled || state.requestedEnabled
    BackHandler(enabled = LocalMusicPageActive.current) { when { preferences -> preferences = false; information -> information = false; else -> onBack() } }
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color(0xFF2D2000), .535f to Color(0xFF0E0900), 1f to Color(0xFF0E0900)))
        .testTag("sound_ai_master_detail")) {
        val pageWidth = maxWidth.coerceAtMost(600.dp)
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize()
            .statusBarsPadding().navigationBarsPadding().padding(bottom = LocalMusicBottomInset.current)) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MasterDetailIcon("返回音效", "sound_ai_master_back", onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                MasterDetailIcon("AI调音大师说明", "sound_ai_master_info", { information = true }) {
                    Icon(Icons.Default.Info, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(21.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .testTag("sound_ai_master_detail_scroll"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(pageWidth * .44f), contentAlignment = Alignment.TopCenter) {
                    // Original streams carry a left alpha plane and right colour plane. Local frames are
                    // composited once during packaging, preserving one transparent native animated sphere.
                    EffectsArtwork(NativeEffectsAssets.local(if (animated) "ai_master_detail_ball.webp" else "ai_master_detail_ball.png"),
                        Modifier.size(pageWidth * .667f).offset(y = -pageWidth * .08f), scale = ContentScale.Fit,
                        animated = animated)
                }
                EffectsArtwork(NativeEffectsAssets.local("ai_master_detail_title.png"), Modifier.width(159.dp).height(26.dp),
                    "AI调音大师 master", ContentScale.Fit)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("大模型分析歌曲内容，定制专属音效", color = Color.White.copy(alpha = .60f), fontSize = 11.sp)
                    Text("偏好设置 ›", Modifier.padding(start = 4.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .12f)).clickable(role = Role.Button) { preferences = true }
                        .padding(horizontal = 7.dp, vertical = 5.dp).testTag("sound_ai_master_diy_open"),
                        color = Color.White.copy(alpha = .85f), fontSize = 10.sp)
                }
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = pageWidth * 1.47f)
                    .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .06f))
                    .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(174.dp), contentAlignment = Alignment.BottomCenter) {
                        EffectsArtwork(NativeEffectsAssets.local(if (animated) "ai_master_detail_waves.webp" else "ai_master_detail_waves.png"),
                            Modifier.fillMaxWidth().height(174.dp).graphicsLayer { alpha = .30f }, animated = animated)
                        Box(Modifier.padding(bottom = 12.dp).size(100.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF5C513A)).clickable(enabled = song != null, role = Role.Button,
                                onClick = if (previewing) onPreviewStop else onPlayback).testTag("sound_ai_master_song_playback")) {
                            if (song != null && song.cover.isNotBlank()) EffectsArtwork(song.cover, Modifier.fillMaxSize(), "当前歌曲封面")
                            if (song != null && (using || previewing)) Icon(
                                if ((using && playing) || previewing) MusicIcons.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(20.dp))
                        }
                    }
                    Text(song?.name?.let { "《$it》" } ?: "暂无正在播放的歌曲", Modifier.padding(horizontal = 16.dp),
                        color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(25.dp))
                    Text("大师调音关键词", color = Color.White.copy(alpha = .60f), fontSize = 11.sp)
                    Text(state.baseName?.takeIf { it.isNotBlank() } ?: if (state.loading) "匹配中" else "",
                        Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(top = 6.dp).testTag("sound_ai_master_keyword"),
                        color = MasterGold, fontSize = 43.sp, lineHeight = 54.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                    Text(state.longText?.takeIf { it.isNotBlank() } ?: state.description?.takeIf { it.isNotBlank() }
                        ?: if (song == null) "播放网易云歌曲后，查看当前歌曲的专属调音方案" else state.message,
                        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp).testTag("sound_ai_master_description"),
                        color = Color.White.copy(alpha = .50f), fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    if (state.usedDiy.isNotEmpty() && (using || previewProcessed && previewing)) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                            Text("● DIY 标签应用中", color = MasterGold.copy(alpha = .85f), fontSize = 10.sp)
                        }
                        FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp,
                            Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            masterDiyOptions.filter { it.key in state.usedDiy }.forEach {
                                Text(it.title, Modifier.clip(CircleShape).background(Color.White.copy(alpha = .06f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!using && song != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MasterPreviewAction("播放原声", previewing && !previewProcessed, Modifier.weight(1f),
                            "sound_ai_master_preview_original") { if (previewing && !previewProcessed) onPreviewStop() else onPreview(false) }
                        MasterPreviewAction("试听音效", previewing && previewProcessed, Modifier.weight(1f),
                            "sound_ai_master_preview_processed") { if (previewing && previewProcessed) onPreviewStop() else onPreview(true) }
                    }
                    Spacer(Modifier.height(110.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = LocalMusicBottomInset.current).background(Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xF20E0900)))).navigationBarsPadding().padding(top = 28.dp, bottom = 22.dp),
            contentAlignment = Alignment.Center) {
            Row(Modifier.width((pageWidth * .64f).coerceAtMost(320.dp)).height(49.dp).clip(CircleShape)
                .background(if (using) Color.Black.copy(alpha = .3f) else MasterGold)
                .border(if (using) 1.dp else 0.dp, if (using) Color.White.copy(alpha = .25f) else Color.Transparent, CircleShape)
                .clickable(role = Role.Button, onClick = onToggle).testTag("sound_ai_master_toggle"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(if (using) "取消使用" else "立即使用", color = if (using) Color.White else Color(0xFF211A0C),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (!using) { Spacer(Modifier.width(5.dp)); EffectsMembership(4) }
            }
        }
    }
    if (preferences) MasterPreferencesSheet(state.selectedDiy, state.diySaving,
        onDismiss = { preferences = false }, onSave = { onSaveDiy(it); preferences = false })
    if (information) MasterInformationSheet { information = false }
}

@Composable private fun MasterPreviewAction(label: String, active: Boolean, modifier: Modifier, tag: String, onClick: () -> Unit) {
    Row(modifier.height(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f))
        .clickable(role = Role.Button, onClick = onClick).testTag(tag),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(if (active) MusicIcons.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable private fun MasterDetailIcon(label: String, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }.testTag(tag), contentAlignment = Alignment.Center) { content() }
}

@Composable private fun MasterPreferencesSheet(selected: Set<String>, saving: Boolean,
    onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var draft by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { draft = selected }
    MasterBottomSheet(onDismiss, dark = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DIY偏好设置 让大师更了解你", Modifier.weight(1f), color = Color.White.copy(alpha = .9f), fontSize = 16.sp)
            MasterDetailIcon("关闭偏好设置", "sound_ai_master_diy_close", onDismiss) {
                Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(20.dp))
            }
        }
        Text("选择偏好后，AI调音大师会在保障好的听歌体验基础上结合偏好为你定制最佳调音效果哟！（PS：偏好标签会基于歌曲生效）",
            Modifier.padding(bottom = 18.dp), color = Color.White.copy(alpha = .5f), fontSize = 11.sp, lineHeight = 16.sp)
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                for (column in 0..1) {
                    val option = masterDiyOptions.getOrNull(row * 2 + column)
                    val chosen = option != null && option.key in draft
                    Box(Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (chosen) MasterGold.copy(alpha = .10f) else Color.White.copy(alpha = .10f))
                        .border(if (chosen) 1.dp else 0.dp, if (chosen) MasterGold else Color.Transparent, RoundedCornerShape(8.dp))
                        .then(if (option != null) Modifier.toggleable(value = chosen, role = Role.Checkbox) { checked ->
                            draft = if (checked) draft + option.key else draft - option.key
                        }.testTag("sound_ai_master_diy_${option.icon}") else Modifier),
                        contentAlignment = if (option == null) Alignment.Center else Alignment.TopStart) {
                        if (option == null) Text("敬请期待", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
                        else Column(Modifier.padding(11.dp)) {
                            EffectsArtwork(NativeEffectsAssets.local("ai_master_detail_diy_${option.icon}${if (chosen) "_selected" else ""}.png"),
                                Modifier.size(28.dp), scale = ContentScale.Fit)
                            Text(option.title, Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = .9f), fontSize = 14.sp)
                            Text(option.subtitle, Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .5f), fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (chosen) Text("✓", Modifier.align(Alignment.BottomEnd).padding(9.dp), color = MasterGold, fontSize = 17.sp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 12.dp).height(48.dp)
            .clip(CircleShape).background(MasterGold).clickable(enabled = !saving, role = Role.Button) { onSave(draft) }
            .testTag("sound_ai_master_diy_save"), contentAlignment = Alignment.Center) {
            Text(if (saving) "保存中" else "保存设置", color = Color(0xFF241C0E), fontSize = 15.sp)
        }
    }
}

@Composable private fun MasterBottomSheet(onDismiss: () -> Unit, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 420.dp).fillMaxWidth().heightIn(max = maxHeight * .86f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(if (dark) Brush.verticalGradient(listOf(Color(0xFF2E251C), Color.Black))
                    else Brush.verticalGradient(listOf(Color.White, Color.White)))
                .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                content = content)
        }
    }
}

@Composable private fun MasterInformationSheet(onDismiss: () -> Unit) {
    MasterBottomSheet(onDismiss, dark = false) {
        Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AI调音大师说明", Modifier.weight(1f), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            MasterDetailIcon("关闭说明", "sound_ai_master_info_close", onDismiss) {
                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
        listOf(
            "1.功能说明" to "通过深度学习的 AI 引擎动态分析歌曲特征（如节奏、乐器、人声频率等），自动生成适配当前歌曲的专业级音效参数，无需手动调节即可获得歌曲的最佳听感",
            "2.歌曲覆盖范围" to "系统优先深度分析热度高的歌曲内容，其他内容排队生成定制参数中，等待期间将自动应用通用音效优化方案。",
            "3.DIY偏好" to "偏好标签会结合当前歌曲生效，并非所有歌曲都会应用全部偏好。展示「DIY 标签应用中」时，表示部分或全部偏好已生效。使用空间音频音质时，DIY 偏好不生效。",
            "4.版权限制" to "部分版权方限制的特殊歌曲（如付费数字专辑）禁用音效调节功能",
            "5.偏好设置" to "偏好保存在当前网易云账号的本机设置中，用于重新计算当前歌曲的音效参数。",
        ).forEach { (title, description) ->
            Text(title, color = Color(0xFF333333), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, Modifier.padding(top = 7.dp, bottom = 18.dp), color = Color(0xFF888888), fontSize = 12.sp, lineHeight = 19.sp)
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(48.dp).clip(CircleShape)
            .background(Color(0xFFFF393C)).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
            Text("知道了", color = Color.White, fontSize = 15.sp)
        }
    }
}
