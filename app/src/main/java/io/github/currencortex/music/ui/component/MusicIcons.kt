// SPDX-License-Identifier: GPL-3.0-only
// Outline construction and download/file paths adapted from LeiFetch TransferIcons.
// Font and TextSize paths from upstream lib/ui/icons/app_glyphs.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
package io.github.currencortex.music.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object MusicIcons {
    val Sun = icon("Sun") {
        moveTo(16f, 12f); curveTo(16f, 17.3f, 8f, 17.3f, 8f, 12f); curveTo(8f, 6.7f, 16f, 6.7f, 16f, 12f)
        moveTo(12f, 2f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(4f, 12f); moveTo(20f, 12f); lineTo(22f, 12f)
        moveTo(5f, 5f); lineTo(6.5f, 6.5f); moveTo(17.5f, 17.5f); lineTo(19f, 19f)
        moveTo(5f, 19f); lineTo(6.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(19f, 5f)
    }
    val Moon = icon("Moon") {
        moveTo(20f, 15f); curveTo(10f, 19f, 5f, 9f, 10f, 3f)
        curveTo(-2f, 6f, 2f, 23f, 13f, 21f); curveTo(17f, 21f, 19f, 18f, 20f, 15f); close()
    }
    val Palette = glyphIcon("Palette", outline = {
        moveTo(20.59f, 13.9f); arcTo(8.8f, 8.8f, 0f, true, false, 13.9f, 20.59f)
        arcTo(1.2f, 1.2f, 0f, false, false, 14.83f, 19.28f)
        arcTo(4f, 4f, 0f, false, true, 19.28f, 14.83f)
        arcTo(1.2f, 1.2f, 0f, false, false, 20.59f, 13.9f); close()
    }, solid = {
        solidCircle(7.54f, 13.89f, 1.4f); solidCircle(7.96f, 9.32f, 1.4f)
        solidCircle(12f, 7.15f, 1.4f); solidCircle(16.04f, 9.32f, 1.4f)
    })
    val PureBlack = glyphIcon("PureBlack", outline = {
        moveTo(5.5f, 3.5f); lineTo(18.5f, 3.5f); arcTo(2.5f, 2.5f, 0f, false, true, 21f, 6f)
        lineTo(21f, 14f); arcTo(2.5f, 2.5f, 0f, false, true, 18.5f, 16.5f)
        lineTo(5.5f, 16.5f); arcTo(2.5f, 2.5f, 0f, false, true, 3f, 14f)
        lineTo(3f, 6f); arcTo(2.5f, 2.5f, 0f, false, true, 5.5f, 3.5f); close()
        moveTo(12f, 16.5f); lineTo(12f, 20.4f); moveTo(8f, 20.4f); lineTo(16f, 20.4f)
    }, solid = {
        moveTo(7f, 6f); lineTo(17f, 6f); arcTo(1.5f, 1.5f, 0f, false, true, 18.5f, 7.5f)
        lineTo(18.5f, 12.5f); arcTo(1.5f, 1.5f, 0f, false, true, 17f, 14f)
        lineTo(7f, 14f); arcTo(1.5f, 1.5f, 0f, false, true, 5.5f, 12.5f)
        lineTo(5.5f, 7.5f); arcTo(1.5f, 1.5f, 0f, false, true, 7f, 6f); close()
    })
    val Eyedropper = icon("Eyedropper") {
        moveTo(4.79f, 19.21f); lineTo(8.46f, 18.08f); lineTo(14.4f, 12.14f)
        lineTo(16.24f, 13.98f); lineTo(18.79f, 11.43f); lineTo(17.73f, 10.37f)
        lineTo(21.4f, 6.7f); lineTo(17.3f, 2.6f); lineTo(13.63f, 6.27f)
        lineTo(12.57f, 5.21f); lineTo(10.02f, 7.76f); lineTo(11.86f, 9.6f); lineTo(5.92f, 15.54f); close()
    }
    val PaletteAdd = icon("PaletteAdd") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Font = icon("Font") {
        moveTo(5.2f, 19.6f); lineTo(11.75f, 4.95f)
        curveTo(11.9167f, 4.5833f, 12.0833f, 4.5833f, 12.25f, 4.95f); lineTo(18.8f, 19.6f)
        moveTo(7.7f, 14f); lineTo(16.3f, 14f)
    }
    val TextSize = icon("TextSize") {
        moveTo(3.2f, 4.4f); lineTo(14f, 4.4f); moveTo(8.6f, 4.4f); lineTo(8.6f, 19.6f)
        moveTo(14.8f, 10.8f); lineTo(21f, 10.8f); moveTo(17.9f, 10.8f); lineTo(17.9f, 19.6f)
    }
    val Reset = icon("Reset") {
        moveTo(5.7006f, 10.5603f)
        arcTo(7.07f, 7.07f, 0f, true, false, 12f, 6.7f)
        moveTo(12f, 6.7f); lineTo(9.9f, 6.7f)
        moveTo(13.7f, 10.5f); lineTo(9.9f, 6.7f); lineTo(13.7f, 2.9f)
    }
    val Stop = icon("Stop") { moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close() }
    val Microphone = icon("Microphone") {
        moveTo(9f, 5f); curveTo(9f, 1f, 15f, 1f, 15f, 5f)
        lineTo(15f, 11f); curveTo(15f, 15f, 9f, 15f, 9f, 11f); close()
        moveTo(5f, 10f); lineTo(5f, 11f); curveTo(5f, 20f, 19f, 20f, 19f, 11f); lineTo(19f, 10f)
        moveTo(12f, 18f); lineTo(12f, 22f); moveTo(8f, 22f); lineTo(16f, 22f)
    }
    val Heart = icon("Heart") {
        moveTo(12f, 21f); lineTo(3.8f, 12.8f)
        curveTo(-1f, 7.8f, 6f, 1f, 12f, 7f)
        curveTo(18f, 1f, 25f, 7.8f, 20.2f, 12.8f); close()
    }
    val Headphones = icon("Headphones") {
        moveTo(4f, 14f); lineTo(4f, 11f); curveTo(4f, .4f, 20f, .4f, 20f, 11f); lineTo(20f, 14f)
        moveTo(4f, 12f); lineTo(7f, 12f); lineTo(7f, 21f); lineTo(3f, 21f); lineTo(3f, 12f); close()
        moveTo(20f, 12f); lineTo(17f, 12f); lineTo(17f, 21f); lineTo(21f, 21f); lineTo(21f, 12f); close()
    }
    val Update = icon("Update") {
        moveTo(20f, 8f); curveTo(14f, -2f, 2f, 4f, 3f, 13f)
        curveTo(4f, 23f, 18f, 25f, 21f, 15f)
        moveTo(20f, 3f); lineTo(20f, 8f); lineTo(15f, 8f)
        moveTo(12f, 7f); lineTo(12f, 13f); lineTo(16f, 15f)
    }
    val Rocket = icon("Rocket") {
        moveTo(8f, 16f); curveTo(7f, 10f, 12f, 3f, 21f, 3f)
        curveTo(21f, 12f, 14f, 17f, 8f, 16f); close()
        moveTo(7f, 10f); lineTo(3f, 12f); lineTo(7f, 14f)
        moveTo(14f, 17f); lineTo(12f, 21f); lineTo(10f, 17f)
        moveTo(7f, 18f); lineTo(3f, 21f)
        moveTo(16f, 8f); lineTo(16.1f, 8.1f)
    }

    val Check = icon("Check") { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }
    val Info = icon("Info") {
        moveTo(21f, 12f); curveTo(21f, 24f, 3f, 24f, 3f, 12f); curveTo(3f, 0f, 21f, 0f, 21f, 12f); close()
        moveTo(12f, 11f); lineTo(12f, 17f); moveTo(12f, 7f); lineTo(12f, 7.2f)
    }
    val Blur = icon("Blur") { repeat(4) { x -> repeat(4) { y -> moveTo(4f + x * 5, 4f + y * 5); lineTo(4.2f + x * 5, 4f + y * 5) } } }
    val BottomBar = icon("BottomBar") {
        moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        moveTo(3f, 14f); lineTo(21f, 14f); moveTo(7f, 17f); lineTo(17f, 17f)
    }
    val Drop = icon("Drop") {
        moveTo(12f, 3f); curveTo(10f, 7f, 5f, 11f, 5f, 15f); curveTo(5f, 24f, 19f, 24f, 19f, 15f); curveTo(19f, 11f, 14f, 7f, 12f, 3f); close()
    }
    val Scale = icon("Scale") {
        moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f); moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
        moveTo(3f, 16f); lineTo(3f, 21f); lineTo(8f, 21f); moveTo(16f, 21f); lineTo(21f, 21f); lineTo(21f, 16f)
    }
    val Storage = icon("Storage") {
        moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(4f, 10f); close()
        moveTo(4f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(7f, 7f); lineTo(7.2f, 7f); moveTo(7f, 17f); lineTo(7.2f, 17f)
    }
    private fun icon(name: String, autoMirror: Boolean = false, draw: PathBuilder.() -> Unit) = ImageVector.Builder(
        name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
    private fun glyphIcon(name: String, outline: PathBuilder.() -> Unit, solid: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = outline)
            path(fill = SolidColor(Color.Black), pathBuilder = solid)
        }.build()
    private fun PathBuilder.solidCircle(x: Float, y: Float, radius: Float) {
        moveTo(x + radius, y); arcTo(radius, radius, 0f, false, true, x - radius, y)
        arcTo(radius, radius, 0f, false, true, x + radius, y); close()
    }
    val Folder = icon("Folder") {
        moveTo(3f, 7f); lineTo(3f, 20f); lineTo(21f, 20f); lineTo(21f, 7f)
        lineTo(11f, 7f); lineTo(9f, 4f); lineTo(3f, 4f); close()
    }
    val Transfer = icon("Transfer") {
        moveTo(8f, 3f); lineTo(8f, 20f); moveTo(3f, 15f); lineTo(8f, 20f); lineTo(13f, 15f)
        moveTo(16f, 21f); lineTo(16f, 4f); moveTo(11f, 9f); lineTo(16f, 4f); lineTo(21f, 9f)
    }
    val Download = icon("Download") {
        moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(4f, 15f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 15f)
    }
    val Upload = icon("Upload") {
        moveTo(12f, 15f); lineTo(12f, 3f); moveTo(7f, 8f); lineTo(12f, 3f); lineTo(17f, 8f)
        moveTo(4f, 15f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 15f)
    }
    val ArrowUp = icon("ArrowUp") {
        moveTo(12f, 20f); lineTo(12f, 4f)
        moveTo(5f, 11f); lineTo(12f, 4f); lineTo(19f, 11f)
    }
    val Share = icon("Share") {
        moveTo(9f, 8f); lineTo(9f, 5f); lineTo(21f, 5f); lineTo(21f, 17f); lineTo(17f, 17f)
        moveTo(3f, 9f); lineTo(15f, 9f); lineTo(15f, 21f); lineTo(3f, 21f); close()
        moveTo(7f, 15f); lineTo(11f, 15f)
    }
    val Account = icon("Account") {
        moveTo(16f, 7f); curveTo(16f, 12.4f, 8f, 12.4f, 8f, 7f)
        curveTo(8f, 1.6f, 16f, 1.6f, 16f, 7f); close()
        moveTo(4f, 21f); curveTo(4f, 11f, 20f, 11f, 20f, 21f)
    }
    val Device = icon("Device") {
        moveTo(6f, 2f); lineTo(18f, 2f); lineTo(18f, 22f); lineTo(6f, 22f); close()
        moveTo(10f, 5f); lineTo(14f, 5f); moveTo(11f, 19f); lineTo(13f, 19f)
    }
    val Search = icon("Search") {
        moveTo(17f, 10f); curveTo(17f, 19.3f, 3f, 19.3f, 3f, 10f)
        curveTo(3f, .7f, 17f, .7f, 17f, 10f); close(); moveTo(15f, 15f); lineTo(21f, 21f)
    }
    val Grid = icon("Grid") {
        moveTo(3f, 3f); lineTo(10f, 3f); lineTo(10f, 10f); lineTo(3f, 10f); close()
        moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(14f, 10f); close()
        moveTo(3f, 14f); lineTo(10f, 14f); lineTo(10f, 21f); lineTo(3f, 21f); close()
        moveTo(14f, 14f); lineTo(21f, 14f); lineTo(21f, 21f); lineTo(14f, 21f); close()
    }
    val List = icon("List") {
        repeat(3) { val y = 5f + it * 7f; moveTo(3f, y); lineTo(5f, y); moveTo(9f, y); lineTo(21f, y) }
    }
    val Sort = icon("Sort") {
        moveTo(4f, 5f); lineTo(20f, 5f); moveTo(4f, 12f); lineTo(15f, 12f); moveTo(4f, 19f); lineTo(10f, 19f)
    }
    val Add = icon("Add") { moveTo(12f, 4f); lineTo(12f, 20f); moveTo(4f, 12f); lineTo(20f, 12f) }
    val Close = icon("Close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val More = icon("More") { repeat(3) { val y = 5f + it * 7f; moveTo(12f, y); lineTo(12f, y + .2f) } }
    val Pause = icon("Pause") { moveTo(8f, 5f); lineTo(8f, 19f); moveTo(16f, 5f); lineTo(16f, 19f) }
    val Play = icon("Play") { moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close() }
    val Shuffle = icon("Shuffle") {
        moveTo(3f, 6f); lineTo(6f, 6f); lineTo(17f, 18f); lineTo(21f, 18f)
        moveTo(17f, 14f); lineTo(21f, 18f); lineTo(17f, 22f)
        moveTo(3f, 18f); lineTo(6f, 18f); lineTo(17f, 6f); lineTo(21f, 6f)
        moveTo(17f, 2f); lineTo(21f, 6f); lineTo(17f, 10f)
    }
    val PreviousTrack = icon("PreviousTrack") {
        moveTo(5f, 5f); lineTo(5f, 19f)
        moveTo(19f, 5f); lineTo(8f, 12f); lineTo(19f, 19f); close()
    }
    val NextTrack = icon("NextTrack") {
        moveTo(19f, 5f); lineTo(19f, 19f)
        moveTo(5f, 5f); lineTo(16f, 12f); lineTo(5f, 19f); close()
    }
    val Trash = icon("Trash") {
        moveTo(4f, 7f); lineTo(20f, 7f); moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
        moveTo(6f, 7f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 7f)
        moveTo(10f, 11f); lineTo(10f, 17f); moveTo(14f, 11f); lineTo(14f, 17f)
    }
    val Image = icon("Image") {
        moveTo(3f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(3f, 21f); close()
        moveTo(3f, 17f); lineTo(9f, 11f); lineTo(14f, 16f); lineTo(17f, 13f); lineTo(21f, 17f)
        moveTo(16f, 7f); lineTo(17f, 7f)
    }
    val Back = icon("Back", autoMirror = true) { moveTo(20f, 12f); lineTo(4f, 12f); moveTo(10f, 6f); lineTo(4f, 12f); lineTo(10f, 18f) }
    val Forward = icon("Forward", autoMirror = true) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val File = icon("File") {
        moveTo(14f, 3f); lineTo(5f, 3f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 8f); close()
        moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
        moveTo(9f, 13f); lineTo(15f, 13f); moveTo(9f, 17f); lineTo(13f, 17f)
    }
    val Rename = icon("Rename") {
        moveTo(4f, 16f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(8f, 20f); lineTo(4f, 20f); close()
        moveTo(13f, 7f); lineTo(17f, 11f)
    }
    val Copy = icon("Copy") {
        moveTo(8f, 8f); lineTo(20f, 8f); lineTo(20f, 21f); lineTo(8f, 21f); close()
        moveTo(16f, 8f); lineTo(16f, 3f); lineTo(3f, 3f); lineTo(3f, 16f); lineTo(8f, 16f)
    }
}
