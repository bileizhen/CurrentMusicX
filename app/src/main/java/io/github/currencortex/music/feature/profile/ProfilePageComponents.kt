package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.profile.ProfileStats
import io.github.currencortex.music.data.profile.listeningDuration
import io.github.currencortex.music.ui.component.rememberPageNavigationSource
import io.github.currencortex.music.ui.component.LocalRootTabSwipe
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun ProfilePageTitle(navigate: (String) -> Unit, onSettings: (() -> Unit)?,
    public: Boolean = false, loading: Boolean = false, reload: () -> Unit = {}) {
    val actions: @Composable RowScope.() -> Unit = {
        if (public) GlassIconButton(reload, "刷新", enabled = !loading) { Icon(MusicIcons.Update, null) }
        else GlassIconButton({ navigate("user/announcements") }, "公告", Modifier.testTag("me_announcements")) {
            Icon(MusicIcons.File, null)
        }
        onSettings?.let { action -> GlassIconButton(action, "设置", Modifier.testTag("me_settings")) {
            Icon(Icons.Outlined.Settings, null)
        } }
    }
    SettingsUiScope {
        if (public) Row(Modifier.fillMaxWidth().height(SettingsToolbarHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("用户主页", Modifier.weight(1f), fontSize = 24.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
            actions()
        }
        else SettingsRootTopBar("我的", Modifier.testTag("me_root_toolbar"), actions = actions)
    }
}

@Composable internal fun ProfilePageLayout(navigate: (String) -> Unit, onSettings: (() -> Unit)?,
    public: Boolean = false, loading: Boolean = false, reload: () -> Unit = {}, onBack: (() -> Unit)? = null, content: @Composable (Dp) -> Unit) {
    if (public) MusicSubpage("用户主页", "public_profile", onBack, actions = {
        GlassIconButton(reload, "刷新", enabled = !loading) { Icon(MusicIcons.Update, null) }
    }, content = content)
    else SettingsScrollLayout(Modifier.fillMaxSize(), topBar = {
        ProfilePageTitle(navigate, onSettings, loading = loading, reload = reload)
    }, content = content)
}

@Composable internal fun ProfileDestination(title: String, summary: String?, icon: ImageVector,
    tag: String, onClick: () -> Unit) {
    val source = rememberPageNavigationSource(title, summary.orEmpty(), icon)
    SettingsUiScope {
        SettingsPreference(title, summary, Modifier.testTag(tag).then(source.modifier), onClick = { source.open(onClick) },
            leading = { Icon(icon, null, Modifier.size(24.dp)) },
            trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) })
    }
}

@Composable internal fun ProfileLibrarySection(navigate: (String) -> Unit) {
    SettingsListSection("我的音乐") {
        ProfileDestination("我喜欢", "收藏的歌曲", MusicIcons.Heart, "open_likes") { navigate("lib/likes") }
        SettingsDivider(52.dp)
        ProfileDestination("歌单", "创建与收藏的歌单", MusicIcons.List, "open_playlists") { navigate("lib/playlists") }
        SettingsDivider(52.dp)
        ProfileDestination("音乐云盘", "网易云音乐个人云盘", MusicIcons.Storage, "open_cloud_drive") { navigate("lib/cloud") }
        SettingsDivider(52.dp)
        ProfileDestination("最近播放", "回顾听过的歌曲", MusicIcons.Update, "open_recent") { navigate("lib/recent") }
        SettingsDivider(52.dp)
        ProfileDestination("听歌时长", "本机与网易云听歌足迹", MusicIcons.Headphones, "open_listening") { navigate("user/listening") }
        SettingsDivider(52.dp)
        ProfileDestination("本地音乐", "手机上的歌曲", MusicIcons.Device, "open_local_music") { navigate("lib/local") }
    }
}

/** Native and CurrentMusic accounts share destinations and the same list geometry. */
@Composable internal fun ProfileServicesSection(navigate: (String) -> Unit, tasks: Boolean,
    native: Boolean = false, bound: Boolean = true,
    navidromeConfigured: Boolean = false, embyConfigured: Boolean = false) {
    SettingsListSection("音乐服务") {
        ProfileDestination(if (native && !bound) "登录网易云" else "网易云账号",
            if (native && !bound) "需先注册并登录 CurrentMusic" else "绑定账号、同步歌单", MusicIcons.Account, "open_binding") { navigate("user/binding") }
        if (tasks) {
            SettingsDivider(52.dp)
            ProfileDestination("网易云任务", "签到、云贝与会员成长", MusicIcons.Check, "open_netease_tasks") { navigate("user/netease-tasks") }
        }
        SettingsDivider(52.dp)
        ProfileDestination("导入歌单", "链接、文字、图片与本地歌曲", MusicIcons.Transfer, "profile_playlist_import") { navigate("user/playlist-import") }
        if (navidromeConfigured) {
            SettingsDivider(52.dp)
            ProfileDestination("Navidrome 曲库", "自己的音乐服务器", MusicIcons.Storage, "profile_navidrome") { navigate("navidrome") }
        }
        if (embyConfigured) {
            SettingsDivider(52.dp)
            ProfileDestination("Emby 曲库", "自己的音乐服务器", MusicIcons.Storage, "profile_emby") { navigate("emby") }
        }
    }
}

@Composable internal fun ProfileAccountSection(navigate: (String) -> Unit, native: Boolean = false, administrator: Boolean = false) {
    SettingsListSection("账号") {
        if (native) ProfileDestination("CurrentMusic 账户", "注册与登录、头像装饰与一起听", MusicIcons.Account,
            "open_currentmusic_login") { navigate("user/login") }
        else {
            ProfileDestination("等级与成长", "查看积分、每日进度与等级试炼", MusicIcons.Check, "open_levels") { navigate("user/levels") }
            SettingsDivider(52.dp)
            ProfileDestination("头像挂件", "装饰你的头像", MusicIcons.Image, "open_decorations") { navigate("user/decorations") }
            SettingsDivider(52.dp)
            ProfileDestination("账号与安全", "切换账号、修改密码与退出登录", MusicIcons.Account, "open_security") { navigate("user/security") }
            if (administrator) {
                SettingsDivider(52.dp)
                ProfileDestination("管理员面板", "用户、公告、设备与系统管理", MusicIcons.Account, "open_admin") { navigate("user/admin") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ProfileStatsBlock(stats: ProfileStats, onListening: (() -> Unit)? = null) {
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).testTag("profile_stats"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("点赞" to "${stats.likes}", "收藏" to "${stats.favs}", "歌单" to "${stats.playlists}").forEach { (label, value) ->
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 280.dp * LocalDensity.current.fontScale + 10.dp) 2 else 1
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val tile = Modifier.weight(1f).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .035f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
            Column(tile, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("听歌天数", fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                Text("${stats.playDays} 天", fontSize = 15.sp, lineHeight = 20.sp)
            }
            Column(tile.then(onListening?.let { Modifier.clickable(role = Role.Button, onClick = it).testTag("profile_listening_duration") }
                ?: Modifier), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("听歌时长", fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                Text(listeningDuration(stats.listenMs), fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        }
    }
}
