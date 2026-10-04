package io.github.currencortex.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlainFloatingBar(selected: Int, labels: List<String>, icons: List<ImageVector>, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, embedded: Boolean = false) {
    val background = if (isInDarkTheme()) MiuixTheme.colorScheme.surfaceContainer else Color.White
    Row(
        modifier.fillMaxWidth().height(64.dp).testTag("plain_floating_bar")
            .then(if (embedded) Modifier else Modifier.shadow(8.dp, CircleShape).clip(CircleShape).background(background))
            .padding(4.dp).selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val tint = if (selected == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
            Column(
                Modifier.weight(1f).fillMaxHeight().testTag("tab_$index").clip(CircleShape)
                    .background(if (selected == index) tint.copy(alpha = .12f) else Color.Transparent)
                    .selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            ) {
                Icon(icons[index], null, tint = tint)
                Text(label, color = tint, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun StandardNavigationBar(selected: Int, labels: List<String>, icons: List<ImageVector>, onSelect: (Int) -> Unit) {
    NavigationBar(modifier = Modifier.testTag("standard_navigation_bar")) {
        labels.forEachIndexed { index, label ->
            NavigationBarItem(
                modifier = Modifier.weight(1f).testTag("tab_$index"),
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = icons[index],
                label = label,
            )
        }
    }
}

@Composable
fun MainSidebar(selected: Int, labels: List<String>, icons: List<ImageVector>, onSelect: (Int) -> Unit) {
    Column(
        Modifier.width(200.dp).fillMaxHeight().background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(16.dp).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("CurrentMusic", fontSize = 24.sp)
        labels.forEachIndexed { index, label ->
            val tint = if (selected == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
                    .background(if (selected == index) tint.copy(alpha = .12f) else Color.Transparent)
                    .selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icons[index], null, tint = tint)
                Text(label, color = tint)
            }
        }
    }
}

