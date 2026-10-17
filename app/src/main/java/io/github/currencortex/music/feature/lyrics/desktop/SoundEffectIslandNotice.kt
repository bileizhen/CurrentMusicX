package io.github.currencortex.music.feature.lyrics.desktop

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import io.github.currencortex.music.feature.lyrics.ui.LyricsFontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.*
import kotlin.math.roundToInt

/** One compact row with a stationary, fading glow; details stay accessible to screen readers. */
@Composable internal fun SoundEffectIslandPill(notice: SoundEffectNotice, modifier: Modifier = Modifier,
    material: Boolean = true, height: Dp = 36.dp, font: Float = DESKTOP_ISLAND_FONT_SIZE) {
    if (notice.kind == IslandNoticeKind.UPDATE_DOWNLOAD) {
        UpdateIslandPill(notice, modifier, material, height, font)
        return
    }
    val motion = rememberDesktopMotion()
    val ready = notice.phase == SoundEffectNoticePhase.READY
    val glow = remember { Animatable(0f) }
    LaunchedEffect(motion, ready) {
        // Preparation never glows; acknowledgement fades in only after success.
        if (ready) glow.animateTo(1f, tween(if (motion) 720 else 0, easing = FastOutSlowInEasing))
        else glow.snapTo(0f)
    }
    val accent by animateColorAsState(soundEffectNoticeAccent(notice), tween(if (motion) 320 else 0), label = "effect content tint")
    val phase = soundEffectPhaseLabel(notice.phase)
    Box(modifier.animateContentSize(tween(if (motion) 320 else 0, easing = FastOutSlowInEasing))
        .widthIn(max = 340.dp).height(height).then(if (material) Modifier.clip(RoundedCornerShape(50))
        .background(Brush.verticalGradient(listOf(Color(0xFF11141B), Color(0xFF080B10))))
        .border(.8.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(50)) else Modifier)
        .semantics { liveRegion = LiveRegionMode.Polite; stateDescription = if (phase.isEmpty()) notice.message else "$phase：${notice.message}" }
        .testTag("sound_effect_island_notice")) {
        // Cache the stationary gradient, fading its layer instead of allocating
        // a new shader and repainting it on every animation frame.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = if (ready) glow.value else 0f }.drawWithCache {
            val brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .22f),
                    accent.copy(alpha = .08f), Color.Transparent),
                center = Offset(size.width * .5f, size.height * .75f),
                radius = size.width.coerceAtLeast(size.height) * .65f)
            onDrawBehind { drawRect(brush) }
        })
        // A phase change only updates the status label. Crossfading two complete
        // rows made the unchanged disc/title visibly dim halfway through a fade.
        val current = notice
        Row(Modifier.fillMaxHeight().padding(start = 7.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val discSize = (height - 14.dp).coerceAtLeast(22.dp)
            if (current.tier != SoundEffectNoticeTier.STANDARD) {
                SoundEffectMembershipDisc(current.tier, Modifier.size(discSize))
            } else Box(Modifier.size(discSize).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) {
                    repeat(5) { index ->
                        val h = size.height * listOf(.3f, .65f, 1f, .65f, .3f)[index]
                        val x = size.width * (index + .5f) / 5f
                        drawLine(accent, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 2.dp.toPx())
                    }
                }
            }
            SoundEffectIslandTitle(current.name, accent, font, Modifier.weight(1f, fill = false))
            // Empty status has no node, width or inter-item gap. A visible error
            // uses only its actual text width; the pill animates that size change.
            if (phase.isNotEmpty()) BasicText(phase,
                style = TextStyle(color = accent.copy(alpha = .85f), fontSize = 9.sp,
                    textAlign = TextAlign.End), maxLines = 1, softWrap = false)
        }
    }
}

private fun soundEffectPhaseLabel(phase: SoundEffectNoticePhase) = when (phase) {
    SoundEffectNoticePhase.APPLYING, SoundEffectNoticePhase.READY -> ""
    SoundEffectNoticePhase.WAITING -> "待生效"; SoundEffectNoticePhase.FAILED -> "未启用"
}

