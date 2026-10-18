package io.github.currencortex.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.ListenTogetherPair

/** Shared slot geometry keeps the island's measured text budget and artwork drawing in sync. */
internal const val listeningPairArtworkAspectRatio = 1.6f

/** Two stationary, overlapping portraits. A missing member keeps a person placeholder, never a song cover. */
@Composable internal fun ListeningPairArtwork(pair: ListenTogetherPair, modifier: Modifier = Modifier) {
    val members = pair.participants.distinctBy { it.userId }.take(2)
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("listening_pair_artwork").semantics(mergeDescendants = true) {
            contentDescription = "一起听：" + (0..1).joinToString("、") { index ->
                members.getOrNull(index)?.nickname?.takeIf(String::isNotBlank) ?: if (index == 0) "我" else "等待好友加入"
            }
        }) {
            val diameter = minOf(maxHeight, maxWidth / listeningPairArtworkAspectRatio)
            (0..1).forEach { index ->
                val member = members.getOrNull(index)
                Box(Modifier.align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .size(diameter).testTag("listening_pair_avatar_$index").clip(CircleShape)
                    .background(Color(0xFFE6E8EE)).border(1.dp, Color.White.copy(alpha = .9f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.user_avatar_placeholder), null, Modifier.fillMaxSize(.62f),
                        colorFilter = ColorFilter.tint(Color(0xFF747B8B)))
                    if (!member?.avatarUrl.isNullOrBlank()) AsyncImage(
                        model = rememberAvatarRequest(member?.avatarUrl), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(1.dp).clip(CircleShape))
                }
            }
        }
    }
}
