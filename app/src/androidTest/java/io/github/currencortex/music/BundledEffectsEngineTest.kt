package io.github.currencortex.music

import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs first in a fresh test package: never imports an APK, opens speakers or uses an account. */
class BundledEffectsEngineTest {
    @Test fun freshInstallLoadsDspSmartAndMnnWithoutImportingAnything() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(BuildConfig.NETEASE_ORIGINAL_EFFECTS)
        val imported = File(app.applicationInfo.dataDir, "app_netease-original-9530")
        assertFalse("This must be a genuinely unimported install", File(imported, "libneaudioeffects.so").exists())
        NeteaseNativeEngine.configure(app)
        assertTrue("Bundled DSP must load", NeteaseNativeEngine.available())
        assertNotNull("Smart definition must be bundled", NeteaseNativeEngine.smartDefinition())
        assertTrue("MNN dependencies must be bundled", NeteaseEngineLibraries.moodAvailable())
        NeteaseEngineLibraries.loadMoodLibraries()
        val preset = NeteaseOriginalPreset(0, "内置引擎", 1, """{"eq":{"on":false}}""".toByteArray())
        val output = ByteBuffer.allocateDirect(32768).order(ByteOrder.nativeOrder())
        var bytes = 0
        NeteaseNativeEngine(48000, 2, preset).use { engine ->
            repeat(32) { block ->
                val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
                repeat(1024) { input.putShort((if ((it + block) % 2 == 0) 1000 else -1000).toShort()) }
                input.flip(); output.clear()
                val count = engine.process(input, output)
                assertTrue(count in 0..output.capacity()); bytes += count
            }
        }
        assertTrue("Actual native PCM must be produced", bytes > 0)
        assertFalse("Bundled loading must not disguise an import", File(imported, "libneaudioeffects.so").exists())
        File(app.getExternalFilesDir(null), "bundled-engine-proof.txt").writeText("bundledDsp=true\nsmart=true\nmnnLoaded=true\nimported=false\npcmBytes=$bytes\n")
    }
}
