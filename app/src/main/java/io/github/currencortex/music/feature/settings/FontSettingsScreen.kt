// SPDX-License-Identifier: GPL-3.0-only
// FontFamilyView / selection sheet layout adapted from chen08209's upstream project.
package io.github.currencortex.music.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.*
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun FontSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val installed by produceState<List<AppearanceFont>?>(null) {
        value = withContext(Dispatchers.IO) { installedFontFamilies() }
    }
    val imports by produceState<List<AppearanceFont>>(emptyList(), revision, settings.customFont) {
        value = withContext(Dispatchers.IO) { importedAppearanceFonts(context, settings.customFont) }
    }
    fun select(key: String) {
        scope.launch {
            try { viewModel.selectFont(key); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "无法保存字体设置，请重试" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true; error = null
            var imported: AppearanceFont? = null
            var saved = false
            try {
                withContext(Dispatchers.IO) { imported = importAppearanceFont(context, uri) }
                // Once the datastore write commits, cancellation must not remove its font file.
                withContext(NonCancellable) {
                    viewModel.selectFont(requireNotNull(imported).key)
                    saved = true
                }
                revision++; query = ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = "字体导入失败：${failure.message ?: "文件无效"}" }
            finally {
                if (!saved) imported?.let { font ->
                    withContext(NonCancellable + Dispatchers.IO) { deleteCustomFont(context, font.key) }
                }
                importing = false
            }
        }
    }
    val colors = MiuixTheme.colorScheme
    val filter = remember(query) { MusicSearchQuery(query) }
    val showDefault = filter.matches("默认")
    val filteredImports = imports.filter { filter.matches(it.label) }
    val filteredInstalled = installed.orEmpty().filter { filter.matches(it.label) }
    CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides colors.onSurface) {
    SettingsUiScope {
        Box(Modifier.fillMaxSize().imePadding().testTag("font_settings_page")) {
            SettingsScrollLayout(Modifier.fillMaxSize(), topBar = {
                SettingsTopBar("字体", onBack, tag = "font_toolbar", actions = {
                    GlassIconButton({ picker.launch(arrayOf("*/*")) }, "导入字体",
                        Modifier.testTag("font_import"), enabled = !importing) {
                        Icon(MusicIcons.Upload, null)
                    }
                })
            }, footer = {
                GlassDockedSearch(query, { query = it }, Modifier.align(Alignment.BottomCenter)
                    .padding(start = DockedSearchMargin.dp, end = DockedSearchMargin.dp,
                        bottom = DockedSearchMargin.dp), inputTag = "font_search")
            }) { top ->
                LazyColumn(Modifier.fillMaxSize().testTag("font_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                        top = top + 24.dp, bottom = (20 + DockedSearchInset).dp)) {
                    if (showDefault) item("default") {
                        SettingsGroup { FontChoice(AppearanceFont("", "默认"), settings.customFont.isBlank(),
                            !importing, { select("") }) }
                    }
                    if (importing) item("importing") { SettingsHint("正在导入字体…") }
                    error?.let { item("error") { SettingsHint(it, Modifier.testTag("font_error"), error = true) } }
                    if (filteredImports.isNotEmpty()) fontSection("导入的字体", filteredImports,
                        settings.customFont, !importing, ::select)
                    if (filteredInstalled.isNotEmpty()) fontSection("已安装字体", filteredInstalled,
                        settings.customFont, !importing, ::select)
                    if (installed == null) item("loading") { SettingsHint("正在读取已安装字体…") }
                    else if (!showDefault && filteredImports.isEmpty() && filteredInstalled.isEmpty())
                        item("empty") { SettingsHint("没有匹配的字体", Modifier.testTag("font_search_empty")) }
                }
            }

        }
    }
    }
}

/** Separate lazy rows retain one rounded group while loading only visible font previews. */
private fun LazyListScope.fontSection(title: String, fonts: List<AppearanceFont>, selected: String,
    enabled: Boolean, onSelect: (String) -> Unit) {
    item("heading_$title") { SettingsListHeader(title) }
    fonts.forEachIndexed { index, font ->
        item(font.key) {
            val shape = RoundedCornerShape(topStart = if (index == 0) 20.dp else 0.dp,
                topEnd = if (index == 0) 20.dp else 0.dp,
                bottomStart = if (index == fonts.lastIndex) 20.dp else 0.dp,
                bottomEnd = if (index == fonts.lastIndex) 20.dp else 0.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(MiuixTheme.colorScheme.surfaceContainer)) {
                FontChoice(font, selected == font.key, enabled, { onSelect(font.key) })
                if (index != fonts.lastIndex) SettingsDivider()
            }
        }
    }
}

@Composable private fun FontChoice(font: AppearanceFont, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val preview = rememberCustomFont(font.key)
    Row(Modifier.fillMaxWidth().testTag("font_choice_${font.key.ifBlank { "default" }}")
        .semantics { this.selected = selected; role = Role.RadioButton }.settingsPressFeedback(press)
        .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(font.label, Modifier.weight(1f), style = TextStyle(fontFamily = preview ?: androidx.compose.ui.text.font.FontFamily.Default,
            fontSize = 17.sp, lineHeight = 24.sp), color = MiuixTheme.colorScheme.onSurface)
        SettingsSelectionCheck(selected)
    }
}
