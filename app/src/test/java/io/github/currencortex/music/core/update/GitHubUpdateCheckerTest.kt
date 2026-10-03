package io.github.currencortex.music.core.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdateCheckerTest {
    private val checker = GitHubUpdateChecker("example", "app", "1.0.0", "Template/Test")
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) = JSONObject()
        .put("tag_name", tag).put("prerelease", prerelease).put("draft", draft)
        .put("html_url", "https://github.com/example/app/releases/tag/$tag")
        .put("body", "Release notes")
        .put("assets", JSONArray().put(JSONObject().put("name", "app.apk")
            .put("browser_download_url", "https://github.com/example/app/releases/download/$tag/app.apk")))

    @Test fun stableRejectsDraftsPrereleasesAndOlderVersions() {
        listOf(release("v1.2.0-rc.1", true), release("v1.2.0", draft = true), release("v1.0.0")).forEach {
            assertNull(checker.parseResponse(it.toString(), UpdateChannel.STABLE))
        }
        assertEquals("1.2.0", checker.parseResponse(release("v1.2.0").toString(), UpdateChannel.STABLE)?.version)
    }

    @Test fun prereleaseSelectsHighestVersionRegardlessOfApiOrdering() {
        val json = JSONArray().put(release("v1.1.0")).put(release("v1.3.0-beta.2", true))
            .put(release("v1.3.0", draft = true)).put(release("v1.2.0"))
        assertEquals("1.3.0-beta.2", checker.parseResponse(json.toString(), UpdateChannel.PRERELEASE)?.version)
    }

    @Test fun assetSizeAndOfficialDigestArePreservedForDownloadValidation() {
        val response = release("v1.2.0")
        response.getJSONArray("assets").getJSONObject(0)
            .put("size", 12345).put("digest", "sha256:" + "a".repeat(64))
        val parsed = checker.parseResponse(response.toString(), UpdateChannel.STABLE)!!
        assertEquals(12345L, parsed.size)
        assertEquals("a".repeat(64), parsed.sha256)
    }

    @Test fun untrustedReleaseLinksAreRejectedAndAssetsStayInsideTheRepository() {
        val untrusted = release("v1.2.0").put("html_url", "https://example.org/fake")
        assertNull(checker.parseResponse(untrusted.toString(), UpdateChannel.STABLE))
        val noTrustedAsset = release("v1.2.0")
        noTrustedAsset.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.org/app.apk")
        val parsed = checker.parseResponse(noTrustedAsset.toString(), UpdateChannel.STABLE)
        assertNotNull(parsed)
        assertNull(parsed?.apkUrl)
    }
}
