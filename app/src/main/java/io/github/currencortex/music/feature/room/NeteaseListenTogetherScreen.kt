package io.github.currencortex.music.feature.room

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import io.github.currencortex.music.feature.player.NeteaseSongsCollectionDialog
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
    val island by container.desktopLyricsStatus.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val islandTop = island.y + island.contentTop
    val islandClearance = if (island.visible && islandTop < statusTop + with(density) { 96.dp.roundToPx() }) {
        if (island.contentHeight > 0) with(density) { (islandTop + island.contentHeight - statusTop).coerceAtLeast(0).toDp() } + 12.dp
        else 48.dp
    } else 0.dp
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archiveController = remember(container, account.revision) {
        ListenTogetherHistoryController(container.listenTogetherHistory, scope, container.musicRepository::details)
    }
    val archive by archiveController.state.collectAsStateWithLifecycle()
    var invitePeer by remember(account.revision) { mutableStateOf<io.github.currencortex.music.data.netease.listen.ListenTogetherParticipant?>(null) }
    var shareJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(archiveController) { onDispose { archiveController.close(); shareJob?.cancel() } }
    LaunchedEffect(archive.page, archive.reportRoomId) { shareJob?.cancel() }
    LaunchedEffect(state.endedRoom?.roomId, state.endedOwnerUid, account.revision) {
        state.endedRoom?.takeIf { state.endedOwnerUid == account.profile?.uid }?.let { ended ->
            archiveController.report(ended.roomId, ended)
            controller.dismissEndedRoom()
        }
    }
    LaunchedEffect(controller, account.revision) {
        if (account.loggedIn && !account.stale && (account.profile?.uid ?: 0) > 0) controller.refresh()
    }
    if (archive.page != null) {
        val collectionShown = !archive.collectionSongs.isNullOrEmpty()
        LaunchedEffect(invitePeer, collectionShown) { onDialogActive(invitePeer != null || collectionShown) }
        DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
        val back = { if (archive.page == ListenArchivePage.HISTORY) archiveController.close() else archiveController.history() }
        BackHandler(onBack = back)
        ListenTogetherHistoryContent(archive, onBack = back, onHistory = archiveController::history,
            onRanking = archiveController::ranking, onReport = { roomId ->
                val knownPeer = archive.history?.records?.firstOrNull { it.roomId == roomId }?.user
                val knownRoom = io.github.currencortex.music.data.netease.listen.ListenTogetherRoom(roomId,
                    archive.self?.userId ?: 0, listOfNotNull(archive.self, knownPeer))
                archiveController.report(roomId, knownRoom)
            },
            onMore = archiveController::more, onRetry = archiveController::retry,
            onInvite = { if (!state.active) invitePeer = it },
            onShare = { report -> if (shareJob?.isActive != true) {
                Toast.makeText(context, "正在生成报告…", Toast.LENGTH_SHORT).show()
                shareJob = scope.launch {
                    try {
                        val file = ListenTogetherReportSharing.create(context, report)
                        context.startActivity(Intent.createChooser(ListenTogetherReportSharing.intent(context, report, file), "分享一起听报告"))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { Toast.makeText(context, "暂时无法分享报告，请重试", Toast.LENGTH_SHORT).show() }
                }
            } },
            onCollect = archiveController::collectSongs,
            onOfficial = { report -> try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.officialUrl()))) }
                catch (_: Exception) { Toast.makeText(context, "无法打开官方报告", Toast.LENGTH_SHORT).show() } },
            topClearance = islandClearance, activeRoom = state.active)
        NeteaseSongsCollectionDialog(archive.collectionSongs.orEmpty(), collectionShown,
            archiveController::dismissCollection,
            loadPlaylists = { container.neteaseLibrary.playlists(fresh = true).filter { it.nativeOwned } },
            collect = { id, songs -> container.neteaseLibrary.addAll(id, songs); Unit })
        invitePeer?.let { peer -> MusicDialog("再次一起听", onDismiss = { invitePeer = null }) {
            Text("创建新的双人会话后，可复制邀请链接发给${peer.nickname.ifBlank { "好友" }}。")
            MusicTextAction("取消", { invitePeer = null })
            MusicTextAction("创建一起听", { invitePeer = null; archiveController.close(); controller.create() }, enabled = !state.active && !state.busy)
        } }
        return
    }
    NeteaseListenTogetherContent(state, account, playback,
        actions = NeteaseListenTogetherActions(controller::create, controller::join, controller::resume,
            controller::refresh, controller::leave, controller::dismissError,
            copyInvitation = { url -> clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("网易云一起听邀请", url))) },
            pasteInvitation = { clipboard.getClipEntry()?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
            } }),
        onBack = onBack, onSearch = onSearch, onPlayer = onPlayer, onBinding = onBinding,
        currentMusicRoomActive = cmRoom.detail != null, onDialogActive = onDialogActive, topClearance = islandClearance,
        onHistory = { archiveController.history() })
}

