// SPDX-License-Identifier: GPL-3.0-only
// Font selection and font-family discovery adapted from chen08209's upstream project.
package io.github.currencortex.music.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.theme.AppearanceFont
import io.github.currencortex.music.ui.theme.BUILT_IN_OPPO_FONT
import io.github.currencortex.music.ui.theme.BUILT_IN_WENKAI_FONT
import io.github.currencortex.music.ui.theme.deleteCustomFont
import io.github.currencortex.music.ui.theme.importAppearanceFont
import io.github.currencortex.music.ui.theme.importedAppearanceFonts
import io.github.currencortex.music.ui.theme.rememberCustomFont
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopTips
import io.github.suqi8.coui.kmp.basic.TopTipsDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.UploadCloud
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BuiltInAppearanceFonts = listOf(
    AppearanceFont("", "系统默认"),
    AppearanceFont(BUILT_IN_OPPO_FONT, "OPPO Sans"),
    AppearanceFont(BUILT_IN_WENKAI_FONT, "霞鹜文楷"),
)

@Composable
internal fun FontSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    val imports by produceState<List<AppearanceFont>>(emptyList(), revision, settings.customFont) {
        value = withContext(Dispatchers.IO) { importedAppearanceFonts(context, settings.customFont) }
    }
    val fonts = remember(imports) { BuiltInAppearanceFonts + imports }
    val initialFont = fonts.firstOrNull { it.key == settings.customFont }?.key ?: ""
    var draftFont by rememberSaveable(settings.customFont) { mutableStateOf(initialFont) }
    var draftScale by rememberSaveable(settings.customTextScale, settings.textScale) {
        mutableFloatStateOf(if (settings.customTextScale) settings.textScale else 1f)
    }
    val savedScale = if (settings.customTextScale) settings.textScale else 1f
    val changed = draftFont != settings.customFont || draftScale != savedScale
    val colors = COUITheme.colorScheme

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            var imported: AppearanceFont? = null
            var saved = false
            try {
                withContext(Dispatchers.IO) { imported = importAppearanceFont(context, uri) }
                withContext(NonCancellable) {
                    viewModel.selectFont(requireNotNull(imported).key)
                    saved = true
                }
                draftFont = requireNotNull(imported).key
                revision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "字体导入失败：${failure.message ?: "文件无效"}"
            } finally {
                if (!saved) imported?.let { font ->
                    withContext(NonCancellable + Dispatchers.IO) { deleteCustomFont(context, font.key) }
                }
                importing = false
            }
        }
    }

    CompositionLocalProvider(LocalContentColor provides colors.labelPrimary) {
        Scaffold(
            containerColor = colors.backgroundWithCard,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                SmallTopAppBar(
                    title = "字体",
                    color = colors.backgroundWithCard,
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("font_back")) {
                            Icon(COUIIcons.Back, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("font_import"),
                            enabled = !importing,
                        ) {
                            Icon(COUIIcons.UploadCloud, contentDescription = "导入字体")
                        }
                    },
                )
            },
        ) { padding ->
            FontPreviewPage(
                fonts = fonts,
                selected = draftFont,
                draftScale = draftScale,
                importing = importing,
                error = error,
                changed = changed,
                onSelectFont = { draftFont = it },
                onScaleChange = { draftScale = it },
                onApply = {
                    viewModel.edit { current ->
                        current.copy(
                            customFont = draftFont,
                            customTextScale = draftScale != 1f,
                            textScale = draftScale,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .testTag("font_settings_page"),
            )
        }
    }
}

@Composable
private fun FontPreviewPage(
    fonts: List<AppearanceFont>,
    selected: String,
    draftScale: Float,
    importing: Boolean,
    error: String?,
    changed: Boolean,
    onSelectFont: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    val previewFont = rememberCustomFont(selected)
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 20.dp)
                .clipToBounds()
        ) {
            Text(
                text = "绿野仙踪",
                style = COUITheme.textStyles.headlineL.copy(
                    fontFamily = previewFont ?: FontFamily.Default,
                    fontSize = (22 * draftScale).sp,
                    lineHeight = (30 * draftScale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.labelPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "尽管有绿色眼镜保护着眼睛，桃乐丝和她的朋友们在刚看到这座奇妙的城市时，还是被它耀眼的光芒照得眼花缭乱。街道两旁耸立着绿色大理石砌成的美丽房屋，到处镶嵌着闪闪发光的翡翠。",
                style = COUITheme.textStyles.bodyL.copy(
                    fontFamily = previewFont ?: FontFamily.Default,
                    fontSize = (14 * draftScale).sp,
                    lineHeight = (24 * draftScale).sp,
                ),
                color = colors.labelPrimary,
            )
        }

        FontPanel(
            fonts = fonts,
            selected = selected,
            draftScale = draftScale,
            importing = importing,
            error = error,
            changed = changed,
            onSelectFont = onSelectFont,
            onScaleChange = onScaleChange,
            onApply = onApply,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FontPanel(
    fonts: List<AppearanceFont>,
    selected: String,
    draftScale: Float,
    importing: Boolean,
    error: String?,
    changed: Boolean,
    onSelectFont: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    val scaleLabel = if (draftScale == 1f) "默认" else "${(draftScale * 100).roundToInt()}%"
    val fontListState = rememberLazyListState()
    LaunchedEffect(fonts, selected) {
        val selectedIndex = fonts.indexOfFirst { it.key == selected }
        if (selectedIndex >= 0) fontListState.animateScrollToItem(selectedIndex)
    }
    Column(
        modifier
            .squircleSurface(
                color = colors.cardBackground,
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
                weight = SquircleDefaults.Weight,
            )
            .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp)
            .navigationBarsPadding()
            .height(300.dp)
    ) {
        if (importing) {
            TopTips(text = "正在导入字体…", modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        error?.let { message ->
            TopTips(
                text = message,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("font_error"),
                colors = TopTipsDefaults.topTipsColors(
                    containerColor = colors.red.copy(alpha = 0.12f),
                    contentColor = colors.red,
                    actionColor = colors.red,
                    actionContentColor = colors.onPrimary,
                ),
            )
        }
        LazyRow(
            state = fontListState,
            modifier = Modifier.fillMaxWidth().overScrollHorizontal(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(fonts, key = { it.key }) { font ->
                FontTypeCard(
                    font = font,
                    selected = selected == font.key,
                    enabled = !importing,
                    onClick = { onSelectFont(font.key) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "字体大小",
                modifier = Modifier.weight(1f),
                style = COUITheme.textStyles.bodyL,
                color = colors.labelPrimary,
            )
            Text(
                text = scaleLabel,
                style = COUITheme.textStyles.bodyM,
                color = colors.labelSecondary,
            )
        }
        Slider(
            value = draftScale,
            onValueChange = onScaleChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("font_size_slider"),
            valueRange = 0.8f..1.2f,
            steps = 3,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 12.dp)
                .heightIn(min = 52.dp)
                .testTag("font_apply"),
            enabled = changed,
            colors = ButtonDefaults.buttonColorsPrimary(),
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = "应用",
                style = COUITheme.textStyles.buttonL,
                color = COUITheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun FontTypeCard(
    font: AppearanceFont,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val preview = rememberCustomFont(font.key)
    val stroke = if (selected) colors.primary else colors.divider.copy(alpha = 0.45f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(96.dp)
                .height(64.dp)
                .squircleSurface(color = stroke, cornerRadius = 14.dp)
                .padding(if (selected) 2.dp else 1.dp)
                .squircleSurface(
                    color = colors.cardBackground,
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomEnd = 12.dp,
                    bottomStart = 12.dp,
                )
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    this.selected = selected
                    role = Role.RadioButton
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                style = COUITheme.textStyles.headlineL.copy(
                    fontFamily = preview ?: FontFamily.Default,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.labelPrimary,
            )
        }
        Text(
            text = font.label,
            modifier = Modifier
                .width(96.dp)
                .padding(top = 8.dp)
                .testTag("font_choice_${font.key.ifBlank { "default" }}"),
            style = COUITheme.textStyles.bodyXS,
            color = if (selected) colors.primary else colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
