package io.github.currencortex.music.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalCustomFont = staticCompositionLocalOf<FontFamily?> { null }
val LocalCustomTypeface = staticCompositionLocalOf<Typeface?> { null }
private val validName = Regex("[0-9a-f-]{36}\\.font")
private const val MAX_FONT_BYTES = 64L * 1024 * 1024

private fun fontFile(context: Context, name: String): File? =
    name.takeIf { validName.matches(it) }?.let { File(context.filesDir, "custom-fonts/$it") }

@Composable fun rememberCustomTypeface(name: String): Typeface? {
    val context = LocalContext.current
    val face by produceState<Typeface?>(null, context, name) {
        value = null
        value = withContext(Dispatchers.IO) {
            fontFile(context, name)?.let { file -> runCatching { Typeface.Builder(file).build() }.getOrNull() }
        }
    }
    return face
}
@Composable fun rememberCustomFont(name: String): FontFamily? {
    val face = rememberCustomTypeface(name)
    return remember(face) { face?.let { FontFamily(it) } }
}

/** Copies into app-private storage so the font stays available after reboot or provider removal. */
fun importCustomFont(context: Context, uri: Uri): String {
    val name = "${UUID.randomUUID()}.font"
    val file = requireNotNull(fontFile(context, name))
    file.parentFile?.mkdirs()
    try {
        val source = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取字体文件" }
        source.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024); var size = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    size += n; require(size <= MAX_FONT_BYTES) { "字体文件超过 64 MB" }
                    output.write(buffer, 0, n)
                }
                require(size > 0) { "字体文件为空" }
            }
        }
        requireNotNull(Typeface.Builder(file).build()) { "文件不是有效的字体" }
        return name
    } catch (e: Exception) { file.delete(); throw e }
}

fun deleteCustomFont(context: Context, name: String) { fontFile(context, name)?.delete() }
