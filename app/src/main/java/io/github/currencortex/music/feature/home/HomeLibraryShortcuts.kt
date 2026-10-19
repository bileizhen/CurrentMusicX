package io.github.currencortex.music.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.feature.library.catalogRoute
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun HomeLibraryShortcuts(navigate: (String) -> Unit, inRoom: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("home_shortcuts")) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 8.dp)) {
            HomeShortcut("我喜欢", MusicIcons.Heart, "open_likes", { navigate("lib/likes") }, Modifier.weight(1f))
            HomeShortcut("歌单", MusicIcons.List, "open_playlists", { navigate("lib/playlists") }, Modifier.weight(1f))
            HomeShortcut("最近播放", MusicIcons.Update, "open_recent", { navigate("lib/recent") }, Modifier.weight(1f))
            HomeShortcut(if (inRoom) "回到房间" else "一起听", MusicIcons.Headphones, "open_rooms",
                { navigate("room/list") }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MusicTextAction("歌手", { navigate(catalogRoute(false)) }, Modifier.testTag("open_artists"))
            MusicTextAction("专辑", { navigate(catalogRoute(true)) }, Modifier.testTag("open_albums"))
            MusicTextAction("自建曲库", { navigate("navidrome") }, Modifier.testTag("library_navidrome"))
            MusicTextAction("私人漫游", { navigate("lib/roaming") }, Modifier.testTag("open_personal_roaming"))
        }
    }
}

@Composable private fun HomeShortcut(title: String, icon: ImageVector, tag: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).testTag(tag)
        .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = MiuixTheme.colorScheme.primary)
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
