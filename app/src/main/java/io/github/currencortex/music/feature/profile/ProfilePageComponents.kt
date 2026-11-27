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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.profile.ProfileStats
import io.github.currencortex.music.data.profile.listeningDuration
import io.github.currencortex.music.data.settings.MusicPlatform
import io.github.currencortex.music.ui.component.rememberPageNavigationSource
import io.github.currencortex.music.ui.component.LocalRootTabSwipe
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable internal fun ProfilePageTitle(navigate: (String) -> Unit, onSettings: (() -> Unit)?,
    public: Boolean = false, loading: Boolean = false, reload: () -> Unit = {}) {
    val actions: @Composable RowScope.() -> Unit = {
        if (public) GlassIconButton(reload, "刷新", enabled = !loading) { Icon(COUIIcons.Refresh, null) }
        else GlassIconButton({ navigate("user/announcements") }, "公告", Modifier.testTag("me_announcements")) {
            Icon(COUIIcons.File, null)
        }
        onSettings?.let { action -> GlassIconButton(action, "设置", Modifier.testTag("me_settings")) {
            Icon(COUIIcons.Settings, null)
        } }
    }
    SettingsUiScope {
        if (public) Row(Modifier.fillMaxWidth().height(SettingsToolbarHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("用户主页", Modifier.weight(1f), fontSize = 24.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold, color = COUITheme.colorScheme.labelPrimary)
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
    tag: String, iconColor: androidx.compose.ui.graphics.Color = COUITheme.colorScheme.primary, onClick: () -> Unit) {
    val source = rememberPageNavigationSource(title, summary.orEmpty(), icon)
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = { Icon(icon, null, Modifier.size(24.dp), tint = iconColor) },
        onClick = { source.open(onClick) },
        modifier = Modifier.testTag(tag).then(source.modifier),
    )
}

/** Category title plus one COUI card group; the group draws the rows' corners and dividers. */
@Composable internal fun ProfileCardGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        PreferenceCategory(title = title, marginStartType = PreferenceCategoryMarginStartType.Small)
        Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { content() }
    }
}

@Composable internal fun ProfileLibrarySection(navigate: (String) -> Unit, platform: MusicPlatform = MusicPlatform.NETEASE,
    showLikes: Boolean = true) {
    val qq = platform == MusicPlatform.QQ_MUSIC
    val colors = COUITheme.colorScheme
    ProfileCardGroup("我的音乐") {
        CardListGroup {
            if (showLikes) item {
                ProfileDestination("我喜欢", "收藏的歌曲", COUIIcons.Favorites, "open_likes", iconColor = colors.red) {
                    navigate(if (qq) "qq/likes" else "lib/likes")
                }
            }
            item {
                ProfileDestination("歌单", "创建与收藏的歌单", COUIIcons.Playlist, "open_playlists", iconColor = colors.blue) {
                    navigate(if (qq) "qq/playlists" else "lib/playlists")
                }
            }
            if (qq) item {
                ProfileDestination("本周常听", "本周播放次数最多的歌曲", COUIIcons.Recent, "open_recent", iconColor = colors.green) {
                    navigate("qq/recent")
                }
            } else {
                item {
                    ProfileDestination("音乐云盘", "网易云音乐个人云盘", COUIIcons.CloudFill, "open_cloud_drive", iconColor = colors.blue) { navigate("lib/cloud") }
                }
                item {
                    ProfileDestination("最近播放", "回顾听过的歌曲", COUIIcons.Recent, "open_recent") { navigate("lib/recent") }
                }
            }
            item {
                ProfileDestination("听歌时长", if (qq) "本机与 QQ 音乐听歌足迹" else "本机与网易云听歌足迹",
                    COUIIcons.Stopwatch, "open_listening", iconColor = colors.azure) { navigate("user/listening") }
            }
            item {
                ProfileDestination("本地音乐", "手机上的歌曲", COUIIcons.Phone, "open_local_music", iconColor = colors.azure) { navigate("lib/local") }
            }
        }
    }
}

