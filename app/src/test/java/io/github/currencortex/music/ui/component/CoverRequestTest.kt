package io.github.currencortex.music.ui.component
import org.junit.Assert.*
import org.junit.Test
class CoverRequestTest {
    @Test fun cdnThumbnailsRequestBoundedPixelsOverHttps() {
        assertEquals("https://p3.music.126.net/cover.jpg?param=160y160",coverRequestUrl("http://p3.music.126.net/cover.jpg?param=2000y2000",160))
        assertTrue(coverRequestUrl("https://p4.music.126.net/cover.jpg",8000).endsWith("param=1200y1200"))
    }
    @Test fun thirdPartySignedImagesAndInvalidUrlsAreUntouched() {
        val signed="https://images.example.com/cover?signature=abc"
        assertEquals(signed,coverRequestUrl(signed,160));assertEquals("",coverRequestUrl("",160))
        val other="https://music.126.net.example.com/a";assertEquals(other,coverRequestUrl(other,160))
    }
}
