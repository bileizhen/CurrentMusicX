package io.github.currencortex.music.feature.binding

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*

fun qrPixels(url: String, size: Int = 512): IntArray {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
    return IntArray(size * size) { index -> if (matrix[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
}
@Composable fun BindingScreen(vm: BindingViewModel, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun finishInput() { focus.clearFocus(); keyboard?.hide() }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val settings by vm.container.musicSettings.state.collectAsStateWithLifecycle()
    val sessionRevision by vm.container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    var qr by rememberSaveable(account.account?.id, settings.server) { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var phone by remember(account.account?.id, settings.server) { mutableStateOf("") }
    var country by remember(account.account?.id, settings.server) { mutableStateOf("86") }
    var captcha by remember(account.account?.id, settings.server) { mutableStateOf("") }
    var unbind by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vm, owner, qr, generation, account.account?.id, settings.server, sessionRevision) {
        if (qr && account.account != null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { vm.qrSession(); awaitCancellation() } finally { vm.clearQr() }
        } else vm.clearQr()
    }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.codeUntil) { while (now < state.codeUntil) { delay(1000); now = SystemClock.elapsedRealtime() } }
    val bitmap by produceState<Bitmap?>(null, state.qrUrl) {
        val url = state.qrUrl
        value = if (url == null) null else withContext(Dispatchers.Default) {
            Bitmap.createBitmap(qrPixels(url), 512, 512, Bitmap.Config.ARGB_8888)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("binding_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton("返回", onClick = onBack); Text("网易云账号", fontSize = 28.sp) }
        if (state.loading) item { Text("正在检查绑定…") }
        state.error?.let { item { Text(it); TextButton("重试", onClick = vm::reload) } }
        state.binding?.let { binding ->
            item {
                Text(if (binding.bound) "已绑定：${binding.profile?.nickname.orEmpty()}" else "尚未绑定")
                if (binding.stale) Text("登录态已失效，请重新登录")
                if (binding.lastSync > 0) Text("上次同步 ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(binding.lastSync * 1000))} · ${binding.lastSyncCount} 个歌单")
            }
            if (binding.bound) {
                item { TextButton("检查登录态", onClick = vm::live, enabled = !busy) }
                item { TextButton("刷新登录态", onClick = vm::refresh, enabled = !busy) }
                item { TextButton("同步网易云歌单", onClick = { vm.sync() }, enabled = !busy, modifier = Modifier.testTag("sync_binding")) }
                item { TextButton("解绑", onClick = { unbind = true }, enabled = !busy) }
            }
        }
        item { Text(if (state.binding?.bound == true) "重新登录网易云" else "绑定网易云音乐") }
        item { Row { TextButton("手机验证码", onClick = { qr = false }); TextButton("扫码登录", onClick = { qr = true }, modifier = Modifier.testTag("qr_binding")) } }
        if (qr) {
            item { bitmap?.let { Image(it.asImageBitmap(), contentDescription = "网易云登录二维码", modifier = Modifier.size(220.dp).testTag("binding_qr")) } }
            item { Text(state.qrMessage); Text("打开网易云音乐扫一扫，授权后自动同步歌单。")
                TextButton("刷新二维码", onClick = { generation++ }, enabled = !busy) }
        } else {
            item {
                Text("区号"); TextField(country, { country = it.filter(Char::isDigit).take(4) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            item {
                Text("手机号"); TextField(phone, { phone = it.filter(Char::isDigit).take(15) }, singleLine = true, modifier = Modifier.testTag("binding_phone"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
            item {
                Text("验证码"); TextField(captcha, { captcha = it }, singleLine = true, modifier = Modifier.testTag("binding_code"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finishInput() }))
            }
            item { Text("获取验证码会向该手机号发送网易云登录短信。绑定成功后自动同步歌单。") }
            item { TextButton(if (now < state.codeUntil) "${(state.codeUntil - now + 999) / 1000}s 后重发" else "获取验证码",
                onClick = { vm.code(phone, country) }, enabled = phone.length >= 5 && country.isNotBlank() && !busy && now >= state.codeUntil) }
            item { TextButton("确认绑定", onClick = { finishInput(); vm.phone(phone, captcha, country); captcha = "" }, enabled = phone.length >= 5 && captcha.isNotBlank() && country.isNotBlank() && !busy,
                modifier = Modifier.testTag("confirm_phone_binding")) }
        }
        if (busy) item { Text("正在处理，请稍候…") }
        message?.let { item { Text(it) } }
    }
    if (unbind) MusicDialog("解绑网易云音乐？", { if (!busy) unbind = false }) {
        Text("已导入歌单会保留为本地快照，不再随网易云更新。")
        TextButton("确认解绑", onClick = { unbind = false; vm.unbind() }, enabled = !busy, modifier = Modifier.testTag("confirm_unbind"))
    }
}
