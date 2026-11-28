package io.github.currencortex.music.feature.kugou

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.kugou.KugouLoginOutcome
import io.github.currencortex.music.data.kugou.KugouMultiAccountOption
import io.github.currencortex.music.data.kugou.KugouQrSession
import io.github.currencortex.music.data.kugou.KugouQrStatus
import io.github.currencortex.music.data.kugou.KugouVipInfo
import io.github.currencortex.music.feature.binding.qrPixels
import io.github.currencortex.music.ui.component.DecoratedAvatar
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.SpringLazyColumn
import io.github.currencortex.music.ui.component.nativeui.GlassActionButton
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
import io.github.currencortex.music.ui.component.nativeui.SettingsScrollLayout
import io.github.currencortex.music.ui.component.nativeui.SettingsSegmentedControl
import io.github.currencortex.music.ui.component.nativeui.SettingsTopBar
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val QR_SIZE = 512
private const val QR_LIFETIME_MS = 180_000L
private const val SMS_COOLDOWN_MS = 60_000L

private enum class KugouLoginTab(val label: String) {
    QR("扫码"),
    SMS("验证码"),
    PASSWORD("密码"),
    TOKEN("Token"),
}

/**
 * KuGou account screen supporting QR scan (`login-user.kugou.com`), SMS verification code
 * (`/v8/send_mobile_code` + `/v7/login_by_verifycode`), account/phone password (`/v9/login_by_pwd`),
 * and direct UID + Token verification.
 */
