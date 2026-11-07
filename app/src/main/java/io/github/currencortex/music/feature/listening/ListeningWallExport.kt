package io.github.currencortex.music.feature.listening

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.GlassActionButton
import io.github.currencortex.music.ui.component.nativeui.LocalGlassBackdrop
import io.github.currencortex.music.ui.component.nativeui.rememberGlassBackdrop
import io.github.currencortex.music.ui.component.nativeui.SettingsFloatingHeader
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable internal fun ListeningWallExportButtons(
    covers: List<String>, title: String, songCount: Int?, modifier: Modifier = Modifier,
    overlay: Boolean = false, info: ListeningWallExportInfo? = null,
) {
    val scale = LocalFootprintScale.current
    val exportInfo = info ?: LocalListeningWallExportInfo.current
    val actualCovers = remember(covers) { covers.filter(String::isNotBlank) }
    var kind by rememberSaveable(covers, title) { mutableStateOf<String?>(null) }
    Row(modifier.fillMaxWidth().padding(horizontal = ((if (overlay) 16.667f else 4f) * scale).dp,
        vertical = ((if (overlay) 0f else 19f) * scale).dp), horizontalArrangement = Arrangement.spacedBy(((if (overlay) 16.667f else 12f) * scale).dp)) {
        listOf(ListeningExportKind.POSTER to "生成海报", ListeningExportKind.WALLPAPER to "生成壁纸").forEach { (type, label) ->
            GlassActionButton(label, { kind=type.name },
                Modifier.weight(1f).testTag("listening_export_${type.name.lowercase()}"),
                primary=type==ListeningExportKind.POSTER, enabled=actualCovers.isNotEmpty(),
                minHeight=((if(overlay)41.667f else 46f)*scale).dp)

        }
    }
    kind?.let { selected ->
        key(actualCovers, title, selected, exportInfo) {
            ListeningWallExportChooser(actualCovers, title, songCount, ListeningExportKind.valueOf(selected), exportInfo) { kind = null }
        }
    }
}

