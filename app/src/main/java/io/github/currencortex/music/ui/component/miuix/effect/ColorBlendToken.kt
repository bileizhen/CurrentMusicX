// Ported from SukiSU-Ultra v4.1.3 (0ca744a), GPL-3.0. See THIRD_PARTY_NOTICES.md.
// Mirrored from compose-miuix-ui example.

package io.github.currencortex.music.ui.component.miuix.effect

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

object ColorBlendToken {

    private val brandLight = listOf(
        BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
    )
    private val brandDark = listOf(
        BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
    )

    fun brandInk(isDark: Boolean) = if (isDark) brandDark else brandLight

    val Pured_Regular_Light = listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )

    val Overlay_Thin_Light = listOf(
        BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
    )
}
