package io.github.currencortex.music.ui.component

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** API 33 material host: the dock owns geometry and the backdrop owns page sampling. */
@RequiresApi(33)
@Composable
fun HighApiFloatingNavigation(selectedIndex: Int, labels: List<String>, icons: List<ImageVector>,
    onSelect: (Int) -> Unit, blur: Boolean, glass: Boolean, content: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}) {
    if (!isRuntimeShaderSupported()) {
        Box(Modifier.fillMaxSize()) { content(); overlay() }
        return
    }
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    val highlight = if (blur && glass) rememberGravityRotatedHighlight(iosIndicatorSpecular, -45f) else iosIndicatorSpecular
    Box(Modifier.fillMaxSize()) {
        // Keep the page at the same composition location when blur is toggled.
        Box(Modifier.fillMaxSize().then(if (blur) Modifier.layerBackdrop(backdrop) else Modifier)) { content() }
        CompositionLocalProvider(
            LocalMusicGlassSurface provides { modifier, body -> MusicGlassCapsule(backdrop, modifier, blur, glass, body) },
            LocalMusicDockSurface provides { modifier, shape, body ->
                Box(modifier.musicGlassMaterial(backdrop, blur, glass, highlight, shape)) { body() }
            },
            LocalMusicSideWaterDrop provides if (blur) { modifier, motion, mirror ->
                HighApiSideWaterDrop(backdrop, modifier, motion, glass, mirror)
            } else null,
            LocalMusicDockNavigation provides { modifier ->
                if (!blur) PlainFloatingBar(selectedIndex, labels, icons, onSelect, modifier, embedded = true)
                else
                FloatingBottomBar(modifier.testTag(if (glass && blur) "glass_floating_bar" else if (blur) "blur_floating_bar" else "solid_floating_bar"),
                    selectedIndex = { selectedIndex }, onSelected = onSelect, backdrop = backdrop,
                    tabsCount = labels.size, isBlurEnabled = blur, isGlassEnabled = glass && blur, embedded = true) {
                    labels.forEachIndexed { index, label ->
                        FloatingBottomBarItem({ onSelect(index) }, Modifier.testTag("tab_$index").semantics { selected = selectedIndex == index }) {
                            Icon(icons[index], null)
                            Text(label, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }) { overlay() }
    }
}
