package io.github.currencortex.music.feature.auth

import io.github.currencortex.music.R
import io.github.currencortex.music.ui.component.musicScrollPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    var revealPassword by remember { mutableStateOf(false) }
    val deadline by vm.codeUntil.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) { while (now < deadline) { delay(1000); now = SystemClock.elapsedRealtime() } }
    LaunchedEffect(state.message) { if (addingAccount && state.message in listOf("登录成功", "注册成功")) onDone() }
    // Entering the page starts a clean attempt: a stale error must not greet the user.
    LaunchedEffect(Unit) { vm.clearMessage() }
    // Editing any field clears the previous outcome, so the message never describes stale input.
    fun edit(block: () -> Unit) { block(); if (state.message != null) vm.clearMessage() }
    val colors = MiuixTheme.colorScheme
    val secondary = colors.onSurfaceVariantSummary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(musicScrollPadding()).testTag("login_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("我的", fontSize = 32.sp)
        account.account?.takeIf { !addingAccount }?.let {
            Text(it.nickname, fontSize = 24.sp)
            TextButton("退出登录", onClick = { vm.logout() }, enabled = !state.loading)
        } ?: run {
            val canLogin = username.isNotBlank() && password.isNotBlank() && !state.loading
            val canRegister = username.isNotBlank() && password.length >= 6 && contact.isNotBlank() &&
                code.isNotBlank() && !state.loading
            fun submit() { vm.login(username, password); password = "" }
            Card(Modifier.fillMaxWidth().testTag(if (register) "register_card" else "login_card"),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The mode switch lives in the header so it stays visible without scrolling.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (register) "注册 CurrentMusic" else "登录 CurrentMusic", Modifier.weight(1f),
                            fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Text(if (register) "返回登录" else "注册新账号",
                            Modifier.clickable(role = Role.Button, enabled = !state.loading) {
                                register = !register; password = ""; code = ""; revealPassword = false
                            }.padding(horizontal = 6.dp, vertical = 8.dp).testTag("login_switch_mode"),
                            fontSize = 14.sp, color = colors.primary)
                    }
                    Text(if (register) "注册后可同步我喜欢与歌单。" else "用户名或手机号均可。", fontSize = 13.sp, color = secondary)
                    // One label per field, keyboard hints and a reveal toggle replace the bare rows.
                    TextField(username, { edit { username = it } }, singleLine = true, label = "用户名",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().testTag("login_username"))
                    TextField(password, { edit { password = it } }, singleLine = true, label = "密码",
                        visualTransformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canLogin) submit() }),
                        trailingIcon = {
                            Image(painterResource(if (revealPassword) R.drawable.auth_password_hidden else R.drawable.auth_password_visible),
                                contentDescription = if (revealPassword) "隐藏密码" else "显示密码",
                                Modifier.size(20.dp).clickable(role = Role.Button,
                                    interactionSource = remember { MutableInteractionSource() }, indication = null) { revealPassword = !revealPassword }
                                    .testTag("login_password_toggle"),
                                colorFilter = ColorFilter.tint(secondary))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("login_password"))
                    // Registration needs a 6-character password; say so instead of only greying the button.
                    if (register && password.isNotEmpty() && password.length < 6)
                        Text("密码至少 6 位（当前 ${password.length} 位）", fontSize = 12.sp,
                            color = colors.error, modifier = Modifier.testTag("register_password_hint"))
                    if (register) {
                        TextField(nickname, { edit { nickname = it } }, singleLine = true, label = "昵称（可选）",
                            modifier = Modifier.fillMaxWidth().testTag("register_nickname"))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(false to "邮箱验证", true to "手机验证").forEach { (isPhone, label) ->
                                TextButton((if (phone == isPhone) "✓ " else "") + label,
                                    onClick = { phone = isPhone; contact = ""; code = "" },
                                    enabled = !state.loading, modifier = Modifier.weight(1f))
                            }
                        }
                        TextField(contact, { edit { contact = it } }, singleLine = true,
                            label = if (phone) "手机号" else "邮箱",
                            keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().testTag("register_contact"))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(code, { edit { code = it } }, singleLine = true, label = "验证码",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (canRegister) {
                                    vm.register(RegistrationInput(username, password, nickname, contact, code, phone)); password = ""; code = ""
                                } }),
                                modifier = Modifier.weight(1f).testTag("register_code"))
                            TextButton(if (deadline > now) "${(deadline - now + 999) / 1000}s" else "获取验证码",
                                onClick = { vm.code(contact, phone) },
                                enabled = contact.isNotBlank() && !state.loading && now >= deadline,
                                modifier = Modifier.testTag("register_send_code"))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Button(onClick = {
                        if (register) { vm.register(RegistrationInput(username, password, nickname, contact, code, phone)); password = ""; code = "" }
                        else submit()
                    }, enabled = if (register) canRegister else canLogin,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth().testTag("login_submit")) {
                        Text(if (state.loading) (if (register) "正在注册…" else "正在登录…") else (if (register) "注册" else "登录"),
                            fontSize = 16.sp)
                    }
                }
            }
        }
        if (account.loading) Text("正在验证会话…", fontSize = 13.sp, color = secondary)
        (state.message ?: account.error)?.let {
            val failed = account.error != null || (state.message != null && !state.message!!.contains("成功"))
            Text(it, fontSize = 13.sp, color = if (failed) colors.error else secondary, modifier = Modifier.testTag("login_message"))
        }
        if (account.offline) {
            Text("当前离线，会话凭据已保留", fontSize = 13.sp, color = secondary)
            TextButton("重新连接", onClick = { vm.retry() })
        }
    }
}