@Composable
internal fun KugouAccountScreen(container: AppContainer, onBack: (() -> Unit)?) {
    val repository = container.kugou
    val account by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val bottom = LocalMusicBottomInset.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun finishInput() { focus.clearFocus(); keyboard?.hide() }

    var tabIndex by rememberSaveable { mutableIntStateOf(KugouLoginTab.SMS.ordinal) }
    var showRelogin by rememberSaveable(account.revision) { mutableStateOf(false) }
    var session by remember { mutableStateOf<KugouQrSession?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var vip by remember(account.revision) { mutableStateOf<KugouVipInfo?>(null) }

    // SMS login state
    var countryCode by rememberSaveable { mutableStateOf("86") }
    var phone by rememberSaveable { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var smsCooldownUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var multiAccounts by remember { mutableStateOf<List<KugouMultiAccountOption>>(emptyList()) }

    // Password login state
    var pwdAccount by rememberSaveable { mutableStateOf("") }
    var pwdPassword by remember { mutableStateOf("") }
    var revealPassword by remember { mutableStateOf(false) }

    // Token login state
    var tokenUid by rememberSaveable { mutableStateOf("") }
    var tokenValue by remember { mutableStateOf("") }

    val loginVisible = !account.loggedIn || showRelogin
    val activeTab = KugouLoginTab.entries[tabIndex.coerceIn(0, KugouLoginTab.entries.lastIndex)]

    LaunchedEffect(account.revision, account.loggedIn) {
        vip = if (account.loggedIn) (appResult { repository.vipInfo() } as? AppResult.Success)?.value else null
    }

    LaunchedEffect(smsCooldownUntil) {
        now = SystemClock.elapsedRealtime()
        while (now < smsCooldownUntil) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }

    fun requestQr() {
        if (busy) return
        busy = true
        status = "正在获取二维码…"
        scope.launch {
            when (val result = appResult { repository.startQrLogin() }) {
                is AppResult.Success -> {
                    session = result.value
                    bitmap = withContext(Dispatchers.Default) {
                        Bitmap.createBitmap(qrPixels(result.value.content, QR_SIZE), QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
                    }
                    status = "请使用酷狗音乐 App 扫码并确认登录"
                }
                is AppResult.Failure -> {
                    session = null
                    bitmap = null
                    status = result.message
                }
            }
            busy = false
        }
    }

    LaunchedEffect(activeTab, loginVisible) {
        if (activeTab == KugouLoginTab.QR && loginVisible && session == null && !busy) {
            requestQr()
        } else if (activeTab != KugouLoginTab.QR) {
            session = null
            bitmap = null
        }
    }

    LaunchedEffect(session?.key) {
        val current = session ?: return@LaunchedEffect
        var waited = 0L
        while (waited < QR_LIFETIME_MS) {
            delay(2_000)
            waited += 2_000
            when (val result = appResult { repository.pollQrLogin(current.key) }) {
                is AppResult.Success -> when (result.value.status) {
                    KugouQrStatus.CONFIRMED -> {
                        session = null
                        bitmap = null
                        showRelogin = false
                        status = "登录成功"
                        return@LaunchedEffect
                    }
                    KugouQrStatus.SCANNED -> status = "已扫码，请在酷狗 App 上确认登录"
                    KugouQrStatus.EXPIRED -> {
                        session = null
                        bitmap = null
                        status = "二维码已过期，请重新获取"
                        return@LaunchedEffect
                    }
                    KugouQrStatus.WAITING -> Unit
                }
                is AppResult.Failure -> status = result.message
            }
        }
        session = null
        bitmap = null
        status = "二维码已过期，请重新获取"
    }

    fun sendSms() {
        if (busy) return
        finishInput()
        busy = true
        multiAccounts = emptyList()
        status = "正在发送短信验证码…"
        scope.launch {
            when (val result = appResult { repository.sendSmsCode(phone, countryCode) }) {
                is AppResult.Success -> {
                    smsCooldownUntil = SystemClock.elapsedRealtime() + SMS_COOLDOWN_MS
                    status = "验证码已发送，请查收短信"
                }
                is AppResult.Failure -> status = result.message
            }
            busy = false
        }
    }

    fun submitSmsLogin(selectedUserId: Long? = null) {
        if (busy) return
        finishInput()
        busy = true
        status = "正在登录酷狗账号…"
        scope.launch {
            when (val result = appResult { repository.loginBySmsCode(phone, smsCode, countryCode, selectedUserId) }) {
                is AppResult.Success -> when (val outcome = result.value) {
                    is KugouLoginOutcome.Success -> {
                        smsCode = ""
                        multiAccounts = emptyList()
                        showRelogin = false
                        status = "登录成功"
                    }
                    is KugouLoginOutcome.MultiAccount -> {
                        multiAccounts = outcome.accounts
                        status = "该手机号绑定了多个酷狗账号，请选择要登录的账号"
                    }
                }
                is AppResult.Failure -> status = result.message
            }
            busy = false
        }
    }

    fun submitPasswordLogin() {
        if (busy) return
        finishInput()
        busy = true
        status = "正在验证账号与密码…"
        scope.launch {
            when (val result = appResult { repository.loginByPassword(pwdAccount, pwdPassword) }) {
                is AppResult.Success -> when (val outcome = result.value) {
                    is KugouLoginOutcome.Success -> {
                        pwdPassword = ""
                        showRelogin = false
                        status = "登录成功"
                    }
                    is KugouLoginOutcome.MultiAccount -> {
                        multiAccounts = outcome.accounts
                        tabIndex = KugouLoginTab.SMS.ordinal
                        status = "该账号绑定了多个子账号，请使用手机验证码登录选择"
                    }
                }
                is AppResult.Failure -> status = result.message
            }
            busy = false
        }
    }

    fun submitTokenLogin() {
        if (busy) return
        finishInput()
        val parsedUid = tokenUid.trim().toLongOrNull() ?: 0L
        if (parsedUid <= 0L || tokenValue.isBlank()) {
            status = "请输入有效的数字 UID 和 Token"
            return
        }
        busy = true
        status = "正在校验 Token…"
        scope.launch {
            when (val result = appResult { repository.loginByToken(parsedUid, tokenValue) }) {
                is AppResult.Success -> {
                    tokenValue = ""
                    showRelogin = false
                    status = "Token 校验通过，登录成功"
                }
                is AppResult.Failure -> status = result.message
            }
            busy = false
        }
    }

    SettingsUiScope {
        SettingsScrollLayout(
            Modifier.fillMaxSize(),
            glassOverContent = false,
            topBar = { SettingsTopBar("酷狗音乐账号", onBack) },
        ) { topInset ->
            SpringLazyColumn(
                Modifier.fillMaxSize().testTag("kugou_account"),
                contentPadding = PaddingValues(start = 16.dp, top = topInset + 8.dp, end = 16.dp, bottom = 24.dp + bottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "state") {
                    SettingsGroup {
                        if (account.loggedIn) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    DecoratedAvatar(account.avatar.takeIf(String::isNotBlank), null, size = 56.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            account.nickname.ifBlank { "酷狗用户 ${account.userId}" },
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "UID ${account.userId}",
                                            fontSize = 12.sp,
                                            color = COUITheme.colorScheme.labelSecondaryVariant,
                                        )
                                        vip?.let { info ->
                                            val detail = listOf(info.label, info.expireTime.takeIf { it.isNotBlank() }?.let { "到期：$it" })
                                                .filterNotNull().filter(String::isNotBlank).joinToString(" · ")
                                            if (detail.isNotBlank()) {
                                                Text(detail, fontSize = 12.sp, color = COUITheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                                SettingsDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    GlassActionButton(
                                        if (showRelogin) "收起登录面板" else "切换 / 重新登录",
                                        { finishInput(); showRelogin = !showRelogin },
                                        Modifier.weight(1f).testTag("kugou_relogin"),
                                        enabled = !busy,
                                        fillWidth = true,
                                    )
                                    GlassActionButton(
                                        "退出账号",
                                        {
                                            scope.launch {
                                                session = null
                                                bitmap = null
                                                showRelogin = false
                                                repository.logout()
                                                status = "已退出酷狗账号"
                                            }
                                        },
                                        Modifier.weight(1f).testTag("kugou_logout"),
                                        enabled = !busy,
                                        destructive = true,
                                        fillWidth = true,
                                    )
                                }
                            }
                        } else {
                            Text(
                                "未登录。搜索、榜单、推荐歌单、评论、免费歌曲和歌词无需登录；云歌单、我喜欢和 VIP 曲库支持手机验证码、账号密码、酷狗 App 扫码或 Token 登录。",
                                Modifier.padding(16.dp),
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = COUITheme.colorScheme.labelPrimary.copy(alpha = .75f),
                            )
                        }
                    }
                }

                if (status.isNotBlank()) {
                    item(key = "status") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(COUITheme.colorScheme.primary.copy(alpha = .06f))
                                .padding(14.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("kugou_login_status"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp))
                            else Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = COUITheme.colorScheme.primary)
                            Text(
                                status,
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = COUITheme.colorScheme.labelPrimary.copy(alpha = .75f),
                            )
                        }
                    }
                }

                if (loginVisible) {
                    item(key = "login_card") {
                        SettingsGroup(Modifier.fillMaxWidth().testTag("kugou_login_card")) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                SettingsSegmentedControl(
                                    KugouLoginTab.entries.map { it.label },
                                    tabIndex,
                                    { selected ->
                                        finishInput()
                                        tabIndex = selected
                                        multiAccounts = emptyList()
                                    },
                                    enabled = !busy,
                                    tag = "kugou_login_method",
                                )

                                when (activeTab) {
                                    KugouLoginTab.QR -> {
                                        Column(
                                            Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Text("使用酷狗音乐 App 扫一扫", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                            Box(
                                                Modifier.size(216.dp).clip(RoundedCornerShape(22.dp))
                                                    .background(Color.White)
                                                    .border(1.dp, Color.Black.copy(alpha = .06f), RoundedCornerShape(22.dp))
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                val qr = bitmap
                                                if (qr != null) {
                                                    Image(qr.asImageBitmap(), "酷狗登录二维码", Modifier.fillMaxSize().testTag("kugou_login_qr"))
                                                } else if (busy) {
                                                    CircularProgressIndicator(Modifier.size(28.dp))
                                                } else {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Icon(Icons.Default.Refresh, null, Modifier.size(32.dp), tint = Color(0xFF747C89))
                                                        Text("点击下方获取二维码", fontSize = 12.sp, color = Color(0xFF535D6B))
                                                    }
                                                }
                                            }
                                            GlassActionButton(
                                                if (session == null) "获取二维码" else "刷新二维码",
                                                ::requestQr,
                                                Modifier.testTag("kugou_login"),
                                                enabled = !busy,
                                                fillWidth = false,
                                            )
                                            Text(
                                                "扫码后在酷狗音乐手机端确认授权，本页将自动完成登录。",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = COUITheme.colorScheme.labelPrimary.copy(alpha = .5f),
                                            )
                                        }
                                    }

                                    KugouLoginTab.SMS -> {
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("手机验证码登录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextField(
                                                    countryCode,
                                                    { countryCode = it.filter(Char::isDigit).take(4) },
                                                    singleLine = true,
                                                    label = "区号",
                                                    modifier = Modifier.width(72.dp).testTag("kugou_sms_country"),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                                )
                                                TextField(
                                                    phone,
                                                    { phone = it.filter(Char::isDigit).take(15) },
                                                    singleLine = true,
                                                    label = "手机号",
                                                    modifier = Modifier.weight(1f).testTag("kugou_sms_phone"),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                                )
                                            }
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextField(
                                                    smsCode,
                                                    { smsCode = it.filter(Char::isDigit).take(8) },
                                                    singleLine = true,
                                                    label = "短信验证码",
                                                    modifier = Modifier.weight(1f).testTag("kugou_sms_code"),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = {
                                                        if (phone.length >= 5 && smsCode.isNotBlank() && !busy) submitSmsLogin()
                                                    }),
                                                )
                                                GlassActionButton(
                                                    if (now < smsCooldownUntil) "${(smsCooldownUntil - now + 999) / 1000}s 后重发" else "获取验证码",
                                                    ::sendSms,
                                                    Modifier.width(116.dp).testTag("kugou_send_sms"),
                                                    enabled = phone.length >= 5 && !busy && now >= smsCooldownUntil,
                                                )
                                            }
                                            if (multiAccounts.isNotEmpty()) {
                                                Column(
                                                    Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(COUITheme.colorScheme.primary.copy(alpha = .05f))
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text("请选择要登录的酷狗账号", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                    multiAccounts.forEach { option ->
                                                        Row(
                                                            Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .clickable(enabled = !busy) { submitSmsLogin(option.userId) }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        ) {
                                                            DecoratedAvatar(option.avatar.takeIf(String::isNotBlank), null, size = 36.dp)
                                                            Column(Modifier.weight(1f)) {
                                                                Text(option.nickname, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                                Text("UID ${option.userId}", fontSize = 11.sp,
                                                                    color = COUITheme.colorScheme.labelSecondaryVariant)
                                                            }
                                                            Text("登录", fontSize = 13.sp, color = COUITheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                            GlassActionButton(
                                                "验证码登录",
                                                { submitSmsLogin() },
                                                Modifier.fillMaxWidth().testTag("kugou_sms_submit"),
                                                primary = true,
                                                enabled = phone.length >= 5 && smsCode.isNotBlank() && !busy,
                                            )
                                            Text(
                                                "验证码由酷狗官方发送；微信/QQ 因官方应用签名校验暂不支持，可通过已绑定手机号或扫码登录。",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = COUITheme.colorScheme.labelPrimary.copy(alpha = .5f),
                                            )
                                        }
                                    }

                                    KugouLoginTab.PASSWORD -> {
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("账号密码登录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                            TextField(
                                                pwdAccount,
                                                { pwdAccount = it },
                                                singleLine = true,
                                                label = "手机号 / 酷狗号",
                                                modifier = Modifier.fillMaxWidth().testTag("kugou_pwd_account"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                            )
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextField(
                                                    pwdPassword,
                                                    { pwdPassword = it },
                                                    singleLine = true,
                                                    label = "密码",
                                                    modifier = Modifier.weight(1f).testTag("kugou_pwd_password"),
                                                    visualTransformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = {
                                                        if (pwdAccount.isNotBlank() && pwdPassword.isNotEmpty() && !busy) submitPasswordLogin()
                                                    }),
                                                )
                                                GlassActionButton(
                                                    if (revealPassword) "隐藏" else "显示",
                                                    { revealPassword = !revealPassword },
                                                    Modifier.width(72.dp),
                                                    enabled = !busy,
                                                )
                                            }
                                            GlassActionButton(
                                                "密码登录",
                                                ::submitPasswordLogin,
                                                Modifier.fillMaxWidth().testTag("kugou_pwd_submit"),
                                                primary = true,
                                                enabled = pwdAccount.isNotBlank() && pwdPassword.isNotEmpty() && !busy,
                                            )
                                            Text(
                                                "密码经 RSA + AES 加密直连酷狗登录服务，本应用不保存明文密码。若酷狗触发安全验证或手机号绑定多账号，请改用验证码或扫码登录。",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = COUITheme.colorScheme.labelPrimary.copy(alpha = .5f),
                                            )
                                        }
                                    }

                                    KugouLoginTab.TOKEN -> {
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("Token 凭据登录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                            TextField(
                                                tokenUid,
                                                { tokenUid = it.filter(Char::isDigit).take(20) },
                                                singleLine = true,
                                                label = "酷狗 UID（userid）",
                                                modifier = Modifier.fillMaxWidth().testTag("kugou_token_uid"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                            )
                                            TextField(
                                                tokenValue,
                                                { tokenValue = it.trim() },
                                                singleLine = true,
                                                label = "Token",
                                                modifier = Modifier.fillMaxWidth().testTag("kugou_token_value"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = {
                                                    if (tokenUid.isNotBlank() && tokenValue.isNotBlank() && !busy) submitTokenLogin()
                                                }),
                                            )
                                            GlassActionButton(
                                                "验证并登录",
                                                ::submitTokenLogin,
                                                Modifier.fillMaxWidth().testTag("kugou_token_submit"),
                                                primary = true,
                                                enabled = tokenUid.isNotBlank() && tokenValue.isNotBlank() && !busy,
                                            )
                                            Text(
                                                "支持直接导入已有酷狗 UID 与 Token，保存前将自动联网验证凭据是否有效。",
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = COUITheme.colorScheme.labelPrimary.copy(alpha = .5f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