@Composable internal fun ListeningWallExportChooser(
    covers: List<String>, title: String, songCount: Int?, kind: ListeningExportKind,
    info: ListeningWallExportInfo,
    loadBitmap: (suspend (String, Int) -> android.graphics.Bitmap)? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val exportDepth = LocalListeningExportDepth.current
    DisposableEffect(exportDepth) {
        exportDepth?.let { it.intValue++ }
        onDispose { exportDepth?.let { it.intValue = (it.intValue - 1).coerceAtLeast(0) } }
    }
    val scope = rememberCoroutineScope()
    val session = remember(covers, title, info, kind) { ListeningWallRenderSession(context, covers, title, info, loadBitmap) }
    DisposableEffect(session) { onDispose { session.markClosed() } }
    LaunchedEffect(session) { try { awaitCancellation() } finally { withContext(NonCancellable) { session.close() } } }
    var busy by remember(session) { mutableStateOf(false) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val file = pendingSave; pendingSave = null
        if (uri != null && file != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } } }
                Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "保存失败，请重试" }
            finally { file.delete() }
        } else file?.delete()
    }
    val lastPendingSave by rememberUpdatedState(pendingSave)
    DisposableEffect(Unit) { onDispose { lastPendingSave?.delete() } }
    val carousel = rememberLazyListState()
    var selectedPage by rememberSaveable(kind) { mutableIntStateOf(0) }
    val dragging by carousel.interactionSource.collectIsDraggedAsState()
    var dragStartedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragging) { if (dragging) dragStartedAt = android.os.SystemClock.uptimeMillis() }
    var appeared by remember(session) { mutableStateOf(false) }
    LaunchedEffect(session) { delay(200); appeared = true }
    val contentAlpha by animateFloatAsState(if (appeared) 1f else 0f,
        tween(300, easing = ExportEaseInOut), label = "poster entrance")
    val choices = remember(session) { mutableStateMapOf<Int, List<Int>>() }
    val template = ListeningWallTemplate(selectedPage, kind)
    val selected = choices[template.index] ?: listeningExportCoverIndices(template, covers.size)
    fun export(share: Boolean) {
        if (busy) return
        if (template.locked(info)) { error = "此网易云模板需要当前账号的会员权限。可以继续选择其他模板。"; return }
        busy = true; error = null
        scope.launch {
            try {
                val file = session.shareCopy(template, selected)
                if (share) {
                    context.startActivity(Intent.createChooser(listeningExportShareIntent(context, file), "分享唱片墙"))
                } else if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        saveListeningExportToGallery(context, file, "${title}-${if (kind == ListeningExportKind.POSTER) "唱片海报" else "手机壁纸"}-${System.currentTimeMillis()}.png")
                    } finally { file.delete() }
                    Toast.makeText(context, "已保存到相册 · CurrentMusicX", Toast.LENGTH_SHORT).show()
                } else {
                    pendingSave = file; saveDocument.launch("${title}-${template.label}.png")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "导出失败，请检查图片网络连接后重试" }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ListeningSongSystemBars(false)
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth.value / 375f
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.matchParentSize().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier)
            .background(Brush.verticalGradient(listOf(Color(0xFF4E4E4E), Color(0xFF3C3C3C)))))
        CompositionLocalProvider(LocalFootprintScale provides scale, LocalGlassBackdrop provides backdrop) {
            val density = LocalDensity.current
            val viewportWidth = maxWidth
            val vh = maxHeight / 2436f
            val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val titleTop = statusTop + vh * 38f
            val previewTop = titleTop + (19.333f * scale).dp + vh * 84f
            val previewHeight = vh * 1728f
            fun pageWidth(index: Int) = vh * (if (kind == ListeningExportKind.WALLPAPER) 800f else if (index % 3 == 2) 882f else 925f)
            val fling = remember(carousel, density) {
                object : FlingBehavior {
                    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                        val layout = carousel.layoutInfo
                        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        val offsets = layout.visibleItemsInfo.map { it.index to (it.offset + it.size / 2f - center) }
                        val previousOffset = offsets.firstOrNull { it.first == selectedPage }?.second ?: 0f
                        val shortSwipe = android.os.SystemClock.uptimeMillis()-dragStartedAt <= 300
                        val nextIndex = (selectedPage + if (previousOffset < -1f) 1 else if (previousOffset > 1f) -1 else 0).coerceIn(0,5)
                        val candidates = if (shortSwipe) offsets.filter { it.first == nextIndex }.ifEmpty { offsets } else offsets
                        val target = candidates.minByOrNull { it.second.absoluteValue } ?: return 0f
                        selectedPage = target.first
                        var consumed = 0f
                        animate(0f, target.second, animationSpec = tween(300, easing = ExportEase)) { value, _ ->
                            consumed += scrollBy(value - consumed)
                        }
                        return 0f
                    }
                }
            }
            Box(Modifier.fillMaxSize().testTag("listening_export_chooser")) {
                LazyRow(Modifier.fillMaxWidth().offset(y = previewTop).height(previewHeight).testTag("listening_export_pager")
                    .semantics { stateDescription = "${selectedPage + 1} / 6   ${template.label}" },
                    state = carousel, flingBehavior = fling, userScrollEnabled = !busy,
                    contentPadding = PaddingValues(start = ((viewportWidth-pageWidth(0))/2).coerceAtLeast(0.dp),
                        end = ((viewportWidth-pageWidth(5))/2).coerceAtLeast(0.dp)),
                    horizontalArrangement = Arrangement.spacedBy((18 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    items(6, key = { it }) { index ->
                        val pageTemplate = ListeningWallTemplate(index, kind)
                        val indices = choices[index] ?: listeningExportCoverIndices(pageTemplate, covers.size)
                        val active = index == selectedPage
                        val size by animateFloatAsState(if (active) 1f else .84f,
                            tween(if (appeared) 500 else 0, easing = ExportEaseInOut), label = "poster selection")
                        ExportTemplatePreview(session, pageTemplate, indices, info,
                            modifier = Modifier.width(pageWidth(index)).height(previewHeight).graphicsLayer {
                                transformOrigin = TransformOrigin(if (index < selectedPage) 1f else if (index > selectedPage) 0f else .5f, .5f)
                                scaleX = size; scaleY = size; alpha = contentAlpha * if (active) 1f else .5f
                            }, editable = !busy && !carousel.isScrollInProgress && active, onSave = { export(false) }) { slot ->
                                if (covers.isNotEmpty()) choices[index] = indices.toMutableList().also { it[slot] = (it[slot] + 1) % covers.size }
                            }
                    }
                }
                val primaryShare = kind == ListeningExportKind.POSTER
                Column(Modifier.fillMaxWidth().offset(y = previewTop + previewHeight + vh * 93f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        GlassActionButton(if(busy) "生成中…" else if(primaryShare) "分享唱片墙" else "保存壁纸",
                            { export(primaryShare) }, Modifier.width((220*scale).dp).testTag("listening_export_primary"),
                            primary=true, enabled=!busy && !carousel.isScrollInProgress, minHeight=(46*scale).dp)

                    }
                    error?.let { FootprintText(it, Modifier.padding(horizontal = (24 * scale).dp, vertical = (8 * scale).dp), size = 13f, color = Color(0xFFFFBDBD), maxLines = 3) }
                }
                SettingsFloatingHeader(background=Color(0xFF4E4E4E),toolbarHeight=(54*scale).dp) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height((54*scale).dp)) {
                        FootprintText(if(kind==ListeningExportKind.POSTER) "唱片海报" else "手机壁纸",
                            Modifier.align(Alignment.Center),size=17f,color=Color.White,weight=FontWeight.SemiBold)
                        GlassIconButton(onClose,"返回",Modifier.align(Alignment.CenterStart).padding(start=12.dp)
                            .testTag("listening_export_close")) {
                            Icon(MusicIcons.Back,null,tint=MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
    }
}

private val ExportEase = CubicBezierEasing(.25f, .1f, .25f, 1f)
private val ExportEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)

@Composable private fun ExportTemplatePreview(session: ListeningWallRenderSession, template: ListeningWallTemplate, indices: List<Int>,
    info: ListeningWallExportInfo, modifier: Modifier, editable: Boolean, onSave: () -> Unit, onCover: (Int) -> Unit) {
    var path by remember(session, template, indices) { mutableStateOf<String?>(null) }
    var failed by remember(session, template, indices) { mutableStateOf(false) }
    var attempt by remember(session, template, indices) { mutableIntStateOf(0) }
    LaunchedEffect(session, template, indices, attempt) {
        path = null; failed = false
        try { path = session.render(template, indices, 600).absolutePath }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = maxWidth
        val height = maxHeight
        Box(Modifier.size(width, height).clip(RoundedCornerShape(12.dp)).background(if (template.dark) Color(0xFF232323) else Color(0xFFE4E4E4))
            .pointerInput(template, indices, editable, path) {
                if (editable && path != null) detectTapGestures(onLongPress = { onSave() }, onTap = { point ->
                    listeningExportHit(template, point.x / size.width * 1125, point.y / size.width * 1125)?.let(onCover)
                })
            }.testTag("listening_export_template_${template.index}"), contentAlignment = Alignment.Center) {
            if (path != null) AsyncImage(File(requireNotNull(path)), "${template.label}预览",
                Modifier.align(Alignment.TopStart).requiredSize(width, width/template.aspectRatio), contentScale = ContentScale.FillBounds)
            else FootprintText(if (failed) "图片加载失败，点此重试" else "正在生成预览…", Modifier.clickable(enabled = failed) { attempt++ }.padding(16.dp),
                size = 14f, color = if (template.dark) Color.White else Color(0xFF283248))
            if (template.kind == ListeningExportKind.WALLPAPER) {
                CompositionLocalProvider(LocalFootprintScale provides 1f) { WallpaperLockPreview(template.dark, width.value) }
            }
            if (template.layout == ListeningExportLayout.SCATTER && info.isCloud) AsyncImage(Media + "preview_vip_corner_pic.4a0c777c.png",
                if (template.locked(info)) "会员专享模板" else "会员模板", Modifier.align(Alignment.TopEnd).width(width * .169778f).height(width * .070222f))
        }
    }
}

/** Phone clock is a preview-only layer and never burns into saved wallpaper pixels. */
@Composable private fun BoxScope.WallpaperLockPreview(dark: Boolean, width: Float) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }
    val tint = (if (dark) Color.White else Color.Black).copy(alpha = .2f)
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    FootprintText(time, Modifier.align(Alignment.TopStart).padding(start = (width * .04673f).dp, top = (width * .02908f).dp),
        size = width * .029698f, color = tint, numeric = true, weight = FontWeight.SemiBold)
    Row(Modifier.align(Alignment.TopEnd).padding(top = (width * .02908f).dp, end = (width * .0283f).dp), horizontalArrangement = Arrangement.spacedBy((width * .008f).dp)) {
        listOf("signal" to .033778f, "wifi" to .032889f, "battery" to .047111f).forEach { (type, ratio) ->
            val file = when (type) {
                "signal" -> if (dark) "record_paper_wallpaper_signal_white.7585797e.png" else "record_paper_wallpaper_signal_black.4642c621.png"
                "wifi" -> if (dark) "record_paper_wallpaper_wifi_white.72fa5387.png" else "record_paper_wallpaper_wifi_black.b3041613.png"
                else -> if (dark) "record_paper_wallpaper_battery_white.00524873.png" else "record_paper_wallpaper_battery_black.2365bf72.png"
            }
            AsyncImage(Media + file, null, Modifier.width((width * ratio).dp).height((width * .023111f).dp).graphicsLayer { alpha = .2f })
        }
    }
    Column(Modifier.align(Alignment.TopCenter).padding(top = (width * .195f).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(Media + if (dark) "record_paper_wallpaper_lock_black.cb57db68.png" else "record_paper_wallpaper_lock_white.0de16d9d.png",
            null, Modifier.height((width * .05375f).dp).width((width * .05375f).dp))
        FootprintText(time, size = width * .168053f, color = tint, numeric = true, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

private const val Media = "https://sg.music.163.com/st/listen-home/static/media/"

/** Grant only this PNG to a chosen receiver; callers launch the system chooser. */
internal fun listeningExportShareIntent(context: android.content.Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "唱片墙", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
