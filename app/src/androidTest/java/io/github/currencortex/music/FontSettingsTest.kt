package io.github.currencortex.music

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class FontSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private lateinit var container: AppContainer
    private lateinit var vm: SettingsViewModel
    private var nextUri: Uri? = null
    private var launches = 0
    private val created = mutableListOf<String>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) {
            val intent = contract.createIntent(context, input)
            assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, intent.action)
            launches++
            dispatchResult(requestCode, nextUri)
        }
    }
    @Before fun prepare() = runBlocking {
        container = AppContainer(context, "font-settings-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT, liquidGlass = false) }
        vm = SettingsViewModel(container.settings)
    }
    private fun start() {
        compose.setContent {
            val settings by container.settings.state.collectAsState()
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                LeiTheme(settings) { FontSettingsScreen(vm, {}) }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("font_choice_system:casual").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun finish() {
        created.forEach { deleteCustomFont(context, it) }
        container.close()
    }
    private fun choose(key: String) {
        compose.onNodeWithTag("font_list").performScrollToNode(hasTestTag("font_choice_$key"))
        compose.onNodeWithTag("font_choice_$key").performClick()
        compose.waitUntil(5000) { container.settings.state.value.customFont == key.takeUnless { it == "default" }.orEmpty() }
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun installedFamiliesSearchAndSelectGloballyThenRestoreDefault() = runBlocking {
        val families = installedFontFamilies()
        assertTrue(families.any { it.label == "casual" })
        assertTrue(families.any { it.label == "serif" })
        start()
        compose.waitUntil(5000) {
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("casual").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(result) }
            result.firstOrNull()?.layoutInput?.style?.fontFamily.let { it != null && it != androidx.compose.ui.text.font.FontFamily.Default }
        }
        val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("casual").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(result) }
        val density = context.resources.displayMetrics.scaledDensity
        val paint = android.text.TextPaint().apply {
            typeface = android.graphics.Typeface.Builder(File("/system/fonts/ComingSoon.ttf")).build()
            textSize = 17 * density
        }
        assertEquals("The preview must draw the family's actual glyphs", paint.measureText("casual"), result.first().getLineRight(0), 2f)
        capture("font-settings-light.png")
        compose.onNodeWithTag("font_search").performTextReplacement("MONOSPACE")
        compose.onNodeWithTag("font_choice_system:monospace").assertExists().performClick()
        compose.waitUntil(5000) { container.settings.state.value.customFont == "system:monospace" }
        assertNotNull(loadCustomTypeface(context, container.settings.snapshot().customFont))
        compose.onNodeWithTag("font_choice_system:casual").assertDoesNotExist()
        compose.onNodeWithTag("font_search").performTextReplacement("no-such-font-123")
        compose.onNodeWithTag("font_search_empty").assertExists()
        compose.onNodeWithTag("font_search").performTextReplacement("")
        compose.onNodeWithTag("font_choice_default").performClick()
        compose.waitUntil(5000) { container.settings.state.value.customFont.isEmpty() }
        compose.onNodeWithTag("font_choice_default").assertIsSelected()
        compose.onNodeWithTag("font_search_cancel").performClick()
        compose.waitUntil(5000) {
            compose.onNodeWithTag("font_settings_page").fetchSemanticsNode().boundsInRoot.height > compose.onRoot().fetchSemanticsNode().boundsInRoot.height * .95f
        }
        container.settings.edit { it.copy(themeMode = ThemeMode.DARK) }
        compose.waitUntil(5000) {
            val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("字体").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layout) }
            val ink = layout.firstOrNull()?.layoutInput?.style?.color
            ink != null && ink.red > .5f
        }
        capture("font-settings-dark.png")
    }
    @Test fun upstreamSearchSeparatesClearAndCancelAndMatchesEveryTerm() = runBlocking {
        container.settings.edit { it.copy(liquidGlass = true) }
        start()
        val dock = compose.onNodeWithTag("font_search_dock")
        val initial = compose.onNodeWithTag("font_search").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("font_search_cancel").assertDoesNotExist()
        compose.onNodeWithTag("font_search").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(160)
        val opening = dock.fetchSemanticsNode().config[io.github.currencortex.music.ui.component.nativeui.DockedSearchExpansion]
        assertTrue("Cancel unfolds on the upstream spring", opening > 0f && opening < 1.05f)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("font_search_cancel").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("font_search").fetchSemanticsNode().boundsInRoot.width < initial)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("font_search").performTextReplacement("SERIF   sans")
        compose.onNodeWithTag("font_choice_system:sans-serif").assertExists()
        compose.onNodeWithTag("font_choice_default").assertDoesNotExist()
        compose.onNodeWithTag("font_choice_system:casual").assertDoesNotExist()
        compose.onNodeWithTag("font_search").performImeAction()
        compose.onNodeWithTag("font_search").assertTextEquals("SERIF   sans")
        compose.onNodeWithTag("font_search_clear").performClick()
        compose.waitUntil(5000) { compose.onNodeWithTag("font_search").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].toString().isEmpty() }
        compose.onNodeWithTag("font_search").assertIsFocused()
        compose.onNodeWithTag("font_search_cancel").assertIsDisplayed()
        compose.onNodeWithTag("font_search").performTextReplacement("monospace")
        compose.mainClock.advanceTimeBy(1500)
        capture("font-search-nativeui-active.png")
        var lastY = Float.NaN
        var stableSince = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(5000) {
            val y = compose.onNodeWithTag("font_search_cancel").fetchSemanticsNode().boundsInRoot.center.y
            if (y != lastY) { lastY = y; stableSince = android.os.SystemClock.uptimeMillis() }
            android.os.SystemClock.uptimeMillis() - stableSince > 400
        }
        val frame = compose.onNodeWithTag("font_search_surface").fetchSemanticsNode().config[io.github.currencortex.music.ui.component.nativeui.DockedSearchFrame]
        assertEquals("The controls rest 12 dp apart", 12 * context.resources.displayMetrics.density,
            frame.droplet.left - frame.bounds.right, 1f)
        compose.onNodeWithTag("font_search_cancel").performClick()
        compose.waitUntil(5000) { compose.onNodeWithTag("font_search").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].toString().isEmpty() }
        compose.onNodeWithTag("font_search").assertIsNotFocused()
        compose.onNodeWithTag("font_choice_default").assertExists()
        compose.onNodeWithTag("font_search_cancel").assertDoesNotExist()
        compose.waitUntil(5000) {
            dock.fetchSemanticsNode().config[io.github.currencortex.music.ui.component.nativeui.DockedSearchExpansion] == 0f
        }
        capture("font-search-nativeui-rest.png")
    }

    @Test fun importedFontSurvivesSwitchingAndPickerCancelAndInvalidFile() = runBlocking {
        val source = File(context.cacheDir, "diagnostics/CurrentMusic-font-test.ttf").apply { parentFile?.mkdirs() }
        context.resources.openRawResource(R.font.lxgw_wenkai_regular).use { input -> source.outputStream().use { input.copyTo(it) } }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", source)
        try {
            start(); nextUri = uri
            compose.onNodeWithTag("font_import").performClick()
            compose.waitUntil(15000) { container.settings.state.value.customFont.endsWith(".font") }
            val key = container.settings.snapshot().customFont.also { created += it }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("font_choice_$key").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("CurrentMusic-font-test").assertExists()
            capture("font-settings-import.png")
            choose("system:casual"); choose("default")
            assertEquals("CurrentMusic-font-test", importedAppearanceFonts(context).first { it.key == key }.label)
            choose(key)
            // Private copy remains valid after the provider's source is removed.
            source.delete()
            assertNotNull(loadCustomTypeface(context, key))
            nextUri = null
            compose.onNodeWithTag("font_import").performClick()
            assertEquals(key, container.settings.snapshot().customFont)
            source.writeText("not a font")
            nextUri = uri
            val before = File(context.filesDir, "custom-fonts").listFiles().orEmpty().map { it.name }.toSet()
            compose.onNodeWithTag("font_import").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("font_error").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(key, container.settings.snapshot().customFont)
            assertEquals(before, File(context.filesDir, "custom-fonts").listFiles().orEmpty().map { it.name }.toSet())
            assertEquals(3, launches)
        } finally { source.delete() }
    }
    @Test fun legacyAppearanceImportIsRetainedButLyricsImportsAreNotListed() = runBlocking {
        val uri = Uri.parse("android.resource://${context.packageName}/${R.font.lxgw_wenkai_regular}")
        val legacy = importCustomFont(context, uri).also { created += it }
        val lyric = importCustomFont(context, uri).also { created += it }
        container.settings.edit { it.copy(customFont = legacy) }
        assertTrue(importedAppearanceFonts(context, legacy).any { it.key == legacy })
        assertFalse(importedAppearanceFonts(context).any { it.key == lyric })
        vm.selectFont("")
        assertTrue(importedAppearanceFonts(context).any { it.key == legacy })
        assertNotNull(loadCustomTypeface(context, lyric))
    }
}
