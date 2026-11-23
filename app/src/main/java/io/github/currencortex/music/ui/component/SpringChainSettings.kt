package io.github.currencortex.music.ui.component

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.effect.SpringChainState
import io.github.suqi8.coui.kmp.effect.rememberSpringChainState
import io.github.suqi8.coui.kmp.effect.springChain
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

/**
 * [LazyColumn] with COUI's edge spring chain: pulling past the first or last row fans the rows out
 * and springs them back in sequence. Scrolling and item keys behave exactly like [LazyColumn].
 */
@Composable
fun SpringLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyListScope.() -> Unit,
) {
    val chain = rememberSpringChainState(state)
    LazyColumn(
        modifier = modifier.springChain(chain),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect,
    ) {
        springChainSettingsItems(chain, content)
    }
}
