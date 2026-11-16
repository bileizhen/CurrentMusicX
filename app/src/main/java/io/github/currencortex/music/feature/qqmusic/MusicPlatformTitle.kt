package io.github.currencortex.music.feature.qqmusic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.settings.MusicPlatform
import io.github.currencortex.music.ui.component.HomeBrandTitle
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.nativeui.GlassMenu
import io.github.currencortex.music.ui.component.nativeui.GlassMenuAnchor
import io.github.currencortex.music.ui.component.nativeui.GlassMenuItem
import io.github.currencortex.music.ui.component.nativeui.GlassMenuTags
import io.github.currencortex.music.ui.component.nativeui.SettingsScrollLayout
import io.github.currencortex.music.ui.component.nativeui.rememberGlassBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop

/** The platform menu is drawn over the page, anchored to the home wordmark. */
@Composable internal fun MusicPlatformScrollLayout(
    container: AppContainer,
    modifier: Modifier = Modifier,
    showPlatformMenu: Boolean = true,
    glassOverContent: Boolean = true,
    topBar: @Composable (title: (@Composable () -> Unit)?) -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    if (!showPlatformMenu) {
        SettingsScrollLayout(modifier, glassOverContent = glassOverContent, topBar = { topBar(null) }, content = content)
        return
    }
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val platform = settings.musicPlatform
    val scope = rememberCoroutineScope()
    val backdrop = rememberGlassBackdrop()
    val active = LocalMusicPageActive.current
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(active) { if (!active) expanded = false }
    val items = MusicPlatform.entries.map { option ->
        GlassMenuItem("music_platform_${option.name}", option.label, checked = option == platform,
            onClick = {
                if (option != platform) scope.launch {
                    container.musicSettings.setMusicPlatform(option)
                }
            })
    }
    Box(modifier) {
        GlassMenu(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            items = items,
            anchor = anchor,
            backdrop = backdrop,
            tags = GlassMenuTags("music_platform_menu", "music_platform_dismiss", "music_platform"),
            menuWidth = null,
            bottomInset = LocalMusicBottomInset.current,
            content = {
                SettingsScrollLayout(
                    Modifier.fillMaxSize().then(if (backdrop == null) Modifier else Modifier.layerBackdrop(backdrop)),
                    glassOverContent = glassOverContent,
                    topBar = {
                        topBar {
                            MusicPlatformTitle({ if (anchor != null) expanded = true }, { anchor = it })
                        }
                    },
                    content = content,
                )
            },
            toolbar = {},
        )
    }
}

@Composable private fun MusicPlatformTitle(onOpen: () -> Unit, onBoundsChanged: (Rect) -> Unit) {
    GlassMenuAnchor(onOpen = onOpen) { open, target ->
        Box(
            target.testTag("music_platform_switch")
                .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = "切换音乐平台",
                    role = Role.Button,
                    onClick = open,
                ),
        ) {
            HomeBrandTitle()
        }
    }
}
