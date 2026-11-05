package io.github.currencortex.music.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.*
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
private fun <T> AppResult<T>.valueOrThrow(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw ApiException(kind, status, message)
}

/** Closing the form drops its scope, credentials, enrollment and recovery codes. */
@Composable internal fun LoginProtectionDialog(container: AppContainer, questionMode: Boolean, onDismiss: () -> Unit) {
    val repo = remember(container) { AccountSecurityRepository(container.apiClient, container.accountRepository) }
    val owner = remember(repo) { repo.owner() }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<LoginProtectionStatus?>(null) }
    var setup by remember { mutableStateOf<AuthenticatorEnrollment?>(null) }
    var recovery by remember { mutableStateOf<RecoveryCodes?>(null) }
    var password by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var remaining by remember { mutableLongStateOf(0L) }
    val window = LocalContext.current.activity()?.window
    DisposableEffect(window) {
        val alreadySecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(owner) {
        while (repo.isCurrent(owner)) { delay(500) }
        dismiss()
    }
    fun launch(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = (e as? ApiException)?.userMessage ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    fun load() = launch { status = repo.status(owner).valueOrThrow() }
    LaunchedEffect(owner) { load() }
    LaunchedEffect(setup) {
        while (setup != null) {
            remaining = setup?.remainingSeconds ?: 0
            if (remaining <= 0) { setup = null; code = ""; error = "绑定已过期，请重新开始"; break }
            delay(1000)
        }
    }
    fun close() {
        if (busy) return
        if (recovery != null && !saved) { error = "恢复码仅显示一次，请先保存并确认"; return }
        onDismiss()
    }
    SettingsUiScope {
        MusicDialog(if (questionMode) "登录密保" else "验证器", ::close) {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                .testTag("login_protection_form"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = status
                val enrollment = setup
                val codes = recovery
                when {
                    completed -> {
                        Text("安全设置已更新，其他设备需重新登录。")
                        MusicDialogButton("完成", onClick = ::close)
                    }
                    codes != null -> {
                        Text("验证器已开启。恢复码仅显示一次，每枚只能使用一次，请保存到安全的位置。")
                        SelectionContainer { Text(codes.codes.joinToString("\n"), Modifier.testTag("security_recovery_codes")) }
                        SettingsSwitch("我已安全保存恢复码", saved, { saved = it })
                        MusicDialogButton("完成", onClick = ::close, enabled = saved)
                    }
                    enrollment != null -> {
                        Text("扫描二维码，或在验证器中手动添加下方密钥。时间型 · SHA1 · 6 位 · 30 秒。")
                        val bitmap = remember(enrollment) {
                            val pixels = QRCodeWriter().encode(enrollment.uri, BarcodeFormat.QR_CODE, 512, 512,
                                mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
                            Bitmap.createBitmap(IntArray(512 * 512) { index ->
                                if (pixels[index % 512, index / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                            }, 512, 512, Bitmap.Config.ARGB_8888).asImageBitmap()
                        }
                        Image(bitmap, "验证器绑定二维码", Modifier.fillMaxWidth().height(200.dp))
                        SelectionContainer { Text(enrollment.secret, Modifier.testTag("security_totp_secret")) }
                        Text("绑定有效期 ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}")
                        TextField(code, { code = it.filter { char -> char in '0'..'9' }.take(6); error = null },
                            label = "6 位验证码", singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("security_totp_code"))
                        MusicDialogButton("确认开启", onClick = {
                            val submitted = code; code = ""
                            launch { recovery = repo.enable(enrollment, submitted).valueOrThrow(); setup = null }
                        }, enabled = !busy && code.length == 6 && remaining > 0, modifier = Modifier.testTag("security_enable_totp"))
                        MusicDialogButton("重新绑定", onClick = { setup = null; code = "" }, enabled = !busy)
                    }
                    current == null -> {
                        Text(if (busy) "正在读取安全设置…" else "无法读取安全设置")
                        if (!busy) MusicDialogButton("重试", onClick = ::load)
                    }
                    (questionMode && current.enabled) || (!questionMode && current.securityQuestionEnabled) -> {
                        Text(if (questionMode) "已开启验证器。请先关闭验证器，再设置密保问题。"
                            else "已开启登录密保。请先验证原答案并关闭密保，再绑定验证器。")
                        MusicDialogButton("返回", onClick = ::close)
                    }
                    else -> {
                        Text(if (questionMode) "密保与验证器只能选择一种开启。更换问题需先验证并关闭原密保。"
                            else if (current.enabled) "验证器已开启，还剩 ${current.recoveryCodesRemaining} 枚恢复码。"
                            else "确认当前密码后，使用验证器扫描二维码并输入验证码完成绑定。")
                        Text("开启或关闭后，其他设备需要重新登录，当前设备保留登录。")
                        SecretField(password, { password = it; error = null }, "当前密码", "security_password", !busy)
                        if (questionMode) {
                            if (current.securityQuestionEnabled) Text(current.securityQuestion)
                            else TextField(question, { question = it.take(100); error = null }, label = "自定义问题（4～100 字）",
                                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("security_question"))
                            SecretField(answer, { answer = it.take(128); error = null }, "密保答案", "security_answer", !busy)
                            if (!current.securityQuestionEnabled) SecretField(confirm, { confirm = it.take(128); error = null },
                                "再次输入答案", "security_answer_confirm", !busy)
                            MusicDialogButton(if (current.securityQuestionEnabled) "验证并关闭密保" else "开启登录密保", onClick = {
                                val pass = password; val response = answer; password = ""; answer = ""; confirm = ""
                                launch {
                                    if (current.securityQuestionEnabled) repo.disableQuestion(owner, pass, response).valueOrThrow()
                                    else repo.setQuestion(owner, pass, question, response).valueOrThrow()
                                    completed = true
                                }
                            }, enabled = !busy && password.isNotEmpty() && answer.isNotBlank() &&
                                (current.securityQuestionEnabled || (question.trim().length in 4..100 && answer.trim().length in 2..128 && answer == confirm)),
                                modifier = Modifier.testTag("security_question_submit"))
                        } else {
                            if (current.enabled) SecretField(code, { code = it.take(128); error = null },
                                "验证码或恢复码", "security_disable_code", !busy)
                            MusicDialogButton(if (current.enabled) "验证并关闭验证器" else "绑定验证器", onClick = {
                                val pass = password; val response = code; password = ""; code = ""
                                launch {
                                    if (current.enabled) { repo.disable(owner, pass, response).valueOrThrow(); completed = true }
                                    else { setup = repo.setup(owner, pass).valueOrThrow(); remaining = setup!!.remainingSeconds }
                                }
                            }, enabled = !busy && password.isNotEmpty() && (!current.enabled || code.isNotBlank()),
                                modifier = Modifier.testTag("security_totp_submit"))
                        }
                    }
                }
                error?.let { Text(it, color = MiuixTheme.colorScheme.error, modifier = Modifier.testTag("security_error")) }
                if (busy) Text("正在处理…")
            }
        }
    }
}

@Composable private fun SecretField(value: String, edit: (String) -> Unit, label: String, tag: String, enabled: Boolean) {
    TextField(value, edit, label = label, singleLine = true, enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}
