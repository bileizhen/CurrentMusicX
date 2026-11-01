package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.progressBarRangeInfo
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
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
private val LocalPlaceholderPulse = staticCompositionLocalOf<State<Float>?> { null }

@Composable private fun rememberPlaceholderPulse(): State<Float> {
    if (!LocalMusicPageActive.current) return remember { mutableFloatStateOf(.8f) }
    return rememberInfiniteTransition(label = "loading").animateFloat(.58f, .92f,
        infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse), label = "placeholder opacity")
}

/** One quiet pulse per loading section, stopped when its retained page is hidden. */
@Composable internal fun MusicPlaceholderScope(content: @Composable () -> Unit) {
    val pulse = LocalPlaceholderPulse.current ?: rememberPlaceholderPulse()
    CompositionLocalProvider(LocalPlaceholderPulse provides pulse, content = content)
}

@Composable fun MusicPlaceholder(modifier: Modifier, animated: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)) {
    val pulse = if (animated) LocalPlaceholderPulse.current ?: rememberPlaceholderPulse() else null
    Box(modifier.graphicsLayer { alpha = pulse?.value ?: 1f }
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .08f), shape))
}

/** Use the same font metrics and line-height trim as the final native text, including custom fonts. */
@Composable private fun LoadingTextBlock(widths: List<Float>, fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
    modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier.fillMaxWidth()) {
        top.yukonga.miuix.kmp.basic.Text(List(widths.size) { "音乐" }.joinToString("\n"),
            Modifier.clearAndSetSemantics { }, fontSize = fontSize, lineHeight = lineHeight, fontWeight = weight,
            color = androidx.compose.ui.graphics.Color.Transparent, minLines = widths.size, maxLines = widths.size)
        Column(Modifier.matchParentSize(), verticalArrangement = Arrangement.SpaceAround) {
            widths.forEach { width ->
                MusicPlaceholder(Modifier.fillMaxWidth(width).height(with(density) { (fontSize * .65f).toDp() }),
                    shape = RoundedCornerShape(4.dp))
            }
        }
    }
}

@Composable internal fun LoadingTextLine(width: Float = .7f, lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp,
    modifier: Modifier = Modifier) {
    LoadingTextBlock(listOf(width), lineHeight * .75f, lineHeight, modifier = modifier)
}

private fun Modifier.loadingState(label: String) = clearAndSetSemantics {
    contentDescription = label
    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate
}

@Composable fun LoadingSongList(count: Int = 3, modifier: Modifier = Modifier, searchActions: Boolean = false) {
    MusicPlaceholderScope {
        Column(modifier.fillMaxWidth().testTag("music_loading").loadingState("正在加载歌曲")) {
            repeat(count) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = if (searchActions) 10.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicPlaceholder(Modifier.size(52.dp), shape = RoundedCornerShape(if (searchActions) 10.dp else 22.dp))
                    Column(Modifier.weight(1f)) {
                        LoadingTextLine(if (index % 2 == 0) .78f else .62f, 22.sp)
                        LoadingTextLine(.5f, 16.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (searchActions) MusicPlaceholder(Modifier.size(88.dp, 44.dp), shape = CircleShape)
                        MusicPlaceholder(Modifier.size(44.dp), shape = CircleShape)
                    }
                }
            }
        }
    }
}

/** Same frame, inset and two-line title allocation as MusicArtworkCard. */
@Composable internal fun LoadingArtworkCard(modifier: Modifier = Modifier) {
    MusicPlaceholderScope {
        SettingsGroup(modifier.loadingState("正在加载封面")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MusicPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(12.dp))
                LoadingTextBlock(listOf(.88f, .55f), 15.sp, 20.sp, androidx.compose.ui.text.font.FontWeight.Medium)
                LoadingTextLine(.62f, 16.sp)
            }
        }
    }
}

/** Cover grids that use the page background rather than a framed recommendation card. */
@Composable internal fun LoadingCoverTile(modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 18.dp, titleGap: androidx.compose.ui.unit.Dp = 10.dp) {
    MusicPlaceholderScope {
        Column(modifier.loadingState("正在加载封面")) {
            MusicPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(cornerRadius))
            Spacer(Modifier.height(titleGap))
            LoadingTextBlock(listOf(.85f, .5f), 15.sp, 20.sp, androidx.compose.ui.text.font.FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            LoadingTextLine(.62f, 16.sp)
        }
    }
}

@Composable internal fun LoadingArtworkCards(cardWidth: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    MusicPlaceholderScope {
        androidx.compose.foundation.lazy.LazyRow(modifier.testTag("artwork_loading").loadingState("正在加载封面列表"),
            userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(3) { LoadingArtworkCard(Modifier.width(cardWidth)) }
        }
    }
}

@Composable internal fun LoadingTextList(count: Int = 3, modifier: Modifier = Modifier, leading: Boolean = false) {
    MusicPlaceholderScope {
        Column(modifier.fillMaxWidth().loadingState("正在加载内容")) {
            repeat(count) { index ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (leading) MusicPlaceholder(Modifier.size(20.dp), shape = CircleShape)
                    LoadingTextLine(if (index % 2 == 0) .72f else .52f, 22.sp, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable internal fun LoadingProfileCard(modifier: Modifier = Modifier) {
    MusicPlaceholderScope {
        SettingsGroup(modifier.testTag("profile_loading").loadingState("正在加载个人资料")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MusicPlaceholder(Modifier.size(64.dp), shape = CircleShape)
                    Column(Modifier.weight(1f)) { LoadingTextLine(.7f, 28.sp); LoadingTextLine(.48f, 18.sp) }
                }
                LoadingTextLine(.85f, 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) { Column(Modifier.weight(1f)) { LoadingTextLine(.45f, 26.sp); LoadingTextLine(.55f, 18.sp) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { MusicPlaceholder(Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) }
                }
            }
        }
    }
}

@Composable internal fun LoadingPostList(count: Int = 2, modifier: Modifier = Modifier) {
    MusicPlaceholderScope {
        Column(modifier.testTag("posts_loading").loadingState("正在加载帖子"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(count) {
                SettingsGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MusicPlaceholder(Modifier.size(36.dp), shape = CircleShape)
                            Column(Modifier.weight(1f)) { LoadingTextLine(.42f, 20.sp); LoadingTextLine(.25f, 16.sp) }
                        }
                        Column { LoadingTextLine(.95f); LoadingTextLine(.85f); LoadingTextLine(.58f) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { MusicPlaceholder(Modifier.size(60.dp, 32.dp), shape = CircleShape) }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun LoadingPreferenceCard(showAction: Boolean = false, modifier: Modifier = Modifier) {
    MusicPlaceholderScope {
        SettingsGroup(modifier) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicPlaceholder(Modifier.size(44.dp), shape = CircleShape)
                    Column(Modifier.weight(1f)) { LoadingTextLine(.55f, 22.sp); LoadingTextLine(.8f, 18.sp) }
                }
                if (showAction) MusicPlaceholder(Modifier.fillMaxWidth().height(44.dp), shape = CircleShape)
            }
        }
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
