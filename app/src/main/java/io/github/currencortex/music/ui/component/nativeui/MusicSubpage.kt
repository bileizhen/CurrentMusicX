package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared page chrome for account forms, grids and music lists with their own scroll state. */
@Composable internal fun MusicSubpage(title: String, tag: String, onBack: (() -> Unit)? = null,
    backTag: String = "navigate_back", toolbarTag: String = "${tag}_toolbar", modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable (Dp) -> Unit) {
    SettingsUiScope {
        CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
            Box(modifier.fillMaxSize().navigationBarsPadding()) {
                SettingsScrollLayout(Modifier.widthIn(max = 840.dp).fillMaxSize().align(androidx.compose.ui.Alignment.TopCenter)
                    .testTag(tag), topBar = {
                    SettingsTopBar(title, onBack, tag = toolbarTag, backTag = backTag, actions = actions)
                }, content = content)
            }
        }
    }
}
