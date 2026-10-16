package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun UserRoleBadge(role: UserRole?, modifier: Modifier = Modifier) {
    if (role == null) return
    val tint = if (role == UserRole.SUPER_ADMIN) Color(0xffb78324) else MiuixTheme.colorScheme.primary
    Text(role.label, modifier.testTag("user_role_${role.name.lowercase()}")
        .background(tint.copy(alpha = .12f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable internal fun UserTitleBadge(title: String, modifier: Modifier = Modifier) {
    val label = title.trim()
    if (label.isEmpty()) return
    val tint = Color(0xFF9A7025)
    Text(label, modifier.testTag("user_title").widthIn(max = 240.dp)
        .background(tint.copy(alpha = .09f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
