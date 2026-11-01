// SPDX-License-Identifier: GPL-3.0-only
// Native port of CommonScaffold's _BarSearch, _SearchOrigin, _SearchLead and
// _SearchSwell (group return), upstream scaffold.dart blob
// 9ae9c661fe9597dd5400aee8a0c8971416635321. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*

internal data class GlassToolbarAction(val icon: ImageVector, val label: String,
    val onClick: () -> Unit, val tag: String)
internal val BarSearchGeometry = SemanticsPropertyKey<Rect>("BarSearchGeometry")
internal data class BarSearchPanes(val field: Rect, val left: Rect, val right: Rect)
internal val BarSearchPanesKey = SemanticsPropertyKey<BarSearchPanes>("BarSearchPanes")

/** Phone searchInBar=true: the button group opens across the bar, with its action
 * on the left and cancel on the right. The body never receives an inserted field. */
@Composable internal fun GlassSearchToolbar(title: String, titleOpacity: Float, onBack: () -> Unit,
    active: Boolean, onActiveChange: (Boolean) -> Unit, value: String, onValueChange: (String) -> Unit,
    action: GlassToolbarAction, trailing: GlassToolbarAction, modifier: Modifier = Modifier,
    inputTag: String = "bar_query", searchTag: String = "bar_search_toggle", placeholder: String = "搜索") {
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val motion = remember { BarSearchMotion(active) }
    val frame = motion.frame
    val fieldPress = rememberElasticPressState()
    val cancelPress = rememberElasticPressState()
    val actionPress = rememberElasticPressState()
    val groupPress = rememberElasticPressState()
    var origin by remember { mutableStateOf(Rect.Zero) }
    var withoutSearch by remember { mutableStateOf(Rect.Zero) }
    var leading by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var measuredGroup by remember { mutableStateOf(Rect.Zero) }
    var measuredBack by remember { mutableStateOf(Rect.Zero) }
    val cancel by rememberUpdatedState<() -> Unit>({
        focus.clearFocus(); keyboard?.hide(); onValueChange(""); onActiveChange(false)
    })
    BackHandler(active) { cancel() }
    LaunchedEffect(active) { motion.animate(active) }
    LaunchedEffect(active) {
        if (active) { withFrameNanos { }; requester.requestFocus(); keyboard?.show() }
    }
    fun open() {
        origin = measuredGroup
        val h = with(density) { 44.dp.toPx() }
        withoutSearch = if (rtl) Rect(origin.left, origin.top, origin.left + h, origin.bottom)
            else Rect(origin.right - h, origin.top, origin.right, origin.bottom)
        leading = measuredBack
        onActiveChange(true)
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(SettingsToolbarHeight)
        .onGloballyPositioned { rootOrigin = it.positionInWindow() }) {
        val width = constraints.maxWidth.toFloat()
        val height = with(density) { 44.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val inset = with(density) { 16.dp.toPx() }
        val top = (constraints.maxHeight - height) / 2f
        fun place(start: Float, end: Float) = Rect(if (rtl) width - end else start, top,
            if (rtl) width - start else end, top + height)
        val slot = place(inset + height + gap, width - inset - height - gap)
        val actionRest = place(inset, inset + height)
        val cancelRest = place(width - inset - height, width - inset)
        // A restored open search has not measured its closed buttons yet.
        val source = if (origin.isEmpty) place(width - inset - height * 3 - gap, width - inset - height - gap) else origin
        val home = if (!withoutSearch.isEmpty) withoutSearch else if (rtl)
            Rect(source.left, source.top, source.left + height, source.bottom)
            else Rect(source.right - height, source.top, source.right, source.bottom)
        val back = leading.takeUnless { it.isEmpty } ?: actionRest
        fun lifted(rect: Rect, lift: Float): Rect {
            val scale = 1 + lift * min(.25f, with(density) { 16.dp.toPx() } / rect.size.maxDimension.coerceAtLeast(1f))
            return Rect(rect.center - Offset(rect.width * scale, rect.height * scale) / 2f, rect.size * scale)
        }
        val grown = max(0f, frame.growth)
        val group = glassLerp(source, home, frame.collapse)
        val from = glassLerp(group, back, frame.towardLeading)
        val field = lifted(glassLerp(from, slot, frame.growth), frame.lift)
        fun flank(rest: Rect, leads: Boolean, home: Rect? = null): Rect {
            val facesRight = leads != rtl
            val reach = glassLerp(height / 2, -gap, grown)
            val anchor = if (facesRight) field.left + reach else field.right - reach
            val size = rest.size * grown
            var rect = Rect(Offset(if (facesRight) anchor - size.width else anchor, rest.center.y - size.height / 2), size)
            if (home != null) rect = glassLerp(rect, glassLerp(home, rest, grown), frame.towardLeading)
            return lifted(rect, frame.lift)
        }
        val left = flank(actionRest, true)
        val right = flank(cancelRest, false, home)
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)
            .then(if (active) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(false, PointerEventPass.Initial).consume()
                    do { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.forEach { it.consume() } }
                    while (e.changes.any { it.pressed })
                }
            } else Modifier), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassIconButton(onBack, "返回", Modifier.testTag("navigate_back")
                .onGloballyPositioned { measuredBack = it.boundsInWindow().translate(-rootOrigin) }
                .graphicsLayer { alpha = frame.leading }, enabled = !active) { Icon(MusicIcons.Back, null) }
            Text(title, Modifier.weight(1f).barSearchFade(frame.title * titleOpacity),
                fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
            ElasticPress(Modifier.height(44.dp).width(44.dp * (2 - frame.collapse))
                .onGloballyPositioned { if (!active && frame.growth == 0f) measuredGroup = it.boundsInWindow().translate(-rootOrigin) }
                .graphicsLayer {
                    alpha = frame.actions
                    val scale = 1 + (frame.lift + frame.swell) * min(.25f, 16.dp.toPx() / size.width.coerceAtLeast(1f))
                    scaleX = scale; scaleY = scale
                }, state = groupPress, enabled = !active) {
                Row(Modifier.fillMaxSize().glassSurface(LocalGlassBackdrop.current, CircleShape, glow = { groupPress.amount }),
                    verticalAlignment = Alignment.CenterVertically) {
                BareBarButton(MusicIcons.Search, "搜索", ::open, Modifier.width(44.dp * (1 - frame.collapse).coerceAtLeast(0f))
                    .fillMaxHeight().barSearchFade(frame.glyph).testTag(searchTag), !active)
                BareBarButton(action.icon, action.label, action.onClick, Modifier.size(44.dp).testTag(action.tag), !active)
                }
            }
            GlassIconButton(trailing.onClick, trailing.label,
                Modifier.testTag(trailing.tag).barSearchFade(frame.actions), enabled = !active) { Icon(trailing.icon, null) }
        }
        if (active || frame.surface > 0f) {
            val growth = with(density) { 16.dp.toPx() }
            fun deformed(rect: Rect, press: ElasticPressState): Rect {
                val p = press.frame(growth)
                return Rect(rect.center + p.pull - Offset(rect.width * p.scaleX, rect.height * p.scaleY) / 2f,
                    Size(rect.width * p.scaleX, rect.height * p.scaleY))
            }
            val pane = deformed(field, fieldPress)
            val drop = deformed(right, cancelPress)
            val companion = deformed(left, actionPress)
            SearchToolbarSurface(pane, companion, drop, fieldPress.amount, actionPress.amount, cancelPress.amount, frame.surface,
                Modifier.matchParentSize().testTag("${inputTag}_surface").semantics {
                    this[BarSearchGeometry] = pane
                    this[BarSearchPanesKey] = BarSearchPanes(pane, companion, drop)
                })
            Box(Modifier.barSearchRect(field, density)) {
                ElasticPress(Modifier.fillMaxSize(), state = fieldPress, enabled = active) {
                    Box(Modifier.fillMaxSize().clip(CircleShape)) {
                        GlassSearchField(value, onValueChange,
                            Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true).width(with(density) { slot.width.toDp() })
                                .height(44.dp).barSearchFade(frame.content)
                                .then(if (!active) Modifier.clearAndSetSemantics {} else Modifier),
                            requester, inputTag, placeholder, onSubmit = { focus.clearFocus(); keyboard?.hide() }, enabled = active)
                    }
                }
            }
            Box(Modifier.barSearchFlank(left, height).barSearchFade(frame.flank)
                .then(if (!active) Modifier.clearAndSetSemantics {} else Modifier)) {
                ElasticPress(Modifier.fillMaxSize(), state = actionPress, enabled = active) {
                    BareBarButton(action.icon, action.label, action.onClick, Modifier.fillMaxSize().testTag(action.tag), active)
                }
            }
            Box(Modifier.barSearchFlank(right, height).barSearchFade(frame.flank)
                .then(if (!active) Modifier.clearAndSetSemantics {} else Modifier)) {
                ElasticPress(Modifier.fillMaxSize().testTag("${inputTag}_cancel")
                    .clickable(enabled = active, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) { cancel() }
                    .semantics { contentDescription = "取消搜索" }, enabled = active, state = cancelPress) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(MusicIcons.Close, null) }
                }
            }
        }
    }
}

