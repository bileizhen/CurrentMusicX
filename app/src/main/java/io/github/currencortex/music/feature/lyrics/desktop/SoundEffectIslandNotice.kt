package io.github.currencortex.music.feature.lyrics.desktop

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.*
import kotlin.math.sin

/** Finite pulse/flow, no perpetual particles, blur or extra window permission. */
@Composable internal fun SoundEffectIslandPill(notice: SoundEffectNotice, modifier: Modifier = Modifier, material: Boolean = true) {
    val motion = rememberDesktopMotion()
    val flow = remember { Animatable(0f) }
    LaunchedEffect(notice.id, notice.phase) {
        flow.snapTo(0f); flow.animateTo(1f, tween(if (motion) 1250 else 0))
    }
    val accent by animateColorAsState(soundEffectNoticeAccent(notice), tween(if (motion) 320 else 0), label = "effect content tint")
    Box(modifier.widthIn(max = 340.dp).fillMaxWidth().then(if (material) Modifier.graphicsLayer {
        val pulse = sin(flow.value * Math.PI).toFloat()
        scaleX = 1f + .015f * pulse; scaleY = scaleX
    }.clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF11141B),
            accent.copy(alpha = .14f).compositeOver(Color(0xFF080B10)), Color(0xFF080B10))))
        .border(.8.dp, accent.copy(alpha = .55f), RoundedCornerShape(28.dp)) else Modifier)
        .semantics { liveRegion = LiveRegionMode.Polite }.testTag("sound_effect_island_notice")) {
        Canvas(Modifier.matchParentSize()) {
            if (motion && flow.value < 1f) {
                val x = (size.width + size.height * 2f) * flow.value - size.height
                drawLine(accent.copy(alpha = .18f * sin(flow.value * Math.PI).toFloat()),
                    Offset(x, size.height), Offset(x + size.height * .7f, 0f), size.height * .38f)
            }
        }
        AnimatedContent(notice, contentKey = { it.id to it.phase }, label = "effect notice content",
            transitionSpec = { (fadeIn(tween(if (motion) 200 else 0, if (motion) 70 else 0)) togetherWith
                fadeOut(tween(if (motion) 120 else 0))).using(SizeTransform(clip = false) { _, _ -> tween(if (motion) 280 else 0) }) }) { current ->
        val failed = current.phase == SoundEffectNoticePhase.FAILED
        val phase = when (current.phase) {
            SoundEffectNoticePhase.APPLYING -> "正在启用"; SoundEffectNoticePhase.READY -> "已启用"
            SoundEffectNoticePhase.WAITING -> "待生效"; SoundEffectNoticePhase.FAILED -> "未启用"
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (current.tier != SoundEffectNoticeTier.STANDARD) {
                SoundEffectMembershipDisc(current.tier)
            } else Box(Modifier.size(32.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(19.dp)) {
                    if (failed) {
                        drawLine(accent, Offset(size.width * .3f, size.height * .3f), Offset(size.width * .7f, size.height * .7f), 2.dp.toPx())
                        drawLine(accent, Offset(size.width * .7f, size.height * .3f), Offset(size.width * .3f, size.height * .7f), 2.dp.toPx())
                    } else repeat(5) { index ->
                        val h = size.height * listOf(.3f, .65f, 1f, .65f, .3f)[index]
                        val x = size.width * (index + .5f) / 5f
                        drawLine(accent, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 2.dp.toPx())
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(current.name, style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(current.message, style = TextStyle(color = Color.White.copy(alpha = .72f), fontSize = 10.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (current.tier != SoundEffectNoticeTier.STANDARD) BasicText(current.tier.name,
                    Modifier.background(accent.copy(alpha = .13f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                BasicText(phase, style = TextStyle(color = accent.copy(alpha = .85f), fontSize = 9.sp))
            }
        }
        }
    }
}

/** Crop only the disc from the same local artwork used by the native membership badges. */
@Composable private fun SoundEffectMembershipDisc(tier: SoundEffectNoticeTier) {
    val assets = LocalContext.current.assets
    val painter = remember(assets, tier) {
        val name = if (tier == SoundEffectNoticeTier.SVIP) "svip_icon_url.png" else "vip_icon_url.png"
        val bitmap = assets.open("netease-effects-ui/assets/$name").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        BitmapPainter(bitmap, srcSize = IntSize(bitmap.height, bitmap.height))
    }
    Image(painter, "${tier.name} 唱片", Modifier.size(32.dp).clip(CircleShape))
}

private fun soundEffectNoticeAccent(notice: SoundEffectNotice?) = when {
    notice == null -> Color.White
    notice.phase == SoundEffectNoticePhase.FAILED -> Color(0xFFFF9B91)
    notice.tier == SoundEffectNoticeTier.SVIP -> Color(0xFFECCB83)
    notice.tier == SoundEffectNoticeTier.VIP -> Color(0xFFFF8393)
    else -> Color(0xFF8DBFFF)
}

/** One continuously measured shell owns both bodies. Retargeting never resets its geometry. */
@Composable internal fun DesktopIslandEffectTransition(notice: SoundEffectNotice?, modifier: Modifier = Modifier,
    normal: @Composable () -> Unit) {
    val motion = rememberDesktopMotion()
    var appeared by remember { mutableStateOf(false) }
    var retained by remember { mutableStateOf(notice) }
    LaunchedEffect(Unit) { appeared = true }
    SideEffect { if (notice != null) retained = notice }
    val expanded = appeared && notice != null
    val tint by animateColorAsState(soundEffectNoticeAccent(notice), tween(if (motion) 400 else 0), label = "island material tint")
    val intensity by animateFloatAsState(if (expanded) 1f else 0f, tween(if (motion) 400 else 0), label = "island material strength")
    val shape = RoundedCornerShape(50)
    AnimatedContent(expanded, modifier.shadow(2.dp, shape, clip = false)
        .background(Brush.verticalGradient(listOf(Color(0xFF11141B),
            tint.copy(alpha = .14f * intensity).compositeOver(Color(0xFF080B10)))), shape)
        .border(.6.dp, Brush.verticalGradient(listOf(tint.copy(alpha = .16f + .39f * intensity),
            tint.copy(alpha = .035f + .20f * intensity))), shape).clip(shape)
        .testTag("desktop_island_transition"), contentAlignment = Alignment.Center,
        label = "island effect morph", transitionSpec = {
            (fadeIn(tween(if (motion) 220 else 0, if (motion) 100 else 0)) togetherWith
                fadeOut(tween(if (motion) 140 else 0))).using(SizeTransform(clip = false) { _, _ ->
                    tween(if (motion) 460 else 0, easing = FastOutSlowInEasing)
                })
        }) { showNotice ->
        if (showNotice) retained?.let { SoundEffectIslandPill(it, material = false) } else normal()
    }
}

/** No existing island: briefly grow from a compact capsule, then shrink before fading away. */
@Composable internal fun SoundEffectIslandFallback(container: AppContainer) {
    val notice by container.soundEffectNotices.state.collectAsStateWithLifecycle()
    val desktop by container.desktopLyricsStatus.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(notice != null && !desktop.visible,
            enter = fadeIn(tween(160)) + scaleIn(tween(260), initialScale = .88f),
            exit = fadeOut(tween(140, 380)) + scaleOut(tween(180, 300), targetScale = .88f)) {
            DesktopIslandEffectTransition(notice.takeUnless { desktop.visible }) {
                Spacer(Modifier.width(112.dp).height(30.dp))
            }
        }
    }
}
