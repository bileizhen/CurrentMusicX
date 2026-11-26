// SPDX-License-Identifier: GPL-3.0-only
// COUI 17 settings chrome: the ColorOS page scaffold, cards and preference rows shared by every
// settings screen in the app.
package io.github.currencortex.music.ui.component.nativeui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.springChainSettingsItems
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.glowScrollOffsetPx
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.rememberSpringChainState
import io.github.suqi8.coui.kmp.effect.springChain
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlaySpinnerPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryDefaults
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType
import io.github.suqi8.coui.kmp.preference.RadioButtonPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** Limits the settings preference controls to settings; music/library rows keep their own design. */
internal val LocalSettingsComponents = compositionLocalOf { false }

/**
 * Whether the current screen is an actual settings page (one built by [SettingsPage] /
 * [SettingsNativePage]). Several product pages also call [SettingsUiScope] to reuse the list
 * chrome, so [LocalSettingsComponents] alone cannot decide between a ColorOS settings row and
 * the app's own denser row; this local can.
 */
internal val LocalCouiSettingsPage = compositionLocalOf { false }

@Composable internal fun SettingsUiScope(couiPage: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettingsComponents provides true,
        LocalCouiSettingsPage provides couiPage,
        content = content,
    )
}

/** Common settings subpage shell: COUI's native top bar, glow and preference list. */
@Composable internal fun SettingsPage(title: String, tag: String, onBack: (() -> Unit)? = null,
    subtitle: String? = null, padding: PaddingValues = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
    largeTitle: Boolean = false, content: LazyListScope.() -> Unit) {
    SettingsUiScope {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize().testTag("settings_page_$tag")) {
            // This layer records only the flat background, so content may sample it safely.
            // The scrolling list is recorded separately for the top bar glow; sampling the layer
            // that records the list from inside the list makes hwui's RenderNode tree reference
            // itself, and RenderThread overflows its stack while preparing that tree.
            Box(Modifier.matchParentSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .background(COUITheme.colorScheme.background))
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    SettingsNativePage(
                        title = title,
                        tag = tag,
                        onBack = onBack,
                        subtitle = subtitle,
                        padding = padding,
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(),
                        largeTitle = largeTitle,
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * The top-bar sample, recorded the way COUI's page scaffold records it: the window fill
 * under the page, then the content. Sampling a transparent layer makes the downscaled
 * glow blur step into hard blocks behind high-contrast rows.
 */
@Composable private fun rememberGlowBackdrop(): LayerBackdrop? {
    if (android.os.Build.VERSION.SDK_INT < 33 || !LocalView.current.isHardwareAccelerated ||
        !isRuntimeShaderSupported()) return null
    val background = COUITheme.colorScheme.background
    return rememberLayerBackdrop { drawRect(background); drawContent() }
}

@Composable internal fun SettingsNativePage(title: String, tag: String, onBack: (() -> Unit)? = null,
    subtitle: String? = null, padding: PaddingValues = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
    modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {},
    verticalSpacing: androidx.compose.ui.unit.Dp = 16.dp, backTag: String = "navigate_back",
    state: LazyListState? = null, contentModifier: Modifier = Modifier,
    largeTitle: Boolean = false, content: LazyListScope.() -> Unit) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val direction = LocalLayoutDirection.current
    val defaultState = rememberLazyListState()
    val listState = state ?: defaultState
    val springChainState = rememberSpringChainState(listState)
    // Records the list itself for the top bar glow. It is deliberately not exposed through
    // LocalGlassBackdrop: this layer contains every row, including its glass controls.
    // COUI's page scaffold records the window fill under the page for this sample; without it
    // the downscaled glow blur steps visibly where the list is transparent.
    val glowBackdrop = rememberGlowBackdrop()
    val scrollBehavior = COUIScrollBehavior()
    // Settings pages opt into COUI's native cards, rows and buttons; product pages that only
    // reuse SettingsUiScope keep the app's own density and glass controls.
    SettingsUiScope(couiPage = true) {
        Scaffold(
            modifier = modifier.fillMaxSize().testTag("settings_page_$tag"),
            containerColor = COUITheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                val navigationIcon: @Composable () -> Unit = {
                    IconButton(
                        onClick = { if (onBack != null) onBack() else dispatcher?.onBackPressed() },
                        modifier = Modifier.testTag(backTag),
                        minWidth = 44.dp,
                        minHeight = 44.dp,
                        cornerRadius = 22.dp,
                        // No material: the bar is a transparent glow, and a material button without
                        // a backdrop falls back to a flat disc that reads as a defect.
                        material = false,
                    ) { Icon(COUIIcons.Back, contentDescription = "返回") }
                }
                // A transparent bar plus frost is COUI 17's material bar; without a backdrop
                // (no runtime shader) keep the opaque bar as the fallback.
                val glow: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
                    if (glowBackdrop != null) {{
                        TopAppBarGlow(
                            backdrop = glowBackdrop,
                            scrollOffsetPx = scrollBehavior.glowScrollOffsetPx(),
                            isDark = COUITheme.isDark,
                            baseColor = COUITheme.colorScheme.background,
                            enabled = true,
                        )
                    }} else null
                val barColor = if (glowBackdrop != null) Color.Transparent else COUITheme.colorScheme.background
                // Pushed settings pages keep COUI's pinned small bar; the About page opts into the
                // collapsing large title.
                if (largeTitle) {
                    TopAppBar(
                        title = title,
                        modifier = Modifier.testTag("${tag}_toolbar"),
                        subtitle = subtitle.orEmpty(),
                        navigationIcon = navigationIcon,
                        actions = actions,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        glow = glow,
                        showDivider = false,
                    )
                } else {
                    SmallTopAppBar(
                        title = title,
                        modifier = Modifier.testTag("${tag}_toolbar"),
                        subtitle = subtitle.orEmpty(),
                        navigationIcon = navigationIcon,
                        actions = actions,
                        color = barColor,
                        scrollBehavior = scrollBehavior,
                        glow = glow,
                        showDivider = false,
                    )
                }
            },
        ) { inner ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().then(contentModifier).testTag(tag).overScrollVertical()
                    .springChain(springChainState)
                    .then(if (glowBackdrop != null) Modifier.layerBackdrop(glowBackdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = musicScrollPadding(PaddingValues(
                    start = padding.calculateStartPadding(direction),
                    top = inner.calculateTopPadding() + padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(direction),
                    bottom = inner.calculateBottomPadding() + padding.calculateBottomPadding(),
                )),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
                subtitle?.let { item { SettingsHint(it) } }
                springChainSettingsItems(springChainState) { content() }
            }
        }
    }
}

/** Utility-page toolbar for callers that do not own a COUI Scaffold. */
@Composable internal fun SettingsTopBar(title: String, onBack: (() -> Unit)? = null, tag: String = "settings_toolbar",
    backTag: String = "navigate_back", titleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val inherited = LocalGlassBackdrop.current
    val ownBackdrop = if (inherited == null) rememberGlassBackdrop() else null
    val backdrop = inherited ?: ownBackdrop
    Box(Modifier.fillMaxWidth().statusBarsPadding()) {
        if (inherited == null) Box(Modifier.matchParentSize().then(if (ownBackdrop != null) Modifier.layerBackdrop(ownBackdrop) else Modifier)
            .background(COUITheme.colorScheme.background))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Row(Modifier.fillMaxWidth().height(SettingsToolbarHeight).padding(horizontal = 16.dp).testTag(tag),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassIconButton({ if (onBack != null) onBack() else dispatcher?.onBackPressed() }, "返回",
                    Modifier.testTag(backTag)) { Icon(MusicIcons.Back, null) }
                Text(title, Modifier.weight(1f).then(titleModifier), style = COUITheme.textStyles.headlineXS,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions()
            }
        }
    }
}

/**
 * A full-width COUI action button for settings forms and receipts. The primary style is the
 * filled ColorOS button; [destructive] uses the error colour instead.
 */
@Composable internal fun SettingsActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, destructive: Boolean = false, enabled: Boolean = true, fillWidth: Boolean = true) {
    val colors = COUITheme.colorScheme
    val buttonColors = when {
        destructive -> ButtonDefaults.buttonColors(color = colors.error, contentColor = colors.onError)
        primary -> ButtonDefaults.buttonColorsPrimary()
        else -> ButtonDefaults.buttonColors()
    }
    Button(
        onClick = onClick,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        enabled = enabled,
        colors = buttonColors,
    ) {
        Text(label, style = COUITheme.textStyles.buttonL)
    }
}

/**
 * The rounded card that groups preference rows. Settings pages use COUI's [Card] so their
 * surface matches the ColorOS settings home; product pages keep the app's own squircle card,
 * which is what their surrounding artwork and spacing were designed against.
 */
@Composable internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalCouiSettingsPage.current) {
        Card(modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Column(modifier.fillMaxWidth().squircleSurface(
            color = COUITheme.colorScheme.cardBackground,
            cornerRadius = 24.dp,
            weight = SquircleDefaults.Weight,
        )) {
            Column(content = content)
        }
    }
}

