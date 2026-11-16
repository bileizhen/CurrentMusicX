package io.github.currencortex.music.data.qqmusic

import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song

data class QqAccountState(val loggedIn: Boolean = false, val nickname: String = "", val avatar: String = "",
    val uin: String = "", val revision: Long = 0)
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
data class QqCatalogPage(val entries: List<QqCatalogEntry> = emptyList(), val total: Int = 0, val hasMore: Boolean = false)
data class QqCatalogDetail(val entry: QqCatalogEntry, val songs: List<Song> = emptyList(),
    val albums: List<QqCatalogEntry> = emptyList(), val hasMore: Boolean = false)
data class QqSearchPage(val songs: List<Song> = emptyList(), val entries: List<QqCatalogEntry> = emptyList(),
    val total: Int = 0, val hasMore: Boolean = false)
data class QqComment(val id: String, val text: String, val nickname: String = "", val avatar: String = "",
    val likes: Long = 0, val time: Long = 0)
data class QqCommentsPage(val comments: List<QqComment> = emptyList(), val total: Int = 0, val hasMore: Boolean = false)
data class QqSongPage(val songs: List<Song>, val hasMore: Boolean = false, val nextPage: Int? = null)
data class QqListeningRankEntry(val song: Song, val listenCount: Long = 0)
data class QqListeningRankPage(
    val entries: List<QqListeningRankEntry> = emptyList(),
    val from: String = "",
    val token: String = "",
)
