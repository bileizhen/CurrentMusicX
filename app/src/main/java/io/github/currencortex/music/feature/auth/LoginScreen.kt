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
import android.os.SystemClock
import kotlinx.coroutines.delay
import io.github.currencortex.music.data.auth.RegistrationInput

@Composable fun LoginScreen(vm: AuthViewModel, addingAccount: Boolean = false, onDone: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    // Password is intentionally not saved in SavedState or a persistent store.
    var password by remember { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val deadline by vm.codeUntil.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) { while (now < deadline) { delay(1000); now = SystemClock.elapsedRealtime() } }
    LaunchedEffect(state.message) { if (addingAccount && state.message in listOf("登录成功", "注册成功")) onDone() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("login_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", fontSize = 32.sp)
        account.account?.takeIf { !addingAccount }?.let {
            Text(it.nickname, fontSize = 24.sp)
            TextButton("退出登录", onClick = { vm.logout() }, enabled = !state.loading)
        } ?: run {
            Text(if (register) "注册 CurrentMusic" else "登录 CurrentMusic（用户名或手机号）")
            Text("用户名")
            TextField(username, { username = it }, singleLine = true, modifier = Modifier.testTag("login_username"))
            Text("密码")
            TextField(password, { password = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.testTag("login_password"))
            if (register) {
                Text("昵称"); TextField(nickname, { nickname = it }, singleLine = true)
                Row { TextButton("邮箱验证", onClick = { phone = false; contact = ""; code = "" }); TextButton("手机验证", onClick = { phone = true; contact = ""; code = "" }) }
                Text(if (phone) "手机号" else "邮箱"); TextField(contact, { contact = it }, singleLine = true, modifier = Modifier.testTag("register_contact"))
                Text("验证码"); TextField(code, { code = it }, singleLine = true)
                TextButton(if (deadline > now) "${(deadline - now + 999) / 1000}s 后重发" else "获取验证码", onClick = { vm.code(contact, phone) }, enabled = contact.isNotBlank() && !state.loading && now >= deadline)
                TextButton("注册", enabled = username.isNotBlank() && password.length >= 6 && contact.isNotBlank() && code.isNotBlank() && !state.loading,
                    onClick = { vm.register(RegistrationInput(username, password, nickname, contact, code, phone)); password = ""; code = "" })
            } else TextButton("登录", enabled = username.isNotBlank() && password.isNotBlank() && !state.loading,
                    onClick = { vm.login(username, password); password = "" }, modifier = Modifier.testTag("login_submit"))
            TextButton(if (register) "返回登录" else "注册新账号", onClick = { register = !register; password = ""; code = "" }, enabled = !state.loading)
        }
        if (state.loading || account.loading) Text("正在验证会话…")
        (state.message ?: account.error)?.let { Text(it) }
        if (account.offline) { Text("当前离线，会话凭据已保留"); TextButton("重新连接", onClick = { vm.retry() }) }
    }
}
