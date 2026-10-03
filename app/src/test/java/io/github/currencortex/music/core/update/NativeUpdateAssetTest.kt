package io.github.currencortex.music.core.update
import org.junit.Assert.*
import org.junit.Test
class NativeUpdateAssetTest {
    @Test fun nativeClientNeverOffersLegacyApkOrPreviewToStableChannel() {
        val checker = GitHubUpdateChecker("backrooms-yrc", "CurrentMusic", "0.1.0", "test", nativeAssetsOnly = true)
        fun release(tag: String, asset: String, pre: Boolean = false) = """{"tag_name":"$tag","prerelease":$pre,"html_url":"https://github.com/backrooms-yrc/CurrentMusic/releases/tag/$tag","assets":[{"name":"$asset","browser_download_url":"https://github.com/backrooms-yrc/CurrentMusic/releases/download/$tag/$asset"}]}"""
        val list = "[" + listOf(release("v1.28.11","CurrentMusic.apk"), release("v0.2.0","CurrentMusic-Android-v0.2.0.apk"),
            release("v0.3.0-rc.1","CurrentMusic-Android-v0.3.0-rc.1.apk",true)).joinToString(",") + "]"
        assertEquals("0.2.0", checker.parseResponse(list, UpdateChannel.STABLE)!!.version)
        assertEquals("0.3.0-rc.1", checker.parseResponse(list, UpdateChannel.PRERELEASE)!!.version)
        assertNull(checker.parseResponse("[" + release("v1.28.11","CurrentMusic.apk") + "]", UpdateChannel.STABLE))
    }
}
