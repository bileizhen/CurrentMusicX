package io.github.currencortex.music.feature.binding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun BindingMedalsCard(state: BindingMedalsState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("binding_medals")) {
        Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("我的勋章", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                state.value?.let { Text("${it.total} 枚", fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f)) }
            }
            when {
                state.loading -> Text("正在加载勋章…", Modifier.padding(horizontal = 18.dp), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                state.error != null -> Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error, Modifier.weight(1f), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                    BindingAction("重试", onRetry, Modifier.testTag("binding_medals_retry"))
                }
                state.value != null -> if (state.value.medals.isEmpty()) {
                    Text("还没有获得勋章", Modifier.padding(horizontal = 18.dp).testTag("binding_medals_empty"), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                } else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.value.medals) { index, medal ->
                        Column(Modifier.width(96.dp).testTag("binding_medal_$index"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(medal.image, null, Modifier.size(72.dp), contentScale = ContentScale.Fit)
                            Text(medal.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
