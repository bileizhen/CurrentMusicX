package io.github.currencortex.music.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.BannerVideoCache
import io.github.currencortex.music.data.profile.BannerTrimSelection
import io.github.currencortex.music.data.profile.BannerVideoInfo
import io.github.currencortex.music.data.profile.BannerVideoRules
import io.github.currencortex.music.data.profile.loadBannerVideoThumbnails
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Non-intercepting TextureView PlayerView so banner taps and pager/list swipes pass through. */
@OptIn(UnstableApi::class)
class BannerVideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PlayerView(context, attrs) {
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
}

/** Muted, non-audio-focus, center-cropped looping video player used by banner hero & preview. */
@OptIn(UnstableApi::class)
@Composable
internal fun BannerVideoSurface(
    source: Any,
    httpClient: OkHttpClient?,
    active: Boolean,
    modifier: Modifier = Modifier,
    videoCache: BannerVideoCache? = null,
    trimRange: BannerTrimSelection? = null,
    scrubPositionMs: Long? = null,
    onPositionUpdate: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var renderedFirstFrame by remember(source) { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val shouldPlay = active && lifecycleStarted && scrubPositionMs == null

    DisposableEffect(context, source, videoCache) {
        val builder = ExoPlayer.Builder(context)
        if (source is String && httpClient != null) {
            val mediaSourceFactory = videoCache?.mediaSourceFactory(httpClient)
                ?: DefaultMediaSourceFactory(OkHttpDataSource.Factory(httpClient))
            builder.setMediaSourceFactory(mediaSourceFactory)
        }
        val exo = builder.build().apply {
            volume = 0f
            setAudioAttributes(AudioAttributes.DEFAULT, false)
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            repeatMode = Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            setMediaItem(
                when (source) {
                    is Uri -> MediaItem.fromUri(source)
                    else -> MediaItem.fromUri(source.toString())
                },
            )
            prepare()
            playWhenReady = shouldPlay
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedFirstFrame = true
            }
        }
        exo.addListener(listener)
        player = exo
        onDispose {
            exo.removeListener(listener)
            player = null
            exo.release()
        }
    }

    LaunchedEffect(player, shouldPlay) {
        val exo = player ?: return@LaunchedEffect
        exo.playWhenReady = shouldPlay
        if (shouldPlay) exo.play() else exo.pause()
    }

    LaunchedEffect(player, scrubPositionMs) {
        val exo = player ?: return@LaunchedEffect
        val target = scrubPositionMs ?: return@LaunchedEffect
        exo.seekTo(target.coerceAtLeast(0L))
        onPositionUpdate?.invoke(target)
    }

    val currentRange by rememberUpdatedState(trimRange)
    val positionCallback by rememberUpdatedState(onPositionUpdate)
    LaunchedEffect(player, trimRange != null, shouldPlay) {
        val exo = player ?: return@LaunchedEffect
        val range = currentRange ?: return@LaunchedEffect
        if (shouldPlay && (exo.currentPosition < range.startMs || exo.currentPosition >= range.endMs)) {
            exo.seekTo(range.startMs)
        }
        while (shouldPlay) {
            val activeRange = currentRange ?: break
            val pos = exo.currentPosition
            if (pos >= activeRange.endMs || pos < activeRange.startMs - 80L) {
                exo.seekTo(activeRange.startMs)
                positionCallback?.invoke(activeRange.startMs)
            } else {
                positionCallback?.invoke(pos.coerceIn(activeRange.startMs, activeRange.endMs))
            }
            delay(33L)
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (renderedFirstFrame) 1f else 0f,
        animationSpec = tween(220),
        label = "banner_video_alpha",
    )

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.profile_banner_video, null, false) as PlayerView).apply {
                this.player = player
                useController = false
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        },
        onRelease = { view ->
            view.player = null
        },
        modifier = modifier.graphicsLayer { alpha = videoAlpha },
    )
}

