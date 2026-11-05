package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.profile.UserRole
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun UserRoleBadge(role: UserRole?, modifier: Modifier = Modifier) {
    if (role == null) return
    val tint = if (role == UserRole.SUPER_ADMIN || role == UserRole.OWNER) Color(0xffb78324) else MiuixTheme.colorScheme.primary
    UserIdentityBadge(role.label, tint, .10f, modifier.testTag("user_role_${role.name.lowercase()}"))
}

@Composable internal fun UserTitleBadge(title: String, modifier: Modifier = Modifier) {
    val label = title.trim()
    if (label.isEmpty()) return
    val tint = Color(0xFF9A7025)
    UserIdentityBadge(label, tint, .08f, modifier.testTag("user_title").widthIn(max = 240.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun UserIdentityDetails(title: String, ipRegion: String) {
    val region = ipRegion.trim()
    if (title.isBlank() && region.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UserTitleBadge(title)
        if (region.isNotEmpty()) Row(Modifier.testTag("user_ip_region").padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f)
            Icon(Icons.Default.LocationOn, null, Modifier.size(12.dp), tint = color)
            Text("IP属地：$region", fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun UserLevelBadge(level: Int?, modifier: Modifier = Modifier) {
    val label = io.github.currencortex.music.data.profile.userLevelLabel(level) ?: return
    val color = MiuixTheme.colorScheme.primary
    UserIdentityBadge(label, color, .08f, modifier.testTag("user_level"))
}

@Composable private fun UserIdentityBadge(label: String, tint: Color, backgroundAlpha: Float, modifier: Modifier) {
    Text(label, modifier.background(tint.copy(alpha = backgroundAlpha), RoundedCornerShape(5.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp), color = tint,
        fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
