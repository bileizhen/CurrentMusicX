package io.github.currencortex.music.feature.update

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import io.github.currencortex.music.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val updateDialogShape = RoundedCornerShape(32.dp)

/** Update-only neutral material. Artwork must not tint the controls purple. */
@Composable internal fun UpdateOptionsDialog(title: String, summary: String? = null,
    onDismiss: () -> Unit, backdrop: Backdrop? = null, onDismissFinished: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null, choiceLayout: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val dark = isInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF6BA5FF), background = Color(0xFF292A2D),
        surface = Color(0xFF37383C), onBackground = Color(0xFFF5F5F7))
    else lightColorScheme(primary = Color(0xFF3485FF), background = Color(0xFFF2F3F5),
        surface = Color.White, onBackground = Color(0xFF17181B))
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        MusicOverlayDialog(show = true, onDismissRequest = onDismiss, onDismissFinished = onDismissFinished,
            matchPanelMotion = true,
            backgroundColor = Color.Transparent, insideMargin = DpSize(0.dp, 0.dp),
            outsideMargin = DpSize(16.dp, 12.dp)) {
            // The material belongs inside the native animated surface, alongside its content.
            // An outer OverlayDialog modifier is drawn before the native entrance transform.
            Box(Modifier.fillMaxWidth().testTag("update_options_surface").updateDialogMaterial(backdrop, dark)) {
            Column(Modifier.fillMaxWidth().then(if (choiceLayout) Modifier.padding(top = 24.dp, bottom = 16.dp)
                else Modifier.padding(24.dp))
                .testTag("update_options_sheet")) {
                Text(title, Modifier.fillMaxWidth().padding(horizontal = if (choiceLayout) 24.dp else 0.dp).testTag("update_options_title"),
                    color = colors.onBackground, fontSize = 20.sp, lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                summary?.let {
                    Text(it, color = colors.onSurfaceVariantSummary, fontSize = 14.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = if (choiceLayout) 24.dp else 0.dp).padding(top = 12.dp))
                }
                Column(Modifier.fillMaxWidth()
                    .padding(top = if (choiceLayout) 8.dp else 20.dp).heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()), content = content)
                Row(Modifier.fillMaxWidth().padding(horizontal = if (choiceLayout) 16.dp else 0.dp)
                    .padding(top = if (choiceLayout) 20.dp else 24.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (actions != null) actions() else UpdateDialogAction("完成", onDismiss,
                        Modifier.weight(1f).semantics { contentDescription = "关闭$title" })
                }
            }
            }
        }
        }
    }
}

@Composable private fun Modifier.updateDialogMaterial(backdrop: Backdrop?, dark: Boolean): Modifier {
    val material = if (Build.VERSION.SDK_INT >= 33 && backdrop != null) Modifier.frostedUpdateDialog(backdrop, dark)
    else Modifier.background(if (dark) Color(0xF22C2D30) else Color(0xF5F2F3F5), updateDialogShape)
    return then(material).border(.7.dp, Color.White.copy(alpha = if (dark) .12f else .65f), updateDialogShape)
}

@RequiresApi(33)
private fun Modifier.frostedUpdateDialog(backdrop: Backdrop, dark: Boolean): Modifier =
    then(Modifier.drawBackdrop(backdrop, shape = { updateDialogShape }, effects = {
        colorControls(brightness = 0f, contrast = 1f, saturation = .35f)
        blur(28.dp.toPx(), 28.dp.toPx())
    }, onDrawSurface = {
        drawRect(if (dark) Color(0xFF292A2D).copy(alpha = .86f) else Color(0xFFF3F4F6).copy(alpha = .88f))
    }))

@Composable internal fun UpdateDialogAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, destructive: Boolean = false, compact: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val dark = isInDarkTheme()
    val press = rememberUpdateDialogPress()
    Box(modifier.heightIn(min = if (compact) 44.dp else 48.dp).graphicsLayer {
            scaleX = 1f - .025f * press.amount.value
            scaleY = 1f - .025f * press.amount.value
        }.clip(CircleShape)
        .background(if (dark) Color.White.copy(alpha = .13f) else Color.White.copy(alpha = .85f))
        .drawBehind { drawRect(colors.onBackground.copy(alpha = .055f * press.amount.value)) }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = press.source, indication = null, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 13.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
            color = (if (destructive) colors.error else colors.onBackground).copy(alpha = if (enabled) 1f else .35f),
            textAlign = TextAlign.Center)
    }
}

@Composable internal fun UpdateOptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable internal fun UpdateChoiceRow(label: String, selected: Boolean, showDivider: Boolean = false, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val dark = isInDarkTheme()
    val selectedRing = androidx.compose.ui.graphics.lerp(colors.primary, colors.onBackground, .12f).copy(alpha = .78f)
    val press = rememberUpdateDialogPress()
    val clickInteractions = remember { MutableInteractionSource() }
    val selection = animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "update choice ring")
    Row(Modifier.fillMaxWidth().drawBehind {
            drawRect(colors.onBackground.copy(alpha = .06f * press.amount.value))
            if (showDivider) drawLine(colors.onBackground.copy(alpha = if (dark) .065f else .045f),
                Offset(24.dp.toPx(), size.height), Offset(size.width - 24.dp.toPx(), size.height), .5.dp.toPx())
        }
        .updateDialogRowPress(press)
        .selectable(selected = selected, role = Role.RadioButton, interactionSource = clickInteractions, indication = null, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.onBackground,
            fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.5.dp.toPx()
            drawCircle(colors.onSurfaceVariantSummary.copy(alpha = if (dark) .32f else .27f),
                radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            val progress = selection.value
            if (progress > 0f) {
                drawCircle(selectedRing.copy(alpha = selectedRing.alpha * progress), radius = size.minDimension / 2 * (.75f + .25f * progress))
                drawCircle(Color.White.copy(alpha = progress), radius = 6.dp.toPx() * (.75f + .25f * progress))
            }
        }
    }
}

@Composable internal fun UpdateAutoCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val press = rememberUpdateDialogPress()
    val clickInteractions = remember { MutableInteractionSource() }
    val ink = MiuixTheme.colorScheme.onBackground
    Row(Modifier.fillMaxWidth().testTag("update_auto_check")
        .drawBehind { drawRect(ink.copy(alpha = .06f * press.amount.value)) }
        .updateDialogRowPress(press)
        .toggleable(checked, role = Role.Switch, interactionSource = clickInteractions, indication = null, onValueChange = onCheckedChange)
        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("启动时自动检查", color = MiuixTheme.colorScheme.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable internal fun UpdateOptionSectionLabel(label: String) {
    Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 4.dp))
}
