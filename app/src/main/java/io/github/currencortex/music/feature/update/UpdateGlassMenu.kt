package io.github.currencortex.music.feature.update

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.blur.Backdrop

internal val UpdateButtonGlowKey = GlassPressLight
internal typealias UpdateMenuInteraction = GlassMenuInteraction
internal val UpdateMenuInteractionKey = GlassMenuInteractionKey
internal typealias UpdateMenuWaves = GlassMenuWaves
internal val UpdateMenuWavesKey = GlassMenuWavesKey

@Composable internal fun rememberUpdateBackdrop() = rememberGlassBackdrop()

@Composable internal fun UpdateGlassButton(backdrop: Backdrop?, tag: String, description: String,
    onClick: () -> Unit, onBoundsChanged: (Rect) -> Unit = {}, enabled: Boolean = true,
    buttonSize: Dp = 44.dp, content: @Composable () -> Unit) {
    GlassIconButton(onClick,description,Modifier.testTag(tag),backdrop,buttonSize,enabled,
        opensMenu=tag=="update_menu",onBoundsChanged=onBoundsChanged,content=content)
}

/** Update owns only menu entries and callbacks; all paint and interaction live in reusable UI. */
@Composable internal fun BoxScope.UpdateGlassMenu(open: Boolean, backdrop: Backdrop?, onDismiss: () -> Unit,
    toolbar: @Composable () -> Unit, actions: List<GlassMenuItem>, anchor: Rect? = null,
    onPathChanged: (List<String>) -> Unit = {}, onInteractionChanged: (Boolean) -> Unit = {}) {
    GlassMenu(open,{if(!it)onDismiss()},actions,anchor,backdrop,
        GlassMenuTags("update_overflow_panel","update_menu_dismiss","update_menu_action"),
        onPathChanged=onPathChanged,onInteractionChanged=onInteractionChanged,toolbar=toolbar)
}
