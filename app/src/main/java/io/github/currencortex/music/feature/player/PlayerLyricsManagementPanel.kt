package io.github.currencortex.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.LyricsSongSettings
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One bounded overlay; its content pages share the same fixed header and native back handling. */
@Composable internal fun PlayerLyricsDialogFrame(song: Song, matching: Boolean, onBack: () -> Unit,
    onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val window = LocalWindowInfo.current.containerSize
    val width = with(density) { window.width.toDp() }
    val height = with(density) { window.height.toDp() }
    val top = with(density) { maxOf(WindowInsets.statusBars.getTop(this), WindowInsets.captionBar.getTop(this),
        WindowInsets.displayCutout.getTop(this)).toDp() }
    val bottom = with(density) { maxOf(WindowInsets.navigationBars.getBottom(this), WindowInsets.ime.getBottom(this),
        WindowInsets.captionBar.getBottom(this)).toDp() }
    val horizontalInsets = with(density) { (WindowInsets.safeDrawing.getLeft(this, direction) +
        WindowInsets.safeDrawing.getRight(this, direction)).toDp() }
    val panelWidth = (width - horizontalInsets - 32.dp).coerceAtLeast(0.dp).coerceAtMost(if (matching) 1000.dp else 520.dp)
    val panelHeight = ((height - top - bottom - 24.dp).coerceAtLeast(0.dp) * .9f)
        .coerceAtMost(if (matching) 840.dp else 700.dp)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val back = { focus.clearFocus(); keyboard?.hide(); onBack() }
    val close = { focus.clearFocus(); keyboard?.hide(); onClose() }
    MusicOverlayDialog(show = true, title = null, modifier = Modifier.width(panelWidth).height(panelHeight),
        onDismissRequest = { if (matching) back() else close() }, insideMargin = DpSize(0.dp, 0.dp),
        outsideMargin = DpSize(16.dp, 12.dp)) {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.fillMaxWidth().height(panelHeight)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (matching) Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = back).semantics { contentDescription = "返回歌词管理"; role = Role.Button }
                    .testTag("lyrics_match_header_back"), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp), tint = colors.onBackground)
                }
                Text(if (matching) "匹配歌词" else "歌词管理", Modifier.weight(1f), fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = close)
                    .semantics { contentDescription = "关闭歌词管理"; role = Role.Button }
                    .testTag("lyrics_management_close"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, Modifier.size(20.dp), tint = colors.onSurfaceVariantSummary)
                }
            }
            if (panelHeight >= 360.dp) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!matching) MusicCover(song.cover, Modifier.size(48.dp), cornerRadius = 12.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(song.name, fontSize = if (matching) 14.sp else 17.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (song.artists.isNotBlank()) Text(song.artists, fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().height(.7.dp).background(colors.onBackground.copy(alpha = .08f)))
            Column(Modifier.fillMaxWidth().weight(1f), content = content)
        }
    }
}