/** Same section hierarchy as the appearance page: category heading, one card, footer outside the card. */
@Composable internal fun SettingsSection(title: String, modifier: Modifier = Modifier,
    footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreferenceCategory(title = title, marginStartType = PreferenceCategoryMarginStartType.Small)
        SettingsGroup(content = content)
        footer?.takeIf { it.isNotBlank() }?.let { SettingsHint(it) }
    }
}

@Composable internal fun SettingsDivider(start: androidx.compose.ui.unit.Dp = 16.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = start, end = 16.dp))
}

@Composable internal fun SettingsHint(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(text, modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, lineHeight = 18.sp,
        color = if (error) COUITheme.colorScheme.error else COUITheme.colorScheme.labelSecondaryVariant)
}

@Composable internal fun SettingsSectionHeader(title: String, action: String? = null, onClick: () -> Unit = {}) {
    PreferenceCategory(
        title = title,
        endWidget = action?.let { label ->
            { PreferenceCategoryDefaults.TextButton(label, onClick) }
        },
    )
}

/**
 * A preference row.
 *
 * Settings pages use COUI's native row: a clickable button row with no caller-supplied
 * [trailing] content is an [ArrowPreference] (COUI draws the chevron), and anything that owns
 * its end slot is a [BasicComponent]. Product pages keep the app's denser row so their pages,
 * which were laid out against it, do not shift.
 */
