package io.github.currencortex.music.core.dlna

import io.github.currencortex.music.data.song.Song

object DidlMetadataBuilder {
    fun build(song: Song, url: String, mime: String): String {
        fun e(value: String) = DlnaXml.escape(value)
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="${song.id}" parentID="0" restricted="1"><dc:title>${e(song.name)}</dc:title><upnp:artist>${e(song.artists)}</upnp:artist><upnp:album>${e(song.album)}</upnp:album><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="http-get:*:${e(mime)}:*" duration="${dlnaTime(song.durationMs)}">${e(url)}</res></item></DIDL-Lite>"""
    }
}
