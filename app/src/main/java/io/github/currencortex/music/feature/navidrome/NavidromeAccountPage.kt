package io.github.currencortex.music.feature.navidrome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun NavidromeAccountPage(vm: NavidromeViewModel, onConnected: () -> Unit, onDisconnect: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.actionError.collectAsStateWithLifecycle()
    var edit by rememberSaveable(account.revision) { mutableStateOf(!account.configured) }
    var server by rememberSaveable(account.server) { mutableStateOf(account.server) }
    var username by rememberSaveable(account.username) { mutableStateOf(account.username) }
    // Never persist the password into saved state, URLs, test diagnostics or logs.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canConnect = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
    fun connect() { if (canConnect) { focus.clearFocus(); keyboard?.hide(); vm.connect(server, username, password) { password = ""; visible = false; onConnected() } } }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (account.configured) "你的私人音乐库" else "连接自己的音乐服务器", fontSize = 20.sp)
            NavHint("兼容 Navidrome / Subsonic，歌曲原音质直连播放，账号加密保存在本机。")
        } }
        if (account.configured) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(account.username, fontSize = 22.sp)
                NavHint(account.server)
                if (account.version.isNotBlank()) NavHint("Navidrome ${account.version}")
            } } }
            item { Card(Modifier.fillMaxWidth()) {
                SettingsSwitch("用作首页曲库", account.home, vm::home, summary = "首页直接浏览自建曲库；关闭后恢复原首页", enabled = !busy)
                MusicDestinationRow(if (edit) "取消更换账号" else "更换服务器或账号", {
                    server = account.server; username = account.username
                    edit = !edit; password = ""; visible = false; vm.clearError(); focus.clearFocus(); keyboard?.hide()
                }, enabled = !busy, modifier = Modifier.testTag("navidrome_edit_account"), chevron = false)
            } }
        }
        if (edit) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(server, { server = it; vm.clearError() }, label = "服务器地址", singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), modifier = Modifier.testTag("navidrome_server"))
            TextField(username, { username = it; vm.clearError() }, label = "用户名", singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.testTag("navidrome_username"))
            TextField(password, { password = it; vm.clearError() }, label = "密码", singleLine = true, enabled = !busy,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { connect() }), modifier = Modifier.testTag("navidrome_password"))
            MusicTextAction(if (visible) "隐藏密码" else "显示密码", { visible = !visible }, enabled = !busy)
            error?.let { Text(it, color = MiuixTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.testTag("navidrome_account_error")) }
            TextButton(if (busy) "正在连接…" else if (account.configured) "确认更换" else "连接服务器", ::connect,
                enabled = canConnect, modifier = Modifier.fillMaxWidth().testTag("navidrome_connect"))
        } } }
        if (!edit) error?.let { item { Text(it, color = MiuixTheme.colorScheme.error, fontSize = 13.sp) } }
        if (account.configured) item { MusicDestinationRow("断开连接", onDisconnect, enabled = !busy, chevron = false,
            summary = "仅退出此服务器，不影响其他音乐账号", modifier = Modifier.testTag("navidrome_disconnect")) }
    }
}
