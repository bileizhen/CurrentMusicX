package io.github.currencortex.music.feature.binding

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.currencortex.music.data.binding.BindingState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun BindingAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null, destructive: Boolean = false) {
    val color = (if (destructive) Color(0xFFD45B5B) else MiuixTheme.colorScheme.primary).copy(alpha = if (enabled) 1f else .4f)
    Row(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable internal fun BindingNotice(text: String, modifier: Modifier = Modifier, busy: Boolean = false) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MiuixTheme.colorScheme.primary.copy(alpha = .06f)).padding(14.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp))
        else Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.primary)
        Text(text, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .7f))
    }
}

@Composable internal fun BindingAccountCard(binding: BindingState, busy: Boolean,
    onSync: () -> Unit, onLive: () -> Unit, onRefresh: () -> Unit, onRelogin: () -> Unit, onUnbind: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("binding_account_card")) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val avatar = binding.profile?.avatar.orEmpty()
                if (avatar.isNotBlank()) AsyncImage(avatar, "网易云头像", Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Box(Modifier.size(48.dp).clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, Modifier.size(26.dp), tint = MiuixTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(binding.profile?.nickname?.takeIf { it.isNotBlank() } ?: "网易云音乐", fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("网易云音乐账号", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
                Text(if (binding.stale) "需登录" else "已绑定", Modifier.clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
            }
            if (binding.stale) BindingNotice("登录态已失效，请重新登录后继续同步。")
            else if (binding.lastSync > 0) {
                val date = remember(binding.lastSync) { SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(binding.lastSync * 1000)) }
                Text("已同步 ${binding.lastSyncCount} 个歌单 · $date", fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
            } else Text("连接已完成，可以同步你的网易云歌单。", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
            Button(onClick = onSync, enabled = !busy, colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sync_binding")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(19.dp), tint = MiuixTheme.colorScheme.onPrimary)
                    Text("同步网易云歌单", color = MiuixTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BindingAction("检查登录态", onLive, Modifier.weight(1f), !busy, Icons.Default.Check)
                Box(Modifier.width(1.dp).height(16.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .08f)))
                BindingAction("刷新登录态", onRefresh, Modifier.weight(1f), !busy, Icons.Default.Refresh)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .05f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BindingAction("重新登录", onRelogin, Modifier.testTag("relogin_binding"), !busy)
                BindingAction("解绑", onUnbind, Modifier.testTag("unbind_binding"), !busy, destructive = true)
            }
        }
    }
}

@Composable internal fun BindingMethods(qr: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    val progress = animateFloatAsState(if (qr) 0f else 1f, tween(220), label = "binding method")
    val shape = remember { RoundedCornerShape(14.dp) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clip(shape)
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .05f))) {
        val width = (maxWidth - 8.dp) / 2
        Box(Modifier.padding(4.dp).width(width).height(40.dp).graphicsLayer { translationX = width.toPx() * progress.value }
            .clip(RoundedCornerShape(11.dp)).background(MiuixTheme.colorScheme.surface))
        Row(Modifier.fillMaxSize().padding(4.dp).selectableGroup()) {
            listOf("扫码登录", "手机验证码").forEachIndexed { index, label ->
                val selected = qr == (index == 0)
                Box(Modifier.weight(1f).fillMaxHeight().testTag(if (index == 0) "qr_binding" else "phone_binding_tab")
                    .selectable(selected, enabled = enabled, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { onSelect(index == 0) }), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
        }
    }
}

@Composable internal fun BindingQrContent(bitmap: Bitmap?, state: BindingUiState, busy: Boolean, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("用网易云音乐扫一扫", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("扫描二维码 · 确认授权 · 自动同步", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
        Box(Modifier.size(216.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = .05f), RoundedCornerShape(22.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), "网易云登录二维码", Modifier.fillMaxSize().testTag("binding_qr"))
            else if (state.qrUrl != null || state.qrMessage.isBlank() || state.qrMessage.contains("正在生成")) CircularProgressIndicator(Modifier.size(28.dp))
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (state.qrMessage.contains("已过期")) Icons.Default.Refresh else Icons.Default.Info, null,
                    Modifier.size(36.dp), tint = Color(0xFF747C89))
                Text(if (state.qrMessage.contains("已过期")) "二维码已过期" else if (state.qrMessage == "绑定成功") "已完成授权" else "二维码暂不可用",
                    color = Color(0xFF535D6B), fontSize = 13.sp)
            }
        }
        Text(state.qrMessage.ifBlank { "正在准备二维码…" }, Modifier.testTag("binding_qr_status").semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 13.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .65f))
        BindingAction("刷新二维码", onRefresh, Modifier.testTag("refresh_binding_qr"), !busy, Icons.Default.Refresh)
        Text("授权后返回此页即可，切换应用不会刷新当前二维码。", fontSize = 12.sp, lineHeight = 19.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f))
    }
}
