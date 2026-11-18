package io.github.currencortex.music.feature.qqmusic

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.qqmusic.QqLoginCapabilities
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.currencortex.music.feature.binding.BindingAction

@Composable internal fun QqAccountScreen(container: AppContainer, onBack: () -> Unit, navigate: (String) -> Unit) {
    val repository = container.qqMusic
    val account by repository.state.collectAsStateWithLifecycle()
    val project by container.accountRepository.state.collectAsStateWithLifecycle()
    val revision by container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pageActive = LocalMusicPageActive.current
    var channel by rememberSaveable { mutableStateOf("") }
    var capabilities by remember { mutableStateOf<QqLoginCapabilities?>(null) }
    var channelsLoading by remember { mutableStateOf(false) }
    var channelsError by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var qrImage by remember { mutableStateOf("") }
    var qrStatus by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loginRequested by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var libraryCount by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var statusChecked by remember { mutableStateOf(false) }
    var vipLevel by remember { mutableStateOf("") }
    val canLogin = repository.directMode
    val qrSupported = capabilities?.let { value ->
        listOf("auth.getQrKey", "auth.createQr", "auth.checkQr").all { value.features[it] != false }
    } == true
    val availableChannels = if (qrSupported) capabilities?.availableChannels.orEmpty() else emptyList()
    fun action(success: String? = null, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                when (val result = appResult(block)) {
                    is AppResult.Success -> notice = success
                    is AppResult.Failure -> error = result.message
                }
            } finally { busy = false }
        }
    }
    LaunchedEffect(settings.server) {
        channelsLoading = true
        channelsError = null
        when (val result = appResult { repository.channels() }) {
            is AppResult.Success -> {
                capabilities = result.value
                val available = result.value.availableChannels
                if (available.none { it.id == channel }) channel = result.value.defaultChannelId.orEmpty()
                if (channel.isBlank()) loginRequested = false
            }
            is AppResult.Failure -> {
                capabilities = null
                channel = ""
                loginRequested = false
                channelsError = result.message
            }
        }
        channelsLoading = false
    }
    LaunchedEffect(revision, settings.server, account.loggedIn) {
        error = null
        if (!account.loggedIn) return@LaunchedEffect
        when (val result = appResult { repository.status() }) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> if (canLogin) error = result.message
        }
        if (account.loggedIn && account.avatar.isBlank()) appResult { repository.profile() }
        vipLevel = when (val result = appResult { repository.vipLevel() }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> "unknown"
        }
    }
    LaunchedEffect(revision, settings.server, account.revision, account.loggedIn) {
        libraryCount = 0
        if (!account.loggedIn) return@LaunchedEffect
        when (val result = appResult { repository.playlists().size }) {
            is AppResult.Success -> libraryCount = result.value
            is AppResult.Failure -> Unit
        }
    }
    // Keep the QR session alive while the user briefly switches to the scanner app.
    LaunchedEffect(pageActive, canLogin, revision, settings.server, generation, channel, loginRequested) {
        qrImage = ""
        if (!loginRequested) qrStatus = ""
        if (!canLogin || !pageActive || !loginRequested || channel.isBlank()) return@LaunchedEffect
        var key: String? = null
        try {
            error = null
            qrStatus = "正在获取二维码…"
            val result = appResult {
                val current = repository.qrKey(channel)
                key = current
                qrImage = repository.qrImage(current)
                val scanHint = if (channel == "qqmusic") "请用 QQ 音乐 App 扫码并确认登录"
                    else "请用手机 ${channelLabel(channel)} App 扫码并确认登录"
                qrStatus = scanHint
                val terminalStatus = withTimeoutOrNull(180_000) {
                    var status: Int
                    do {
                        delay(2_000)
                        status = repository.qrCheck(current)
                        when (status) {
                            801 -> qrStatus = scanHint
                            802 -> qrStatus = "已扫码，请在手机上确认"
                        }
                    } while (status == 801 || status == 802)
                    status
                }
                qrImage = ""
                when (terminalStatus) {
                    803 -> {
                        qrStatus = "登录成功"
                        loginRequested = false
                    }
                    804 -> qrStatus = "已取消授权，点击重新获取"
                    null, 800 -> qrStatus = "二维码已过期，点击重新获取"
                    else -> qrStatus = "登录状态异常，点击重新获取"
                }
            }
            when (result) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> {
                    error = result.message
                    qrStatus = if (qrImage.isBlank()) "二维码获取失败，请重试" else "扫码登录失败，请重试"
                    qrImage = ""
                }
            }
        } finally {
            key?.let { pending -> withContext(NonCancellable) { withTimeoutOrNull(3_000) { appResult { repository.qrCancel(pending) } } } }
        }
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, qrImage) {
        value = if (qrImage.startsWith("data:image/")) withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(qrImage.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        } else null
    }
    MusicSubpage("QQ 音乐账号", "qq_account", onBack) { inset ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = musicScrollPadding(PaddingValues(16.dp, inset + 8.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (canLogin) item {
                SettingsGroup(Modifier.testTag("qq_project_sync")) {
                    SettingsPreference("QQ 音乐直连", "登录、搜索和播放请求直接发送至腾讯 QQ 音乐，不经 CurrentMusic 服务中转。")
                    SettingsDivider()
                    SettingsPreference(if (account.loggedIn) "本机登录态已保存" else "尚未登录 QQ 音乐",
                        trailing = { if (account.loggedIn) SettingsSelectionCheck(true) })
                }
            }
            if (account.loggedIn) item {
                SettingsGroup(Modifier.testTag("qq_account_card")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (account.avatar.isNotBlank()) AsyncImage(account.avatar, "QQ 音乐头像",
                                Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            else MusicCover("", Modifier.size(48.dp).clip(CircleShape))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(account.nickname.ifBlank { "QQ 音乐" }, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("QQ 音乐账号 · ${account.uin}", fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                            }
                            Text("已登录", Modifier.clip(RoundedCornerShape(50))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = .08f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                        }
                        Text("已直接连接 QQ 音乐我喜欢与歌单${if (libraryCount > 0) " · $libraryCount 个歌单" else ""}",
                            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                        if (vipLevel.isNotBlank()) Text(when (vipLevel) {
                            "super" -> "超级会员"
                            "green" -> "绿钻会员"
                            "free" -> "免费账号"
                            else -> "会员状态暂无法确认"
                        }, Modifier.clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF9A7529).copy(alpha = .10f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF9A7529))
                        GlassActionButton("刷新 QQ 音乐库", {
                            action("QQ 音乐库已刷新") {
                                libraryCount = repository.playlists().size
                                repository.likedSongs(fresh = true)
                            }
                        }, Modifier.fillMaxWidth().testTag("refresh_qq_library"), primary = true, enabled = !busy)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BindingAction("检查登录态", {
                                statusChecked = true
                                action("QQ 音乐登录态正常") { repository.status() }
                            }, Modifier.weight(1f), !busy, fillWidth = true)
                            BindingAction("续期登录态", {
                                action("QQ 音乐登录会话已续期") { repository.refresh() }
                            }, Modifier.weight(1f), !busy, fillWidth = true)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BindingAction("重新登录", { loginRequested = true; generation++ },
                                Modifier.weight(1f).testTag("relogin_qq"), !busy, fillWidth = true)
                            BindingAction("退出", { confirmLogout = true },
                                Modifier.weight(1f).testTag("logout_qq"), !busy, destructive = true, fillWidth = true)
                        }
                    }
                }
            }
            error?.let { item { SettingsHint(it) } }
            notice?.let { item { SettingsHint(it) } }
            if (canLogin && (!account.loggedIn || loginRequested)) item {
                SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when {
                            channelsLoading -> Text("正在读取可用登录方式…", fontSize = 14.sp)
                            channelsError != null -> SettingsHint(channelsError.orEmpty(), error = true)
                            availableChannels.isEmpty() -> SettingsHint(
                                if (capabilities?.configured == false)
                                    "当前服务器未配置 QQ 音乐登录，请联系服务管理员。"
                                else "当前服务器没有可用的扫码登录方式，请联系服务管理员。",
                                error = true,
                            )
                            else -> {
                                if (availableChannels.size > 1) {
                                    MusicCategoryTabs(
                                        availableChannels.map { it.id to channelLabel(it.id) },
                                        channel,
                                        { channel = it; generation++ },
                                        "qq_login_channel",
                                    )
                                }
                                if (!loginRequested) GlassActionButton(
                                    "获取登录二维码",
                                    { loginRequested = true; generation++ },
                                    primary = true,
                                    enabled = channel.isNotBlank(),
                                )
                                bitmap?.let {
                                    Image(it.asImageBitmap(), "QQ 音乐登录二维码", Modifier.size(232.dp).testTag("qq_login_qr"))
                                }
                                if (bitmap == null && qrImage.isNotBlank() && !qrImage.startsWith("data:"))
                                    AsyncImage(qrImage, "QQ 音乐登录二维码", Modifier.size(232.dp))
                                if (qrStatus.isNotBlank()) Text(qrStatus, fontSize = 14.sp)
                                if (loginRequested) MusicTextAction("重新获取二维码", { generation++ })
                                SettingsHint("扫码授权只保存在本机，播放与收藏直接使用你自己的 QQ 音乐权限。")
                            }
                        }
                    }
                }
            }
            item { SettingsHint("切换音乐平台会保留两边的账号与收藏。QQ 音乐会员及地区限制以账号实际权限为准。") }
        }
    }
    if (confirmLogout) MusicDialog("退出 QQ 音乐？", { confirmLogout = false }) {
        Text("下次使用个人音乐库时需要重新扫码登录。")
        MusicTextAction("退出登录", {
            confirmLogout = false
            loginRequested = false
            action { repository.logout() }
        }, destructive = true, enabled = !busy)
    }
}

private fun channelLabel(channel: String): String = when (channel) {
    "qq" -> "QQ"
    "wechat" -> "微信"
    "qqmusic" -> "QQ 音乐"
    else -> channel
}
