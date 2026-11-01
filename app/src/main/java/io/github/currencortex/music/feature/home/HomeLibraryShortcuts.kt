package io.github.currencortex.music.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun HomeLibraryShortcuts(navigate: (String) -> Unit, inRoom: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("home_shortcuts")) {
        SettingsGroup {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            HomeShortcut("我喜欢", MusicIcons.Heart, "open_likes", { navigate("lib/likes") }, Modifier.weight(1f))
            HomeShortcut("歌单", MusicIcons.List, "open_playlists", { navigate("lib/playlists") }, Modifier.weight(1f))
            HomeShortcut("听歌识曲", MusicIcons.Microphone, "open_recognition", { navigate("lib/recognition") }, Modifier.weight(1f))
            HomeShortcut(if (inRoom) "回到房间" else "一起听", MusicIcons.Headphones, "open_rooms",
                { navigate("room/list") }, Modifier.weight(1f))
        }
        }
    }
}

@Composable private fun HomeShortcut(title: String, icon: ImageVector, tag: String, onClick: () -> Unit, modifier: Modifier) {
    ElasticPress(modifier.testTag(tag).clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() },
        indication = null, onClick = onClick)) {
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = MiuixTheme.colorScheme.primary)
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
}
