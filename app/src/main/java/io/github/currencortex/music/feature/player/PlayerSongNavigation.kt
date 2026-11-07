package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.library.catalogRoute
import io.github.currencortex.music.ui.component.MusicChoiceDialog
import io.github.currencortex.music.ui.component.MusicDestinationRow
import kotlinx.coroutines.withTimeoutOrNull

internal data class PlayerArtistLink(val name: String, val id: Long) {
    fun route(biography: Boolean) = "lib/artist/$id" + if (biography) "/bio" else ""
}

internal fun playerArtistLinks(song: Song?): List<PlayerArtistLink> {
    if (song == null || song.musicSource != MusicSource.NETEASE) return emptyList()
    val names = song.artists.split(" / ")
    return song.artistIds.mapIndexedNotNull { index, id ->
        if (id <= 0) null else PlayerArtistLink(
            if (names.size == song.artistIds.size) names[index].ifBlank { "艺人 ${index + 1}" }
            else "艺人 ${index + 1}", id)
    }.distinctBy { it.id }
}

internal fun playerAlbumRoute(song: Song): String =
    if (song.musicSource == MusicSource.NETEASE && song.albumId > 0) "lib/album/${song.albumId}"
    else catalogRoute(true, song.album)

internal class PlayerSongNavigation(val artistsEnabled: Boolean, val albumEnabled: Boolean,
    val albumLoading: Boolean, val dialogActive: Boolean, val openArtists: (Boolean) -> Unit, val openAlbum: () -> Unit)

/** Requests belong to the displayed song; changing songs or servers cancels an unfinished lookup. */
@Composable internal fun rememberPlayerSongNavigation(song: Song?, repository: MusicRepository,
    server: String, navigate: (String) -> Unit): PlayerSongNavigation {
    val artists = remember(song) { playerArtistLinks(song) }
    val currentNavigate by rememberUpdatedState(navigate)
    var chooseBiography by remember(song, server) { mutableStateOf<Boolean?>(null) }
    var albumLoading by remember(song, server) { mutableStateOf(false) }
    val artistsEnabled = artists.isNotEmpty() || !song?.artists.isNullOrBlank()
    val albumEnabled = !song?.album.isNullOrBlank() || song?.let { it.musicSource == MusicSource.NETEASE && it.albumId > 0 } == true
    LaunchedEffect(song, server, albumLoading) {
        if (!albumLoading || song == null) return@LaunchedEffect
        val id = NeteaseSongActionsRepository.songId(song)
        val detail = if (id != null) withTimeoutOrNull(5_000) { appResult { repository.detail(id) } } else null
        val resolved = (detail as? AppResult.Success)?.value?.takeIf { it.id == id && it.albumId > 0 }
        albumLoading = false
        currentNavigate(playerAlbumRoute(resolved ?: song))
    }
    chooseBiography?.let { biography ->
        MusicChoiceDialog(if (biography) "选择艺人百科" else "选择歌手", { chooseBiography = null }) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                artists.forEach { artist ->
                    MusicDestinationRow(artist.name, {
                        chooseBiography = null
                        currentNavigate(artist.route(biography))
                    }, modifier = Modifier.testTag("player_choose_artist_${artist.id}"))
                }
            }
        }
    }
    return PlayerSongNavigation(artistsEnabled, albumEnabled, albumLoading, chooseBiography != null,
        openArtists = { biography ->
            when {
                artists.size == 1 -> currentNavigate(artists.single().route(biography))
                artists.size > 1 -> chooseBiography = biography
                song != null && artistsEnabled -> currentNavigate(catalogRoute(false, song.artists))
            }
        }, openAlbum = {
            if (song != null && albumEnabled && !albumLoading) {
                if (song.musicSource == MusicSource.NETEASE && song.albumId <= 0) albumLoading = true
                else currentNavigate(playerAlbumRoute(song))
            }
        })
}
