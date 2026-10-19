package io.github.currencortex.music.feature.announcement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun AnnouncementArchiveScreen(vm: AnnouncementArchiveViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    AnnouncementArchivePage(state, onBack, vm::refresh)
}

@Composable internal fun AnnouncementArchivePage(state: AnnouncementArchiveState, onBack: () -> Unit,
    refresh: () -> Unit, onOpenLink: ((String) -> Unit)? = null) {
    val colors = MiuixTheme.colorScheme
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    // A refreshed catalog cannot leave a detail dialog pointing at a different announcement.
    LaunchedEffect(state.items) { selected = null }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("announcement_archive")) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainer)
                .clickable(role = Role.Button, onClick = onBack).testTag("announcement_back"), Alignment.Center) {
                Icon(MusicIcons.Back, "返回", Modifier.size(23.dp))
            }
            Text("公告", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            MusicTextAction(if (state.loading) "加载中" else "刷新", refresh,
                Modifier.testTag("announcement_refresh"), enabled = !state.loading)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("announcement_list"),
            contentPadding = musicScrollPadding(PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.error != null) item {
                Text(state.error, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                MusicTextAction("重试", refresh, Modifier.testTag("announcement_retry"))
            }
            if (state.loading && state.items.isEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("正在加载公告…", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            if (!state.loading && state.error == null && state.items.isEmpty()) item {
                Text("暂无公告", Modifier.padding(vertical = 24.dp).testTag("announcement_empty"),
                    fontSize = 15.sp, color = colors.onSurfaceVariantSummary)
            }
            if (state.items.isNotEmpty()) item {
                Text("全部公告 · ${state.items.size} 条", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            }
            itemsIndexed(state.items, key = { index, item -> "${item.id}:$index" }) { index, item ->
                Card(Modifier.fillMaxWidth().testTag("announcement_item_${item.id}")
                    .clickable(role = Role.Button) { selected = index }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.pinned) Text("置顶", fontSize = 12.sp, color = colors.primary)
                        Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        val date = remember(item.createdAt) {
                            if (item.createdAt <= 0) "" else runCatching {
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                                    .format(Instant.ofEpochSecond(item.createdAt))
                            }.getOrDefault("")
                        }
                        Text(listOf(item.author.ifBlank { "CurrentStation" }, date).filter(String::isNotBlank).joinToString(" · "),
                            fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
    selected?.takeIf { it in state.items.indices }?.let { index ->
        key(state.items) {
            AnnouncementDialog(state.items, initialIndex = index, showSuppressionAction = false,
                onOpenLink = onOpenLink, onDismiss = { selected = null })
        }
    }
}
