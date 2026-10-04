package io.github.currencortex.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** End-of-list clearance for controls drawn over scrolling root content. */
val LocalMusicBottomInset = staticCompositionLocalOf { 0.dp }

@Composable fun MusicTextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(label, modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp),
        fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .4f))
}

@Composable fun MusicSectionHeader(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        action?.let { MusicTextAction(it, onClick) }
    }
}

@Composable fun MusicDestinationRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    summary: String? = null, enabled: Boolean = true, chevron: Boolean = true) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
            summary?.let { Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f),
                maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (chevron) Text("›", Modifier.padding(start = 12.dp), fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f))
    }
}

@Composable fun MusicTransportButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, playing: Boolean = false, direction: Int = 0, prominent: Boolean = false) {
    val color = if (prominent) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
    val ink = color.copy(alpha = if (enabled) 1f else .3f)
    Box(modifier.size(if (prominent) 64.dp else 48.dp)
        .background(if (prominent) MiuixTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label; if (!enabled) disabled() },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (prominent) 28.dp else 22.dp)) {
            if (playing && direction == 0) {
                drawRect(ink, Offset(size.width * .22f, size.height * .16f), Size(size.width * .18f, size.height * .68f))
                drawRect(ink, Offset(size.width * .60f, size.height * .16f), Size(size.width * .18f, size.height * .68f))
            } else {
                val path = Path().apply {
                    if (direction < 0) { moveTo(size.width * .78f, size.height * .14f); lineTo(size.width * .25f, size.height * .5f); lineTo(size.width * .78f, size.height * .86f) }
                    else { moveTo(size.width * .22f, size.height * .14f); lineTo(size.width * .78f, size.height * .5f); lineTo(size.width * .22f, size.height * .86f) }
                    close()
                }
                drawPath(path, ink)
                if (direction != 0) drawRect(ink, Offset(size.width * if (direction > 0) .8f else .12f, size.height * .14f), Size(size.width * .08f, size.height * .72f))
            }
        }
    }
}
