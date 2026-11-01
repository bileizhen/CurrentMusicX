package io.github.currencortex.music.feature.auth

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import kotlinx.coroutines.delay
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One transient form for public recovery and authenticated password changes. No secrets in saved state. */
@Composable internal fun PasswordDialog(recovery: Boolean, busy: Boolean, codeUntil: Long,
    onDismiss: () -> Unit, onSendCode: (String) -> Unit,
    onSubmit: (email: String, credential: String, password: String, usingCode: Boolean) -> Unit) {
    var usingCode by remember { mutableStateOf(recovery) }
    var email by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(codeUntil) {
        now = SystemClock.elapsedRealtime()
        while (now < codeUntil) { delay(1000); now = SystemClock.elapsedRealtime() }
    }
    SettingsUiScope {
    MusicDialog(if (recovery) "找回密码" else "修改密码", { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!recovery) SettingsSegmentedControl(listOf("原密码验证", "邮箱验证"), if (usingCode) 1 else 0, {
            usingCode = it == 1; credential = ""
        }, enabled = !busy, tag = "password_verification",
            itemTag = { if (it == 1) "password_email_mode" else "password_old_mode" })
        if (recovery) TextField(email, { email = it }, label = "已注册的邮箱", singleLine = true,
            enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("password_email"))
        if (usingCode) {
            Text(if (recovery) "验证码 5 分钟内有效。重置后需重新登录，其他设备也会退出。"
                else "验证码发送到当前账号绑定的邮箱。修改后保留本机登录，其他设备下线。",
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(credential, { credential = it }, label = "邮箱验证码", singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f).testTag("password_code"))
                GlassActionButton(if (now < codeUntil) "${(codeUntil - now + 999) / 1000}s" else "获取验证码",
                    onClick = { onSendCode(email.trim()) },
                    enabled = !busy && now >= codeUntil && (!recovery || email.isNotBlank()),
                    modifier = Modifier.testTag("password_send_code"), fillWidth = false)
            }
        } else TextField(credential, { credential = it }, label = "当前密码", singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("password_old"))
        TextField(password, { password = it }, label = "新密码（6～64 位）", singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("password_new"))
        TextField(confirm, { confirm = it }, label = "再次输入新密码", singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("password_confirm"))
        if (confirm.isNotEmpty() && confirm != password) Text("两次输入的密码不一致", fontSize = 12.sp,
            color = MiuixTheme.colorScheme.error, modifier = Modifier.testTag("password_mismatch"))
        MusicDialogButton(if (busy) "处理中…" else if (recovery) "重置密码" else "更新密码",
            onClick = { onSubmit(email.trim(), credential, password, usingCode) },
            enabled = !busy && (!recovery || email.isNotBlank()) && credential.isNotBlank() &&
                password.length in 6..64 && password == confirm,
            modifier = Modifier.testTag("password_submit"))
        }
    }
}
}
