package io.github.currencortex.music.feature.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.Image
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.util.showMusicError
import io.github.currencortex.music.ui.util.showMusicMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*

/** Viewing is available on public profiles too; changing the image remains owner-only. */
@Composable internal fun AvatarPreviewDialog(user: ProfileUser, container: AppContainer,
    onDismiss: () -> Unit, onChange: (() -> Unit)? = null, onDecorations: (() -> Unit)? = null) {
    val context = LocalContext.current
    val preferences by container.musicSettings.state.collectAsState()
    val versions by container.profileRepository.avatarVersions.collectAsState()
    val url = container.profileRepository.avatarUrl(preferences.server, user.avatar, versions)
    var image by remember(url) { mutableStateOf<Image?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<Image?>(null) }
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val selected = pendingImage
        pendingImage = null
        if (uri != null && selected != null) scope.launch {
            saving = true
            try {
                saveAvatarImage(context.contentResolver, selected, uri)
                context.showMusicMessage("头像已保存")
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { context.showMusicError("保存失败，请重新选择位置") }
            finally { saving = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            } }
            onDispose { }
        }
        MiuixTheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark, isDark = true) }) {
            Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("avatar_preview")) {
                Box(Modifier.padding(start = 12.dp, top = 8.dp).size(48.dp)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "关闭头像预览" }.testTag("avatar_preview_close"),
                    contentAlignment = Alignment.Center) {
                    Icon(MusicIcons.Close, null, Modifier.size(28.dp), tint = Color.White)
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (url != null) key(url, attempt) {
                        AsyncImage(remember(url, attempt) { ImageRequest.Builder(context).data(url)
                            .size(1600).allowHardware(false).build() }, "${user.nickname}的头像",
                            Modifier.fillMaxSize().testTag("avatar_preview_image"), contentScale = ContentScale.Fit,
                            onLoading = { image = null; failed = false },
                            onSuccess = { image = it.result.image; failed = false },
                            onError = { image = null; failed = true })
                    }
                    if (url == null || failed) Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(MusicIcons.Account, null, Modifier.size(72.dp), tint = Color.White.copy(alpha = .6f))
                        Text(if (url == null) "还没有设置头像" else "头像加载失败", color = Color.White.copy(alpha = .6f))
                        if (failed) TextButton("重新加载", onClick = { attempt++ })
                    } else if (image == null) CircularProgressIndicator()
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                    .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    onDecorations?.let { action -> Card(Modifier.fillMaxWidth()) {
                        MusicDestinationRow("去设置头像挂件", action, Modifier.testTag("avatar_preview_decorations"))
                    } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(Modifier.weight(1f)) {
                            MusicDestinationRow(if (saving) "正在保存…" else "保存头像", {
                                pendingImage = image
                                try { save.launch("CurrentMusic-avatar-${user.id}.png") }
                                catch (_: Exception) { pendingImage = null; context.showMusicError("无法打开保存位置") }
                            }, Modifier.testTag("avatar_preview_save"), enabled = image != null && !saving,
                                chevron = false)
                        }
                        onChange?.let { action -> Card(Modifier.weight(1f)) {
                            MusicDestinationRow("更换头像", action, Modifier.testTag("avatar_preview_change"), chevron = false)
                        } }
                    }
                }
            }
        }
    }
}

internal suspend fun saveAvatarImage(resolver: ContentResolver, image: Image, destination: Uri) =
    withContext(Dispatchers.IO) {
        // Coil may share this bitmap with the displayed image; it must not be recycled here.
        checkNotNull(resolver.openOutputStream(destination, "w")).use { output ->
            check(image.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
