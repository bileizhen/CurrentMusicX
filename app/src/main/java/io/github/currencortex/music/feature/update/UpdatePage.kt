package io.github.currencortex.music.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.config.AppMetadata
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.update.UpdateSettings
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.SettingsSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.darkColorScheme

private val pageWhite = Color(0xFFF7F4FF)
private val pageMuted = Color(0xFFC6BDD9)
private val glassShape = RoundedCornerShape(26.dp)

@Composable
internal fun UpdatePageHost(visible: Boolean, state: UpdateState, onDismiss: () -> Unit,
    onRetry: () -> Unit, onIgnore: () -> Unit, onOpenRelease: (String) -> Unit,
    transferState: UpdateTransferState, onDownload: (UpdateSource) -> Unit,
    onInstall: () -> Unit, onCancel: () -> Unit, onChangeSource: (UpdateSource) -> Unit,
    downloadExperience: Boolean, settings: UpdateSettings, onAutoCheck: (Boolean) -> Unit,
    onChannel: (UpdateChannel) -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(280)) + slideInHorizontally(tween(420)) { it / 5 },
        exit = fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { it / 5 }) {
        val context = LocalContext.current
        val notes by produceState("正在读取更新说明…") {
            value = withContext(Dispatchers.IO) {
                runCatching { installedReleaseNotes(context.assets.open("release-history.md").bufferedReader().use { it.readText() },
                    BuildConfig.VERSION_NAME) }.getOrDefault("此版本未附带更新说明，可查看完整发布记录。")
            }
        }
        UpdatePageContent(state, transferState, notes, onDismiss, onRetry, onIgnore, onOpenRelease,
            onDownload, onInstall, onCancel, onChangeSource, downloadExperience, settings, onAutoCheck, onChannel)
    }
}

