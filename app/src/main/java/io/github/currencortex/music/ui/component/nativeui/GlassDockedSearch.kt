// SPDX-License-Identifier: GPL-3.0-only
// Native port of upstream SearchField, CommonScaffold._buildDockedSearch, SearchQuery,
// BottomInsetScope and GooSurface. See THIRD_PARTY_NOTICES.md for upstream blob IDs.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.ceil

internal const val DockedSearchHeight = 48
internal const val DockedSearchMargin = 16
internal const val DockedSearchInset = DockedSearchHeight + DockedSearchMargin
internal val DockedSearchFrame = SemanticsPropertyKey<GlassPopupFrame>("DockedSearchFrame")
internal val DockedSearchExpansion = SemanticsPropertyKey<Float>("DockedSearchExpansion")

/** common/search.dart: every whitespace-separated term must occur, regardless of order/case. */
internal class MusicSearchQuery(text: String) {
    private val terms = text.lowercase(Locale.ROOT).split(Regex("[\\p{Z}\\s]+"))
    fun matches(vararg fields: String): Boolean {
        val haystack = fields.joinToString("\n").lowercase(Locale.ROOT)
        return terms.filter(String::isNotEmpty).all(haystack::contains)
    }
}


private val dockedSearchCurve = Easing { t ->
    if (t == 0f || t == 1f) t else glassSpring(t * .45f, 390f, .75f)
}

