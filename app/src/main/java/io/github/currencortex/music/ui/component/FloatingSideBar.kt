package io.github.currencortex.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsPropertyKey
import io.github.currencortex.music.ui.component.miuix.animation.DampedDragAnimation
import io.github.currencortex.music.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** The page and mini player reserve this strip; the actual floating panel is narrower. */
internal val SideNavigationSpace = 92.dp
internal data class SideWaterDropFrame(val index: Float, val press: Float, val scaleX: Float, val scaleY: Float)
internal val SideWaterDropVisual = SemanticsPropertyKey<SideWaterDropFrame>("SideWaterDropVisual")
internal val LocalMusicSideWaterDrop = staticCompositionLocalOf<(@Composable
    (Modifier, DampedDragAnimation, @Composable (Modifier) -> Unit) -> Unit)?> { null }

/** A bottom-aligned vertical tab capsule using the same material as the music dock. */
@Composable internal fun FloatingSideBar(selected: Int, labels: List<String>, icons: List<ImageVector>,
    onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val surface = LocalMusicDockSurface.current
    val dark = isInDarkTheme()
    val shape = remember { RoundedCornerShape(32.dp) }
    val selectionShape = remember { RoundedCornerShape(26.dp) }
    val primary = MiuixTheme.colorScheme.primary
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val waterDrop = LocalMusicSideWaterDrop.current
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier.width(64.dp).testTag("wide_navigation")) {
        val gap = 4.dp
        val padding = 6.dp
        val itemHeight = ((maxHeight - padding * 2 - gap * (labels.size - 1)) / labels.size).coerceIn(48.dp, 56.dp)
        val stride = itemHeight + gap
        val contentHeight = itemHeight * labels.size + gap * (labels.size - 1) + padding * 2
        val panelHeight = contentHeight.coerceAtMost(maxHeight)
        val stridePx = with(density) { stride.toPx() }
        var dragging by remember { mutableStateOf(false) }
        val motion = if (waterDrop != null) remember(scope, stridePx, labels.size) {
            DampedDragAnimation(scope, selected.toFloat(), 0f..labels.lastIndex.toFloat(), .001f, 1f, 78f / 56f,
                onDragStarted = { dragging = true },
                onDragStopped = {
                    val target = targetValue.roundToInt().coerceIn(labels.indices)
                    dragging = false
                    animateToValue(target.toFloat())
                    select(target)
                },
                onDrag = { _, amount -> updateValue(targetValue + amount.y / stridePx) })
        } else null
        LaunchedEffect(selected, motion) {
            if (motion != null && !dragging && motion.targetValue != selected.toFloat()) motion.animateToValue(selected.toFloat())
        }
        val selectedOffset = animateDpAsState(stride * selected.coerceIn(labels.indices),
            tween(260, easing = CubicBezierEasing(.2f, 0f, .2f, 1f)), label = "side tab indicator")
        // In short split windows keep touch targets intact and reveal the selected item.
        LaunchedEffect(selected, itemHeight, panelHeight) {
            val top = with(density) { (padding + stride * selected).toPx() }
            val bottom = top + with(density) { itemHeight.toPx() }
            val viewport = with(density) { panelHeight.toPx() }
            val offset = scroll.value.toFloat()
            val target = when {
                top < offset -> top
                bottom > offset + viewport -> bottom - viewport
                else -> offset
            }
            scroll.animateScrollTo(target.toInt().coerceAtLeast(0))
        }
        Box(Modifier.fillMaxWidth().height(panelHeight)) {
        surface(Modifier.fillMaxSize()
            .dropShadow(shape, Shadow(radius = 8.dp, color = Color.Black, alpha = if (dark) .18f else .07f)), { shape }) {
            Box(Modifier.fillMaxSize().clip(shape)) {
                Box(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                    if (waterDrop == null) Box(Modifier.padding(padding).width(52.dp).height(itemHeight)
                        .graphicsLayer { translationY = selectedOffset.value.toPx() }
                        .testTag("side_tab_indicator").clip(selectionShape)
                        .background(primary.copy(alpha = if (dark) .20f else .12f)))
                    SideTabLabels(selected, labels, icons, onSelect, itemHeight)
                }
            }
        }
        if (waterDrop != null && motion != null) waterDrop(
            Modifier.padding(padding).width(52.dp).height(itemHeight)
                .graphicsLayer { translationY = motion.value * stridePx - scroll.value }, motion) { mirrorModifier ->
            Box(mirrorModifier.fillMaxSize().clip(shape)) {
                SideTabLabels(selected, labels, icons, {}, itemHeight, mirror = true,
                    modifier = Modifier.graphicsLayer { translationY = -scroll.value.toFloat() },
                    mirrorScale = { 1f + .2f * motion.pressProgress })
            }
        }
        }
    }
}

@Composable private fun SideTabLabels(selected: Int, labels: List<String>, icons: List<ImageVector>,
    onSelect: (Int) -> Unit, itemHeight: Dp, mirror: Boolean = false, modifier: Modifier = Modifier,
    mirrorScale: () -> Float = { 1f }) {
    val shape = remember { RoundedCornerShape(26.dp) }
    Column(modifier.fillMaxWidth().padding(6.dp).then(if (mirror) Modifier else Modifier.selectableGroup()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val interactions = remember { MutableInteractionSource() }
            val pressed by interactions.collectIsPressedAsState()
            val scale = animateFloatAsState(if (pressed) .94f else 1f,
                tween(if (pressed) 90 else 150), label = "side tab press")
            val tint = animateColorAsState(if (selected == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                tween(180), label = "side tab tint")
            Column(Modifier.fillMaxWidth().height(itemHeight)
                .then(if (mirror) Modifier else Modifier.testTag("tab_$index").clip(shape)
                    .selectable(selected == index, role = Role.Tab, interactionSource = interactions,
                        indication = null, onClick = { onSelect(index) })
                    .semantics { contentDescription = label })
                .graphicsLayer { scaleX = scale.value; scaleY = scaleX },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                Column(Modifier.graphicsLayer { if (mirror) { scaleX = mirrorScale(); scaleY = scaleX } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(icons[index], null, Modifier.size(23.dp), tint = tint.value)
                    Text(label, Modifier.clearAndSetSemantics {}, color = tint.value,
                        fontSize = 10.sp, lineHeight = 12.sp,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
