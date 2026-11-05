package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.profile.PostAttachment
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun postAttachmentLabel(type: String) = when (type) {
    "song" -> "单曲"; "album" -> "专辑"; "artist" -> "歌手"; "playlist" -> "歌单"; else -> "音乐内容"
}

@Composable internal fun PostAttachmentTile(card: PostAttachment, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, enabled: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.onSurface.copy(alpha = .04f))
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 68.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicCover(card.pic, Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(postAttachmentLabel(card.type), card.sub).filter(String::isNotBlank).joinToString(" · "),
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
        }
        trailing()
    }
}

@Composable internal fun PostAction(icon: ImageVector, label: String, click: () -> Unit, modifier: Modifier = Modifier,
    count: Int? = null, selected: Boolean = false, enabled: Boolean = true) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(click, label, modifier.semantics { this.selected = selected }, enabled = enabled) {
            Icon(icon, null, Modifier.size(21.dp), tint = color.copy(alpha = if (enabled) 1f else .4f))
        }
        count?.takeIf { it > 0 }?.let {
            Text("$it", Modifier.clearAndSetSemantics {}, fontSize = 12.sp, color = color)
        }
    }
}
