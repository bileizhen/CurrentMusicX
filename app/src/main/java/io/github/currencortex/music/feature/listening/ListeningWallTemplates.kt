package io.github.currencortex.music.feature.listening

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.cos
import kotlin.math.sin

/** Optional identity and editorial data supplied by the host; nothing is inferred from cover URLs. */
internal data class ListeningWallExportInfo(
    val nickname: String = "",
    val timeline: String = "",
    val songTitles: List<String> = emptyList(),
    val isCloud: Boolean = false,
    val cloudVip: Boolean? = null,
    val superVip: Boolean = false,
    val qrUrl: String? = null,
)
internal val LocalListeningWallExportInfo = staticCompositionLocalOf { ListeningWallExportInfo() }
internal enum class ListeningExportKind { POSTER, WALLPAPER }
internal enum class ListeningExportLayout { GRID, SCATTER, MOSAIC }
internal data class ListeningWallTemplate(val index: Int, val kind: ListeningExportKind) {
    val dark: Boolean get() = index < 3
    val layout: ListeningExportLayout get() = ListeningExportLayout.entries[index % 3]
    val height: Int get() = if (kind == ListeningExportKind.WALLPAPER) 2436 else if (layout == ListeningExportLayout.MOSAIC) 2205 else 2106
    val aspectRatio: Float get() = 1125f / height
    val label: String get() = (if (dark) "深色" else "浅色") + " · " + when (layout) {
        ListeningExportLayout.GRID -> "整齐唱片"
        ListeningExportLayout.SCATTER -> "散落唱片"
        ListeningExportLayout.MOSAIC -> "大小拼贴"
    }
    fun locked(info: ListeningWallExportInfo): Boolean = info.isCloud && layout == ListeningExportLayout.SCATTER && info.cloudVip != true
}

internal data class ListeningExportSlot(val x: Float, val y: Float, val size: Float, val angle: Float = 0f, val z: Int = 0, val pinLogo: Boolean = false) {
    fun contains(px: Float, py: Float): Boolean {
        val radians = Math.toRadians(-angle.toDouble())
        val dx = px - x
        val dy = py - y
        val localX = dx * cos(radians) - dy * sin(radians)
        val localY = dx * sin(radians) + dy * cos(radians)
        return localX >= 0 && localY >= 0 && localX <= size && localY <= size
    }
}

/** Geometry transcribed from official modules 30332/77976/24500 and CSS, in a 1125px canvas. */
internal fun listeningExportSlots(template: ListeningWallTemplate): List<ListeningExportSlot> {
    val wallpaper = template.kind == ListeningExportKind.WALLPAPER
    return when (template.layout) {
        ListeningExportLayout.GRID -> List(if (wallpaper) 15 else 12) { index ->
            ListeningExportSlot((index % 3) * 375f, (if (wallpaper) 843f else 610f) + (index / 3) * 375f, 375f)
        }
        ListeningExportLayout.MOSAIC -> buildList {
            val start = if (wallpaper) 827f else 589f
            add(ListeningExportSlot(0f, start, 750f)); add(ListeningExportSlot(750f, start, 375f)); add(ListeningExportSlot(750f, start + 375, 375f))
            val cell = 1125f / 7
            repeat(3) { column -> repeat(2) { row -> add(ListeningExportSlot(column * 2 * cell, start + 750 + row * 2 * cell, cell * 2)) } }
            repeat(4) { row -> add(ListeningExportSlot(6 * cell, start + 750 + row * cell, cell)) }
            repeat(10) { index -> add(ListeningExportSlot(index % 5 * 225f, start + 750 + 4 * cell + index / 5 * 225f, 225f)) }
        }
        ListeningExportLayout.SCATTER -> if (wallpaper) WallpaperScatter else PosterScatter
    }
}

/** Original repeats are deliberate: they spread a small, real collection across the selected layout. */
internal fun listeningExportCoverIndices(template: ListeningWallTemplate, coverCount: Int): List<Int> {
    if (coverCount <= 0) return emptyList()
    return when (template.layout) {
        ListeningExportLayout.GRID -> {
            val count = if (template.kind == ListeningExportKind.POSTER) 12 else 15
            val original = (0 until minOf(coverCount, count)).toList()
            var repeated = original
            val input = if (original.size < count / 2 + 1) {
                while (repeated.size < count / 2) repeated = repeated + repeated
                repeated.take(count / 3) + repeated.drop(1).take(count / 3) + repeated.drop(2).take(count / 3)
            } else {
                while (repeated.size < count) repeated = repeated + repeated
                repeated.take(count)
            }
            val order = if (count == 12) listOf(1,5,9,2,6,10,3,7,11,4,8,12) else listOf(1,6,11,2,7,12,3,8,13,4,9,14,5,10,15)
            order.map { input[(it - 1) % input.size] }
        }
        ListeningExportLayout.SCATTER -> (when (minOf(coverCount, 3)) {
            1 -> List(11) { 1 }
            2 -> listOf(1,2,1,2,1,2,1,2,1,2,1)
            else -> listOf(1,2,3,3,1,2,1,2,3,2,3)
        }).map { it - 1 }
        ListeningExportLayout.MOSAIC -> (when {
            coverCount == 1 -> List(23) { 1 }
            coverCount == 2 -> listOf(1,2,1,2,1,1,2,2,1,1,2,1,2,1,2,1,2,1,2,1,2,1,2)
            coverCount < 6 -> listOf(1,2,3,2,3,1,2,3,1,1,2,1,3,1,2,3,2,1,3,1,2,3,2)
            else -> listOf(1,2,3,5,3,4,5,1,4,2,1,2,1,6,3,1,2,4,5,6,4,1,6)
        }).map { it - 1 }
    }
}

internal fun listeningExportHit(template: ListeningWallTemplate, x: Float, y: Float): Int? =
    listeningExportSlots(template).withIndex().sortedWith(compareBy<IndexedValue<ListeningExportSlot>> { it.value.z }.thenBy { it.index })
        .lastOrNull { it.value.contains(x, y) }?.index

private val PosterScatter = listOf(
    ListeningExportSlot(220f,937f,612f,-6f,10), ListeningExportSlot(516f,1204f,462f,5f,9),
    ListeningExportSlot(100f,1380f,347f,-8.6f,8), ListeningExportSlot(-73f,553f,243f,15f,5),
    ListeningExportSlot(188f,731f,385f,0f,4), ListeningExportSlot(586f,750f,347f,18f,4),
    ListeningExportSlot(861f,629f,265f,-22.35f,4), ListeningExportSlot(-28f,1188f,347f,-19.46f,5),
    ListeningExportSlot(672f,1026f,347f,-9.52f,7), ListeningExportSlot(-230f,1723f,292f,-32.58f,4),
    ListeningExportSlot(921f,1516f,410f,15f,4),
)
private val WallpaperScatter = listOf(
    ListeningExportSlot(238f,1173f,621f,-6f,10), ListeningExportSlot(576f,1460f,513f,5f,9,true),
    ListeningExportSlot(112f,1658f,386f,-8.6f,8), ListeningExportSlot(-136f,686f,300f,15f,5),
    ListeningExportSlot(127f,1055f,362f,-21.93f,4), ListeningExportSlot(653f,957f,385f,18f,5),
    ListeningExportSlot(877f,751f,293f,-32.31f,4), ListeningExportSlot(-52f,1443f,385f,-19.46f,6),
    ListeningExportSlot(748f,1266f,385f,-9.52f,7), ListeningExportSlot(-225f,2049f,325f,-29.62f,4),
    ListeningExportSlot(913f,1816f,427f,15f,4),
)
