package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdateSourceDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateSourceBadgeTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("1.2.0", "", "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.2.0",
        "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.2.0/app.apk", "app.apk", false, 1024)

    @Test fun everySourceHasALeftBadgeAndNewSourcesCanBeSelected() {
        val selected = mutableStateOf(UpdateSource.default)
        val check = UpdateSourceCheck { _, source ->
            when (source) {
                UpdateSource.GEEKERTAO -> UpdateSourceHealth(UpdateSourceStatus.LIMITED, 429, "HTTP 429")
                UpdateSource.DPIK -> UpdateSourceHealth(UpdateSourceStatus.TIMEOUT, 8000)
                else -> UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 125)
            }
        }
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                UpdateSourceDialog(release, selected.value, { selected.value = it }, {}, sourceCheck = check)
            }
        } }
        UpdateSource.available.forEach { source ->
            val badge = compose.onNodeWithTag("update_source_badge_${source.name}", useUnmergedTree = true)
            val label = compose.onNodeWithText(source.label, useUnmergedTree = true)
            compose.onNodeWithTag("update_source_${source.name}").performScrollTo()
            badge.assertIsDisplayed()
            assertTrue("Badge must be left of ${source.label}",
                badge.getUnclippedBoundsInRoot().right <= label.getUnclippedBoundsInRoot().left)
        }
        compose.onNode(hasText("限流") and hasAnyAncestor(hasTestTag("update_source_badge_GEEKERTAO")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("8000 ms") and hasAnyAncestor(hasTestTag("update_source_badge_DPIK")), useUnmergedTree = true).assertExists()
        listOf(UpdateSource.TBAP, UpdateSource.GH_PROXY).forEach { source ->
            compose.onNodeWithTag("update_source_${source.name}").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(source, selected.value) }
        }
        capture("update-source-badges.png")
    }

    @Test fun closingTheSelectorCancelsAllChecks() {
        val visible = mutableStateOf(true)
        val started = mutableSetOf<UpdateSource>()
        val cancelled = mutableSetOf<UpdateSource>()
        val check = UpdateSourceCheck { _, source ->
            started += source
            try { awaitCancellation() } finally { cancelled += source }
        }
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                if (visible.value) UpdateSourceDialog(release, UpdateSource.default, {}, { visible.value = false }, sourceCheck = check)
            }
        } }
        compose.runOnIdle { assertEquals(UpdateSource.available.toSet(), started) }
        compose.onNodeWithTag("update_source_cancel").performClick()
        compose.runOnIdle { assertEquals(started, cancelled) }
    }

    @Test fun completedChecksReorderRowsButKeepTheSelectedSource() {
        val selected = mutableStateOf(UpdateSource.CURRENTMUSIC)
        val pending = UpdateSource.available.associateWith { CompletableDeferred<UpdateSourceHealth>() }
        val check = UpdateSourceCheck { _, source -> pending.getValue(source).await() }
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                UpdateSourceDialog(release, selected.value, { selected.value = it }, {}, sourceCheck = check)
            }
        } }
        compose.onNodeWithTag("update_source_GH_PROXY").performScrollTo().performClick()
        compose.runOnIdle {
            pending.getValue(UpdateSource.CURRENTMUSIC).complete(UpdateSourceHealth(UpdateSourceStatus.UNAVAILABLE, 1000))
            pending.getValue(UpdateSource.TBAP).complete(UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 50))
            pending.getValue(UpdateSource.GITHUB).complete(UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 100))
            pending.getValue(UpdateSource.GH_PROXY).complete(UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 200))
            pending.getValue(UpdateSource.GEEKERTAO).complete(UpdateSourceHealth(UpdateSourceStatus.LIMITED, 25))
            pending.getValue(UpdateSource.DPIK).complete(UpdateSourceHealth(UpdateSourceStatus.TIMEOUT, 5000))
        }
        val ordered = listOf(UpdateSource.CURRENTMUSIC, UpdateSource.TBAP, UpdateSource.GITHUB,
            UpdateSource.GH_PROXY, UpdateSource.GEEKERTAO, UpdateSource.DPIK)
        ordered.zipWithNext().forEach { (above, below) ->
            assertTrue("$above must remain above $below", compose.onNodeWithTag("update_source_${above.name}")
                .getUnclippedBoundsInRoot().top < compose.onNodeWithTag("update_source_${below.name}").getUnclippedBoundsInRoot().top)
        }
        compose.onNodeWithTag("update_source_GH_PROXY").assertIsSelected()
        compose.runOnIdle { assertEquals(UpdateSource.GH_PROXY, selected.value) }
        capture("update-source-sorted.png")
    }

    @Test fun sortingMovesThroughIntermediatePositionsWhileDedicatedSourceStaysPinned() {
        val selected = mutableStateOf(UpdateSource.GH_PROXY)
        val pending = UpdateSource.available.associateWith { CompletableDeferred<UpdateSourceHealth>() }
        val check = UpdateSourceCheck { _, source -> pending.getValue(source).await() }
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                UpdateSourceDialog(release, selected.value, { selected.value = it }, {}, sourceCheck = check)
            }
        } }
        fun top(source: UpdateSource) = compose.onNodeWithTag("update_source_${source.name}")
            .getUnclippedBoundsInRoot().top.value
        val pinned = top(UpdateSource.CURRENTMUSIC)
        val start = top(UpdateSource.TBAP)
        val target = top(UpdateSource.GITHUB)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            pending.getValue(UpdateSource.CURRENTMUSIC).complete(UpdateSourceHealth(UpdateSourceStatus.UNAVAILABLE, 1000))
            pending.getValue(UpdateSource.TBAP).complete(UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 50))
            pending.getValue(UpdateSource.GH_PROXY).complete(UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 100))
            pending.getValue(UpdateSource.GITHUB).complete(UpdateSourceHealth(UpdateSourceStatus.AVAILABLE, 300))
            pending.getValue(UpdateSource.GEEKERTAO).complete(UpdateSourceHealth(UpdateSourceStatus.LIMITED, 25))
            pending.getValue(UpdateSource.DPIK).complete(UpdateSourceHealth(UpdateSourceStatus.TIMEOUT, 5000))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val middle = top(UpdateSource.TBAP)
        assertTrue("Source must move between its old and new positions: $start -> $middle -> $target",
            middle < start - .5f && middle > target + .5f)
        assertEquals(pinned, top(UpdateSource.CURRENTMUSIC), .5f)
        capture("update-source-motion-middle.png")
        compose.mainClock.advanceTimeBy(120)
        val later = top(UpdateSource.TBAP)
        assertTrue("Source must continue toward its destination", later < middle)
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(target, top(UpdateSource.TBAP), .5f)
        assertEquals(pinned, top(UpdateSource.CURRENTMUSIC), .5f)
        compose.onNodeWithTag("update_source_GH_PROXY").assertIsSelected()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("update_source_TBAP").performTouchInput { click() }
        compose.onNodeWithTag("update_source_TBAP").assertIsSelected()
        compose.runOnIdle { assertEquals(UpdateSource.TBAP, selected.value) }
        capture("update-source-motion-settled.png")
    }

    @Test fun liveApkHeaderChecksWhenReleaseMetadataIsProvided() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("source_probe_url")
        org.junit.Assume.assumeTrue("Public network checks require explicit metadata", url != null)
        val target = release.copy(apkUrl = url, size = args.getString("source_probe_size")!!.toLong())
        val probe = UpdateSourceProbe()
        val results = UpdateSource.available.map { source -> async {
            val result = probe.check(target, source)
            assertNotNull(result.latencyMs)
            assertNotEquals(UpdateSourceStatus.CHECKING, result.status)
            "${source.name}: ${result.status}, ${result.latencyMs} ms, ${result.detail}"
        } }.awaitAll()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        File(context.getExternalFilesDir(null), "update-source-live-status.txt").writeText(results.joinToString("\n"))
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
