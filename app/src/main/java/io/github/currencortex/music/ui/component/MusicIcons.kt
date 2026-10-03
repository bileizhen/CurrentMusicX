// SPDX-License-Identifier: GPL-3.0-only
// Outline construction and download/file paths adapted from LeiFetch TransferIcons.
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
    private fun icon(name: String, autoMirror: Boolean = false, draw: PathBuilder.() -> Unit) = ImageVector.Builder(
        name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
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
