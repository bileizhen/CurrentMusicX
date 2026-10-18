package io.github.currencortex.music.feature.room

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.ListenTogetherPair
import io.github.currencortex.music.core.media.ListenTogetherParticipant
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.netease.NeteaseAccountState
import io.github.currencortex.music.core.netease.listen.NeteaseListenTogetherState
import io.github.currencortex.music.data.netease.listen.ListenTogetherInvitation
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Actions are explicit, so opening this page cannot send an invitation to another person. */
internal data class NeteaseListenTogetherActions(
    val create: () -> Unit, val join: (String) -> Unit, val resume: () -> Unit,
    val refresh: () -> Unit, val leave: () -> Unit, val dismissError: () -> Unit,
    val copyInvitation: suspend (String) -> Unit, val pasteInvitation: suspend () -> String?)

@Composable internal fun NeteaseListenTogetherScreen(container: AppContainer, onBack: () -> Unit,
    onSearch: () -> Unit, onPlayer: () -> Unit, onBinding: (() -> Unit)? = null,
    onDialogActive: (Boolean) -> Unit = {}) {
    val controller = container.listenTogether
    val state by controller.state.collectAsStateWithLifecycle()
    val account by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val playback by container.playerController.state.collectAsStateWithLifecycle()
    val cmRoom by container.roomSession.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    LaunchedEffect(controller, account.revision) {
        if (account.loggedIn && !account.stale && (account.profile?.uid ?: 0) > 0) controller.refresh()
    }
    NeteaseListenTogetherContent(state, account, playback,
        actions = NeteaseListenTogetherActions(controller::create, controller::join, controller::resume,
            controller::refresh, controller::leave, controller::dismissError,
            copyInvitation = { url -> clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("网易云一起听邀请", url))) },
            pasteInvitation = { clipboard.getClipEntry()?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
            } }),
        onBack = onBack, onSearch = onSearch, onPlayer = onPlayer, onBinding = onBinding,
        currentMusicRoomActive = cmRoom.detail != null, onDialogActive = onDialogActive)
}

