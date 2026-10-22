package io.github.currencortex.music.feature.share

import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import io.github.currencortex.music.ui.util.showMusicMessage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.currencortex.music.ui.util.showMusicError
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun SongShareDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember(song) { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<SongShareFile?>(null) }
    val result by produceState<Result<SongShareFile>?>(null, song, attempt) {
        value = null
        value = try { Result.success(SongShareFiles.create(context.applicationContext, song)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
    }
    val poster = result?.getOrNull()
    fun message(text: String, error: Boolean = false) {
        if (error) context.showMusicError(text) else context.showMusicMessage(text)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val selected = pendingSave
        pendingSave = null
        if (uri != null && selected != null) scope.launch {
            saving = true
            try { SongShareFiles.save(context, selected, uri); message("海报已保存") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("保存失败，请重新选择位置", error = true) }
            finally { saving = false }
        }
    }
    val previewHeight = (LocalConfiguration.current.screenHeightDp * .42f).coerceIn(170f, 420f).dp
    MusicDialog("分享歌曲", onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .72f).dp)
            .verticalScroll(rememberScrollState()).testTag("song_share_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (poster != null) AsyncImage(poster.file, "${poster.content.title} 分享海报",
                Modifier.fillMaxWidth().height(previewHeight).testTag("song_share_preview"), contentScale = ContentScale.Fit)
            else Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
                if (result == null) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在生成海报…", fontSize = 13.sp)
                } else Text("海报生成失败，请重试", Modifier.testTag("song_share_error"))
            }
            if (poster != null && !poster.coverLoaded) Text("封面暂未加载，可重新生成海报", fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicDialogButton("保存图片", onClick = {
                    if (poster != null) {
                        pendingSave = poster
                        try { save.launch(poster.content.filename) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { pendingSave = null; message("无法打开保存位置", error = true) }
                    }
                }, enabled = poster != null && !saving, modifier = Modifier.weight(1f).testTag("song_share_save"))
                MusicDialogButton("分享图片", onClick = {
                    if (poster != null) try { context.startActivity(Intent.createChooser(SongShareFiles.shareIntent(context, poster), "分享歌曲海报")) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message("无法打开分享面板，请保存图片后分享", error = true) }
                }, enabled = poster != null, colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary, textColor = MiuixTheme.colorScheme.onPrimary),
                    modifier = Modifier.weight(1f).testTag("song_share_send"))
            }
            MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicDialogButton(if (SongShareContent.from(song).publicUrl == null) "复制歌曲信息" else "复制链接", onClick = {
                    val content = SongShareContent.from(song)
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText("歌曲分享", content.publicUrl ?: content.text))
                    message(if (content.publicUrl == null) "已复制歌曲信息" else "已复制链接")
                }, modifier = Modifier.weight(1f).testTag("song_share_copy"))
                if (result?.isFailure == true || poster?.coverLoaded == false) MusicDialogButton("重新生成", onClick = { attempt++ },
                    modifier = Modifier.weight(1f).testTag("song_share_retry"))
            }
        }
    }
}
