package io.github.currencortex.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun MusicCategoryTabs(tabs: List<Pair<String, String>>, selectedKey: String, onSelect: (String) -> Unit,
    tagPrefix: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEach { (key, label) ->
            val active = key == selectedKey
            val source = remember(key) { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(160), label = "category press")
            val ink by animateColorAsState(if (active) colors.primary else colors.onSurface.copy(alpha = .55f), tween(200), label = "category ink")
            val indicator by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "category indicator")
            Column(Modifier.weight(1f).heightIn(min = 52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .semantics { selected = active }.clickable(source, indication = null, role = Role.Tab) { onSelect(key) }
                .testTag("${tagPrefix}_$key"), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(label, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = ink)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(22.dp).height(3.dp).graphicsLayer { alpha = indicator; scaleX = indicator }
                    .background(colors.primary, RoundedCornerShape(2.dp)))
            }
        }
    }
}