/** Native and CurrentMusic accounts share destinations and the same list geometry. */
/** Account rows live on the account page opened from settings; services stay here. */
@Composable internal fun ProfileServicesSection(navigate: (String) -> Unit, tasks: Boolean,
    navidromeConfigured: Boolean = false, embyConfigured: Boolean = false,
    platform: MusicPlatform = MusicPlatform.NETEASE, qqBound: Boolean = false) {
    val colors = COUITheme.colorScheme
    ProfileCardGroup("音乐服务") {
        CardListGroup {
            if (platform == MusicPlatform.QQ_MUSIC) {
                item {
                    ProfileDestination(if (qqBound) "QQ 音乐账号" else "登录 QQ 音乐",
                        if (qqBound) "已连接，管理个人收藏、歌单与推荐" else "扫码登录后访问个人音乐库与推荐",
                        COUIIcons.ContactsCircle, "open_qq_binding", iconColor = colors.green) { navigate("qq/account") }
                }
                item {
                    ProfileDestination("收藏的专辑", "管理 QQ 音乐收藏专辑", COUIIcons.Album, "open_qq_albums", iconColor = colors.orange) {
                        navigate("qq/albums")
                    }
                }
            }
            if (tasks && platform == MusicPlatform.NETEASE) item {
                ProfileDestination("网易云任务", "签到、云贝与会员成长", COUIIcons.Tasks, "open_netease_tasks", iconColor = colors.orange) { navigate("user/netease-tasks") }
            }
            if (platform == MusicPlatform.NETEASE) item {
                ProfileDestination("导入歌单", "链接、文字、图片与本地歌曲", COUIIcons.Import, "profile_playlist_import", iconColor = colors.violet) {
                    navigate("user/playlist-import")
                }
            }
            if (navidromeConfigured) item {
                ProfileDestination("Navidrome 曲库", "自己的音乐服务器", COUIIcons.Folder, "profile_navidrome", iconColor = colors.mint) { navigate("navidrome") }
            }
            if (embyConfigured) item {
                ProfileDestination("Emby 曲库", "自己的音乐服务器", COUIIcons.Folder, "profile_emby", iconColor = colors.mint) { navigate("emby") }
            }
        }
    }
}

@Composable internal fun ProfileAccountSection(navigate: (String) -> Unit, native: Boolean = false) {
    val colors = COUITheme.colorScheme
    ProfileCardGroup("账号") {
        CardListGroup {
            if (native) item {
                ProfileDestination("CurrentMusic 账户", "注册与登录、头像装饰与一起听", COUIIcons.Contacts,
                    "open_currentmusic_login", iconColor = COUITheme.colorScheme.blue) { navigate("user/login") }
            }
            else {
                item {
                    ProfileDestination("等级与成长", "查看积分、每日进度与等级试炼", COUIIcons.Ok, "open_levels", iconColor = colors.orange) { navigate("user/levels") }
                }
                item {
                    ProfileDestination("头像挂件", "装饰你的头像", COUIIcons.Image, "open_decorations", iconColor = colors.violet) { navigate("user/decorations") }
                }
            }
        }
    }
}

@Composable internal fun ProfileStatsBlock(stats: ProfileStats, likesLabel: String = "点赞",
    likesValue: String = "${stats.likes}", onLikes: (() -> Unit)? = null, onListening: (() -> Unit)? = null) {
    val colors = COUITheme.colorScheme
    val muted = colors.labelSecondaryVariant
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 18.dp).testTag("profile_stats"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val entries = listOf(Triple(likesLabel, likesValue, onLikes), Triple("收藏", "${stats.favs}", null),
            Triple("歌单", "${stats.playlists}", null))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { index, (label, value, action) ->
                if (index > 0) Box(
                    Modifier.width(0.5.dp).height(26.dp).background(colors.divider.copy(alpha = 0.55f)),
                )
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).semantics(mergeDescendants = true) {}
                    .then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action)
                        .testTag("profile_platform_likes") else Modifier)
                    .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = colors.labelPrimary)
                    Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                }
            }
        }
        val tileShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileStatTile(
                label = "听歌天数",
                value = "${stats.playDays} 天",
                shape = tileShape,
            )
            ProfileStatTile(
                label = "听歌时长",
                value = listeningDuration(stats.listenMs),
                shape = tileShape,
                onClick = onListening,
                tag = "profile_listening_duration",
            )
        }
    }
}

@Composable
private fun RowScope.ProfileStatTile(
    label: String,
    value: String,
    shape: RoundedCornerShape,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
) {
    val colors = COUITheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(colors.fillThin)
            .semantics(mergeDescendants = true) {}
            .then(
                onClick?.let {
                    Modifier
                        .clickable(role = Role.Button, onClick = it)
                        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                } ?: Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = colors.labelSecondaryVariant)
        Text(
            value,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
