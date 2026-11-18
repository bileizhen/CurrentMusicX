package io.github.currencortex.music.data.qqmusic

import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.library.Daily

data class QqAccountState(val loggedIn: Boolean = false, val nickname: String = "", val avatar: String = "",
    val uin: String = "", val revision: Long = 0)
data class QqHomeSnapshot(
    val daily: Daily? = null,
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val forYou: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recent: QqListeningRankPage? = null,
    val complete: Boolean = false,
)
data class QqLoginChannel(val id: String)
data class QqLoginCapabilities(
    val configured: Boolean = false,
    val sessionMode: String = "",
    val channels: List<QqLoginChannel> = emptyList(),
    val features: Map<String, Boolean> = emptyMap(),
) {
    val availableChannels: List<QqLoginChannel>
        get() = if (configured) channels.filter { it.id in SUPPORTED_CHANNELS } else emptyList()
    val defaultChannelId: String?
        get() = availableChannels.firstOrNull()?.id

    private companion object {
        val SUPPORTED_CHANNELS = setOf("qq", "qqmusic", "wechat")
    }
}
data class QqMusicCategorySort(val id: Int, val name: String)
data class QqMusicCategory(val id: String, val name: String, val group: String,
    val sorts: List<QqMusicCategorySort> = emptyList())
data class QqCategoryPlaylistPage(val playlists: List<Playlist> = emptyList(), val total: Int = 0,
    val hasMore: Boolean = false)
data class QqCatalogEntry(val id: String, val name: String, val cover: String = "", val subtitle: String = "", val type: String = "playlist")
data class QqFavoriteCount(val count: Long, val display: String = "")
data class QqCatalogPage(val entries: List<QqCatalogEntry> = emptyList(), val total: Int = 0, val hasMore: Boolean = false)
data class QqCatalogDetail(val entry: QqCatalogEntry, val songs: List<Song> = emptyList(),
    val albums: List<QqCatalogEntry> = emptyList(), val hasMore: Boolean = false)
data class QqArtistDetail(
    val mid: String,
    val name: String,
    val cover: String = "",
    val alias: String = "",
    val brief: String = "",
    val fans: Long = 0,
    val totalSong: Int = 0,
    val totalAlbum: Int = 0,
    val totalMv: Int = 0,
    val songs: List<Song> = emptyList(),
    val albums: List<QqCatalogEntry> = emptyList(),
    val hasMoreSongs: Boolean = false,
    val hasMoreAlbums: Boolean = false,
    val albumsFailed: Boolean = false,
)
data class QqAlbumDetail(
    val mid: String,
    val title: String,
    val singerNames: String = "",
    val singerMids: List<String> = emptyList(),
    val cover: String = "",
    val description: String = "",
    val publishDate: String = "",
    val company: String = "",
    val language: String = "",
    val albumType: String = "",
    val songs: List<Song> = emptyList(),
    val totalSongs: Int = 0,
    val hasMoreSongs: Boolean = false,
)
data class QqSearchPage(val songs: List<Song> = emptyList(), val entries: List<QqCatalogEntry> = emptyList(),
    val total: Int = 0, val hasMore: Boolean = false)
data class QqComment(val id: String, val text: String, val nickname: String = "", val avatar: String = "",
    val likes: Long = 0, val time: Long = 0, val likedByMe: Boolean = false, val isMine: Boolean = false,
    val replyCount: Int = 0, val seqNo: String = "", val quotedNickname: String = "", val quotedContent: String? = null)
data class QqCommentsPage(val comments: List<QqComment> = emptyList(), val total: Int = 0,
    val hasMore: Boolean = false, val nextSeq: String = "")
data class QqSongPage(val songs: List<Song>, val hasMore: Boolean = false, val nextPage: Int? = null)
data class QqRadioScene(val id: String, val name: String, val poolId: String = "",
    val cover: String = "", val sort: Int = 0, val status: Int = 0)
data class QqPreferenceOption(val id: String, val name: String, val status: Int = 0, val type: Int = 0)
data class QqPreferenceGroup(val name: String, val type: Int = 0, val options: List<QqPreferenceOption> = emptyList())
data class QqListeningRankEntry(val song: Song, val listenCount: Long = 0)
data class QqListeningRankPage(
    val entries: List<QqListeningRankEntry> = emptyList(),
    val from: String = "",
    val token: String = "",
)
