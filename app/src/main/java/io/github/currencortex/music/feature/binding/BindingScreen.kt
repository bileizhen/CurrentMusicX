package io.github.currencortex.music.feature.binding

import io.github.currencortex.music.ui.component.musicScrollPadding
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import io.github.currencortex.music.ui.component.MusicPlaceholder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.currencortex.music.ui.util.collectAsPageState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun qrPixels(url: String, size: Int = 512): IntArray {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
    return IntArray(size * size) { index -> if (matrix[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
}
@Composable fun BindingScreen(vm: BindingViewModel, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun finishInput() { focus.clearFocus(); keyboard?.hide() }
    val state by vm.state.collectAsPageState()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val settings by vm.container.musicSettings.state.collectAsStateWithLifecycle()
    val sessionRevision by vm.container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    var qr by rememberSaveable(account.account?.id, settings.server, sessionRevision) { mutableStateOf(true) }
    var generation by remember { mutableIntStateOf(0) }
    var phone by rememberSaveable(account.account?.id, settings.server, sessionRevision) { mutableStateOf("") }
    var country by rememberSaveable(account.account?.id, settings.server, sessionRevision) { mutableStateOf("86") }
    var captcha by remember(account.account?.id, settings.server, sessionRevision) { mutableStateOf("") }
    var unbind by remember { mutableStateOf(false) }
    var reauthenticate by rememberSaveable(account.account?.id, settings.server, sessionRevision) { mutableStateOf(false) }
    val list = rememberLazyListState()
    var revealLogin by remember { mutableStateOf(false) }
    LaunchedEffect(revealLogin) {
        if (revealLogin) {
            withFrameNanos { }; withFrameNanos { }
            list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            revealLogin = false
        }
    }
    val loginVisible = reauthenticate || state.binding?.let { !it.bound || it.stale } == true ||
        (state.binding == null && !state.loading && state.error != null)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vm, owner, qr, loginVisible, generation, account.account?.id, settings.server, sessionRevision) {
        if (qr && loginVisible && account.account != null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.qrSession(); awaitCancellation()
        } else vm.clearQr()
    }
    LaunchedEffect(state.phoneBoundRevision) { if (state.phoneBoundRevision > 0) { captcha = ""; reauthenticate = false } }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.codeUntil) {
        now = SystemClock.elapsedRealtime()
        while (now < state.codeUntil) { delay(1000); now = SystemClock.elapsedRealtime() }
    }
    val bitmap by produceState<Bitmap?>(null, state.qrUrl) {
        val url = state.qrUrl
        value = if (url == null) null else withContext(Dispatchers.Default) {
            Bitmap.createBitmap(qrPixels(url), 512, 512, Bitmap.Config.ARGB_8888)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button,
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack)
                .testTag("binding_back"), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, "返回", Modifier.size(23.dp))
            }
            Text("网易云账号", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("binding_screen"), state = list,
        contentPadding = musicScrollPadding(PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.loading && state.binding == null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicPlaceholder(Modifier.fillMaxWidth(.65f).height(24.dp))
                    MusicPlaceholder(Modifier.fillMaxWidth(.9f).height(14.dp))
                    Text("正在检查绑定…", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
        }
        state.error?.let { error -> item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BindingNotice(error)
                BindingAction("重试", vm::reload, enabled = !busy)
            }
        } }
        state.binding?.let { binding ->
            if (binding.bound) item {
                BindingAccountCard(binding, busy, { vm.sync() }, vm::live, vm::refresh,
                    { finishInput(); reauthenticate = true; revealLogin = true }, { unbind = true })
            } else item {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("连接你的音乐库", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("同步网易云歌单，在 CurrentMusic 中继续聆听。", fontSize = 13.sp, lineHeight = 21.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            }
        }
        if (busy) item { BindingNotice("正在处理，请稍候…", busy = true) }
        message?.let { value -> item { BindingNotice(value, Modifier.testTag("binding_result")) } }
        if (loginVisible) {
            if (state.binding?.bound == true) item {
                Text("重新登录网易云", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth().testTag("binding_login_card")) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        BindingMethods(qr, !busy) { method -> finishInput(); qr = method }
                        if (qr) BindingQrContent(bitmap, state, busy) { vm.clearQr(); generation++ }
                        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("使用手机验证码登录", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(country, { country = it.filter(Char::isDigit).take(4) }, singleLine = true, label = "区号",
                                    modifier = Modifier.width(72.dp).testTag("binding_country"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                                TextField(phone, { phone = it.filter(Char::isDigit).take(15) }, singleLine = true, label = "手机号",
                                    modifier = Modifier.weight(1f).testTag("binding_phone"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextField(captcha, { captcha = it.filter(Char::isDigit).take(8) }, singleLine = true, label = "验证码",
                                    modifier = Modifier.weight(1f).testTag("binding_code"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { finishInput() }))
                                Button(onClick = { vm.code(phone, country) },
                                    enabled = phone.length >= 5 && country.isNotBlank() && !busy && now >= state.codeUntil,
                                    modifier = Modifier.width(116.dp).heightIn(min = 52.dp).testTag("send_binding_code")) {
                                    Text(if (now < state.codeUntil) "${(state.codeUntil - now + 999) / 1000}s 后重发" else "获取验证码", fontSize = 12.sp)
                                }
                            }
                            if (state.codeFailed) {
                                Text("若已收到短信，可直接填写；未收到可等倒计时结束后尝试备用发送。", fontSize = 12.sp, lineHeight = 19.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                                BindingAction("备用发送验证码", { vm.code(phone, country, alternate = true) },
                                    Modifier.testTag("send_binding_code_alternate"),
                                    phone.length >= 5 && country.isNotBlank() && !busy && now >= state.codeUntil)
                            }
                            Button(onClick = { finishInput(); vm.phone(phone, captcha, country) },
                                enabled = phone.length >= 5 && captcha.isNotBlank() && country.isNotBlank() && !busy,
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_phone_binding")) {
                                Text("确认绑定", color = MiuixTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("验证码由网易云发送。绑定成功后会自动同步歌单。", fontSize = 12.sp, lineHeight = 19.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .45f))
                        }
                    }
                }
            }
        }
    }
    }
    }
    if (unbind) MusicDialog("解绑网易云音乐？", { if (!busy) unbind = false }) {
        Text("已导入歌单会保留为本地快照，不再随网易云更新。")
        TextButton("确认解绑", onClick = { unbind = false; vm.unbind() }, enabled = !busy, modifier = Modifier.testTag("confirm_unbind"))
        BindingAction("取消", { unbind = false }, enabled = !busy)
    }
}