@Composable internal fun SettingsPreference(title: String, summary: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: (() -> Unit)? = null, role: Role = Role.Button,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null) {
    if (LocalCouiSettingsPage.current) {
        val endActions: @Composable RowScope.() -> Unit = trailing?.let { action -> { action() } } ?: {}
        // The caller's trailing content owns the whole end slot; only a bare row gets the chevron.
        if (onClick != null && role == Role.Button && trailing == null) {
            ArrowPreference(
                title = title,
                modifier = modifier,
                summary = summary,
                startAction = leading,
                bottomAction = footer,
                onClick = onClick,
                enabled = enabled,
            )
        } else {
            BasicComponent(
                modifier = modifier,
                title = title,
                summary = summary,
                startAction = leading,
                endActions = endActions,
                bottomAction = footer,
                onClick = onClick,
                enabled = enabled,
                role = role,
            )
        }
        return
    }
    val interactions = remember { MutableInteractionSource() }
    val ink = COUITheme.colorScheme.labelPrimary
    Column(modifier.fillMaxWidth().settingsPressFeedback(interactions).then(if (onClick != null) Modifier.clickable(
        enabled = enabled, role = role, interactionSource = interactions, indication = null, onClick = onClick,
    ) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 17.sp, lineHeight = 22.sp, color = ink.copy(alpha = if (enabled) 1f else .38f))
                summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, lineHeight = 18.sp,
                        color = COUITheme.colorScheme.labelSecondaryVariant.let { color ->
                            color.copy(alpha = color.alpha * (if (enabled) 1f else .45f))
                        })
                }
            }
            if (trailing != null) trailing()
            else if (onClick != null && role == Role.Button) Icon(
                COUIIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = COUITheme.colorScheme.labelTertiary,
            )
        }
        footer?.let { Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { it() } }
    }
}

@Composable internal fun SettingsToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        modifier = modifier,
        summary = summary,
        startAction = leading,
        enabled = enabled,
    )
}

@Composable internal fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    if (LocalCouiSettingsPage.current) {
        RadioButtonPreference(
            title = title,
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            summary = summary,
            startAction = leading,
            enabled = enabled,
        )
    } else {
        SettingsPreference(title, summary, modifier.semantics { this.selected = selected }, enabled, onClick,
            Role.RadioButton, leading, trailing = { SettingsSelectionCheck(selected) })
    }
}

@Immutable internal data class SettingsOption(val id: String, val label: String, val selected: Boolean,
    val summary: String? = null, val leadingContent: (@Composable () -> Unit)? = null, val onSelect: () -> Unit)

/** Options open in COUI's native spinner popup; selecting an entry saves once. */
@Composable internal fun SettingsOptionsRow(title: String, value: String, options: List<SettingsOption>,
    modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true, valueLeading: (@Composable () -> Unit)? = null) {
    val selected = options.indexOfFirst { it.selected }
    // COUI renders the item at selectedIndex as the row's value, so an unanswered row would
    // otherwise show its first option as if it were chosen. Show the caller's value instead.
    val placeholder = if (selected < 0 && options.isNotEmpty() && value.isNotBlank())
        listOf(SettingsOption("settings_none", value, true) {}) else emptyList()
    val entries = placeholder + options
    val selectedIndex = when {
        selected >= 0 -> selected
        placeholder.isEmpty() -> -1
        else -> 0
    }
    OverlaySpinnerPreference(
        items = entries.map { option ->
            DropdownItem(
                text = option.label,
                summary = option.summary,
                icon = option.leadingContent?.let { content -> { _: Modifier -> content() } },
            )
        },
        selectedIndex = selectedIndex,
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        startAction = valueLeading,
        onSelectedIndexChange = { index -> entries.getOrNull(index)?.onSelect() },
    )
}