/** One surface morphs between status and notes; the background and scroll owner stay mounted. */
@Composable
fun UpdatePageContent(state: UpdateState, transferState: UpdateTransferState = UpdateTransferState(),
    installedNotes: String = "", onDismiss: () -> Unit = {}, onRetry: () -> Unit = {},
    onIgnore: () -> Unit = {}, onOpenRelease: (String) -> Unit = {},
    onDownload: (UpdateSource) -> Unit = {}, onInstall: () -> Unit = {}, onCancel: () -> Unit = {},
    onChangeSource: (UpdateSource) -> Unit = {}, downloadExperience: Boolean = false,
    settings: UpdateSettings = UpdateSettings(), onAutoCheck: (Boolean) -> Unit = {},
    onChannel: (UpdateChannel) -> Unit = {}) {
    val release = (state as? UpdateState.Available)?.release
    val installedVersion = BuildConfig.VERSION_NAME.substringBefore('-')
    var heroVersion by remember { mutableStateOf(installedVersion) }
    LaunchedEffect(state) {
        when (state) {
            is UpdateState.Available -> heroVersion = state.release.version
            UpdateState.UpToDate -> heroVersion = installedVersion
            else -> Unit // Rechecking must not first morph back to the installed version.
        }
    }
    val checking = state == UpdateState.Checking || state == UpdateState.Idle
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(UpdateSource.default) }
    LaunchedEffect(transferState.activeSource) { transferState.activeSource?.let { source = it } }
    val settled by animateFloatAsState(if (checking) 0f else 1f, tween(680, easing = FastOutSlowInEasing), label = "update_check_result")
    val detail by animateFloatAsState(if (expanded) 1f else 0f, tween(600, easing = FastOutSlowInEasing), label = "update_details_morph")
    val notesScroll = rememberScrollState()
    BackHandler {
        when {
            confirmCancel -> confirmCancel = false
            showSources -> showSources = false
            showSettings -> showSettings = false
            menu -> menu = false
            expanded -> expanded = false
            else -> onDismiss()
        }
    }
    val colors = remember { darkColorScheme(primary = Color(0xFFB8A6FF), onBackground = pageWhite,
        background = Color(0xFF100D29), surface = Color(0xFF201C35), surfaceContainer = Color(0xFF29253F)) }
    MiuixTheme(colors = colors) {
    CompositionLocalProvider(LocalContentColor provides pageWhite) {
    Box(Modifier.fillMaxSize().testTag("update_page").background(Color(0xFF100D29))
        .updateRibbonBackground().pointerInput(Unit) { detectTapGestures { menu = false } }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (expanded) expanded = false else onDismiss() },
                    modifier = Modifier.background(Color.White.copy(alpha = .09f), CircleShape).testTag("update_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = pageWhite)
                }
                Text(if (expanded) "更新说明" else "软件更新", fontSize = 19.sp, color = pageWhite,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = { menu = !menu },
                    modifier = Modifier.background(Color.White.copy(alpha = .09f), CircleShape).testTag("update_menu")) {
                    Icon(Icons.Default.MoreVert, "更多", tint = pageWhite)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width = maxWidth
                val height = maxHeight
                val busy = transferState.download !is UpdateDownloadState.Idle
                val fontScale = LocalDensity.current.fontScale
                val compact = height < 400.dp
                val targetHeight = when {
                    transferState.download is UpdateDownloadState.Ready -> if (downloadExperience) 274.dp else 222.dp
                    busy -> 288.dp
                    release != null -> 232.dp
                    state is UpdateState.Failed -> 236.dp
                    else -> 192.dp
                } + (80 * (fontScale - 1).coerceAtLeast(0f)).dp
                val summaryHeight by animateDpAsState(targetHeight
                    .coerceAtMost((height - 24.dp).coerceAtLeast(120.dp)), tween(480), label = "update_transfer_card_height")
                val compactTop = (height - summaryHeight - 20.dp).coerceAtLeast(12.dp)
                val checkingTop = (height - 88.dp).coerceAtLeast(12.dp)
                val statusTop = checkingTop + (compactTop - checkingTop) * settled
                val top = statusTop + (12.dp - statusTop) * detail
                val heroY = height * (.30f - .15f * settled) - 100.dp * detail
                Column(Modifier.fillMaxWidth().offset(y = heroY).graphicsLayer {
                    alpha = 1f - detail; scaleX = 1f - .18f * detail; scaleY = scaleX
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphingVersionNumber(heroVersion, installedVersion,
                        fontSize = if (width < 360.dp) 66.sp else 78.sp, color = pageWhite,
                        modifier = Modifier.testTag("update_hero_version"))
                    Text("CurrentMusic", color = pageWhite, fontSize = 25.sp, fontWeight = FontWeight.Light)
                    Text(if (release?.prerelease == true || settings.channel == UpdateChannel.PRERELEASE) "预发布版" else "正式版",
                        color = pageMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
                Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 20.dp).offset(y = top).widthIn(max = 620.dp).fillMaxWidth()
                    .height((height - top - 20.dp).coerceAtLeast(56.dp)).clip(glassShape)
                    .background(Color(0xB51C1930)).border(1.dp, Color.White.copy(alpha = .14f), glassShape)
                    .testTag("update_status_card")) {
                    Column(Modifier.fillMaxSize().padding(18.dp)
                        .then(if (compact && !expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        AnimatedContent(expanded, if (compact && !expanded) Modifier.fillMaxWidth().heightIn(min = 62.dp) else Modifier.weight(1f), label = "update_card_content",
                            transitionSpec = { fadeIn(tween(300, delayMillis = 90)) togetherWith fadeOut(tween(180)) },
                            contentAlignment = Alignment.TopStart) { showDetails ->
                        if (showDetails) {
                            Column(Modifier.fillMaxSize().verticalScroll(notesScroll).testTag("update_message"),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("软件版本", fontSize = 16.sp, color = pageWhite, fontWeight = FontWeight.Medium)
                                Text("CurrentMusic ${release?.version ?: BuildConfig.VERSION_NAME}" +
                                    (release?.let { " · ${updateSize(it.size)}" } ?: ""), fontSize = 12.sp, color = pageMuted)
                                HorizontalDivider(color = Color.White.copy(alpha = .12f))
                                Row(Modifier.fillMaxWidth().clickable { expanded = false }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("更新说明", color = pageWhite, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    Text("收起 ⌃", color = pageMuted, fontSize = 12.sp)
                                }
                                MarkdownText(release?.notes?.ifBlank {
                                    if (release.version.substringBefore('-') == BuildConfig.VERSION_NAME.substringBefore('-')) installedNotes else ""
                                }?.let { stripVersionHeadings(release.version, it) }
                                    ?.ifBlank { "新版本已发布。" } ?: installedNotes.ifBlank { "此版本未附带更新说明。" }, compact = true)
                                Spacer(Modifier.height(8.dp))
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.fillMaxHeight().verticalScroll(rememberScrollState())),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(when {
                                        transferState.download is UpdateDownloadState.Ready -> "下载完成"
                                        transferState.download is UpdateDownloadState.Downloading -> "正在下载更新"
                                        transferState.download is UpdateDownloadState.Failed -> "下载未完成"
                                        checking -> "正在检查更新"
                                        state is UpdateState.Failed -> "暂时无法检查更新"
                                        release != null -> if (downloadExperience) "下载体验" else "发现新版本"
                                        else -> "已是最新版本"
                                    }, color = pageWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f))
                                    if (checking) CircularProgressIndicator(Modifier.size(20.dp))
                                }
                                if (!checking) Text(release?.let { "v${it.version} · ${updateSize(it.size)}" }
                                    ?: "当前版本 ${BuildConfig.VERSION_NAME}", color = pageMuted, fontSize = 12.sp)
                                if (state is UpdateState.Failed) Text(state.reason, color = pageMuted, fontSize = 13.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        }
                        if (!checking || busy) {
                            if (release != null) {
                                TransferSummary(transferState, source, onSources = { showSources = true })
                                val downloading = transferState.download is UpdateDownloadState.Downloading
                                val ready = transferState.download is UpdateDownloadState.Ready
                                PageButton(when {
                                    transferState.installing -> "请求安装…"
                                    downloading -> "取消下载"
                                    ready -> "点击安装"
                                    !verifiedUpdate(release) -> "查看发布"
                                    transferState.download is UpdateDownloadState.Failed -> "重试下载"
                                    else -> "下载并安装"
                                }, "update_download", enabled = !transferState.installing) {
                                    when {
                                        downloading -> confirmCancel = true
                                        ready -> onInstall()
                                        verifiedUpdate(release) -> onDownload(source)
                                        else -> onOpenRelease(release.pageUrl)
                                    }
                                }
                                if (ready && downloadExperience) PageButton("再次下载", "update_redownload", primary = false) {
                                    onDownload(source)
                                }
                            } else if (state is UpdateState.Failed || !expanded) {
                                PageButton(if (state is UpdateState.Failed) "重新检查" else "检查更新", "update_retry", onClick = onRetry)
                            }
                            if (!expanded) PageButton("查看更新说明", "update_details", primary = false) { expanded = true }
                        }
                    }
                }
            }
        }
        if (menu) {
            Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 20.dp)
                .width(180.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF29243F))
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp))) {
                MenuItem("重新检查") { menu = false; onRetry() }
                MenuItem("更新设置") { menu = false; showSettings = true }
                MenuItem("发布记录") { menu = false; onOpenRelease(AppMetadata.RELEASES_URL) }
                if (release != null && transferState.download is UpdateDownloadState.Idle)
                    MenuItem("忽略此版本") { menu = false; onIgnore() }
            }
        }
        if (showSources) MusicDialog("下载来源", { showSources = false }) {
            Text(if (transferState.download is UpdateDownloadState.Downloading) "切换来源会从头重新下载" else "失败时自动尝试其他来源",
                fontSize = 13.sp, color = pageMuted)
            UpdateSource.available.forEach { candidate ->
                MenuItem((if (candidate == source) "✓  " else "") + candidate.label) {
                    if (source != candidate) {
                        source = candidate
                        if (transferState.download is UpdateDownloadState.Downloading) onChangeSource(candidate)
                    }
                    showSources = false
                }
            }
        }
        if (showSettings) MusicDialog("更新设置", { showSettings = false }) {
            SettingsSwitch("启动时自动检查", settings.autoCheckOnLaunch, onAutoCheck)
            listOf(UpdateChannel.STABLE to "正式版", UpdateChannel.PRERELEASE to "预发布版").forEach { (channel, label) ->
                MenuItem((if (settings.channel == channel) "✓  " else "") + label) {
                    showSettings = false; onChannel(channel)
                }
            }
        }
        if (confirmCancel) MusicDialog("取消下载？", { confirmCancel = false }) {
            Text("返回页面后仍会在后台下载。只有取消下载才会停止当前任务。", fontSize = 14.sp)
            PageButton("继续下载", "update_keep_download") { confirmCancel = false }
            PageButton("取消下载", "update_confirm_cancel", primary = false) { confirmCancel = false; onCancel() }
        }
    }
    }
    }
}

