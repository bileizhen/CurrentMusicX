package io.github.currencortex.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.currencortex.music.ui.component.nativeui.LocalElasticPressDiagnostics

internal fun ComposeContentTestRule.setContentWithPressDiagnostics(content: @Composable () -> Unit) {
    setContent { CompositionLocalProvider(LocalElasticPressDiagnostics provides true, content = content) }
}