@Composable internal fun NeteaseListenTogetherContent(state: NeteaseListenTogetherState,
    account: NeteaseAccountState, playback: PlayerState, actions: NeteaseListenTogetherActions,
    onBack: () -> Unit, onSearch: () -> Unit, onPlayer: () -> Unit,
    onBinding: (() -> Unit)? = null, currentMusicRoomActive: Boolean = false,
    onDialogActive: (Boolean) -> Unit = {}) {
    val colors = MiuixTheme.colorScheme
    val ready = account.loggedIn && !account.stale && (account.profile?.uid ?: 0) > 0
    val canStart = ready && !state.busy && !currentMusicRoomActive
    var invite by rememberSaveable(account.revision) { mutableStateOf("") }
    var inputError by remember(account.revision) { mutableStateOf<String?>(null) }
    var clipboardMessage by remember { mutableStateOf<String?>(null) }
    var leaveConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(leaveConfirmation) { onDialogActive(leaveConfirmation) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    LaunchedEffect(state.active) { if (state.active) { invite = ""; inputError = null } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .padding(horizontal = 20.dp).testTag("netease_listen_screen")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            MusicTextAction("返回", onBack, Modifier.testTag("netease_listen_back"))
            Text("双人一起听", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = musicScrollPadding(PaddingValues(top = 12.dp, bottom = 20.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网易云音乐", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(when { account.stale -> "本机网易云登录已过期"; ready -> "已登录 · ${account.profile?.nickname?.ifBlank { "网易云用户" }}"; else -> "尚未登录本机网易云账号" },
                    Modifier.testTag("netease_listen_account"), fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                if (!ready) {
                    Text("登录后，与网易云音乐好友同步听同一首歌。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    if (onBinding != null) MusicTextAction(if (account.stale) "重新登录网易云" else "登录网易云", onBinding,
                        Modifier.testTag("netease_listen_login"))
                }
            } } }
            if (currentMusicRoomActive) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("正在使用 CurrentMusic 房间", fontWeight = FontWeight.SemiBold)
                Text("请先退出当前房间，再开启网易云双人一起听。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                MusicTextAction("返回房间", onBack)
            } } }
            state.error?.let { error -> item { Card(Modifier.fillMaxWidth().testTag("netease_listen_error")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(error, fontSize = 14.sp, color = colors.error)
                    Row { MusicTextAction("重试同步", actions.refresh, Modifier.testTag("netease_listen_retry"), enabled = ready && !state.busy)
                        MusicTextAction("知道了", actions.dismissError) }
                }
            } } }
            if (state.active) {
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val participants = state.room?.participants.orEmpty()
                    val pair = ListenTogetherPair(participants.map { ListenTogetherParticipant(it.userId, it.nickname, it.avatarUrl) })
                    ListeningPairArtwork(pair, Modifier.size(96.dp, 60.dp).align(Alignment.CenterHorizontally))
                    Text(if (participants.size >= 2) "正在一起听" else "等待好友加入", Modifier.align(Alignment.CenterHorizontally),
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(participants.joinToString(" · ") { it.nickname.ifBlank { "网易云用户" } }.ifBlank { account.profile?.nickname.orEmpty() },
                        Modifier.fillMaxWidth(), fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                    Text(when { state.busy -> "正在同步…"; state.connected -> "已连接 · 播放同步中"; else -> "连接中，正在恢复同步…" },
                        Modifier.testTag("netease_listen_connection"), fontSize = 13.sp, color = colors.primary)
                    val invitation = state.invitationUrl
                    Button(onClick = { if (invitation != null) scope.launch {
                        clipboardMessage = try { actions.copyInvitation(invitation); "邀请链接已复制" }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { "暂时无法复制，请重试" }
                    } },
                        enabled = !invitation.isNullOrBlank(), modifier = Modifier.fillMaxWidth().testTag("netease_listen_copy")) { Text("复制邀请链接") }
                    Text("将链接发给好友，即可在网易云音乐加入。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    clipboardMessage?.let { Text(it, fontSize = 12.sp, color = colors.primary) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MusicTextAction("刷新同步", actions.refresh, Modifier.testTag("netease_listen_refresh"), enabled = ready && !state.busy)
                        MusicTextAction("退出一起听", { leaveConfirmation = true }, Modifier.testTag("netease_listen_leave"), enabled = !state.busy, destructive = true)
                    }
                } } }
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("正在播放", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(playback.song?.name ?: "等待选择歌曲", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 20.sp)
                    playback.song?.artists?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
                    Text("同步队列 ${state.queueCount} 首", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    Row { MusicTextAction("选择歌曲", onSearch, Modifier.testTag("netease_listen_search"), enabled = !state.busy)
                        MusicTextAction("打开播放器", onPlayer, Modifier.testTag("netease_listen_player"), enabled = playback.song != null) }
                } } }
            } else {
                if (state.availableRoom != null) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("发现已有一起听会话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("恢复后在本机继续同步播放。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Button(onClick = actions.resume, enabled = canStart,
                        modifier = Modifier.fillMaxWidth().testTag("netease_listen_resume")) { Text("恢复一起听") }
                } } }
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("和好友听同一首歌", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("创建双人会话，复制邀请链接，与好友同步播放、暂停和切歌。", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                    Button(onClick = actions.create, enabled = canStart,
                        modifier = Modifier.fillMaxWidth().testTag("netease_listen_create")) { Text(if (state.busy) "正在处理…" else "创建一起听") }
                } } }
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("加入好友", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    TextField(invite, { invite = it; inputError = null; clipboardMessage = null }, label = "网易云一起听邀请链接",
                        modifier = Modifier.fillMaxWidth().testTag("netease_listen_invite"))
                    inputError?.let { Text(it, Modifier.testTag("netease_listen_invite_error"), fontSize = 12.sp, color = colors.error) }
                    clipboardMessage?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariantSummary) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MusicTextAction("粘贴邀请", {
                            scope.launch {
                                try {
                                    val value = actions.pasteInvitation()
                                    if (value.isNullOrBlank()) clipboardMessage = "剪贴板中没有邀请链接"
                                    else { invite = value; inputError = null; clipboardMessage = null }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { clipboardMessage = "暂时无法读取剪贴板，请手动粘贴" }
                            }
                        }, Modifier.testTag("netease_listen_paste"), enabled = !state.busy)
                        MusicTextAction("加入一起听", {
                            if (ListenTogetherInvitation.parse(invite) == null) inputError = "请粘贴有效的网易云一起听邀请链接"
                            else { focus.clearFocus(); actions.join(invite.trim()) }
                        }, Modifier.testTag("netease_listen_join"), enabled = canStart && invite.isNotBlank())
                    }
                    Text("请使用网易云音乐的邀请链接；CurrentMusic 六位房间号请返回房间页加入。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                } } }
                item { MusicTextAction(if (state.busy) "正在检查…" else "检查已有会话", actions.refresh,
                    Modifier.testTag("netease_listen_check"), enabled = ready && !state.busy) }
            }
        }
    }
    if (leaveConfirmation) MusicDialog("退出双人一起听", onDismiss = { leaveConfirmation = false }) {
        Text("退出将结束当前双人会话，并恢复本机播放队列。")
        MusicTextAction("取消", { leaveConfirmation = false }, Modifier.testTag("netease_listen_cancel_leave"))
        MusicTextAction("确认退出", { leaveConfirmation = false; actions.leave() },
            Modifier.testTag("netease_listen_confirm_leave"), destructive = true, enabled = !state.busy)
    }
}
