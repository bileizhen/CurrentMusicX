package io.github.currencortex.music.feature.roaming

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.roaming.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.core.media.PlayerState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun PersonalRoamingScreen(container: AppContainer, onBack: () -> Unit,
    onBinding: () -> Unit, onPlayer: () -> Unit) {
    val station = container.personalRoaming
    val state by station.state.collectAsStateWithLifecycle()
    val playbackSummary = remember(container) { container.playerController.state.map {
        PlayerState(song = it.song, playing = it.playing, playRequested = it.playRequested)
    }.distinctUntilChanged() }
    val playback by playbackSummary.collectAsStateWithLifecycle(initialValue = PlayerState())
    val account by container.neteaseSessions.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(!state.active) }
    var settings by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(Unit) { station.loadCatalog() }
    LaunchedEffect(state.active, state.choice.key) { if (state.active) expanded = false }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("personal_roaming"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainer)
                    .clickable(role = Role.Button, onClick = onBack).testTag("roaming_back"), contentAlignment = Alignment.Center) {
                    Icon(MusicIcons.Back, "返回", Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable { expanded = !expanded }
                    .testTag("roaming_mode_picker")) {
                    Text("私人漫游", fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Text(state.choice.title + if (expanded) "  ⌃" else "  ⌄", fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MusicTextAction("设置", { settings = true }, Modifier.testTag("roaming_settings"))
            }
        }
        if (!account.loggedIn || account.stale) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("让推荐更懂你", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("登录本机网易云账号后开启私人漫游", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                MusicTextAction("登录网易云 ›", onBinding, Modifier.testTag("roaming_login"))
            }
        }
        state.error?.let { error -> item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(error, fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.testTag("roaming_error"))
                MusicTextAction("重试推荐", station::retry, Modifier.testTag("roaming_retry"), enabled = !state.loading)
            }
        } }
        if (state.active && !expanded) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.choice.code == "PUZZLE_MODE_RCMD") RoamingCoverPuzzle(playback.song?.cover.orEmpty(), playback.song?.id)
                else MusicCover(playback.song?.cover.orEmpty(), Modifier.fillMaxWidth().aspectRatio(1f), 900, 24.dp)
                Text(playback.song?.name.orEmpty(), fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(playback.song?.artists.orEmpty(), fontSize = 14.sp, color = colors.onSurfaceVariantSummary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.loading) Text(if (state.waiting) "正在寻找下一首…" else "正在补充推荐…", fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary, modifier = Modifier.testTag("roaming_loading"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoamingTransport("上一首", MusicIcons.PreviousTrack, "roaming_previous") { container.playerController.previous() }
                    RoamingTransport(if (playback.showPause) "暂停" else "播放", if (playback.showPause) MusicIcons.Pause else MusicIcons.Play,
                        "roaming_toggle") { container.playerController.toggle() }
                    RoamingTransport("下一首", MusicIcons.NextTrack, "roaming_next") { container.playerController.next() }
                }
                MusicTextAction("打开完整播放器 ›", onPlayer, Modifier.testTag("roaming_player"))
            }
        }
        if (expanded || !state.active) {
            items(state.catalog.modes.size, key = { "mode:${state.catalog.modes[it].key}" }) { index ->
                val mode = state.catalog.modes[index]
                val selected = state.choice.key == mode.key
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (selected) colors.primary.copy(alpha = .12f) else colors.surfaceContainer)
                    .clickable(role = Role.Button, enabled = !state.loading && state.ready) { station.start(mode) }
                    .testTag("roaming_mode_${mode.code}").padding(horizontal = 18.dp, vertical = 17.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(mode.title, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                        color = if (selected) colors.primary else colors.onSurface)
                    if (mode.subtitle.isNotBlank()) Text(mode.subtitle, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            item { Text("场景模式", fontSize = 15.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(8.dp)) {
                    state.catalog.scenes.chunked(4).forEach { group ->
                        Row(Modifier.fillMaxWidth()) {
                            group.forEach { choice ->
                                val selected = state.choice.key == choice.key
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) colors.primary.copy(alpha = .1f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(role = Role.Button, enabled = !state.loading && state.ready) { station.start(choice) }
                                    .testTag("roaming_scene_${choice.code}").padding(vertical = 16.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    if (choice.icon.isNotBlank()) AsyncImage(choice.icon, null, Modifier.size(26.dp),
                                        colorFilter = ColorFilter.tint(if (selected) colors.primary else colors.onSurfaceVariantSummary))
                                    else Icon(MusicIcons.Headphones, null, Modifier.size(26.dp), tint = colors.onSurfaceVariantSummary)
                                    Text(choice.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (selected) colors.primary else colors.onSurfaceVariantSummary)
                                }
                            }
                            repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (state.loading) item { Text("正在获取推荐…", Modifier.testTag("roaming_loading"), fontSize = 13.sp) }
            state.catalogError?.let { error -> item {
                Text("场景列表暂未刷新：$error", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                MusicTextAction("刷新场景", station::loadCatalog, Modifier.testTag("roaming_reload_catalog"))
            } }
        }
        if (state.active) item { MusicTextAction("退出私人漫游", { station.stop(); expanded = true },
            Modifier.fillMaxWidth().testTag("roaming_stop"), destructive = true) }
    }
    if (settings) MusicDialog("漫游设置", { settings = false }, footer = {
        MusicDialogButton("完成", { settings = false }, Modifier.testTag("roaming_settings_done"))
    }) {
        SettingsSwitch("提前补充推荐", state.settings.preload, { station.updateSettings(state.settings.copy(preload = it)) }, Modifier.testTag("roaming_preload"))
        SettingsSwitch("退出时恢复原队列", state.settings.restoreQueue, { station.updateSettings(state.settings.copy(restoreQueue = it)) }, Modifier.testTag("roaming_restore_queue"))
        Text("设置按本机网易云账号分别保存", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    }
}

@Composable private fun RoamingTransport(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String, action: () -> Unit) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(MiuixTheme.colorScheme.background)
        .clickable(role = Role.Button, onClick = action).testTag(tag), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(26.dp), tint = MiuixTheme.colorScheme.onSurface)
    }
}

/** Swap any two tiles to complete the current song's cover; the recommendation mode stays native. */
@Composable private fun RoamingCoverPuzzle(cover: String, songId: Long?) {
    val context = LocalContext.current
    var bitmap by remember(cover) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(cover) { mutableStateOf(false) }
    var tiles by rememberSaveable(songId) { mutableStateOf(listOf(4, 1, 6, 3, 8, 0, 7, 2, 5)) }
    var selected by remember(songId) { mutableStateOf<Int?>(null) }
    val complete = tiles == (0..8).toList()
    LaunchedEffect(cover) {
        bitmap = null; failed = false
        if (cover.isBlank()) { failed = true; return@LaunchedEffect }
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(cover).size(900).build())
        bitmap = (result as? SuccessResult)?.image?.toBitmap()
        failed = bitmap == null
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).testTag("roaming_puzzle")) {
            val image = bitmap?.asImageBitmap()
            if (image == null) {
                MusicCover(cover, Modifier.fillMaxSize(), 900, 22.dp)
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                tiles.chunked(3).forEachIndexed { row, values ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        values.forEachIndexed { column, tile ->
                            val index = row * 3 + column
                            Canvas(Modifier.weight(1f).fillMaxHeight().border(if (selected == index) 2.dp else 0.dp,
                                MiuixTheme.colorScheme.primary).clickable(enabled = !complete) {
                                val first = selected
                                if (first == null) selected = index else {
                                    tiles = tiles.toMutableList().apply { val old = this[first]; this[first] = this[index]; this[index] = old }
                                    selected = null
                                }
                            }.testTag("roaming_puzzle_tile_$index")) {
                                val side = minOf(image.width, image.height) / 3
                                drawImage(image, IntOffset((image.width - side * 3) / 2 + tile % 3 * side,
                                    (image.height - side * 3) / 2 + tile / 3 * side), IntSize(side, side),
                                    IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()))
                            }
                        }
                    }
                }
            }
        }
        Text(if (complete) "拼好了，继续聆听这首歌" else if (failed) "封面暂不可用，歌曲继续播放" else "轻点两块碎片交换位置",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (complete) MusicTextAction("再拼一次", { tiles = (0..8).shuffled(); selected = null })
    }
}
