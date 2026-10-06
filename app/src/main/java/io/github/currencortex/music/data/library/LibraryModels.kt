package io.github.currencortex.music.data.library

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class SongListDto(val songs: List<SongDto> = emptyList())
@Serializable data class DailyDto(val daily: List<SongDto> = emptyList(), val forYou: List<SongDto> = emptyList(), val artists: List<String> = emptyList())
data class Daily(val songs: List<Song>, val forYou: List<Song>, val artists: List<String>)
@Serializable data class PlaylistDto(val id: Long, val name: String, val description: String = "", val cover: String = "",
    val source: String = "", @SerialName("user_id") val ownerId: Long = 0,
    @SerialName("track_count") val count: Int = 0, val tracks: List<SongDto> = emptyList()) {
    fun domain() = Playlist(id, name, description, cover, source, ownerId, count, tracks.map(SongDto::toDomain))
}
data class Playlist(val id: Long, val name: String, val description: String, val cover: String, val source: String,
    val ownerId: Long, val count: Int, val songs: List<Song>) {
    fun editable(accountId: Long) = source != "ncm" && ownerId == accountId && accountId > 0
}
@Serializable data class PlaylistsDto(val playlists: List<PlaylistDto> = emptyList())
@Serializable data class SongStatusDto(val liked: List<Long> = emptyList(), val faved: List<Long> = emptyList())
data class SongStatus(val liked: Boolean = false, val favorite: Boolean = false, val pending: Boolean = false)
@Serializable data class AlbumDto(val id: Long = 0, val name: String = "", val pic: String = "", val artist: String = "",
    val description: String = "", val publishTime: Long = 0, val company: String = "", val size: Int = 0,
    val songs: List<SongDto> = emptyList()) {
    fun domain() = Album(id, name, pic.replace("http:", "https:"), artist, description, songs.map(SongDto::toDomain), publishTime, size)
}
data class Album(val id: Long, val name: String, val cover: String, val artist: String, val description: String, val songs: List<Song>,
    val publishTime: Long = 0, val size: Int = 0)
@Serializable data class ArtistDto(val id: Long = 0, val name: String = "", val pic: String = "", val alias: String = "",
    val total: Int = 0, val more: Boolean = false, val songs: List<SongDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(), val albumTotal: Int = 0)
data class Artist(val id: Long, val name: String, val cover: String, val description: String, val songs: List<Song>,
    val more: Boolean, val albums: List<Album>, val albumTotal: Int, val songTotal: Int = 0)
@Serializable data class ArtistIntroduction(@SerialName("ti") val title: String = "", @SerialName("txt") val text: String = "")
@Serializable data class ArtistBiography(val briefDesc: String = "", val introduction: List<ArtistIntroduction> = emptyList(), val code: Int = 200)
@Serializable data class ArtistAlbumsDto(val albums: List<AlbumDto> = emptyList(), val more: Boolean = false)
@Serializable data class CatalogSearchDto(val artists: List<ArtistDto> = emptyList(), val albums: List<AlbumDto> = emptyList(),
    val hasMore: CatalogMore = CatalogMore(), val totals: CatalogTotals = CatalogTotals())
@Serializable data class CatalogMore(val artist: Boolean = false, val album: Boolean = false)
@Serializable data class CatalogTotals(val artist: Int = 0, val album: Int = 0)
data class CatalogEntry(val id: Long, val name: String, val cover: String, val subtitle: String = "")
data class CatalogPage(val entries: List<CatalogEntry>, val more: Boolean, val total: Int = 0)
@Serializable data class MvDto(val id: Long = 0, val name: String = "", val cover: String = "", val artistName: String = "",
    val artistId: Long = 0, val desc: String? = null, val duration: Long = 0)
@Serializable data class MvDetailDto(val data: MvDto)
@Serializable data class MvUrlData(val url: String? = null)
@Serializable data class MvUrlDto(val data: MvUrlData)