@Composable
internal fun ProfileBannerVideoDialog(
    uri: Uri,
    info: BannerVideoInfo,
    maxDurationMs: Long,
    busy: Boolean,
    statusText: String?,
    onCancel: () -> Unit,
    onSave: (startMs: Long, endMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val durationMs = info.durationMs
    val defaultRange = BannerVideoRules.defaultRange(durationMs, maxDurationMs)
    var selection by remember(uri, durationMs, maxDurationMs) { mutableStateOf(defaultRange) }
    var scrubMs by remember(uri) { mutableStateOf<Long?>(null) }
    var playheadMs by remember(uri) { mutableStateOf(selection.startMs) }

    val thumbnails by produceState<List<Bitmap>>(emptyList(), uri, durationMs) {
        value = loadBannerVideoThumbnails(context, uri, durationMs)
    }

    DisposableEffect(thumbnails) {
        val bitmaps = thumbnails
        onDispose { bitmaps.forEach { if (!it.isRecycled) it.recycle() } }
    }

    MusicDialog("编辑视频横幅", onDismiss = { if (!busy) onCancel() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PROFILE_BANNER_ASPECT)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141519)),
            contentAlignment = Alignment.Center,
        ) {
            DefaultProfileBannerBackdrop(Modifier.matchParentSize())
            BannerVideoSurface(
                source = uri,
                httpClient = null,
                active = !busy,
                trimRange = selection,
                scrubPositionMs = scrubMs,
                onPositionUpdate = { playheadMs = it },
                modifier = Modifier
                    .matchParentSize()
                    .testTag("banner_video_preview"),
            )
            if (busy) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = statusText ?: "正在处理视频…",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        val colors = COUITheme.colorScheme
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (durationMs > maxDurationMs) {
                    stringResource(R.string.profile_banner_video_trim_limit, maxDurationMs / 1_000)
                } else {
                    "保存前可预览或裁切片段，按 3:2 静音循环"
                },
                fontSize = 12.sp,
                color = colors.labelSecondaryVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${BannerVideoRules.formatSeconds(selection.startMs)} - ${BannerVideoRules.formatSeconds(selection.endMs)}（${BannerVideoRules.formatSeconds(selection.durationMs)}）",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
            )
        }

        BannerVideoTrimBar(
            durationMs = durationMs,
            maxDurationMs = maxDurationMs,
            selection = selection,
            playheadMs = scrubMs ?: playheadMs,
            thumbnails = thumbnails,
            enabled = !busy,
            onSelectionChange = { selection = it },
            onScrub = { scrubMs = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("banner_video_trim_bar"),
        )

        val isDefault = selection == defaultRange
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicDialogButton(
                text = "重置",
                onClick = {
                    selection = defaultRange
                    scrubMs = null
                    playheadMs = defaultRange.startMs
                },
                enabled = !busy && !isDefault,
                modifier = Modifier
                    .weight(1f)
                    .testTag("banner_video_reset"),
            )
            MusicDialogButton(
                text = if (busy) "处理中…" else "保存横幅",
                onClick = {
                    scrubMs = null
                    onSave(selection.startMs, selection.endMs)
                },
                enabled = !busy && selection.durationMs > 0L,
                modifier = Modifier
                    .weight(1f)
                    .testTag("banner_video_confirm"),
            )
        }
        MusicDialogButton(
            text = "取消",
            onClick = onCancel,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("banner_video_cancel"),
        )
    }
}

private enum class TrimDragTarget { START, END, WINDOW }

