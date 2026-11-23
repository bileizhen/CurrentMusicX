package io.github.currencortex.music.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.suqi8.coui.kmp.blur.Backdrop

internal val UpdateButtonGlowKey = GlassPressLight

@Composable internal fun rememberUpdateBackdrop() = rememberGlassBackdrop()

@Composable internal fun UpdateGlassButton(backdrop: Backdrop?, tag: String, description: String,
    onClick: () -> Unit, onBoundsChanged: (Rect) -> Unit = {}, enabled: Boolean = true,
    buttonSize: Dp = 44.dp, content: @Composable () -> Unit) {
    GlassIconButton(onClick,description,Modifier.testTag(tag),backdrop,buttonSize,enabled,
        opensMenu=false,onBoundsChanged=onBoundsChanged,content=content)
}
