package io.github.currencortex.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import io.github.currencortex.music.core.network.SourceAvailabilityStatus
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun SourceAvailabilityBadge(status: SourceAvailabilityStatus, modifier: Modifier = Modifier,
    description: String = status.label) {
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > .5f
    val color = when (status) {
        SourceAvailabilityStatus.AVAILABLE -> if (dark) Color(0xFF73D4A4) else Color(0xFF24865B)
        SourceAvailabilityStatus.LIMITED, SourceAvailabilityStatus.TIMEOUT -> if (dark) Color(0xFFE8BA69) else Color(0xFFAD731B)
        SourceAvailabilityStatus.UNAVAILABLE -> if (dark) Color(0xFFF28F99) else Color(0xFFCD5058)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Box(modifier.width(with(LocalDensity.current) { 44.sp.toDp() })
        .background(color.copy(alpha = .12f), RoundedCornerShape(8.dp)).padding(horizontal = 4.dp, vertical = 5.dp)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(status.label, color = color, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}
