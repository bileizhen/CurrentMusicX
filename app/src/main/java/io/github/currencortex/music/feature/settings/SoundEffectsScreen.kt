package io.github.currencortex.music.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.effects.NeteaseSoundEffect
import io.github.currencortex.music.data.effects.NativeEffectTile
import io.github.currencortex.music.data.settings.EqualizerPreset
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.abs
import kotlinx.serialization.json.*

/** Original presets and local EQ share one visible selection, with independent verified apply paths. */
@Composable internal fun SoundEffectsMainScreen(vm: SoundEffectsViewModel, onBack: () -> Unit, onEqualizer: () -> Unit, onDetails: (SoundEffectKind) -> Unit) {
    val options by vm.preferences.collectAsStateWithLifecycle()
    val tuning by vm.tuning.collectAsStateWithLifecycle()
    val manual by vm.manual.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val currentSong = playback.song ?: queue.current
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val more by vm.more.collectAsStateWithLifecycle()
    val tileLoading by vm.tileLoading.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val animationsActive = LocalMusicPageActive.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val original by vm.original.collectAsStateWithLifecycle()
    val originalSmart by vm.originalSmart.collectAsStateWithLifecycle()
    val originalMood by vm.originalMood.collectAsStateWithLifecycle()
    val originalMaster by vm.originalAiMaster.collectAsStateWithLifecycle()
    val originalSpeaker by vm.originalSpeaker.collectAsStateWithLifecycle()
    val engineImport by vm.originalImport.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val background = if (dark) Color.Black else Color(0xFFF4F7FA)
    val listState = rememberLazyListState()
    val catalogListState = rememberLazyListState()
    val context = LocalContext.current
    val selectNeteaseApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importOriginal(uri)
    }
    var selectedEffect by remember { mutableStateOf<NeteaseSoundEffect?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var expandedCatalog by rememberSaveable { mutableStateOf(false) }
    var showEngine by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expandedCatalog) { if (expandedCatalog) vm.refreshMore() }
    var tileDetails by remember { mutableStateOf<NativeEffectTile?>(null) }
    BackHandler(enabled = LocalMusicPageActive.current && expandedCatalog) { expandedCatalog = false }
    val canApply = runtime.supported && playback.mode != PlayerMode.CAST
    val status = when {
        playback.mode == PlayerMode.CAST -> "投屏音效由接收设备处理"
        !runtime.supported -> "此设备暂不支持本机均衡器"
        runtime.error != null -> runtime.error!!
        options.smartEnabled -> tuning.message
        else -> "根据歌曲的声音特征，自动调整本机均衡器"
    }
    val masterChosen = originalMaster.requestedEnabled
    val speakerChosen = originalSpeaker.requestedEnabled
    val moodChosen = originalMood.requestedEnabled
    val smartChosen = originalSmart.requestedEnabled
    val moodFailed = !moodChosen && originalMood.message != io.github.currencortex.music.core.media.OriginalMoodState().message
    val smartFailed = !smartChosen && originalSmart.message != io.github.currencortex.music.core.media.OriginalSmartState().message
    val singleChosen = original.requestedId != null || original.selectedId != null
    val anyEnabled = masterChosen || speakerChosen || moodChosen || smartChosen || singleChosen || options.smartEnabled || manual.enabled
    val activeStatus = when {
        !anyEnabled -> "保留你的调节，下次随时开启"
        playback.mode == PlayerMode.CAST -> "投屏由接收设备处理"
        masterChosen -> originalMaster.message
        speakerChosen -> originalSpeaker.message
        moodChosen -> if (originalMood.enabled && !originalMood.loading && !playback.playing)
            "已开启，播放网易云歌曲后自动匹配" else originalMood.message
        smartChosen -> if (originalSmart.enabled && !originalSmart.loading && !playback.playing)
            "已开启，播放网易云歌曲后自动匹配" else originalSmart.message
        singleChosen -> when {
            original.loading -> "正在加载，可随时取消"
            original.error != null -> original.error!!
            !playback.playing -> "已就绪，播放后生效"
            original.processing -> "正在处理音频"
            else -> "等待音频处理"
        }
        options.smartEnabled -> status
        runtime.error != null -> runtime.error!!
        !runtime.supported -> "此设备暂不支持本机均衡器"
        !playback.playing -> "已就绪，播放后生效"
        runtime.active -> "正在使用本机均衡器"
        else -> "等待连接播放器"
    }
    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().statusBarsPadding().testTag("native_sound_effects_page")) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            IconButton({ if (expandedCatalog) expandedCatalog = false else onBack() }, Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(48.dp)
                .testTag("sound_effects_back")) {
                    EffectsArtwork(NativeEffectsAssets.local(if (dark) "back_dark-V7HQ34UL.png" else "back_light-JEKHG5AD.png"),
                        Modifier.size(28.dp), "返回")
                }
            Text(if (expandedCatalog) "官方精选音效" else "音效", Modifier.align(Alignment.Center), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onEqualizer, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(48.dp)
                .testTag("sound_open_equalizer")) {
                EffectsArtwork(NativeEffectsAssets.local(if (dark) "equalizer_dark-E6LSHWIG.png" else "equalizer_light-F2DPORXJ.png"),
                    Modifier.size(32.dp), "均衡器")
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("sound_effects_screen"), state = if (expandedCatalog) catalogListState else listState,
            contentPadding = musicScrollPadding(PaddingValues(bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!expandedCatalog) {
            item { Text("大师音效", Modifier.padding(horizontal = 16.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item(key = "masters") {
                val visible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "masters" } } }
                NativeMasterPager(dark, moodChosen, originalMood.loading,
                    if (moodChosen) ((originalMood.visualExtra?.get("copywriting") as? JsonObject)
                        ?.get("description") as? JsonPrimitive)?.contentOrNull ?: activeStatus else "按歌曲情绪智能调音，读懂每首歌背后的情感温度",
                    currentSong?.name, visible && animationsActive && selectedEffect == null && tileDetails == null && !showEngine,
                    playback.mode != PlayerMode.CAST,
                    onMood = { if (moodChosen) vm.turnOff() else vm.originalMoodEnabled(true) },
                    masterChosen = masterChosen, masterReady = originalMaster.enabled && !originalMaster.loading, masterLoading = originalMaster.loading, masterStatus = if (masterChosen) originalMaster.description ?: originalMaster.message
                        else "AI 大模型分析歌曲内容，为每一首歌定制专属音效",
                    onMaster = { if (masterChosen) vm.turnOff() else vm.originalAiMasterEnabled(true) },
                    speakerChosen = speakerChosen, speakerReady = originalSpeaker.enabled && !originalSpeaker.loading, speakerLoading = originalSpeaker.loading, speakerStatus = if (speakerChosen) originalSpeaker.message
                        else "开启外放沉浸式体验，多场景自由调节",
                    onSpeaker = { if (speakerChosen) vm.turnOff() else vm.originalSpeakerEnabled(true) },
                    moodEmotion = originalMood.emotion, songCover = currentSong?.cover, playing = playback.playing,
                    speakerScene = originalSpeaker.scene, onDetails = onDetails)
            }
            // Normal progress/success already lives in the master card and island.
            // Inserting a retry row on every successful use used to jump the list.
            if (!originalMaster.loading && !originalMaster.enabled &&
                originalMaster.message != io.github.currencortex.music.core.media.OriginalAiMasterState().message) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(originalMaster.message, Modifier.weight(1f).testTag("sound_master_status"), fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary)
                    if (!originalMaster.loading) MusicTextAction("重试", { vm.originalAiMasterEnabled(true) }, enabled = playback.mode != PlayerMode.CAST)
                    if (!originalMaster.available) MusicTextAction("导入引擎", { showEngine = true })
                }
            }
            if (!originalSpeaker.loading && !originalSpeaker.enabled &&
                originalSpeaker.message != io.github.currencortex.music.core.media.OriginalSpeakerState().message) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(originalSpeaker.message, Modifier.weight(1f).testTag("sound_speaker_status"), fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary)
                    if (!originalSpeaker.loading) MusicTextAction("重试", { vm.originalSpeakerEnabled(true) }, enabled = playback.mode != PlayerMode.CAST)
                    if (!originalSpeaker.available) MusicTextAction("导入引擎", { showEngine = true })
                }
            }
            if (moodFailed || moodChosen && !originalMood.loading && !originalMood.enabled) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(originalMood.message, Modifier.weight(1f).testTag("sound_original_mood_status"), fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary)
                    MusicTextAction("重试", vm::retryOriginalMood, Modifier.testTag("sound_original_mood_retry"),
                        enabled = playback.mode != PlayerMode.CAST)
                    if (!originalMood.available) MusicTextAction("导入引擎", { showEngine = true })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).height(28.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("官方精选音效", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (expandedCatalog) "收起" else "更多 ›",
                        Modifier.testTag("sound_catalog_expand").clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { expandedCatalog = !expandedCatalog }
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            if (smartFailed) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(originalSmart.message, Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    MusicTextAction("重试", vm::retryOriginalSmart, Modifier.testTag("sound_original_smart_retry"), enabled = playback.mode != PlayerMode.CAST)
                    if (!originalSmart.available) MusicTextAction("导入引擎", { showEngine = true })
                }
            }
            item(key = "smart") {
                val banner = page.banner.firstOrNull()
                val smartBanner = banner == null || banner.smart
                val bannerChosen = if (smartBanner) smartChosen else tileLoading == banner?.id ||
                    (original.requestedId ?: original.selectedId)?.let { it == banner?.id } == true
                val bannerLoading = if (smartBanner) originalSmart.loading else tileLoading == banner?.id || original.loading && bannerChosen
                val visible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "smart" } } }
                NativeSmartBanner(banner, dark, bannerChosen, bannerLoading,
                    if (bannerChosen) activeStatus else if (smartBanner && smartFailed) originalSmart.message else null,
                    visible && animationsActive,
                    playback.mode != PlayerMode.CAST || bannerChosen,
                    onApply = {
                        if (bannerChosen) vm.turnOff()
                        else if (banner != null) vm.useTile(banner)
                        else vm.originalSmartEnabled(true)
                    })
            }
            }
            if (page.error != null && !expandedCatalog) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(page.error!!, Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    MusicTextAction("重试", vm::refreshPage, Modifier.testTag("sound_page_retry"))
                }
            }
            if (actionError != null) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(actionError!!, Modifier.weight(1f).testTag("sound_tile_error"), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    MusicTextAction("关闭", { vm.actionError.value = null })
                }
            }
            if (!expandedCatalog && catalog.loading) item { Text("正在获取网易云音效…", Modifier.padding(horizontal = 16.dp)
                .testTag("sound_catalog_loading"), fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
            if (!expandedCatalog && catalog.error != null) item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(catalog.error!!, Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                MusicTextAction("重试", vm::refreshCatalog, Modifier.testTag("sound_catalog_retry"))
            } }
            if (!expandedCatalog && !catalog.loading && catalog.error == null && catalog.items.isEmpty()) item {
                Text("网易云暂未返回可用音效", Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            }
            if (!expandedCatalog) item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = ((maxWidth - 48.dp) / 3f).coerceIn(104.dp, 196.dp)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(if (page.featured.isEmpty()) catalog.items else page.featured.map { tile ->
                            catalog.items.firstOrNull { it.id == tile.id } ?: NeteaseSoundEffect(tile.id, tile.name, tile.description)
                        }, key = { it.id }) { effect ->
                            val chosen = tileLoading == effect.id || (original.requestedId ?: original.selectedId) == effect.id
                            OfficialSoundCard(effect, Modifier.width(cardWidth), chosen,
                                when {
                                    tileLoading == effect.id || original.loading && chosen -> "加载中…"
                                    original.error != null && chosen -> "加载失败"
                                    chosen -> if (original.processing && playback.playing) "使用中" else "已选择"
                                    effect.legacyMemberOriginal -> if (effect.memberType == 4) "SVIP" else "VIP"
                                    else -> "免费"
                                }, enabled = playback.mode != PlayerMode.CAST || chosen,
                                tile = page.featured.firstOrNull { it.id == effect.id }, dark = dark,
                                onDetails = {
                                    launchError = null
                                    if (catalog.items.none { it.id == effect.id }) tileDetails = page.featured.firstOrNull { it.id == effect.id }
                                    else selectedEffect = effect
                                }, onApply = {
                                    when {
                                        chosen -> vm.turnOff()
                                        catalog.items.none { it.id == effect.id } -> page.featured.firstOrNull { it.id == effect.id }?.let(vm::useTile)
                                        else -> vm.useOriginal(effect)
                                    }
                                })
                        }
                    }
                }
            } else {
                if (more.loading) item { Text("正在获取官方精选音效…", Modifier.padding(16.dp), fontSize = 13.sp,
                    color = colors.onSurfaceVariantSummary) }
                if (more.error != null) item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(more.error!!, Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    MusicTextAction("重试", vm::refreshMore, Modifier.testTag("sound_more_retry"))
                } }
                if (!more.loading && more.error == null && more.featured.isEmpty()) item {
                    Text("暂无官方精选音效", Modifier.padding(16.dp), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                }
                items(more.featured, key = { "more_${it.id}" }) { tile ->
                    val chosen = tileLoading == tile.id || (if (tile.smart) smartChosen else (original.requestedId ?: original.selectedId) == tile.id)
                    NativeMoreEffectRow(tile, chosen, tileLoading == tile.id || original.loading && chosen,
                        playback.mode != PlayerMode.CAST || chosen,
                        onDetails = { tileDetails = tile }, onApply = { if (chosen) vm.turnOff() else vm.useTile(tile) })
                }
            }
            if (!expandedCatalog) {
            if (original.error != null) item {
                Card(Modifier.padding(horizontal = 16.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("原版音效 · ${original.requestedName ?: original.selectedName.orEmpty()}",
                        Modifier.testTag("sound_original_selected"), fontSize = 17.sp)
                    if (original.loading) {
                        Text(when {
                        original.permissionPending -> "正在向网易云核验黑胶会员权益…"
                        original.restoring -> "正在重新校验并恢复 ${original.requestedName.orEmpty()}…"
                        else -> "正在下载并校验 ${original.requestedName.orEmpty()}…"
                        }, Modifier.testTag("sound_original_loading"), fontSize = 13.sp)
                    }
                    original.error?.let { Text(it, Modifier.testTag("sound_original_error"), color = colors.onSurfaceVariantSummary, fontSize = 13.sp) }
                    if (original.selectedId != null) {
                        Text(when {
                            playback.mode == PlayerMode.CAST -> "投屏由接收设备处理"
                            original.requestedId != null && original.requestedId != original.selectedId ->
                                "当前仍使用 ${original.selectedName.orEmpty()}，新音效就绪后切换"
                            !playback.playing -> "预设已就绪，开始本机播放后处理"
                            original.processing -> "正在使用原版引擎处理音频"
                            else -> "等待原版引擎处理音频…"
                        }, Modifier.testTag("sound_original_processing"), color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                        Text("均衡器参数已保留，可随时切回", color = colors.onSurfaceVariantSummary, fontSize = 12.sp)
                    }
                    if (original.error != null && original.available && original.requestedId != null &&
                        original.requestedId !in original.unsupported && !original.loading)
                        MusicTextAction("重试加载", vm::retryOriginal, Modifier.testTag("sound_original_retry"))
                    if (original.selectedId != null || original.requestedId != null)
                        MusicTextAction("关闭音效", vm::turnOff, Modifier.testTag("sound_original_clear"))
                } }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本机调音", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("免费智能分析，也可以按自己的喜好调节", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            item { Card(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("本机智能调音", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (options.smartEnabled) status else "依据网易云歌曲分析，随切歌调整均衡器",
                            Modifier.testTag("sound_smart_status"), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                    EffectButton(if (options.smartEnabled) "取消" else "使用", { if (options.smartEnabled) vm.turnOff() else vm.smartEnabled(true) },
                        Modifier.padding(start = 10.dp).testTag("sound_smart_action"),
                        selected = options.smartEnabled, enabled = canApply || options.smartEnabled)
                }
            } }
            if (options.smartEnabled) item {
                Card(Modifier.padding(horizontal = 16.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(playback.song?.name ?: "等待播放", fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val analysis = tuning.analysis
                    when {
                        tuning.loading -> Text("正在读取歌曲的声音特征…", color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                        analysis != null -> {
                            val metadata = buildList {
                                analysis.bpm?.let { add("${it.roundToInt()} BPM") }
                                analysis.loudnessLufs?.let { add(String.format(Locale.ROOT, "%.1f LUFS", it)) }
                            }.joinToString(" · ")
                            if (metadata.isNotBlank()) Text(metadata, Modifier.testTag("sound_song_analysis"), fontSize = 13.sp, color = colors.primary)
                            if (analysis.emotions.isNotEmpty()) Text(analysis.emotions.take(4).joinToString(" · "),
                                maxLines = 2, color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                        }
                        else -> Text(tuning.message, color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                    }
                    tuning.gains?.let { gains ->
                        Text(if (canApply && runtime.active && tuning.active) "当前歌曲调音已生效" else "开始本机播放后生效",
                            Modifier.testTag("sound_tuning_applied"), fontSize = 13.sp, color = colors.primary)
                        TuningCurve(gains)
                    }
                    Text("歌曲分析来自网易云，调音由本机完成；与网易云客户端的 AI 音效算法不同。",
                        color = colors.onSurfaceVariantSummary, fontSize = 12.sp)
                } }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本机音效", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("直接生效，也可以进入均衡器继续调节", color = colors.onSurfaceVariantSummary, fontSize = 12.sp)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.chunked(3).forEach { presets ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            presets.forEach { preset ->
                                val chosen = original.selectedId == null && !smartChosen && !moodChosen && !singleChosen && !options.smartEnabled && manual.enabled && manual.preset == preset
                                Card(Modifier.weight(1f).testTag("sound_local_${preset.name.lowercase()}")
                                    .semantics { selected = chosen }
                                    .clickable(enabled = canApply, role = Role.Button) { vm.useLocal(preset) }) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 17.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(if (chosen) MusicIcons.Check else MusicIcons.Headphones, null,
                                            tint = if (chosen) colors.primary else colors.onSurfaceVariantSummary, modifier = Modifier.size(22.dp))
                                        Text(preset.label, fontSize = 15.sp, color = if (chosen) colors.primary else colors.onSurface)
                                        Text(if (chosen) "使用中" else "使用", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            }
        }
    }
    }
    tileDetails?.let { tile -> MusicDialog(tile.name, onDismiss = { tileDetails = null }) {
        Text(tile.description, fontSize = 14.sp)
        MusicTextAction("使用", { vm.useTile(tile); tileDetails = null }, enabled = playback.mode != PlayerMode.CAST)
        MusicTextAction("返回音效页", { tileDetails = null })
    } }
    if (showEngine) MusicDialog("原版音效引擎", onDismiss = { showEngine = false }) {
        Text(if (original.available && originalMood.available) "引擎已就绪，支持原版音效和 AI 情绪调音师，无需额外导入。"
            else "从本机网易云音乐或安装包导入兼容引擎。会员音效使用时会核验当前账号权益。",
            fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
        engineImport.message?.let { Text(it, Modifier.testTag("sound_engine_import_status"), fontSize = 13.sp,
            color = if (engineImport.failed) colors.onSurfaceVariantSummary else colors.primary) }
        if (!original.available || !originalMood.available) {
            MusicTextAction("从已安装的网易云导入", vm::importInstalledOriginal,
                Modifier.testTag("sound_import_installed"), enabled = !engineImport.loading)
            MusicTextAction("选择网易云安装包", {
                selectNeteaseApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
            }, Modifier.testTag("sound_import_apk"), enabled = !engineImport.loading)
        }
        MusicTextAction("完成", { showEngine = false })
    }
    selectedEffect?.let { effect ->
        val unavailable = vm.originalUnavailableReason(effect)
        MusicDialog(effect.name, onDismiss = { selectedEffect = null }) {
            Text(effect.description, fontSize = 15.sp)
            Text(unavailable ?: if (effect.legacyMemberOriginal)
                "此音效需要有效的网易云${if (effect.memberType == 4) "SVIP" else "黑胶会员"}。使用前会向网易云核验当前账号权益，到期自动关闭；可在“我的 → 网易云账号”登录。选择会保存，重启后重新核验。"
                else "使用网易云公开的原版预设与原生音效引擎。选择会保存，重启后重新下载并校验；启用后暂时替代本机智能调音和均衡器。",
                color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
            if (playback.mode == PlayerMode.CAST) Text("请结束投屏后使用本机原版音效", color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
            if (playback.mode != PlayerMode.CAST) {
                MusicTextAction(if (unavailable != null) "重新检查并使用" else if (effect.legacyMemberOriginal) "核验权益并使用" else if (original.selectedId == effect.id) "重新载入原版音效" else "使用原版音效", {
                    vm.useOriginal(effect)
                    selectedEffect = null
                }, Modifier.testTag("sound_use_original"))
            }
            launchError?.let { Text(it, color = colors.onSurfaceVariantSummary, fontSize = 13.sp) }
            MusicTextAction("前往网易云音效", {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("orpheus://effect")).setPackage("com.netease.cloudmusic"))
                    launchError = null
                } catch (_: ActivityNotFoundException) { launchError = "请先安装网易云音乐客户端" }
                catch (_: SecurityException) { launchError = "暂时无法打开网易云音乐" }
            }, Modifier.testTag("sound_open_netease"))
            MusicTextAction("返回音效页", { selectedEffect = null }, Modifier.testTag("sound_official_close"))
        }
    }
}

@Composable private fun OfficialSoundCard(effect: NeteaseSoundEffect, modifier: Modifier, chosen: Boolean,
    status: String, enabled: Boolean, tile: NativeEffectTile? = null, dark: Boolean = false,
    onDetails: () -> Unit, onApply: () -> Unit) {
    val background = if (dark) Color(0xFF1C2028) else Color(0xFFE4EAF2)
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(background)
        .border(if (chosen) 1.5.dp else 0.dp, if (chosen) MiuixTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))) {
        if (tile != null && tile.image(dark).isNotBlank()) EffectsArtwork(tile.image(dark), Modifier.matchParentSize())
        else if (effect.artwork.isNotBlank()) EffectsArtwork(effect.artwork, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(62.dp)) {
                EffectsMembership(tile?.membership ?: effect.memberType, Modifier.align(Alignment.TopEnd).padding(8.dp))
                if (tile == null && effect.artwork.isBlank()) Icon(MusicIcons.Headphones, null,
                    Modifier.align(Alignment.Center).size(28.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f))
            }
            Column(Modifier.fillMaxWidth().testTag("sound_official_${effect.id}").semantics { selected = chosen }
                .clickable(role = Role.Button, onClick = onDetails).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(effect.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tile?.description ?: effect.description, Modifier.padding(top = 2.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SoundAction(if (chosen) (if (status == "加载中…") "加载中…" else "取消") else "使用", onApply,
                Modifier.padding(top = 10.dp, bottom = 12.dp).width(60.dp).testTag("sound_apply_${effect.id}"), enabled)
        }
    }
}

@Composable private fun EffectButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
        .background(if (selected) colors.primary.copy(alpha = if (enabled) 1f else .35f)
            else colors.onSurface.copy(alpha = if (enabled) .06f else .025f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) colors.onPrimary else colors.onSurface, fontSize = 13.sp)
    }
}

@Composable private fun SoundAction(label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    NativeEffectsAction(label, onClick, modifier, enabled)
}

@Composable private fun TuningCurve(gains: List<Int>) {
    val accent = MiuixTheme.colorScheme.primary
    val grid = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .18f)
    Canvas(Modifier.fillMaxWidth().height(60.dp).testTag("sound_tuning_curve")) {
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        val path = Path()
        gains.forEachIndexed { index, value ->
            val x = 5.dp.toPx() + (size.width - 10.dp.toPx()) * index / 4f
            val y = size.height / 2 - value.coerceIn(-300, 300) / 300f * size.height * .4f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(accent, 2.5.dp.toPx(), Offset(x, y))
        }
        drawPath(path, accent, style = Stroke(2.dp.toPx()))
    }
}
