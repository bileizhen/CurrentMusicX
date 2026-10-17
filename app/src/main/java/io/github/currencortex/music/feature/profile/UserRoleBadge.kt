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
    Text(role.label, modifier.testTag("user_role_${role.name.lowercase()}")
        .background(tint.copy(alpha = .10f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable internal fun UserTitleBadge(title: String, modifier: Modifier = Modifier) {
    val label = title.trim()
    if (label.isEmpty()) return
    val tint = Color(0xFF9A7025)
    Text(label, modifier.testTag("user_title").widthIn(max = 240.dp)
        .background(tint.copy(alpha = .08f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