@Composable internal fun PlayerLyricsManagementPanel(currentSource: String, defaultSource: String,
    automatic: Boolean, options: LyricsSongSettings, blocked: Boolean, offsetInput: String,
    loading: Boolean, loadError: String?, recoveryMessage: String?, message: String?, messageError: Boolean,
    canExport: Boolean, onOffsetInput: (String) -> Unit, onOffsetStep: (Long) -> Unit,
    onOffsetApply: () -> Unit, onOffsetReset: () -> Unit, onDefault: () -> Unit, onMatched: () -> Unit,
    onImported: () -> Unit, onReset: () -> Unit, onMatch: () -> Unit, onImport: () -> Unit,
    onExportFia: () -> Unit, onExportLrc: () -> Unit, onClose: () -> Unit, alignmentLabel: String? = null) {
    val colors = MiuixTheme.colorScheme
    val hint = colors.onSurfaceVariantSummary
    var fileHelp by remember { mutableStateOf(false) }
    val parsedOffset = offsetInput.toLongOrNull()
    val validOffset = parsedOffset != null && parsedOffset in -60_000..60_000
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp).testTag("lyrics_management"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("歌词来源", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (loading) "读取中…" else "当前 · $currentSource", Modifier.weight(1f).padding(start = 12.dp).testTag("lyrics_source_current"),
                    fontSize = 12.sp, color = hint, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            }
            recoveryMessage?.let { Text(it, Modifier.testTag("lyrics_storage_recovery"), fontSize = 12.sp, color = colors.error) }
            loadError?.let { Text(it, Modifier.testTag("lyrics_management_load_error"), fontSize = 12.sp, color = colors.error) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.onBackground.copy(alpha = .035f))) {
                LyricsSourceChoice(if (automatic) "自动择优" else "默认来源 · $defaultSource",
                    if (automatic) "内容与时间轴校验 · 合格逐字优先 · AMLL 优先" else "使用默认歌词服务", options.matchedOrigin == null && !options.useImported,
                    !blocked, onDefault, Modifier.testTag("lyrics_source_default"))
                options.matchedOrigin?.let { LyricsSourceChoice("匹配歌词", it.label, !options.useImported, !blocked,
                    onMatched, Modifier.testTag("lyrics_source_matched")) }
                options.importedOrigin?.let { LyricsSourceChoice("导入文件", it.label, options.useImported, !blocked,
                    onImported, Modifier.testTag("lyrics_source_imported")) }
                if (options.matchedOrigin != null || options.importedOrigin != null) {
                    Text(if (automatic) "恢复自动择优" else "清除单曲覆盖",
                        Modifier.fillMaxWidth().clickable(enabled = !blocked, role = Role.Button, onClick = onReset)
                            .heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 12.dp).testTag("lyrics_source_reset"),
                        fontSize = 12.sp, color = colors.primary.copy(alpha = if (blocked) .4f else 1f))
                }
            }
            LyricsManagementDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("时间偏移", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("毫秒 · 仅此歌曲", fontSize = 11.sp, color = hint)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LyricsActionButton("−250", { onOffsetStep(-250) }, Modifier.width(68.dp).height(44.dp).testTag("lyrics_offset_minus"), enabled = !blocked)
                TextField(value = offsetInput, onValueChange = onOffsetInput, label = "毫秒", singleLine = true,
                    enabled = !blocked, insideMargin = DpSize(8.dp, 8.dp), cornerRadius = 12.dp, useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace, fontSize = 16.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).height(44.dp).testTag("lyrics_offset_input"))
                LyricsActionButton("+250", { onOffsetStep(250) }, Modifier.width(68.dp).height(44.dp).testTag("lyrics_offset_plus"), enabled = !blocked)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (!validOffset && offsetInput.isNotEmpty()) "范围 −60000 至 60000 ms" else "正值延后 · 负值提前",
                    Modifier.weight(1f), fontSize = 11.sp, color = if (!validOffset && offsetInput.isNotEmpty()) colors.error else hint)
                LyricsActionButton("归零", onOffsetReset, Modifier.testTag("lyrics_offset_reset"),
                    enabled = !blocked && options.offsetMs != 0L)
                LyricsActionButton("保存", onOffsetApply, Modifier.testTag("lyrics_offset_apply"),
                    enabled = !blocked && validOffset && parsedOffset != options.offsetMs, primary = true)
            }
            alignmentLabel?.let {
                Text("$it · 手动偏移额外叠加", Modifier.testTag("lyrics_adaptive_alignment"), fontSize = 11.sp, color = hint)
            }
            LyricsManagementDivider()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.primary.copy(alpha = .09f))
                .clickable(enabled = !blocked, role = Role.Button, onClick = onMatch).padding(14.dp)
                .testTag("lyrics_match_open"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("在线匹配歌词", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text("比较来源、查看匹配度与歌词预览", fontSize = 11.sp, color = hint)
                }
                Text("›", fontSize = 24.sp, color = colors.primary)
            }
            Text("歌词文件", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricsActionButton("导入", onImport, Modifier.weight(1f).testTag("lyrics_import"), enabled = !blocked)
                LyricsActionButton("导出 FIA", onExportFia, Modifier.weight(1f).testTag("lyrics_export_fia"), enabled = !blocked && canExport)
                LyricsActionButton("导出 LRC", onExportLrc, Modifier.weight(1f).testTag("lyrics_export_lrc"), enabled = !blocked && canExport)
            }
            Text(if (fileHelp) "收起格式说明" else "支持的格式与导出说明", Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { fileHelp = !fileHelp }.padding(vertical = 8.dp), fontSize = 12.sp, color = hint)
            if (fileHelp) Text("支持 LRC、TTML、FIA、VTT、YRC、明文 QRC 和 KRC，最大 2 MB。FIA 保留翻译、和声与单曲偏移；LRC 导出原文和真实逐字时间。",
                fontSize = 11.sp, lineHeight = 17.sp, color = hint)
            if (blocked) Text("正在处理…", fontSize = 12.sp, color = hint)
            message?.let { Text(it, Modifier.testTag("lyrics_management_message"), fontSize = 12.sp,
                color = if (messageError) colors.error else colors.primary) }
        }
        LyricsManagementDivider()
        LyricsActionButton("完成", onClose, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), enabled = !blocked)
    }
}

@Composable private fun LyricsManagementDivider() {
    Box(Modifier.fillMaxWidth().height(.7.dp).background(MiuixTheme.colorScheme.onBackground.copy(alpha = .07f)))
}

@Composable private fun LyricsSourceChoice(title: String, subtitle: String, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (selected) "●" else "○", fontSize = 19.sp, color = if (selected) colors.primary else colors.onSurfaceVariantSummary)
    }
}