@Composable private fun TransferSummary(state: UpdateTransferState, source: UpdateSource, onSources: () -> Unit) {
    when (val download = state.download) {
        is UpdateDownloadState.Downloading -> {
            val progress by animateFloatAsState((download.received.toDouble() / download.total.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                tween(220), label = "update_page_progress")
            Text("${updateSize(download.received)} / ${updateSize(download.total)} · ${(progress * 100).toInt()}%",
                fontSize = 12.sp, color = pageMuted, modifier = Modifier.testTag("update_progress"))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        is UpdateDownloadState.Failed -> Text(download.message, fontSize = 12.sp, color = Color(0xFFFFBCB4),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        is UpdateDownloadState.Ready -> Text("已校验安装包，安装需经系统确认", fontSize = 12.sp, color = pageMuted)
        UpdateDownloadState.Idle -> Unit
    }
    state.message?.let { Text(it, fontSize = 12.sp, color = pageMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    if (state.download !is UpdateDownloadState.Ready) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .clickable(role = Role.Button, enabled = !state.installing, onClick = onSources).padding(vertical = 10.dp)
        .testTag("update_source")) {
        Text("下载源", color = pageMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("${state.activeSource?.label ?: source.label}  ›", color = pageWhite, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
    }
}

@Composable private fun PageButton(label: String, tag: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 46.dp).clip(CircleShape)
        .background(if (primary) pageWhite else Color.White.copy(alpha = .13f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp)
        .testTag(tag), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = (if (primary) Color(0xFF201934) else pageWhite).copy(alpha = if (enabled) 1f else .5f))
    }
}

@Composable private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(label, color = pageWhite, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp))
}

private fun verifiedUpdate(release: AppRelease) = release.apkUrl != null &&
    release.size in 1..UpdateDownloader.MAX_APK_BYTES && Regex("[a-f0-9]{64}").matches(release.sha256)

private fun updateSize(bytes: Long): String = if (bytes <= 0) "0 MB" else
    String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))

