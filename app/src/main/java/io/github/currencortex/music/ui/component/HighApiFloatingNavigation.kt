package io.github.currencortex.music.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.tween

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** All miuix-blur entry points live behind the API 33 boundary in this file. */
@RequiresApi(33)
@Composable
fun HighApiFloatingNavigation(
    selectedIndex: Int, labels: List<String>, icons: List<ImageVector>, onSelect: (Int) -> Unit,
    blur: Boolean, glass: Boolean, visible: Boolean = true, content: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    // Support detection precedes FloatingBottomBar, which owns an AGSL highlight.
    if (!isRuntimeShaderSupported()) {
        Box(Modifier.fillMaxSize()) {
            content()
            overlay()
            AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it }, exit = fadeOut(tween(100)) + slideOutVertically(tween(160)) { it }) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp).widthIn(max = 480.dp)) {
                PlainFloatingBar(selectedIndex, labels, icons, onSelect)
            }
            }
        }
        return
    }
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
        CompositionLocalProvider(LocalMusicGlassSurface provides { modifier, body -> MusicGlassCapsule(backdrop, modifier, blur, glass, body) }) {
            overlay()
        }
        AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it }, exit = fadeOut(tween(100)) + slideOutVertically(tween(160)) { it }) {
        FloatingBottomBar(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp).widthIn(max = 480.dp).fillMaxWidth()
                .testTag(if (glass && blur) "glass_floating_bar" else if (blur) "blur_floating_bar" else "solid_floating_bar"),
            selectedIndex = { selectedIndex }, onSelected = onSelect, backdrop = backdrop, tabsCount = labels.size,
            isBlurEnabled = blur, isGlassEnabled = glass && blur,
        ) {
            labels.forEachIndexed { index, label ->
                FloatingBottomBarItem(onClick = { onSelect(index) }, modifier = Modifier.testTag("tab_$index").semantics { selected = selectedIndex == index }) {
                    Icon(icons[index], null)
                    Text(label, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
        }
    }
}