@Composable internal fun NeteaseListenTogetherContent(state: NeteaseListenTogetherState,
    account: NeteaseAccountState, playback: PlayerState, actions: NeteaseListenTogetherActions,
    onBack: () -> Unit, onSearch: () -> Unit, onPlayer: () -> Unit,
    onBinding: (() -> Unit)? = null, currentMusicRoomActive: Boolean = false,
    onDialogActive: (Boolean) -> Unit = {}, topClearance: Dp = 0.dp, onHistory: (() -> Unit)? = null) {
    val colors = MiuixTheme.colorScheme
    val ready = account.loggedIn && !account.stale && (account.profile?.uid ?: 0) > 0
    val canStart = ready && !state.busy && !currentMusicRoomActive
    val showAccountCard = !state.active || !ready
    val accountSummary = when { account.stale -> "本机网易云登录已过期"; ready -> "已登录 · ${account.profile?.nickname?.ifBlank { "网易云用户" }}"; else -> "尚未登录本机网易云账号" }
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
        Spacer(Modifier.height(topClearance))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainer)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack).testTag("netease_listen_back"), contentAlignment = Alignment.Center) {
                Icon(MusicIcons.Back, "返回", Modifier.size(22.dp), tint = colors.onSurface)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("双人一起听", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(if (showAccountCard) "网易云音乐" else "网易云音乐 · $accountSummary",
                    if (showAccountCard) Modifier else Modifier.testTag("netease_listen_account"),
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (ready && onHistory != null) MusicTextAction("记录", onHistory, Modifier.testTag("netease_listen_history"))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = musicScrollPadding(PaddingValues(top = 8.dp, bottom = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showAccountCard) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网易云账号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(accountSummary, Modifier.testTag("netease_listen_account"), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
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
                item { Card(Modifier.fillMaxWidth().testTag("netease_listen_session")) { Column(Modifier.padding(20.dp)) {
                    val participants = state.room?.participants.orEmpty()
                    val pair = ListenTogetherPair(participants.map { ListenTogetherParticipant(it.userId, it.nickname, it.avatarUrl) })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ListeningPairArtwork(pair, Modifier.size(84.dp, 52.dp))
                        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (participants.size >= 2) "正在一起听" else "等待好友加入", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(participants.joinToString(" · ") { it.nickname.ifBlank { "网易云用户" } }.ifBlank { account.profile?.nickname.orEmpty() },
                                fontSize = 13.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(5.dp).background(colors.primary, CircleShape))
                                Text(when { state.busy -> "正在同步…"; state.connected -> "已连接 · 播放同步中"; else -> "正在恢复同步…" },
                                    Modifier.testTag("netease_listen_connection"), fontSize = 11.sp, color = colors.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    val invitation = state.invitationUrl
                    Button(onClick = { if (invitation != null) scope.launch {
                        clipboardMessage = try { actions.copyInvitation(invitation); "邀请链接已复制" }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { "暂时无法复制，请重试" }
                    } },
                        enabled = !invitation.isNullOrBlank(), colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("netease_listen_copy")) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(MusicIcons.Copy, null, Modifier.size(18.dp), tint = colors.onPrimary)
                            Text("复制邀请链接", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary)
                        }
                    }
                    Text(clipboardMessage ?: "好友可在网易云音乐打开链接加入。", Modifier.fillMaxWidth().padding(top = 8.dp),
                        fontSize = 11.sp, color = if (clipboardMessage == null) colors.onSurfaceVariantSummary else colors.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ListenPageAction("刷新同步", MusicIcons.Update, actions.refresh, Modifier.weight(1f).testTag("netease_listen_refresh"), enabled = ready && !state.busy)
                        ListenPageAction("退出一起听", MusicIcons.Close, { leaveConfirmation = true }, Modifier.weight(1f).testTag("netease_listen_leave"), enabled = !state.busy, destructive = true)
                    }
                } } }
                item { Card(Modifier.fillMaxWidth().testTag("netease_listen_now_playing")) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MusicCover(playback.song?.cover.orEmpty(), Modifier.size(56.dp), cornerRadius = 12.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("正在播放", Modifier.weight(1f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                                Text("${state.queueCount} 首", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                            }
                            Text(playback.song?.name ?: "等待选择歌曲", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            playback.song?.artists?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariantSummary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ListenPageAction("选择歌曲", MusicIcons.Search, onSearch, Modifier.weight(1f).testTag("netease_listen_search"), enabled = !state.busy)
                        ListenPageAction("打开播放器", MusicIcons.Headphones, onPlayer, Modifier.weight(1f).testTag("netease_listen_player"), enabled = playback.song != null)
                    }
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
                            if (!ListenTogetherInvitation.isSupported(invite)) inputError = "请粘贴有效的网易云一起听邀请链接"
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

@Composable private fun ListenPageAction(label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val ink = (if (destructive) colors.error else colors.onSurface).copy(alpha = if (enabled) 1f else .4f)
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer)
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = ink)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
