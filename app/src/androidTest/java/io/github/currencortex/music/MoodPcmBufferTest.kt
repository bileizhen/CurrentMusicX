package io.github.currencortex.music

import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun moodSpatialPreset() = NeteaseOriginalPreset(0, "Mood spatial regression", 1, """{"aev2":{
            "eq":{"on":true,"eqs":[-0.36,0.8,0.6,1.0,0.28,0.61,-0.38,1.82,1.39,0.0]},
            "rvb":{"on":true,"rvb":{"pdelay":25,"hfdamping":6,"rshape":25,"density":80,"swidth":20,"q":6,"diffusion":20,"on":true,"dtime":0.9175},"er":{"on":false},"tc":{"on":false},"il":{"center":0,"lfe":0},"ol":{"dry":0,"er":-32,"rvb":-27},"rl":{"lfe":0,"front":0,"rear":0,"center":0}},
            "spspatial":{"on":true,"upmix":1,"lronoff":0,"lrangle":10,"otheronoff":0,"global":-0.2,"center":0.6,"lfe":0,"fl":-0.2,"fr":-0.2,"left":0,"right":0,"rl":0,"rr":0,"ul":0,"ur":0}
        }}""".toByteArray())
class MoodPcmBufferTest {
    @Test fun moodEffectPreservesFramesAcrossDecoderBufferSizes() {
        val i = InstrumentationRegistry.getInstrumentation()
        NeteaseNativeEngine.configure(i.targetContext)
        val preset = moodSpatialPreset()
        for (rate in listOf(44100, 48000, 88200, 96000, 176400, 192000)) {
          for (channels in listOf(1, 2)) {
            for (frames in listOf(512, 16384)) {
                val blocks = maxOf(16, rate / frames)
                val bytesPerFrame = channels * 2
                var produced = 0L
                val pcm = ByteBuffer.allocateDirect(frames * bytesPerFrame).order(ByteOrder.nativeOrder())
                NeteaseNativeEngine(rate, channels, preset).use { fx ->
                    val output = ByteBuffer.allocateDirect(frames * bytesPerFrame + fx.outputHeadroom)
                    repeat(blocks) { block ->
                        pcm.clear(); repeat(frames * channels) { pcm.putShort(((it + block) % 2000).toShort()) }; pcm.flip()
                        output.clear()
                        val count = fx.process(pcm, output)
                        assertTrue(count >= 0 && count % bytesPerFrame == 0)
                        produced += count
                    }
                    val expected = frames * bytesPerFrame.toLong() * blocks
                    val streaming = "rate=$rate channels=$channels frames=$frames expected=$expected streaming=$produced"
                    i.sendStatus(0, android.os.Bundle().apply { putString("stream", "\n$streaming\n") })
                    assertTrue(streaming, produced in (expected - rate * bytesPerFrame / 10)..expected)
                    val tail = ByteBuffer.allocateDirect(32768)
                    var ended = false
                    repeat(2000) {
                        if (!ended) {
                            tail.clear()
                            val count = fx.drain(tail)
                            assertTrue(count >= 0 && count % bytesPerFrame == 0)
                            produced += count
                            ended = count == 0
                        }
                    }
                    assertTrue("$streaming: effect tail must finish", ended)
                    assertTrue("$streaming: no frames lost at EOS", produced >= expected)
                }
            }
          }
        }
    }
}
