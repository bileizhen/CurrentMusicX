package io.github.currencortex.music.feature.share

import io.github.currencortex.music.data.song.*
import org.junit.Assert.*
import org.junit.Test

class SongShareContentTest {
    @Test fun publicLinkUsesTheActualNeteaseIdentity() {
        val content = SongShareContent.from(Song(10, "歌名\nLive", "歌手", externalIds = SongExternalIds(neteaseId = "3438848556")))
        assertEquals("https://music.20110208.xyz/app/#/song/3438848556", content.publicUrl)
        assertEquals("歌名 Live", content.title)
        assertTrue(content.text.contains(content.publicUrl!!))
        for (style in SongShareStyle.entries) assertEquals(
            "https://music.20110208.xyz/cm/share/song/3438848556/image?style=${style.apiValue}", content.imageUrl(style))
    }

    @Test fun privateAndUnrelatedSourceIdsDoNotBecomePublicNeteaseLinks() {
        for (source in MusicSource.entries.filter { it != MusicSource.NETEASE }) {
            val content = SongShareContent.from(Song(123, "Private song", "Artist", cover = "https://private.test/art?token=secret",
                musicSource = source, externalIds = SongExternalIds(neteaseId = "123")))
            assertNull(content.publicUrl)
            for (style in SongShareStyle.entries) assertNull(content.imageUrl(style))
            assertFalse(content.text.contains("secret"))
            assertFalse(content.text.contains("private.test"))
        }
        assertNull(SongShareContent.from(Song(-1, "Unknown")).publicUrl)
        assertNull(SongShareContent.from(Song(1, "Video", video = true)).publicUrl)
        assertNull(SongShareContent.from(Song(1, "Private", navidrome = NavidromeTrackRef("1", "server")))
            .imageUrl(SongShareStyle.IMMERSIVE))
        assertNull(SongShareContent.from(Song(1, "Private", emby = EmbyTrackRef("1", "server")))
            .imageUrl(SongShareStyle.SQUARE))
    }

    @Test fun documentNamesCannotEscapeTheSelectedDirectory() {
        val content = SongShareContent.from(Song(1, "../Music:Live/?*\"<>|\nSong".repeat(20)))
        assertTrue(content.filename.endsWith(".png"))
        assertTrue(content.filename.length <= 68)
        assertFalse(content.filename.contains('/'))
        assertFalse(content.filename.contains('\\'))
        assertEquals("未命名歌曲", SongShareContent.from(Song(0, "  ")).title)
    }
}
