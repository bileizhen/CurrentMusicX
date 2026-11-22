package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.nativeui.MusicSubpage
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsListSection

/**
 * Account entry points grouped into one page, opened from the settings profile card:
 * the Netease binding, the CurrentMusic account's security page and the admin panel.
 */
@Composable
fun AccountHubScreen(container: AppContainer, navigate: (String) -> Unit, onBack: () -> Unit) {
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val netease by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val qq by container.qqMusic.state.collectAsStateWithLifecycle()
    // Without a CurrentMusic account the app runs on the built-in Netease session only.
    val native = account.account == null && container.nativeNetease != null
    val bound = if (native) netease.loggedIn else true
    // A failed startup refresh leaves the cached account's role intact and the admin page
    // reports its own errors, so being offline must not hide the entry.
    val administrator = !account.loading &&
        account.account?.let { it.isAdmin || it.isSuper || it.isOwner } == true
    MusicSubpage("账号", "account_page", onBack) { topInset ->
        LazyColumn(Modifier.fillMaxSize().testTag("account_page"),
            contentPadding = musicScrollPadding(PaddingValues(16.dp, topInset + 8.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("services") {
                SettingsListSection("音乐服务") {
                    ProfileDestination(if (native && !bound) "登录网易云" else "网易云账号",
                        if (native && !bound) "需先注册并登录 CurrentMusic" else "绑定账号、同步歌单",
                        MusicIcons.Account, "account_binding") { navigate("user/binding") }
                    SettingsDivider(52.dp)
                    ProfileDestination(if (qq.loggedIn) "QQ 音乐账号" else "登录 QQ 音乐",
                        if (qq.loggedIn) "已连接，管理个人收藏、歌单与推荐" else "扫码登录后访问个人音乐库与推荐",
                        MusicIcons.Account, "account_qq") { navigate("qq/account") }
                }
            }
            if (!native) item("account") {
                SettingsListSection("账号") {
                    ProfileDestination("账号与安全", "切换账号、修改密码与退出登录", MusicIcons.Account,
                        "account_security") { navigate("user/security") }
                    if (administrator) {
                        SettingsDivider(52.dp)
                        ProfileDestination("管理员面板", "用户、公告、设备与系统管理", MusicIcons.Account,
                            "account_admin") { navigate("user/admin") }
                    }
                }
            }
        }
    }
}