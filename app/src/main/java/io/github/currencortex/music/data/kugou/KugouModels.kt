package io.github.currencortex.music.data.kugou

import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song

data class KugouAccountState(val loggedIn: Boolean = false, val nickname: String = "", val avatar: String = "",
    val userId: Long = 0, val revision: Long = 0)

/** Persisted device identity and, after login, the account credentials. */
data class KugouCredentials(
    val userId: Long,
    val token: String,
    val nickname: String = "",
    val avatar: String = "",
    val vipType: Int = 0,
    val appId: String = KugouProtocol.APP_ID,
)

data class KugouDevice(val mid: String, val dfid: String = "-")

enum class KugouQrStatus { WAITING, SCANNED, CONFIRMED, EXPIRED }
data class KugouQrSession(val key: String, val content: String)
data class KugouQrResult(val status: KugouQrStatus, val credentials: KugouCredentials? = null)
data class KugouMultiAccountOption(val userId: Long, val nickname: String, val avatar: String = "")
sealed interface KugouLoginOutcome {
    data class Success(val credentials: KugouCredentials) : KugouLoginOutcome
    data class MultiAccount(val accounts: List<KugouMultiAccountOption>) : KugouLoginOutcome
}

data class KugouCatalogEntry(val id: String, val name: String, val cover: String = "", val subtitle: String = "",
    val type: String = "playlist", val count: Int = 0)
data class KugouSearchPage(val songs: List<Song> = emptyList(), val entries: List<KugouCatalogEntry> = emptyList(),
    val total: Int = 0, val hasMore: Boolean = false)
data class KugouSongPage(val songs: List<Song> = emptyList(), val total: Int = 0, val hasMore: Boolean = false)
data class KugouRank(val id: String, val name: String, val cover: String = "", val intro: String = "",
    val updateText: String = "")
data class KugouAlbumDetail(val entry: KugouCatalogEntry, val intro: String = "", val songs: List<Song> = emptyList())
data class KugouArtistDetail(val id: String, val name: String, val cover: String = "", val intro: String = "",
    val songCount: Int = 0, val albumCount: Int = 0, val songs: List<Song> = emptyList(), val hasMoreSongs: Boolean = false)
data class KugouCategory(val id: String, val name: String, val group: String = "歌单风格")
data class KugouPlaylistPage(val playlists: List<Playlist> = emptyList(), val total: Int = 0, val hasMore: Boolean = false)
data class KugouComment(val id: String, val nickname: String, val avatar: String = "", val text: String,
    val time: Long = 0, val likes: Long = 0, val likedByMe: Boolean = false, val replyCount: Int = 0,
    val userId: Long = 0, val quotedNickname: String = "", val quotedContent: String? = null)
data class KugouCommentsPage(val comments: List<KugouComment> = emptyList(), val total: Long = 0, val hasMore: Boolean = false)
data class KugouFavoriteCount(val count: Long = 0, val display: String = "")
data class KugouVipInfo(val isVip: Boolean = false, val vipType: Int = 0, val label: String = "", val expireTime: String = "")
data class KugouHomeSnapshot(val daily: List<Song> = emptyList(), val ranks: List<KugouRank> = emptyList(),
    val newSongs: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val complete: Boolean = false)