/** The docked field stays visible; its cancel button grows out when focused or non-empty. */
@Composable internal fun GlassDockedSearch(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, inputTag: String = "settings_search",
    placeholder: String = "搜索", focusRequester: FocusRequester? = null,
    onInputFocus: (Boolean) -> Unit = {}, onSubmit: (() -> Unit)? = null,
    onActivate: (() -> Unit)? = null, submitTag: String? = null, clearTag: String = "${inputTag}_clear",
    onCancel: () -> Unit = {}) {
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ownedRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownedRequester
    var focused by remember { mutableStateOf(false) }
    val active = onActivate == null && (focused || value.isNotEmpty())
    val motion = animateFloatAsState(if (active) 1f else 0f,
        tween(450, easing = dockedSearchCurve), label = "Native UI docked search")
    val expansion = motion.value
    val fieldPress = rememberElasticPressState()
    val cancelPress = rememberElasticPressState()
    val growth = with(density) { 16.dp.toPx() }
    val glass = LocalLiquidGlassEnabled.current && android.os.Build.VERSION.SDK_INT >= 33
    val dark = colors.onSurface.luminance() > .5f
    val backdrop = LocalGlassBackdrop.current
    val currentActive by rememberUpdatedState(active)
    val change by rememberUpdatedState(onValueChange)
    val cancelSearch by rememberUpdatedState<() -> Unit>({
        focus.clearFocus(); keyboard?.hide(); change(""); onCancel()
    })
    BoxWithConstraints(modifier.fillMaxWidth().height(DockedSearchHeight.dp)
        .then(if (onActivate != null) Modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            onClick("搜索") { onActivate(); true }
        } else Modifier)
        .testTag("${inputTag}_dock").semantics { this[DockedSearchExpansion] = expansion }
        // Flutter's TextFieldTapRegion keeps this sibling button out of the editor's
        // tap handling. Route its resting hit region before Android's text gestures.
        .pointerInput(rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(false, PointerEventPass.Initial)
                fun inCancel(point: Offset) = point.y in 0f..size.height.toFloat() &&
                    if (rtl) point.x in 0f..size.height.toFloat()
                    else point.x in (size.width - size.height).toFloat()..size.width.toFloat()
                if (currentActive && inCancel(down.position)) {
                    down.consume(); cancelPress.press(down.position)
                    var clicked = false
                    var moved = false
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (pointer.pressed) cancelPress.drag(pointer.position)
                            event.changes.forEach { it.consume() }
                            if (!pointer.pressed) { clicked = !moved && inCancel(pointer.position); break }
                        } while (true)
                    } finally { cancelPress.release() }
                    if (clicked) cancelSearch()
                }
            }
        }) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { DockedSearchHeight.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val frame = remember(width, height, gap, rtl, fieldPress, cancelPress, motion) {
            {
                val t = motion.value
                val grown = max(t, 0f)
                fun painted(rect: Rect, press: ElasticPressState): Rect {
                    val frame = press.frame(growth)
                    return Rect(rect.center + frame.pull - Offset(rect.width * frame.scaleX, rect.height * frame.scaleY) / 2f,
                        Size(rect.width * frame.scaleX, rect.height * frame.scaleY))
                }
                val end = (width - (height + gap) * grown).coerceAtLeast(1f)
                val field = painted(if (rtl) Rect(width - end, 0f, width, height) else Rect(0f, 0f, end, height), fieldPress)
                val start = if (rtl) height else width - height
                val shift = (if (rtl) -1 else 1) * height / 2 * (1 - t)
                val cancel = if (grown <= .001f) Rect.Zero else painted(
                    if (rtl) Rect(start + shift - height * grown, height * (1 - grown) / 2,
                        start + shift, height * (1 + grown) / 2)
                    else Rect(start + shift, height * (1 - grown) / 2,
                        start + shift + height * grown, height * (1 + grown) / 2), cancelPress)
                GlassPopupFrame(field, field.height / 2, cancel, 1f, 1f, 1f,
                    blend = with(density) { (8 + 8 * max(fieldPress.amount, cancelPress.amount)).dp.toPx() })
            }
        }
        val currentFrame = frame()
        // goo.dart: pills.reduce((a, b) => a.expandToInclude(b)). The render target
        // follows the deformed panes, independently of their resting layout/hit box.
        val pane = currentFrame.bounds
        val drop = currentFrame.droplet
        val paintLeft = floor(if (drop.isEmpty) pane.left else min(pane.left, drop.left)).toInt()
        val paintTop = floor(if (drop.isEmpty) pane.top else min(pane.top, drop.top)).toInt()
        val paintRight = ceil(if (drop.isEmpty) pane.right else max(pane.right, drop.right)).toInt()
        val paintBottom = ceil(if (drop.isEmpty) pane.bottom else max(pane.bottom, drop.bottom)).toInt()
        val paintOffset = Offset(-paintLeft.toFloat(), -paintTop.toFloat())
        val paintFrame = currentFrame.copy(bounds = currentFrame.bounds.translate(paintOffset),
            droplet = if (currentFrame.droplet.isEmpty) Rect.Zero else currentFrame.droplet.translate(paintOffset))
        // Re-key the native backdrop's cached outline/effect when either pane moves.
        val shape = remember(paintFrame) { glassGooShape { paintFrame } }
        val surface = if (glass) Modifier.glassSurface(backdrop, shape, clipToShape = false,
            glow = { max(fieldPress.amount, cancelPress.amount) })
        else Modifier.dropShadow(shape, Shadow(24.dp, Color.Black.copy(alpha = if (dark) .24f else .08f), offset = DpOffset(0.dp, 8.dp)))
            .dropShadow(shape, Shadow(3.dp, Color.Black.copy(alpha = if (dark) .12f else .04f), offset = DpOffset(0.dp, 1.dp)))
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), if (dark) colors.surfaceContainerHigh else colors.surfaceContainer) }
        Box(Modifier.matchParentSize().layout { measurable, constraints ->
            val child = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(
                (paintRight - paintLeft).coerceAtLeast(1), (paintBottom - paintTop).coerceAtLeast(1)))
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(paintLeft, paintTop) }
        }.then(surface).testTag("${inputTag}_surface").semantics { this[DockedSearchFrame] = frame() })
        val fieldWidth = (maxWidth - (DockedSearchHeight + 12).dp * expansion.coerceAtLeast(0f)).coerceAtLeast(1.dp)
        ElasticPress(Modifier.align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart).width(fieldWidth).fillMaxHeight(), state = fieldPress) {
            GlassSearchField(value, onValueChange, Modifier.fillMaxSize(), requester, inputTag,
                placeholder, onInputFocus = { focused = it; onInputFocus(it) }, onSubmit = onSubmit,
                submitTag = submitTag, clearTag = clearTag, onActivate = onActivate)
        }
        Box(Modifier.align(if (rtl) Alignment.CenterStart else Alignment.CenterEnd).size(DockedSearchHeight.dp).zIndex(2f)
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, .5f)
                translationX = (if (rtl) -1 else 1) * height / 2 * (1 - expansion)
                scaleX = max(expansion, 0f); scaleY = scaleX
                alpha = ((expansion - .5f) / .4f).coerceIn(0f, 1f)
            }) {
            ElasticPress(Modifier.fillMaxSize().then(if (active) Modifier.testTag("${inputTag}_cancel")
                    .clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        cancelSearch()
                    }.semantics { contentDescription = "取消搜索" } else Modifier.clearAndSetSemantics {}),
                state = cancelPress, enabled = active) {
                Box(Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center) { Icon(MusicIcons.Close, null, Modifier.size(24.dp), tint = colors.onSurface) }
            }
        }
    }
}
