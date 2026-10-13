package io.github.currencortex.music.feature.mv

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.library.MvDto
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibraryDetailState
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun MvDetailScreen(state: LibraryDetailState, container: AppContainer, onBack: () -> Unit,
    onPlay: (MvDto) -> Unit, onArtist: (Long) -> Unit, onRefresh: () -> Unit,
    onDialogActive: (Boolean) -> Unit = {}) {
    val mv = state.mv
    val song = remember(mv) { mv?.let { Song(-it.id, it.name, it.artistName, cover = it.cover,
        durationMs = it.duration, mv = it.id, video = true) } }
    var download by rememberSaveable(mv?.id) { mutableStateOf(false) }
    val dialogCallback by rememberUpdatedState(onDialogActive)
    LaunchedEffect(download) { dialogCallback(download) }
    DisposableEffect(Unit) { onDispose { dialogCallback(false) } }
    val downloadLabel = if (song != null) {
        val work by remember(song.mv) { container.mvDownloads.observe(song) }.collectAsStateWithLifecycle(emptyList())
        work.lastOrNull().mvLabel()
    } else "下载 MV"
    MusicPullToRefresh(state.loading || state.refreshing, onRefresh, Modifier.fillMaxSize()) {
        MvDetailContent(state, downloadLabel, onBack, { mv?.let(onPlay) }, { download = true },
            { mv?.artistId?.takeIf { it > 0 }?.let(onArtist) }, onRefresh)
    }
    if (download && song != null) MvDownloadDialog(song, container) { download = false }
}

@Composable internal fun MvDetailContent(state: LibraryDetailState, downloadLabel: String, onBack: () -> Unit,
    onPlay: () -> Unit, onDownload: () -> Unit, onArtist: () -> Unit, onRetry: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()
        .testTag("mv_detail")) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(musicScrollPadding(PaddingValues(horizontal = 20.dp, vertical = 12.dp))),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                    .testTag("mv_detail_back"), contentAlignment = Alignment.Center) {
                    Icon(MusicIcons.Back, "返回", modifier = Modifier.size(24.dp))
                }
                Text("音乐视频", Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            val mv = state.mv
            if (mv != null) {
                if (wide) Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    MvDetailCover(mv, onPlay, Modifier.weight(1.25f))
                    MvDetailInfo(mv, downloadLabel, onPlay, onDownload, onArtist, Modifier.weight(1f))
                } else Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    MvDetailCover(mv, onPlay, Modifier.fillMaxWidth())
                    MvDetailInfo(mv, downloadLabel, onPlay, onDownload, onArtist, Modifier.fillMaxWidth())
                }
            } else if (state.loading) {
                MusicPlaceholder(Modifier.widthIn(max = 640.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)))
                Text("正在加载 MV…", Modifier.padding(24.dp), fontSize = 14.sp, color = colors.onSurface.copy(alpha = .55f))
            } else Column(Modifier.fillMaxWidth().padding(vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("暂时无法打开 MV", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(state.error ?: "未找到视频信息", fontSize = 14.sp, color = colors.onSurface.copy(alpha = .6f))
                TextButton("重新加载", onRetry, Modifier.testTag("mv_detail_retry"))
            }
        }
    }
}

@Composable private fun MvDetailCover(mv: MvDto, onPlay: () -> Unit, modifier: Modifier) {
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp))
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .08f))
        .clickable(role = Role.Button, onClick = onPlay).testTag("mv_detail_cover")) {
        // Keep the original video thumbnail ratio; album artwork requests use square crops.
        AsyncImage(mv.cover, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .45f)))))
        Box(Modifier.align(Alignment.Center).size(58.dp).background(Color.Black.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(MusicIcons.Play, "播放 MV", Modifier.size(26.dp), tint = Color.White)
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MV", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (mv.duration > 0) {
                val seconds = mv.duration / 1000
                Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun MvDetailInfo(mv: MvDto, downloadLabel: String, onPlay: () -> Unit,
    onDownload: () -> Unit, onArtist: () -> Unit, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    var expanded by rememberSaveable(mv.id) { mutableStateOf(false) }
    var overflows by remember(mv.id) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(mv.name.ifBlank { "音乐视频" }, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 35.sp)
        if (mv.artistName.isNotBlank()) Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = mv.artistId > 0, role = Role.Button, onClick = onArtist).testTag("mv_detail_artist"),
            verticalAlignment = Alignment.CenterVertically) {
            Text(mv.artistName, Modifier.weight(1f, fill = false), fontSize = 15.sp, color = colors.onSurface.copy(alpha = .6f))
            if (mv.artistId > 0) Text("›", Modifier.padding(start = 8.dp), fontSize = 20.sp, color = colors.onSurface.copy(alpha = .4f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("播放 MV", onPlay, Modifier.weight(1f).testTag("play_mv"),
                colors = ButtonDefaults.textButtonColors(color = colors.primary, textColor = colors.onPrimary))
            TextButton(downloadLabel, onDownload, Modifier.weight(1f).testTag("mv_detail_download"))
        }
        mv.desc?.trim()?.takeIf { it.isNotBlank() }?.let { description ->
            HorizontalDivider(color = colors.onSurface.copy(alpha = .08f))
            Text("视频简介", Modifier.padding(top = 14.dp, bottom = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Column(Modifier.animateContentSize()) {
                Text(description, fontSize = 14.sp, lineHeight = 23.sp, color = colors.onSurface.copy(alpha = .6f),
                    maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }, modifier = Modifier.testTag("mv_detail_description"))
                if (overflows || expanded) MusicTextAction(if (expanded) "收起简介" else "展开简介", { expanded = !expanded }, Modifier.testTag("mv_detail_expand"))
            }
        }
    }
}