@Composable
private fun BannerVideoTrimBar(
    durationMs: Long,
    maxDurationMs: Long,
    selection: BannerTrimSelection,
    playheadMs: Long,
    thumbnails: List<Bitmap>,
    enabled: Boolean,
    onSelectionChange: (BannerTrimSelection) -> Unit,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = COUITheme.colorScheme.primary
    val handleWidthPx = with(LocalDensity.current) { 12.dp.toPx() }
    val touchSlopPx = with(LocalDensity.current) { 20.dp.toPx() }
    val currentSelection by rememberUpdatedState(selection)
    val selectionChanged by rememberUpdatedState(onSelectionChange)
    val scrubCallback by rememberUpdatedState(onScrub)
    val images = remember(thumbnails) { thumbnails.map { it.asImageBitmap() } }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var dragTarget by remember { mutableStateOf<TrimDragTarget?>(null) }

    Canvas(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { contentDescription = "视频横幅时间轴裁切条" }
            .pointerInput(durationMs, maxDurationMs, enabled) {
                if (!enabled || durationMs <= 0L) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val usable = (barWidthPx - handleWidthPx * 2f).coerceAtLeast(1f)
                        val startX = handleWidthPx + usable * (currentSelection.startMs.toFloat() / durationMs)
                        val endX = handleWidthPx + usable * (currentSelection.endMs.toFloat() / durationMs)
                        val x = offset.x
                        val inner = endX - startX
                        val target = when {
                            inner >= touchSlopPx * 1.8f && abs(x - startX) <= touchSlopPx &&
                                abs(x - startX) <= abs(x - endX) -> TrimDragTarget.START
                            inner >= touchSlopPx * 1.8f && abs(x - endX) <= touchSlopPx -> TrimDragTarget.END
                            x in (startX - touchSlopPx)..startX -> TrimDragTarget.START
                            x in endX..(endX + touchSlopPx) -> TrimDragTarget.END
                            x in startX..endX -> TrimDragTarget.WINDOW
                            else -> {
                                val clickFraction = ((x - handleWidthPx) / usable).coerceIn(0f, 1f)
                                val clickMs = (clickFraction * durationMs).roundToLong()
                                val halfLen = currentSelection.durationMs / 2L
                                val jumped = BannerVideoRules.shiftWindow(
                                    durationMs,
                                    currentSelection.startMs,
                                    currentSelection.endMs,
                                    clickMs - halfLen,
                                    maxDurationMs,
                                )
                                selectionChanged(jumped)
                                scrubCallback(jumped.startMs)
                                TrimDragTarget.WINDOW
                            }
                        }
                        dragTarget = target
                        scrubCallback(
                            when (target) {
                                TrimDragTarget.START -> currentSelection.startMs
                                TrimDragTarget.END -> currentSelection.endMs
                                TrimDragTarget.WINDOW -> currentSelection.startMs
                            },
                        )
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val usable = (barWidthPx - handleWidthPx * 2f).coerceAtLeast(1f)
                        val deltaMs = (dragAmount / usable * durationMs).roundToLong()
                        val active = currentSelection
                        val updated = when (dragTarget) {
                            TrimDragTarget.START -> BannerVideoRules.moveStart(
                                durationMs, active.endMs, active.startMs + deltaMs, maxDurationMs,
                            )
                            TrimDragTarget.END -> BannerVideoRules.moveEnd(
                                durationMs, active.startMs, active.endMs + deltaMs, maxDurationMs,
                            )
                            TrimDragTarget.WINDOW -> BannerVideoRules.shiftWindow(
                                durationMs, active.startMs, active.endMs, active.startMs + deltaMs, maxDurationMs,
                            )
                            null -> active
                        }
                        selectionChanged(updated)
                        scrubCallback(
                            when (dragTarget) {
                                TrimDragTarget.END -> updated.endMs
                                TrimDragTarget.START, TrimDragTarget.WINDOW -> updated.startMs
                                null -> null
                            },
                        )
                    },
                    onDragEnd = {
                        dragTarget = null
                        scrubCallback(null)
                    },
                    onDragCancel = {
                        dragTarget = null
                        scrubCallback(null)
                    },
                )
            },
    ) {
        val usable = (size.width - handleWidthPx * 2f).coerceAtLeast(1f)
        drawRect(Color(0xFF1A1C22))

        if (images.isNotEmpty()) {
            val cellWidth = usable / images.size
            images.forEachIndexed { index, bitmap ->
                val x0 = (handleWidthPx + index * cellWidth).roundToInt()
                val x1 = (handleWidthPx + (index + 1) * cellWidth).roundToInt()
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(x0, 0),
                    dstSize = IntSize((x1 - x0).coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                )
            }
        }

        if (durationMs <= 0L) return@Canvas
        val startX = handleWidthPx + usable * (selection.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val endX = handleWidthPx + usable * (selection.endMs.toFloat() / durationMs).coerceIn(0f, 1f)

        val scrim = Color.Black.copy(alpha = 0.58f)
        if (startX > 0f) drawRect(scrim, topLeft = Offset.Zero, size = Size(startX, size.height))
        if (endX < size.width) drawRect(scrim, topLeft = Offset(endX, 0f), size = Size(size.width - endX, size.height))

        val strokePx = 2.dp.toPx()
        drawRect(
            color = accentColor,
            topLeft = Offset(startX, strokePx / 2f),
            size = Size((endX - startX).coerceAtLeast(1f), size.height - strokePx),
            style = Stroke(width = strokePx),
        )

        drawRoundRect(
            color = accentColor,
            topLeft = Offset((startX - handleWidthPx).coerceAtLeast(0f), 0f),
            size = Size(handleWidthPx, size.height),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(endX.coerceAtMost(size.width - handleWidthPx), 0f),
            size = Size(handleWidthPx, size.height),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )

        val gripColor = Color.White.copy(alpha = 0.9f)
        val gripHeight = size.height * 0.4f
        val gripTop = (size.height - gripHeight) / 2f
        val leftHandleCenter = (startX - handleWidthPx / 2f).coerceAtLeast(handleWidthPx / 2f)
        val rightHandleCenter = (endX + handleWidthPx / 2f).coerceAtMost(size.width - handleWidthPx / 2f)
        drawLine(gripColor, Offset(leftHandleCenter, gripTop), Offset(leftHandleCenter, gripTop + gripHeight), strokeWidth = 2.dp.toPx())
        drawLine(gripColor, Offset(rightHandleCenter, gripTop), Offset(rightHandleCenter, gripTop + gripHeight), strokeWidth = 2.dp.toPx())

        val headFraction = (playheadMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val headX = (handleWidthPx + usable * headFraction).coerceIn(startX, endX)
        drawLine(
            color = Color.White,
            start = Offset(headX, strokePx),
            end = Offset(headX, size.height - strokePx),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
