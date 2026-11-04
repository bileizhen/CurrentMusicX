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

    @Test fun downloadExperienceCanOfferTheStableAssetRepeatedlyWithoutChangingNormalEligibility() {
        val response = release("v1.0.0").toString()
        val experience = GitHubUpdateChecker("example", "app", "1.0.0-alpha.0", "Experience/Test")
        repeat(2) { assertEquals("1.0.0", experience.parseResponse(response, UpdateChannel.STABLE)?.version) }
        assertNull(checker.parseResponse(response, UpdateChannel.STABLE))
    }

    @Test fun stableRejectsDraftsPrereleasesAndOlderVersions() {
        listOf(release("v1.2.0-rc.1", true), release("v1.2.0", draft = true), release("v1.0.0")).forEach {
            assertNull(checker.parseResponse(it.toString(), UpdateChannel.STABLE))
        }
        assertEquals("1.2.0", checker.parseResponse(release("v1.2.0").toString(), UpdateChannel.STABLE)?.version)
    }

    @Test fun installedNotesAcceptExactVersionEvenWithoutAnInstallableAsset() {
        val installed = GitHubUpdateChecker("example", "app", "1.0.0-ci.119", "Test", nativeAssetsOnly = true)
        val json = release("v1.0.0-ci.119", prerelease = true).put("assets", JSONArray()).toString()
        assertEquals("Release notes", installed.parseInstalledResponse(json)?.notes)
        assertNull(installed.parseResponse(json, UpdateChannel.CI))
        assertNull(installed.parseInstalledResponse(release("v1.0.0").toString()))
        assertNull(installed.parseInstalledResponse(release("v1.0.0-ci.120", prerelease = true).toString()))
        assertEquals("1.0.0", checker.parseInstalledResponse(release("v1.0.0").toString())?.version)
    }

    @Test fun installedNotesRejectDraftsAndForeignReleasePages() {
        assertNull(checker.parseInstalledResponse(release("v1.0.0", draft = true).toString()))
        assertNull(checker.parseInstalledResponse(release("v1.0.0")
            .put("html_url", "https://example.org/release").toString()))
    }

    private fun ci(tag: String) = release(tag, prerelease = true).apply {
        getJSONArray("assets").getJSONObject(0)
            .put("name", "CurrentMusic-Android-$tag.apk")
            .put("browser_download_url", "https://github.com/example/app/releases/download/$tag/CurrentMusic-Android-$tag.apk")
            .put("size", 123).put("digest", "sha256:" + "a".repeat(64))
    }

    @Test fun ciOnlySelectsNewestVerifiedCiArtifactNotBetaOrStable() {
        val json = JSONArray().put(ci("v1.0.0-ci.9")).put(release("v9.0.0"))
            .put(ci("v1.0.0-ci.12")).put(release("v9.0.0-beta.1", true))
        assertEquals("1.0.0-ci.12", checker.parseResponse(json.toString(), UpdateChannel.CI)?.version)
        assertNull(GitHubUpdateChecker("example", "app", "1.0.0-ci.12", "Test")
            .parseResponse(json.toString(), UpdateChannel.CI))
        assertEquals("9.0.0", checker.parseResponse(json.toString(), UpdateChannel.STABLE)?.version)
    }

    @Test fun ciRejectsDraftMissingDigestAndWrongArtifact() {
        val draft = ci("v1.1.0-ci.1").put("draft", true)
        val noDigest = ci("v1.1.0-ci.2").apply { getJSONArray("assets").getJSONObject(0).remove("digest") }
        val wrongApk = ci("v1.1.0-ci.3").apply { getJSONArray("assets").getJSONObject(0).put("name", "app-debug.apk") }
        assertNull(checker.parseResponse(JSONArray().put(draft).put(noDigest).put(wrongApk).toString(), UpdateChannel.CI))
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
