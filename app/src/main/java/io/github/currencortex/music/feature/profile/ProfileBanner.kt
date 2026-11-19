package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProfileBannerImage(model: Any?, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.fillMaxWidth().aspectRatio(4f).clip(RoundedCornerShape(14.dp))
        .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = .65f), colors.surface)))) {
        if (model != null) AsyncImage(model, contentDescription = null, modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun ProfileBannerPreview(bytes: ByteArray, busy: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    MusicDialog("预览主页横幅", { if (!busy) onCancel() }) {
        ProfileBannerImage(bytes, Modifier.testTag("profile_banner_preview"))
        Text("横幅会公开展示。图片将居中裁剪为 4∶1。")
        MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicDialogButton("取消", onCancel, enabled = !busy, modifier = Modifier.weight(1f))
            MusicDialogButton(if (busy) "保存中…" else "保存横幅", onSave,
                enabled = !busy, modifier = Modifier.weight(1f).testTag("profile_banner_save"))
        }
    }
}
