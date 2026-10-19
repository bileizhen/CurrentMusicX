package io.github.currencortex.music.feature.roaming

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Swap any two tiles to complete the current song's cover; the recommendation mode stays native. */
@Composable internal fun RoamingCoverPuzzle(cover: String, songId: Long?, showCaption: Boolean = true) {
    val context = LocalContext.current
    var bitmap by remember(cover) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(cover) { mutableStateOf(false) }
    var tiles by rememberSaveable(songId) { mutableStateOf(listOf(4, 1, 6, 3, 8, 0, 7, 2, 5)) }
    var selected by remember(songId) { mutableStateOf<Int?>(null) }
    val complete = tiles == (0..8).toList()
    LaunchedEffect(cover) {
        bitmap = null; failed = false
        if (cover.isBlank()) { failed = true; return@LaunchedEffect }
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(cover).size(900).build())
        bitmap = (result as? SuccessResult)?.image?.toBitmap()
        failed = bitmap == null
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).testTag("roaming_puzzle")) {
            val image = bitmap?.asImageBitmap()
            if (image == null) {
                MusicCover(cover, Modifier.fillMaxSize(), 900, 22.dp)
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                tiles.chunked(3).forEachIndexed { row, values ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        values.forEachIndexed { column, tile ->
                            val index = row * 3 + column
                            Canvas(Modifier.weight(1f).fillMaxHeight().border(if (selected == index) 2.dp else 0.dp,
                                MiuixTheme.colorScheme.primary).clickable(enabled = !complete) {
                                val first = selected
                                if (first == null) selected = index else {
                                    tiles = tiles.toMutableList().apply { val old = this[first]; this[first] = this[index]; this[index] = old }
                                    selected = null
                                }
                            }.testTag("roaming_puzzle_tile_$index")) {
                                val side = minOf(image.width, image.height) / 3
                                drawImage(image, IntOffset((image.width - side * 3) / 2 + tile % 3 * side,
                                    (image.height - side * 3) / 2 + tile / 3 * side), IntSize(side, side),
                                    IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()))
                            }
                        }
                    }
                }
            }
        }
        if (showCaption) Text(if (complete) "拼好了，继续聆听这首歌" else if (failed) "封面暂不可用，歌曲继续播放" else "轻点两块碎片交换位置",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (complete && showCaption) MusicTextAction("再拼一次", { tiles = (0..8).shuffled(); selected = null })
    }
}
