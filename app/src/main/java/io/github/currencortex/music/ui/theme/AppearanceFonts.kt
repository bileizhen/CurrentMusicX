// SPDX-License-Identifier: GPL-3.0-only
// Android family discovery/sorting adapted from upstream lib/common/system_fonts.dart.
package io.github.currencortex.music.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import java.io.File
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs

internal const val SYSTEM_FONT_PREFIX = "system:"
internal data class AppearanceFont(val key: String, val label: String)
private val importedFontName = Regex("[0-9a-f-]{36}\\.font")

private data class SystemFontFile(val file: File, val weight: Int, val italic: Boolean, val index: Int)
private val systemFontFiles: Map<String, SystemFontFile?> by lazy {
    runCatching {
        File("/system/etc/fonts.xml").inputStream().use { input ->
            val xml = Xml.newPullParser().apply { setInput(input, null) }
            buildMap {
                var family: String? = null
                var inFont = false
                var weight = 400
                var italic = false
                var index = 0
                val files = mutableListOf<SystemFontFile>()
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    when (xml.eventType) {
                        XmlPullParser.START_TAG -> when (xml.name) {
                            "family" -> { family = xml.getAttributeValue(null, "name"); files.clear() }
                            "font" -> {
                                inFont = true
                                weight = xml.getAttributeValue(null, "weight")?.toIntOrNull() ?: 400
                                italic = xml.getAttributeValue(null, "style") == "italic"
                                index = xml.getAttributeValue(null, "index")?.toIntOrNull() ?: 0
                            }
                        }
                        XmlPullParser.TEXT -> if (inFont && family != null) {
                            val name = xml.text.trim()
                            if (name.isNotEmpty()) {
                                val file = if (name.startsWith('/')) File(name) else File("/system/fonts", name)
                                if (file.isFile) files += SystemFontFile(file, weight, italic, index)
                            }
                        }
                        XmlPullParser.END_TAG -> when (xml.name) {
                            "font" -> inFont = false
                            "family" -> {
                                family?.let { name -> put(name, files.minByOrNull { abs(it.weight - 400) + if (it.italic) 1000 else 0 }) }
                                family = null
                            }
                        }
                    }
                    xml.next()
                }
            }
        }
    }.getOrDefault(emptyMap()).ifEmpty {
        listOf("sans-serif", "sans-serif-condensed", "serif", "monospace", "casual", "cursive").associateWith { null }
    }
}

/** Named families only, matching upstream's list rather than exposing font-file names. */
internal fun installedFontFamilies(): List<AppearanceFont> = systemFontFiles.keys
        .map(String::trim).filter { it.isNotEmpty() && !it.startsWith('.') }
        .distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }
        .map { AppearanceFont(SYSTEM_FONT_PREFIX + it, it) }

internal fun loadSystemTypeface(family: String): Typeface {
    // Loading the file keeps OEM font substitution from flattening all family previews.
    val source = systemFontFiles[family]
    return source?.let {
        runCatching { Typeface.Builder(it.file).setTtcIndex(it.index).setWeight(it.weight).setItalic(it.italic).build() }.getOrNull()
    } ?: Typeface.create(family, Typeface.NORMAL)
}

/** A separate catalog prevents appearance imports from taking ownership of lyric font files. */
internal fun importedAppearanceFonts(context: Context, selected: String = ""): List<AppearanceFont> {
    val directory = File(context.filesDir, "custom-fonts")
    if (importedFontName.matches(selected) && File(directory, selected).isFile) {
        val marker = File(directory, "$selected.name")
        if (!marker.exists()) marker.writeText("导入字体") // Preserve the previously selected legacy import.
    }
    return directory.listFiles().orEmpty().mapNotNull { marker ->
        val key = marker.name.removeSuffix(".name")
        if (!marker.name.endsWith(".name") || !importedFontName.matches(key) || !File(directory, key).isFile) null
        else AppearanceFont(key, runCatching { marker.readText().take(128) }.getOrDefault("导入字体"))
    }.sortedWith(compareBy({ it.label.lowercase(Locale.ROOT) }, { it.key }))
}

internal fun importAppearanceFont(context: Context, uri: Uri): AppearanceFont {
    val displayName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    val label = displayName.substringBeforeLast('.', displayName).filterNot(Char::isISOControl)
        .take(128).ifBlank { "导入字体" }
    val key = importCustomFont(context, uri)
    try {
        File(context.filesDir, "custom-fonts/$key.name").writeText(label)
    } catch (error: Exception) { deleteCustomFont(context, key); throw error }
    return AppearanceFont(key, label)
}

internal fun appearanceFontLabel(context: Context, key: String): String = when {
    key.isBlank() -> "默认"
    key.startsWith(SYSTEM_FONT_PREFIX) -> key.removePrefix(SYSTEM_FONT_PREFIX)
    else -> importedAppearanceFonts(context, key).firstOrNull { it.key == key }?.label ?: "导入字体"
}
