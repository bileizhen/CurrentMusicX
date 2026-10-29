package io.github.currencortex.music.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.LyricsFont
import io.github.currencortex.music.ui.theme.deleteCustomFont
import io.github.currencortex.music.ui.theme.importCustomFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.menu.OverlayDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun LyricsFontSelector(font: LyricsFont, file: String,
    onFont: (LyricsFont) -> Unit, onFile: suspend (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentOnFile by rememberUpdatedState(onFile)
    val currentFile by rememberUpdatedState(file)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true; error = null
            var imported: String? = null
            var saved = false
            try {
                val old = currentFile
                val name = withContext(Dispatchers.IO) { importCustomFont(context, uri).also { imported = it } }
                withContext(kotlinx.coroutines.NonCancellable) {
                    currentOnFile(name)
                    saved = true
                    if (old.isNotBlank() && old != name) withContext(Dispatchers.IO) { deleteCustomFont(context, old) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "无法导入字体，请选择有效的 TTF 或 OTF 文件" }
            finally {
                if (!saved) imported?.let { deleteCustomFont(context, it) }
                importing = false
            }
        }
    }
    val import = { picker.launch(arrayOf("*/*")) }
    OverlayDropdownMenu(title = "歌词字体", summary = if (importing) "正在导入…" else font.label,
        insideMargin = PaddingValues(vertical = 12.dp),
        modifier = Modifier.testTag("lyrics_font_dropdown"), enabled = !importing,
        renderInRootScaffold = false,
        entry = DropdownEntry(LyricsFont.entries.map { option ->
            DropdownItem(text = option.label, selected = option == font, onClick = {
                error = null
                if (option == LyricsFont.IMPORTED && file.isBlank()) import() else onFont(option)
            })
        }))
    if (file.isNotBlank()) Text("更换导入字体", fontSize = 13.sp,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.testTag("lyrics_font_import").clickable(enabled = !importing, role = Role.Button, onClick = import)
            .padding(vertical = 12.dp))
    error?.let { Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 6.dp)) }
}
