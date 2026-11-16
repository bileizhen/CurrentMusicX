package io.github.currencortex.music.feature.support

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.data.support.*
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.showMusicError
import io.github.currencortex.music.ui.util.showMusicMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun OfficialDonationScreen(vm: DonationViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBusy by remember { mutableStateOf(false) }
    val pendingSave by vm.qrSavePending.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val bytes = vm.takePendingQrSave()
        if (uri != null && bytes == null) context.showMusicError("捐赠码保存状态已丢失，请重新保存")
        else if (uri != null && bytes != null) scope.launch {
            fileBusy = true
            try {
                SupportFiles.saveOfficial(context, bytes, uri)
                context.showMusicMessage("图片已保存")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showMusicError("无法保存图片，请重试") }
            finally { fileBusy = false }
        }
    }
    SettingsPage("官方捐赠", "official_donation_screen", onBack) {
        item("intro") {
            SettingsHint("自愿支持 CurrentMusic 官方的持续维护。捐赠不会解锁功能、等级或会员权益。")
        }
        if (!state.signedIn) item("login") {
            SettingsGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("请先登录 CurrentMusic 账号", fontSize = 17.sp)
                    Text("登录后确认账号、获取官方捐赠码，并查看仅本人可见的捐赠记录。", fontSize = 14.sp)
                    GlassActionButton("登录 CurrentMusic", onLogin, Modifier.testTag("donation_login"), primary = true)
                }
            }
        } else {
            item("refresh") {
                GlassActionButton(if (state.loading) "正在读取…" else "刷新账号与记录", vm::refresh,
                    Modifier.testTag("donation_refresh"), enabled = !state.loading && !state.busy)
            }
            state.message?.let { message -> item("success") { SettingsHint(message) } }
            if (state.step != 2) state.error?.let { error -> item("error") { SettingsHint(error, error = true) } }
            val info = state.info
            if (info != null) {
                item("steps") { SettingsHint("第 ${state.step + 1} 步 / 3 · ${listOf("确认账号", "扫码捐赠", "登记交易单号")[state.step]}") }
                when (state.step) {
                    0 -> item("account") {
                        SettingsSection("确认捐赠账号", MusicIcons.Heart) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("用户名：${info.profile.username}")
                                Text("昵称：${info.profile.nickname.orEmpty().ifBlank { "未设置" }}")
                                Text("邮箱：${info.profile.email.orEmpty().ifBlank { "未绑定" }}")
                                Text("手机号：${info.profile.phone.orEmpty().ifBlank { "未绑定" }}")
                                info.profile.created_at?.let { Text("注册时间：${donationDate(it)}") }
                                Text("以上信息来自当前账号，将随捐赠记录交由站长核对。", fontSize = 13.sp)
                                GlassActionButton("确认账号，查看捐赠码", vm::confirmAccount,
                                    Modifier.testTag("donation_confirm_account"), primary = true)
                            }
                        }
                    }
                    1 -> item("qr") {
                        SettingsSection("扫码捐赠", MusicIcons.Image) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.qrLoading) SettingsHint("正在获取官方捐赠码…")
                                state.qrError?.let { SettingsHint(it, error = true) }
                                var decoded by remember(state.qr) { mutableStateOf(false) }
                                var decodeFailed by remember(state.qr) { mutableStateOf(false) }
                                state.qr?.let { bytes ->
                                    AsyncImage(bytes, "CurrentMusic 官方捐赠码",
                                        Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
                                            .background(Color.White).padding(12.dp).testTag("donation_qr"),
                                        contentScale = ContentScale.Fit, onSuccess = { decoded = true },
                                        onError = { decodeFailed = true; decoded = false })
                                    if (decodeFailed) SettingsHint("捐赠码无法显示，请重新获取", error = true)
                                    GlassActionButton("保存捐赠码", {
                                        if (!fileBusy && vm.beginQrSave(bytes)) {
                                            try { save.launch("currentmusic-official-donation.jpg") }
                                            catch (_: Exception) { vm.takePendingQrSave(); context.showMusicError("未找到可以保存图片的应用") }
                                        }
                                    }, Modifier.testTag("donation_save_qr"), enabled = decoded && !fileBusy && pendingSave == null)
                                    GlassActionButton("分享捐赠码", {
                                        if (!fileBusy) {
                                            fileBusy = true
                                            scope.launch {
                                                try { context.startActivity(Intent.createChooser(
                                                    SupportFiles.shareOfficial(context, bytes), "官方捐赠码")) }
                                                catch (cancelled: CancellationException) { throw cancelled }
                                                catch (_: Exception) { context.showMusicError("无法分享图片，请重试") }
                                                finally { fileBusy = false }
                                            }
                                        }
                                    }, enabled = decoded && !fileBusy)
                                }
                                if (state.qr == null || decodeFailed) GlassActionButton("重新获取捐赠码", vm::loadQr,
                                    enabled = !state.qrLoading)
                                Text("请使用对应支付应用扫码。金额由你自行决定；同一部手机可保存图片后从扫一扫的相册选择。", fontSize = 14.sp)
                                Text("支付完成后，请在账单详情中找到交易单号，返回这里登记。", fontSize = 13.sp)
                                GlassActionButton("已完成扫码，登记捐赠", vm::showForm,
                                    Modifier.testTag("donation_show_form"), primary = true, enabled = decoded)
                                GlassActionButton("返回确认账号", vm::previous)
                            }
                        }
                    }
                    2 -> item("form") {
                        SettingsSection("登记捐赠", MusicIcons.Heart) {
                            Column(Modifier.fillMaxWidth().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("捐赠账号：${info.profile.username}")
                                TextField(state.input.amount, { vm.edit(state.input.copy(amount = it.take(16))) },
                                    label = "捐赠金额（元）", singleLine = true, enabled = !state.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth().testTag("donation_amount"))
                                TextField(state.input.transaction, { vm.edit(state.input.copy(transaction = it.take(info.transactionLimit))) },
                                    label = "交易单号", singleLine = true, enabled = !state.busy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                    modifier = Modifier.fillMaxWidth().testTag("donation_transaction"))
                                Text("在支付应用的账单详情中查看。请输入 6 至 ${info.transactionLimit} 位数字、字母或 ._-", fontSize = 12.sp)
                                if (info.noteLimit > 0) TextField(state.input.note, { vm.edit(state.input.copy(note = it.take(info.noteLimit))) },
                                    label = "备注（可选，最多 ${info.noteLimit} 字）", enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth().testTag("donation_note"))
                                Text("提交的是你填写的金额与交易单号，仅供站长核对；提交成功不代表支付已被自动验证。", fontSize = 13.sp)
                                state.error?.let { SettingsHint(it, error = true) }
                                GlassActionButton(if (state.busy) "正在提交…" else "提交捐赠记录", vm::submit,
                                    Modifier.testTag("donation_submit"), primary = true,
                                    enabled = !state.busy && state.input.amount.isNotBlank() && state.input.transaction.isNotBlank())
                                GlassActionButton("返回查看捐赠码", vm::previous, enabled = !state.busy)
                            }
                        }
                    }
                }
                item("history_header") {
                    SettingsSection("本人捐赠记录", MusicIcons.Heart) {
                        SettingsHint("已登记 ${info.mine.count} 次 · 累计 ¥${donationMoney(info.mine.totalCents)}")
                        if (info.mine.items.isEmpty()) SettingsHint("暂无捐赠记录")
                    }
                }
                items(info.mine.items, key = { "donation_${it.id}" }) { record ->
                    SettingsGroup {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("¥${donationMoney(record.amount_cents)} · ${donationDate(record.created_at)}")
                            Text("交易单号：${record.txn_id}", fontSize = 13.sp)
                            record.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
        item("privacy") { SettingsHint("交易单号、自报金额与账号信息仅用于站长核对，不向其他用户公开。应用不会读取你的支付记录。") }
    }
}

private fun donationDate(seconds: Long): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(seconds))
}.getOrDefault("未知时间")
