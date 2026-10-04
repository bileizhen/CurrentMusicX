package io.github.currencortex.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** API neutral surface host. The root supplies the shared backdrop only on supported devices. */
val LocalMusicGlassSurface = staticCompositionLocalOf<@Composable (Modifier, @Composable () -> Unit) -> Unit> {
    { modifier, content ->
        Box(modifier.clip(CircleShape).background(MiuixTheme.colorScheme.surfaceContainer)) { content() }
    }
}
