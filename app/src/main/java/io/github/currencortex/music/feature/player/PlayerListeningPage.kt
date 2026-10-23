package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.data.profile.listeningDuration
import io.github.currencortex.music.feature.profile.UserAvatar
import io.github.currencortex.music.feature.profile.UserRoleBadge
import io.github.currencortex.music.feature.profile.UserTitleBadge
import io.github.currencortex.music.feature.roaming.PersonalRoamingPage
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun PlayerListeningPage(container: AppContainer, onPlay: () -> Unit,
    onBinding: () -> Unit, onDialogActive: (Boolean) -> Unit) {
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val netease by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val today by container.todayListening.today.collectAsStateWithLifecycle()
    val user = account.account?.let { ProfileUser(id = it.id, username = it.username, nickname = it.nickname,
        avatar = it.avatar, isAdmin = it.isAdmin, isSuper = it.isSuper, title = it.title) }
        ?: netease.profile?.takeIf { netease.loggedIn }?.let { ProfileUser(nickname = it.nickname, avatar = it.avatar) }
        ?: ProfileUser(nickname = "未登录")
    LaunchedEffect(container) {
        while (true) { container.todayListening.refresh(); delay(15_000) }
    }
    val colors = MiuixTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        PersonalRoamingPage(container,
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(end = maxWidth * .08f).testTag("player_listening_scroll"),
            onPlay, onBinding, onDialogActive) {
            Text("我的聆听", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UserAvatar(user, container, size = 48.dp, reserveOverlay = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(user.nickname.ifBlank { user.username }, Modifier.testTag("player_side_username"),
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            UserRoleBadge(user.role)
                            UserTitleBadge(user.title)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("今日听歌", fontSize = 14.sp)
                            Text("本机播放时长", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                        Text(listeningDuration(today), Modifier.testTag("player_today_listening"),
                            fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
