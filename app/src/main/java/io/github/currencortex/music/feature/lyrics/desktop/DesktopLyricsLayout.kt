package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.ui.unit.dp

// Reuse transparent halo margins as spacing without clipping the bitmap.
// Only adjacent title/translation layouts overlap; the canvas keeps all 20 dp.
internal val desktopHaloPadding = 20.dp
internal val desktopCaptionSpacing = (-14).dp
internal fun desktopTranslationSize(font: Float): Float = (font * .62f).coerceAtLeast(12f)
