package io.github.currencortex.music.feature.support

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.util.showMusicError
import io.github.currencortex.music.ui.util.showMusicMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SupportScreen(onBack: () -> Unit, afdianUrl: String = SupportConfig.afdianUrl) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(192, 192).asImageBitmap()
    }
    val scope = rememberCoroutineScope()
    val gate = remember { SupportClickGate(SystemClock::elapsedRealtime) }
    var preview by rememberSaveable { mutableStateOf<SupportCode?>(null) }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    var pendingSave by rememberSaveable { mutableStateOf<SupportCode?>(null) }
    var busy by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val code = pendingSave
        pendingSave = null
        if (uri != null && code != null) scope.launch {
            busy = true
            try {
                SupportFiles.save(context, code, uri)
                context.showMusicMessage("图片已保存")
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { context.showMusicError("无法保存图片，请重试") }
            finally { busy = false }
        }
    }
    SettingsPage("支持开发者", "support_screen", onBack) {
        item("brand") {
            SettingsGroup {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(appIcon, "CurrentMusic 图标", Modifier.size(64.dp))
                    Text("CurrentMusic", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("CurrentMusic 由独立开发者持续维护。如果你喜欢这款音乐播放器，欢迎通过自愿赞助支持项目的开发与维护。",
                        fontSize = 15.sp, lineHeight = 23.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("开发者 · bileizhen", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        item("afdian") {
            SettingsSection("爱发电", MusicIcons.Heart) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("通过爱发电支持 CurrentMusic 的持续开发。", fontSize = 15.sp, lineHeight = 22.sp)
                    val available = supportHttpsUrl(afdianUrl) != null
                    GlassActionButton(if (available) "前往爱发电" else "暂未开放", {
                        if (gate.acquire()) when (openSupportLink(context, afdianUrl)) {
                            SupportLinkResult.INVALID -> context.showMusicError("链接无效")
                            SupportLinkResult.NO_APP -> context.showMusicError("未找到可以打开链接的应用")
                            SupportLinkResult.OPENED -> Unit
                        }
                    }, Modifier.testTag("support_afdian"), primary = true, enabled = available)
                }
            }
        }
        item("codes") {
            SettingsSection("快捷赞赏", MusicIcons.Image) {
                SupportCode.entries.forEachIndexed { index, code ->
                    if (index > 0) SettingsDivider()
                    SettingsPreference(code.title, code.summary, Modifier.testTag("support_${code.name}"),
                        onClick = { preview = code; previewOpen = true },
                        trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp)) })
                }
            }
        }
        item("privacy") {
            SettingsHint("所有赞助均为自愿行为，不影响 CurrentMusic 的正常使用。赞助不会解锁额外的软件功能或会员权益。")
            SettingsHint("无需登录。支付由对应平台处理，CurrentMusic 不收集支付凭据、不读取支付记录，也不会判断支付是否完成。")
        }
    }
    val code = preview
    val dialogMaxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .65f }
    MusicOverlayDialog(show = previewOpen, title = code?.title, onDismissRequest = { previewOpen = false },
        // Opaque panel: the image cannot become part of a refracting glass surface.
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer) {
        if (code != null) Column(Modifier.fillMaxWidth()
            .heightIn(max = dialogMaxHeight)
            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SupportQrImage(code)
            Text("请使用${code.scanner}扫描", fontSize = 15.sp)
            Text("同一部手机可先保存图片，再从${code.scanner}扫一扫的相册中选择。",
                fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MusicDialogButton("保存图片", {
                if (!busy && pendingSave == null) {
                    pendingSave = code
                    try { save.launch(code.fileName) }
                    catch (_: Exception) { pendingSave = null; context.showMusicError("未找到可以保存图片的应用") }
                }
            }, Modifier.fillMaxWidth().testTag("support_save"), enabled = !busy && pendingSave == null)
            MusicDialogButton("分享图片", {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try { context.startActivity(Intent.createChooser(SupportFiles.shareIntent(context, code), code.title)) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { context.showMusicError("无法分享图片，请重试") }
                        finally { busy = false }
                    }
                }
            }, Modifier.fillMaxWidth().testTag("support_share"), enabled = !busy)
            MusicDialogButton("关闭", { previewOpen = false }, Modifier.fillMaxWidth().testTag("support_close"))
        }
    }
}

@Composable
internal fun SupportQrImage(code: SupportCode) {
    val context = LocalContext.current
    val request = remember(context, code) { ImageRequest.Builder(context).data(code.image).crossfade(false).build() }
    // One square, unmodified local image. No elastic press, glass capture, tint or image animation.
    AsyncImage(request, code.title, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
        .background(Color.White).padding(12.dp).testTag("support_qr_${code.name}"), contentScale = ContentScale.Fit)
}
