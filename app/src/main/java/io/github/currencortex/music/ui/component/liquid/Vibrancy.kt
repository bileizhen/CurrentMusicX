// Ported from SukiSU-Ultra v4.1.3 (0ca744a), 2026-09-06.
// LeiFetch changes: package/import adaptation; see THIRD_PARTY_NOTICES.md.
// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).
// Mirrored from compose-miuix-ui example.

package io.github.currencortex.music.ui.component.liquid

import io.github.suqi8.coui.kmp.blur.BackdropEffectScope
import io.github.suqi8.coui.kmp.blur.colorControls

fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
