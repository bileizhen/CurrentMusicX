package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.effect.SpringChainState
import io.github.suqi8.coui.kmp.effect.springChainItem

/** Wraps settings list items without changing their keys or content types. */
internal fun LazyListScope.springChainSettingsItems(
    state: SpringChainState,
    content: LazyListScope.() -> Unit,
) {
    val target = this
    var row = 0
    val scope = object : LazyListScope by target {
        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
            val currentRow = row++
            target.item(key, contentType) {
                val itemScope = this
                Box(Modifier.springChainItem(state, currentRow)) { content(itemScope) }
            }
        }

        override fun items(
            count: Int,
            key: ((Int) -> Any)?,
            contentType: (Int) -> Any?,
            itemContent: @Composable LazyItemScope.(Int) -> Unit,
        ) {
            val start = row
            row += count
            target.items(count, key, contentType) { index ->
                val itemScope = this
                Box(Modifier.springChainItem(state, start + index)) { itemContent(itemScope, index) }
            }
        }
    }
    content(scope)
}
