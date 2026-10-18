package io.github.currencortex.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.profile.PostAttachment
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun MusicAttachmentCard(card: PostAttachment, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("music_attachment_${card.type}_${card.id}").clickable(onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MusicCover(card.pic, Modifier.size(42.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(card.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(card.sub.ifBlank { when (card.type) { "song" -> "歌曲"; "album" -> "专辑"; "artist" -> "歌手"; else -> "歌单" } },
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
