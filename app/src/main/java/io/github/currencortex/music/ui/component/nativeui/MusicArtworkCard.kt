package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicCover
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared cover tile for home recommendations, playlists and artist albums. */
@Composable internal fun MusicArtworkCard(cover: String, title: String, subtitle: String,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, pixels: Int = 320) {
    ElasticPress(modifier.clickable(enabled = enabled, role = Role.Button,
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick), enabled = enabled) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MusicCover(cover, Modifier.fillMaxWidth().aspectRatio(1f), pixels = pixels, cornerRadius = 12.dp)
            Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, minLines = 1, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
