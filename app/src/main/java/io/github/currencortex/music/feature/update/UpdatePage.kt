package io.github.currencortex.music.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.config.AppMetadata
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.update.UpdateSettings
import io.github.currencortex.music.ui.component.ArtworkGlowPalette
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.blur.layerBackdrop

private val pageWhite = Color(0xFFF7F4FF)
private val pageMuted = Color(0xFFC6BDD9)
private val glassShape = RoundedCornerShape(26.dp)

@Composable
internal fun UpdatePageHost(visible: Boolean, state: UpdateState, onDismiss: () -> Unit,
    onRetry: () -> Unit, onIgnore: () -> Unit, onOpenRelease: (String) -> Unit,
    transferState: UpdateTransferState, onDownload: (UpdateSource) -> Unit,
    onInstall: () -> Unit, onCancel: () -> Unit, onChangeSource: (UpdateSource) -> Unit,
    downloadExperience: Boolean, settings: UpdateSettings, onAutoCheck: (Boolean) -> Unit,
    onChannel: (UpdateChannel) -> Unit, cover: String, origin: UpdatePageOrigin? = null,
    onPresentedChanged: (Boolean) -> Unit = {}, versionPreview: String = BuildConfig.UPDATE_ANIMATION_PREVIEW_VERSION) {
    var entryCheckFinished by remember(visible) { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(2000); entryCheckFinished = true }
    }
    var exitCheckFinished by remember { mutableStateOf(false) }
    SideEffect { if (visible) exitCheckFinished = entryCheckFinished }
    val showResult = if (visible) entryCheckFinished else exitCheckFinished
    UpdateContainerTransform(visible, origin, onPresentedChanged) {
        val context = LocalContext.current
        val notes by produceState("正在读取更新说明…") {
            value = withContext(Dispatchers.IO) {
                runCatching { installedReleaseNotes(context.assets.open("release-history.md").bufferedReader().use { it.readText() },
                    BuildConfig.VERSION_NAME) }.getOrDefault("此版本未附带更新说明，可查看完整发布记录。")
            }
        }
        UpdatePageContent(if (showResult) state else UpdateState.Checking,
            if (showResult) transferState else UpdateTransferState(), notes, onDismiss, onRetry, onIgnore, onOpenRelease,
            onDownload, onInstall, onCancel, onChangeSource, downloadExperience, settings, onAutoCheck, onChannel,
            cover = cover, backgroundActive = visible,
            versionPreview = if (showResult) versionPreview else "")
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
    onChannel: (UpdateChannel) -> Unit = {}, cover: String = "", backgroundActive: Boolean = true,
    versionPreview: String = "") {
    val release = (state as? UpdateState.Available)?.release
    val installedVersion = BuildConfig.VERSION_NAME.substringBefore('-')
    var heroVersion by remember { mutableStateOf(installedVersion) }
    LaunchedEffect(state, versionPreview) {
        // Isolated visual preview only; release/download metadata continues to identify the real APK.
        if (versionPreview.isNotBlank()) {
            heroVersion = versionPreview
            return@LaunchedEffect
        }
        when (state) {
            is UpdateState.Available -> heroVersion = state.release.version
            UpdateState.UpToDate -> heroVersion = installedVersion
            else -> Unit // Rechecking must not first morph back to the installed version.
        }
    }
    val checking = state == UpdateState.Checking || state == UpdateState.Idle
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(UpdateSource.default) }
    LaunchedEffect(transferState.activeSource) { transferState.activeSource?.let { source = it } }
    val settled by animateFloatAsState(if (checking) 0f else 1f, tween(680, easing = FastOutSlowInEasing), label = "update_check_result")
    val detail by animateFloatAsState(if (expanded) 1f else 0f, tween(600, easing = FastOutSlowInEasing), label = "update_details_morph")
    val notesScroll = rememberScrollState()
    val artworkPalette by rememberArtworkGlowPalette(cover)
    val accent = MiuixTheme.colorScheme.primary
    val palette = if (cover.isBlank()) ArtworkGlowPalette(accent.toArgb(),
        androidx.compose.ui.graphics.lerp(accent, Color(0xFFFF9FFC), .4f).toArgb()) else artworkPalette
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
    val glassBackdrop = rememberUpdateBackdrop()
    val colors = remember { darkColorScheme(primary = Color(0xFFB8A6FF), onBackground = pageWhite,
        background = Color(0xFF100D29), surface = Color(0xFF201C35), surfaceContainer = Color(0xFF29253F)) }
    MiuixTheme(colors = colors) {
    CompositionLocalProvider(LocalContentColor provides pageWhite) {
    Box(Modifier.fillMaxSize().testTag("update_page").background(Color(0xFF0A0A0A))
        .pointerInput(Unit) { detectTapGestures {
            menu = false
        } }) {
        Box(Modifier.matchParentSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
        NeonRevealBackground(palette, Modifier.matchParentSize(), active = backgroundActive)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Spacer(Modifier.fillMaxWidth().height(60.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width = maxWidth
                val height = maxHeight
                val busy = transferState.download !is UpdateDownloadState.Idle
                val fontScale = LocalDensity.current.fontScale
                val compact = height < 400.dp
                val targetHeight = when {
                    transferState.download is UpdateDownloadState.Ready -> if (downloadExperience) 274.dp else 222.dp
                    busy -> 248.dp
                    release != null -> 192.dp
                    state is UpdateState.Failed -> 236.dp
                    else -> 192.dp
                } + (80 * (fontScale - 1).coerceAtLeast(0f)).dp
                val summaryHeight by animateDpAsState(targetHeight
                    .coerceAtMost((height - 24.dp).coerceAtLeast(120.dp)), tween(480), label = "update_transfer_card_height")
                val compactTop = (height - summaryHeight - 20.dp).coerceAtLeast(12.dp)
                val checkingTop = (height - 96.dp).coerceAtLeast(12.dp)
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
                val statusShape = RoundedCornerShape((36f - 10f * settled).dp)
                val bottomSpace = (32f - 12f * settled).dp
                Box(Modifier.align(Alignment.TopCenter).padding(horizontal = (16f + 4f * settled).dp)
                    .offset(y = top).widthIn(max = 620.dp).fillMaxWidth()
                    .height((height - top - bottomSpace).coerceAtLeast(56.dp)).clip(statusShape)
                    .background(androidx.compose.ui.graphics.lerp(Color(0xD9100F1D), Color(0xB516151D), settled))
                    .border(1.dp, Color.White.copy(alpha = .10f + .04f * settled), statusShape)
                    .testTag("update_status_card")) {
                    Column(Modifier.fillMaxSize().padding(horizontal = (22f - 4f * settled).dp,
                        vertical = (16f + 2f * settled).dp)
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
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("更新说明", color = pageWhite, fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { expanded = false }, modifier = Modifier.size(44.dp)
                                        .background(Color.White.copy(alpha = .08f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = .04f), CircleShape)
                                        .semantics { contentDescription = "收起更新说明" }
                                        .testTag("update_collapse_notes")) {
                                        Canvas(Modifier.size(18.dp)) {
                                            val left = Offset(size.width * .12f, size.height * .34f)
                                            val tip = Offset(size.width * .5f, size.height * .72f)
                                            val right = Offset(size.width * .88f, size.height * .34f)
                                            drawLine(pageWhite, left, tip, 2.dp.toPx(), StrokeCap.Round)
                                            drawLine(pageWhite, tip, right, 2.dp.toPx(), StrokeCap.Round)
                                        }
                                    }
                                }
                                MarkdownText(release?.notes?.ifBlank {
                                    if (release.version.substringBefore('-') == BuildConfig.VERSION_NAME.substringBefore('-')) installedNotes else ""
                                }?.let { stripVersionHeadings(release.version, it) }
                                    ?.ifBlank { "新版本已发布。" } ?: installedNotes.ifBlank { "此版本未附带更新说明。" }, compact = true)
                                Spacer(Modifier.height(8.dp))
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.fillMaxHeight().verticalScroll(rememberScrollState())),
                                verticalArrangement = if (checking) Arrangement.Center else Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(when {
                                        transferState.download is UpdateDownloadState.Ready -> "下载完成"
                                        transferState.download is UpdateDownloadState.Downloading -> "正在下载更新"
                                        transferState.download is UpdateDownloadState.Failed -> "下载未完成"
                                        checking -> "正在检查更新"
                                        state is UpdateState.Failed -> "暂时无法检查更新"
                                        release != null -> if (downloadExperience) "下载体验" else "发现新版本"
                                        else -> "已是最新版本"
                                    }, color = pageWhite, fontSize = if (checking) 16.sp else 18.sp,
                                        fontWeight = if (checking) FontWeight.Normal else FontWeight.Medium,
                                        modifier = Modifier.weight(1f))
                                    if (checking) UpdateCheckingSpinner()
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
                                TransferSummary(transferState)
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
        }
        UpdateGlassMenu(menu, glassBackdrop, onDismiss = { menu = false }, anchor = menuAnchor, actions = buildList {
            add(UpdateMenuAction("更新设置") { menu = false; showSettings = true })
            add(UpdateMenuAction("下载源") { menu = false; showSources = true })
            add(UpdateMenuAction("发布记录") { menu = false; onOpenRelease(AppMetadata.RELEASES_URL) })
            if (release != null && transferState.download is UpdateDownloadState.Idle)
                add(UpdateMenuAction("忽略此版本") { menu = false; onIgnore() })
        }, toolbar = {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).height(60.dp)
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                UpdateGlassButton(glassBackdrop, "update_back", "返回", onClick = {
                    if (menu) menu = false else if (expanded) expanded = false else onDismiss()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = pageWhite) }
                Text(if (expanded) "更新说明" else "软件更新", fontSize = 19.sp, color = pageWhite,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                UpdateGlassButton(glassBackdrop, "update_menu", "更多", onClick = { menu = !menu },
                    onBoundsChanged = { menuAnchor = it }) {
                    Icon(Icons.Default.MoreVert, null, tint = pageWhite)
                }
            }
        })
        if (showSources) UpdateOptionsDialog("下载来源",
            summary = if (transferState.download is UpdateDownloadState.Downloading) "切换来源会从头重新下载" else "失败时自动尝试其他来源",
            onDismiss = { showSources = false }) {
            UpdateOptionGroup { UpdateSource.available.forEach { candidate ->
                UpdateChoiceRow(candidate.label, selected = candidate == source) {
                    if (source != candidate) {
                        source = candidate
                        if (transferState.download is UpdateDownloadState.Downloading) onChangeSource(candidate)
                    }
                    showSources = false
                }
            } }
        }
        if (showSettings) UpdateOptionsDialog("更新设置", onDismiss = { showSettings = false }) {
            UpdateOptionGroup { UpdateAutoCheckRow(settings.autoCheckOnLaunch, onAutoCheck) }
            UpdateOptionSectionLabel("更新渠道")
            UpdateOptionGroup { listOf(UpdateChannel.STABLE to "正式版", UpdateChannel.PRERELEASE to "预发布版").forEach { (channel, label) ->
                UpdateChoiceRow(label, selected = settings.channel == channel) {
                    showSettings = false; onChannel(channel)
                }
            } }
        }
        if (confirmCancel) UpdateOptionsDialog("取消下载？", onDismiss = { confirmCancel = false }) {
            Text("返回页面后仍会在后台下载。只有取消下载才会停止当前任务。", fontSize = 14.sp)
            PageButton("继续下载", "update_keep_download") { confirmCancel = false }
            PageButton("取消下载", "update_confirm_cancel", primary = false) { confirmCancel = false; onCancel() }
        }
    }
    }
    }
}

@Composable private fun UpdateCheckingSpinner() {
    val rotation by rememberInfiniteTransition(label = "update check rotation").animateFloat(0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "update check angle")
    Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
        .testTag("update_check_spinner")) {
        val stroke = 1.3.dp.toPx()
        drawArc(pageWhite.copy(alpha = .9f), 35f, 285f, false,
            topLeft = Offset(stroke, stroke),
            size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable private fun TransferSummary(state: UpdateTransferState) {
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

private fun verifiedUpdate(release: AppRelease) = release.apkUrl != null &&
    release.size in 1..UpdateDownloader.MAX_APK_BYTES && Regex("[a-f0-9]{64}").matches(release.sha256)

private fun updateSize(bytes: Long): String = if (bytes <= 0) "0 MB" else
    String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