/** The two master wordmarks are the exact local artwork used by their main cards. */
@Composable private fun SoundEffectIslandTitle(name: String, accent: Color, font: Float, modifier: Modifier) {
    val asset = when (name) {
        "AI 调音大师" -> "ai_model_title_icon_url.png"
        "AI 情绪调音师" -> "emotion_title_icon_dark_url.png"
        else -> null
    }
    if (asset == null) BasicText(name, modifier.testTag("sound_effect_island_title"),
        style = TextStyle(fontFamily = LyricsFontFamily, color = accent, fontSize = font.sp,
            lineHeight = (font * 1.2f).sp, fontWeight = FontWeight.Medium),
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    else {
        val assets = LocalContext.current.assets
        val bitmap = remember(assets, asset) {
            assets.open("netease-effects-ui/assets/$asset").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        }
        val titleHeight = with(LocalDensity.current) { (font * 1.2f).sp.toDp() }
        Image(BitmapPainter(bitmap), null, modifier.height(titleHeight)
            .width(titleHeight * bitmap.width / bitmap.height)
            .testTag("sound_effect_island_wordmark").semantics { text = AnnotatedString(name) })
    }
}

/** Crop only the disc from the same local artwork used by the native membership badges. */
@Composable private fun SoundEffectMembershipDisc(tier: SoundEffectNoticeTier, modifier: Modifier) {
    val assets = LocalContext.current.assets
    val painter = remember(assets, tier) {
        val name = if (tier == SoundEffectNoticeTier.SVIP) "svip_icon_url.png" else "vip_icon_url.png"
        val bitmap = assets.open("netease-effects-ui/assets/$name").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        BitmapPainter(bitmap, srcSize = IntSize(bitmap.height, bitmap.height))
    }
    Image(painter, "${tier.name} 唱片", modifier.clip(CircleShape))
}

private fun soundEffectNoticeAccent(notice: SoundEffectNotice?) = when {
    notice == null -> Color.White
    notice.phase == SoundEffectNoticePhase.FAILED -> Color(0xFFFF9B91)
    notice.tier == SoundEffectNoticeTier.SVIP -> Color(0xFFECCB83)
    notice.tier == SoundEffectNoticeTier.VIP -> Color(0xFFFF8393)
    else -> Color(0xFF8DBFFF)
}

/** Keep the normal body alive; measure both at their final width, then morph only the shell.
 * No delayed incoming fade or AnimatedContent size correction can leave an empty capsule.
 */
@Composable internal fun DesktopIslandEffectTransition(notice: SoundEffectNotice?, modifier: Modifier = Modifier,
    effectHeight: Dp = 36.dp, font: Float = DESKTOP_ISLAND_FONT_SIZE, normal: @Composable () -> Unit) {
    val motion = rememberDesktopMotion()
    var retained by remember { mutableStateOf(notice) }
    SideEffect { if (notice != null) retained = notice }
    val expanded = notice != null
    val progress = animateFloatAsState(if (expanded) 1f else 0f,
        tween(if (motion) 420 else 0, easing = FastOutSlowInEasing), label = "island effect morph")
    val tint = animateColorAsState(soundEffectNoticeAccent(notice ?: retained), tween(if (motion) 400 else 0), label = "island material tint")
    val ready = notice?.phase == SoundEffectNoticePhase.READY
    val retainedReady = (notice ?: retained)?.phase == SoundEffectNoticePhase.READY
    val intensity = animateFloatAsState(if (ready) 1f else 0f, tween(if (motion) 400 else 0), label = "island material strength")
    val shape = RoundedCornerShape(50)
    Layout(modifier = modifier.shadow(2.dp, shape, clip = false)
        .drawWithCache {
            val base = Brush.verticalGradient(listOf(Color(0xFF11141B), Color(0xFF080B10)))
            val neutralBorder = Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .035f)))
            val color = tint.value
            val fill = Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = .14f)))
            val border = Brush.verticalGradient(listOf(color.copy(alpha = .45f), color.copy(alpha = .24f)))
            val radius = CornerRadius(size.height / 2f)
            val stroke = Stroke(.6.dp.toPx())
            onDrawBehind {
                val amount = if (retainedReady) intensity.value else 0f
                drawRoundRect(base, cornerRadius = radius)
                drawRoundRect(fill, cornerRadius = radius, alpha = amount)
                drawRoundRect(neutralBorder, cornerRadius = radius, style = stroke)
                drawRoundRect(border, cornerRadius = radius, style = stroke, alpha = amount)
            }
        }.clip(shape)
        .testTag("desktop_island_transition"), content = {
        Box { normal() }
        Box { (notice ?: retained)?.let { SoundEffectIslandPill(it, material = false, height = effectHeight, font = font) } }
    }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val compact = children[0].measure(loose)
        val effect = children[1].measure(loose)
        val p = progress.value
        val width = constraints.constrainWidth((compact.width + (effect.width - compact.width) * p).roundToInt())
        val height = constraints.constrainHeight((compact.height + (effect.height - compact.height) * p).roundToInt())
        layout(width, height) {
            // Complementary fades share the geometry clock, including interrupted reversals.
            if (p < 1f) compact.placeWithLayer((width - compact.width) / 2, (height - compact.height) / 2) { alpha = 1f - p }
            if (p > 0f) effect.placeWithLayer((width - effect.width) / 2, (height - effect.height) / 2) { alpha = p }
        }
    }
}

/** No existing island: briefly grow from a compact capsule, then shrink before fading away. */
@Composable internal fun SoundEffectIslandFallback(container: AppContainer) {
    val notice by container.islandNotices.collectAsStateWithLifecycle()
    val desktop by container.desktopLyricsStatus.collectAsStateWithLifecycle()
    val options by container.desktopLyrics.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(notice != null && !desktop.visible,
            enter = fadeIn(tween(160)) + scaleIn(tween(260), initialScale = .88f),
            exit = fadeOut(tween(140, 380)) + scaleOut(tween(180, 300), targetScale = .88f)) {
            DesktopIslandEffectTransition(notice.takeUnless { desktop.visible },
                effectHeight = rememberIslandFpsHeight(options.displayFontSize), font = options.displayFontSize) {
                Spacer(Modifier.width(112.dp).height(rememberIslandFpsHeight(options.displayFontSize)))
            }
        }
    }
}
