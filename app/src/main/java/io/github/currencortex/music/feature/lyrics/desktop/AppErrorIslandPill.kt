package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.SoundEffectNotice
import io.github.currencortex.music.core.media.IslandNoticeKind
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable internal fun AppErrorIslandPill(notice: SoundEffectNotice, modifier: Modifier = Modifier,
    material: Boolean = true, height: Dp = 36.dp, font: Float = DESKTOP_ISLAND_FONT_SIZE) {
    val shape = RoundedCornerShape(50)
    val error = notice.kind == IslandNoticeKind.ERROR
    val surface = if (material) Modifier.background(Color(0xFF25262B), shape)
        .border(.6.dp, Color.White.copy(alpha = .16f), shape) else Modifier
    Row(modifier.widthIn(min = 156.dp, max = 320.dp).heightIn(min = height).then(surface)
        .padding(horizontal = 14.dp, vertical = 7.dp).testTag(if (error) "island_error_notice" else "island_message_notice")
        .semantics { liveRegion = if (error) LiveRegionMode.Assertive else LiveRegionMode.Polite; stateDescription = notice.message },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (notice.action != null) Icons.Outlined.FavoriteBorder else MusicIcons.Info, null, Modifier.size(18.dp),
            tint = if (notice.action != null) Color(0xFFFF5B75) else if (error) Color(0xFFFF9B91) else Color(0xFF8AB4FF))
        Text(notice.message, Modifier.weight(1f, fill = false), fontSize = font.sp, lineHeight = (font * 1.2f).sp,
            fontWeight = FontWeight.Medium, color = Color(0xFFF6F4FA), maxLines = 2, overflow = TextOverflow.Ellipsis)
        notice.action?.let { action ->
            Text(action.label, Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .09f))
                .clickable(role = Role.Button, onClick = action.invoke).padding(horizontal = 9.dp, vertical = 4.dp)
                .testTag("island_notice_action"), fontSize = font.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF8CA0))
        }
    }
}