/** Static cached paths, no bitmap decoding, shader loop or per-frame background animation. */
private fun Modifier.updateRibbonBackground() = drawWithCache {
    val w = size.width; val h = size.height
    val base = Brush.verticalGradient(listOf(Color(0xFF100D29), Color(0xFF18113C), Color(0xFF392879), Color(0xFF100D29)))
    val glow = Brush.radialGradient(listOf(Color(0xFFA086ED).copy(alpha = .7f), Color.Transparent),
        Offset(w * .65f, h * .55f), w * .85f)
    val lower = Path().apply {
        moveTo(-w * .2f, h * .62f); cubicTo(w * .2f, h * .34f, w * .55f, h * .90f, w * 1.2f, h * .62f)
        lineTo(w * 1.2f, h * .90f); cubicTo(w * .6f, h * .98f, w * .15f, h * .55f, -w * .2f, h * .82f); close()
    }
    val upper = Path().apply {
        moveTo(w * .3f, h * .59f); cubicTo(w * .18f, h * .35f, w * .95f, h * .28f, w * 1.2f, h * .45f)
        lineTo(w * 1.2f, h * .71f); cubicTo(w * .85f, h * .49f, w * .7f, h * .81f, w * .3f, h * .59f); close()
    }
    val warm = Brush.verticalGradient(0f to Color(0xFFA389DB), .36f to Color(0xFFFBBF89),
        .5f to Color(0xFFFF8745), .64f to Color(0xFFBC79DD), 1f to Color(0xFF3A2C79), startY = h * .3f, endY = h * .92f)
    val edge = Color(0xFFE7C0FF).copy(alpha = .22f)
    onDrawBehind {
        drawRect(base); drawRect(glow); drawPath(lower, warm); drawPath(lower, edge, style = Stroke(1.dp.toPx()))
        drawPath(upper, warm); drawPath(upper, edge, style = Stroke(1.dp.toPx()))
    }
}
