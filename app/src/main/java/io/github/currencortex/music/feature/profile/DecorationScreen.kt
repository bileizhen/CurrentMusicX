package io.github.currencortex.music.feature.profile

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun DecorationScreen(vm: DecorationViewModel, onBack: () -> Unit = {}) {
    val state by vm.state.collectAsPageState()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val user = ProfileUser(id = account.account?.id ?: 0L, avatar = account.account?.avatar.orEmpty())
    DecorationPage(state, busy, message, onBack, vm::reload, vm::query, vm::preview, vm::set) { id, size ->
        UserAvatar(user, vm.container, size, id, reserveOverlay = false)
    }
}

private fun Decoration.label() = name.ifBlank { id }.removePrefix("key-").ifBlank { "不佩戴" }

@Composable internal fun DecorationPage(state: DecorationState, busy: Boolean, message: String?, onBack: () -> Unit,
    reload: () -> Unit, query: (String) -> Unit, preview: (Decoration?) -> Unit, apply: (String) -> Unit,
    avatar: @Composable (String, Dp) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val hint = colors.onSurfaceVariantSummary
    val catalog = state.catalog
    val selectedId = state.preview?.id ?: catalog?.current.orEmpty()
    val chosen = state.preview ?: catalog?.decorations?.firstOrNull { it.id == selectedId }
    val changed = catalog != null && selectedId != catalog.current
    val name = if (selectedId.isEmpty()) "不佩戴挂件" else chosen?.label() ?: selectedId.removePrefix("key-")
    val focus = LocalFocusManager.current
    val grid = rememberLazyGridState()
    LaunchedEffect(state.query) { grid.scrollToItem(0) }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).testTag("decoration_screen")) {
        val compact = maxHeight < 480.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = LocalMusicBottomInset.current)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainer)
                    .clickable(role = Role.Button, onClick = onBack).testTag("decoration_back"), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(23.dp))
                }
                Text("头像挂件", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                MusicTextAction(if (state.loading) "更新中" else "刷新", reload, enabled = !busy && !state.loading)
            }
            if (catalog != null && !compact) {
                Card(Modifier.fillMaxWidth().testTag("decoration_preview")) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(64.dp), Alignment.Center) {
                            Crossfade(selectedId, label = "decoration_preview") { id ->
                                Box(Modifier.fillMaxSize(), Alignment.Center) { avatar(id, 26.dp) }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(name, Modifier.testTag("decoration_preview_name"), fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (changed) "正在试戴" else "当前装扮", Modifier.weight(1f), fontSize = 11.sp,
                                    color = colors.primary, maxLines = 1)
                                if (!catalog.unlocked) Text("${catalog.listenMs.coerceAtLeast(0) / 60000} / ${catalog.minListenMs.coerceAtLeast(0) / 60000} 分钟解锁",
                                    fontSize = 11.sp, color = hint)
                            }
                            if (!catalog.unlocked) {
                                val progress = if (catalog.minListenMs > 0) (catalog.listenMs.toFloat() / catalog.minListenMs).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = .06f))) {
                                    if (progress > 0f) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.primary))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (catalog != null) {
                MusicSearchBar(state.query, { focus.clearFocus() }, onInput = query, placeholder = "搜索挂件名称或 ID",
                    inputTag = "decoration_query", modifier = Modifier.testTag("decoration_search"))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (state.query.isBlank()) "全部挂件" else "搜索结果", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    val count = catalog.decorations.count { it.name.contains(state.query.trim(), true) || it.id.contains(state.query.trim(), true) }
                    Text("$count 款", fontSize = 12.sp, color = hint)
                }
            }
            when {
                catalog == null -> Column(Modifier.weight(1f).fillMaxWidth(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text(if (state.loading) "正在加载挂件…" else state.error ?: "暂无可用挂件", fontSize = 14.sp, color = hint)
                    if (!state.loading) MusicTextAction("重新加载", reload)
                }
                else -> {
                    val matches = catalog.decorations.filter { it.name.contains(state.query.trim(), true) || it.id.contains(state.query.trim(), true) }
                    state.error?.let { Text("更新失败，可刷新重试", fontSize = 12.sp, color = hint, modifier = Modifier.padding(bottom = 8.dp)) }
                    if (matches.isEmpty() && state.query.isNotBlank()) Column(Modifier.weight(1f).fillMaxWidth(), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("没有找到相关挂件", fontSize = 15.sp)
                        MusicTextAction("清除搜索", { focus.clearFocus(); query("") })
                    } else LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.weight(1f).testTag("decoration_grid"), state = grid,
                        contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.query.isBlank()) item(key = "none") {
                            DecorationTile(Decoration("", "不佩戴"), selectedId.isEmpty(), catalog.current.isEmpty(), busy,
                                { focus.clearFocus(); preview(Decoration("", "不佩戴")) }, avatar)
                        }
                        items(matches, key = { "frame:${it.id}" }) { item ->
                            DecorationTile(item, selectedId == item.id, catalog.current == item.id, busy,
                                { focus.clearFocus(); preview(item) }, avatar)
                        }
                        if (matches.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("暂时没有上架的挂件", Modifier.padding(vertical = 20.dp), fontSize = 13.sp, color = hint)
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        message?.let { Text(it, fontSize = 12.sp, color = colors.primary,
                            modifier = Modifier.testTag("decoration_status")) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MusicTextAction("还原", { preview(null) }, enabled = changed && !busy)
                            TextButton(if (busy) "正在保存…" else if (!changed) "当前装扮" else if (selectedId.isEmpty()) "取消佩戴"
                                else if (!catalog.unlocked) "听歌后解锁" else "佩戴此挂件", { apply(selectedId) },
                                Modifier.weight(1f).testTag("wear_decoration"), enabled = !busy && changed && (selectedId.isEmpty() || catalog.unlocked),
                                colors = ButtonDefaults.textButtonColors(color = colors.primary, textColor = colors.onPrimary),
                                minHeight = 44.dp, textStyle = MiuixTheme.textStyles.button.copy(fontSize = 14.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DecorationTile(item: Decoration, selected: Boolean, worn: Boolean, busy: Boolean,
    onClick: () -> Unit, avatar: @Composable (String, Dp) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(if (selected) colors.primary.copy(alpha = .08f) else colors.surfaceContainer)
        .border(if (selected) 1.5.dp else 0.dp, if (selected) colors.primary.copy(alpha = .65f) else androidx.compose.ui.graphics.Color.Transparent, shape)
        .clickable(enabled = !busy, role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }
        .testTag("decoration_${item.id.ifEmpty { "none" }}").padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(78.dp), Alignment.Center) {
            avatar(item.id, 30.dp)
            if (selected) Icon(Icons.Default.Check, "已选择", Modifier.align(Alignment.TopEnd).size(16.dp), tint = colors.primary)
        }
        Text(item.label(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (worn) (if (item.id.isEmpty()) "当前" else "已佩戴") else if (selected) "试戴中" else "", fontSize = 10.sp, lineHeight = 14.sp,
            color = if (selected) colors.primary else colors.onSurfaceVariantSummary)
    }
}
