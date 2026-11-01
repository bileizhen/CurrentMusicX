package io.github.currencortex.music.feature.update

import androidx.compose.runtime.Composable
import io.github.currencortex.music.ui.component.*

// Keep the update API while settings and updates use the same renderer and timing.
typealias UpdatePageOrigin = PageContainerOrigin
internal typealias UpdateTransformFrame = PageContainerFrame
internal val UpdateTransformGeometry = PageContainerGeometry

@Composable internal fun UpdateContainerTransform(visible: Boolean, origin: UpdatePageOrigin?,
    onPresentedChanged: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    PageContainerTransform(visible, origin, onPresentedChanged, "update_container_transform", content)
}
