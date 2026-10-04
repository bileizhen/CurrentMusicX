package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val coverPrefetchPermits = Semaphore(3)

@Composable fun MusicPlaceholder(modifier: Modifier, animated: Boolean = true) {
    val pulse = if (animated) {
        val transition = rememberInfiniteTransition(label = "loading")
        val alpha by transition.animateFloat(.45f, 1f,
            infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "placeholder opacity")
        alpha
    } else 1f
    Box(modifier.clip(RoundedCornerShape(12.dp)).graphicsLayer { alpha = pulse }
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .09f)))
}

@Composable fun LoadingSongList(count: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("music_loading").clearAndSetSemantics { contentDescription = "正在加载歌曲" },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicPlaceholder(Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MusicPlaceholder(Modifier.fillMaxWidth(.72f).height(15.dp))
                MusicPlaceholder(Modifier.fillMaxWidth(.48f).height(11.dp))
            }
        } }
    }
}

/** Uses the same URL/size and shared Coil cache as visible covers; cancellation stops queued work. */
@Composable fun PreloadMusicCovers(urls: List<String>, pixels: Int = 160) {
    val context = LocalContext.current.applicationContext
    val targets = urls.filter(String::isNotBlank).distinct().take(12)
    LaunchedEffect(targets, pixels) {
        val loader = SingletonImageLoader.get(context)
        coroutineScope { targets.forEach { url -> launch {
            coverPrefetchPermits.withPermit { loader.execute(ImageRequest.Builder(context).data(coverRequestUrl(url, pixels)).size(pixels).build()) }
        } } }
    }
}
