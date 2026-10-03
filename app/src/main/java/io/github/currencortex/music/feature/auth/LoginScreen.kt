package io.github.currencortex.music.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.*

@Composable fun LoginScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    // Password is intentionally not saved in SavedState or a persistent store.
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("login_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", fontSize = 32.sp)
        account.account?.let {
            Text(it.nickname, fontSize = 24.sp)
            TextButton("退出登录", onClick = { vm.logout() }, enabled = !state.loading)
        } ?: run {
            Text("登录 CurrentMusic")
            TextField(username, { username = it }, singleLine = true, modifier = Modifier.testTag("login_username"))
            TextField(password, { password = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.testTag("login_password"))
            TextButton("登录", enabled = username.isNotBlank() && password.isNotBlank() && !state.loading,
                onClick = { vm.login(username, password); password = "" }, modifier = Modifier.testTag("login_submit"))
        }
        if (state.loading || account.loading) Text("正在验证会话…")
        (state.message ?: account.error)?.let { Text(it) }
        if (account.offline) { Text("当前离线，会话凭据已保留"); TextButton("重新连接", onClick = { vm.retry() }) }
    }
}