@Composable private fun BareBarButton(icon: ImageVector, label: String, click: () -> Unit,
    modifier: Modifier, enabled: Boolean) {
    Box(modifier.clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() },
        indication = null, onClick = click).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp))
    }
}

private fun Modifier.barSearchFade(opacity: Float): Modifier = graphicsLayer {
    alpha = opacity.coerceIn(0f, 1f)
}.blur((6 * (1 - opacity.coerceIn(0f, 1f))).dp, BlurredEdgeTreatment.Unbounded)

private fun Modifier.barSearchRect(rect: Rect, density: Density): Modifier = absoluteOffset {
    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
}.size(with(density) { rect.width.coerceAtLeast(1f).toDp() }, with(density) { rect.height.coerceAtLeast(1f).toDp() })

private fun Modifier.barSearchFlank(rect: Rect, height: Float): Modifier = size(44.dp).graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = rect.left; translationY = rect.top
    scaleX = rect.width / height; scaleY = rect.height / height
}

/** The capture follows the deformed pills, rather than clipping at the toolbar's box. */
@Composable private fun SearchToolbarSurface(pane: Rect, left: Rect, drop: Rect,
    fieldPress: Float, leftPress: Float, rightPress: Float, opacity: Float, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current && android.os.Build.VERSION.SDK_INT >= 33
    val dark = colors.onSurface.luminance() > .5f
    val press = maxOf(fieldPress, leftPress, rightPress)
    val union = Rect(minOf(pane.left, left.left, drop.left), minOf(pane.top, left.top, drop.top),
        maxOf(pane.right, left.right, drop.right), maxOf(pane.bottom, left.bottom, drop.bottom))
    val start = Offset(floor(union.left), floor(union.top))
    val a = pane.translate(-start); val b = left.translate(-start); val c = drop.translate(-start)
    val density = LocalDensity.current
    fun blend(neighbor: Rect, amount: Float): Float = with(density) {
        if (abs(pane.center.y - neighbor.center.y) < min(pane.height, neighbor.height) / 2)
            (8 + 8 * max(fieldPress, amount)).dp.toPx() else 0f
    }
    val leftBlend = blend(left, leftPress); val rightBlend = blend(drop, rightPress)
    val shape = remember(a, b, c, leftBlend, rightBlend) { GlassGooRowShape(a, b, c, leftBlend, rightBlend) }
    val surface = if (glass) Modifier.glassSurface(LocalGlassBackdrop.current, shape, clipToShape = false, glow = { press })
        else Modifier.dropShadow(shape, Shadow(24.dp, Color.Black.copy(alpha = if (dark) .24f else .08f), offset = DpOffset(0.dp, 8.dp)))
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), if (dark) colors.surfaceContainerHigh else colors.surfaceContainer) }
    Box(modifier.layout { measurable, constraints ->
        val child = measurable.measure(Constraints.fixed((ceil(union.right) - start.x).toInt().coerceAtLeast(1),
            (ceil(union.bottom) - start.y).toInt().coerceAtLeast(1)))
        layout(constraints.maxWidth, constraints.maxHeight) { child.place(start.x.toInt(), start.y.toInt()) }
    }.graphicsLayer { alpha = opacity }.then(surface))
}
