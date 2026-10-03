package io.github.currencortex.music.feature.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.config.AppMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text

enum class LegalDocument(val title: String, val asset: String) {
    LICENSE("开源许可", "legal/GPL-3.0.txt"),
    NOTICES("第三方声明", "legal/NOTICES.md"),
    APACHE("Apache License 2.0", "legal/APACHE-2.0.txt"),
    PRIVACY("隐私说明", "legal/PRIVACY.md"),
}

@Composable
fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit, onOpenDocument: (LegalDocument) -> Unit) {
    val context = LocalContext.current
    var text by remember(document) { mutableStateOf<String?>(null) }
    var error by remember(document) { mutableStateOf<String?>(null) }
    var attempt by remember(document) { mutableStateOf(0) }
    LaunchedEffect(document, attempt) {
        error = null
        try {
            text = withContext(Dispatchers.IO) {
                context.assets.open(document.asset).bufferedReader().use { it.readText() }
                    .replace("\r\n", "\n").replace('\r', '\n')
                    .replace("@@APP_NAME@@", AppMetadata.APP_NAME)
                    .replace("@@ISSUES_URL@@", AppMetadata.ISSUES_URL)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { error = "文档暂时无法读取，请重试。" }
    }
    val paragraphs = remember(text) { text?.split(Regex("\\n\\s*\\n")) ?: emptyList() }
    top.yukonga.miuix.kmp.basic.Scaffold(topBar = {
        top.yukonga.miuix.kmp.basic.SmallTopAppBar(title = document.title, navigationIcon = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("navigate_back")) {
                top.yukonga.miuix.kmp.basic.Icon(io.github.currencortex.music.ui.component.MusicIcons.Back, "返回")
            }
        })
    }, popupHost = {}) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).testTag("legal_document"), contentPadding = PaddingValues(16.dp, 24.dp, 16.dp, 48.dp)) {
        if (document == LegalDocument.LICENSE) item {
            top.yukonga.miuix.kmp.preference.ArrowPreference(title = "Apache License 2.0",
                summary = "MIUIX、AndroidX 等组件的许可", onClick = { onOpenDocument(LegalDocument.APACHE) })
        }
        if (error != null) item { BasicComponent(title = error, summary = "点击重试", onClick = { attempt++ }) }
        else if (text == null) item { Text("正在读取…") }
        items(paragraphs) { paragraph ->
            val heading = paragraph.startsWith('#')
            SelectionContainer {
                Text(if (heading) paragraph.trimStart('#', ' ') else paragraph,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontSize = if (heading) 20.sp else 14.sp,
                    fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = if (document == LegalDocument.LICENSE || document == LegalDocument.APACHE) FontFamily.Monospace else FontFamily.Default)
            }
        }
    }
}

}
