package io.github.currencortex.music.feature.qqmusic

import androidx.compose.runtime.Composable
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibrarySongActions
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.component.LocalDismissSongMenu
import io.github.currencortex.music.ui.component.MusicDestinationRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** QQ-only additions stay on top of the shared song actions owned by LibraryViewModel. */
@Composable
internal fun QqSongMenuActions(
    container: AppContainer,
    library: LibraryViewModel,
    song: Song,
    navigate: (String) -> Unit,
    scope: CoroutineScope,
) {
    LibrarySongActions(library, song, navigate)
    val dismiss = LocalDismissSongMenu.current
    MusicDestinationRow("查看 MV", {
        dismiss()
        scope.launch {
            when (val result = appResult { container.qqMusic.songMvs(song) }) {
                is AppResult.Success -> {
                    val mv = result.value.firstOrNull()
                    if (mv == null) container.errorNotices.show("这首歌暂时没有 MV")
                    else navigate(qqRoute("mv", mv.id))
                }
                is AppResult.Failure -> container.errorNotices.show(result.message)
            }
        }
    }, chevron = false)
}
