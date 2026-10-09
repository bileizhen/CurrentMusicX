package io.github.currencortex.music.feature.visualizer.render

import android.graphics.RuntimeShader
import android.graphics.RenderEffect
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.currencortex.music.core.visualizer.*

/** Optional, API-isolated enhancement. One background program plus two cached cover effects. */
@RequiresApi(33)
internal class ShaderEffectRenderer(preset: VisualizerPreset) : AutoCloseable {
    private var shader: RuntimeShader? = if (preset.circular) null else RuntimeShader(when (preset) {
        VisualizerPreset.NEON_PULSE -> NEON
        VisualizerPreset.BASS_IMPACT -> RADIAL
        else -> SCAN
    })
    private var brush: ShaderBrush? = shader?.let(::ShaderBrush)
    private var quietCover: androidx.compose.ui.graphics.RenderEffect? = null
    private var activeCover: androidx.compose.ui.graphics.RenderEffect? = null
    init {
        if (preset == VisualizerPreset.DARK_GLITCH) {
            // RenderEffect snapshots shader uniforms. Cache two effects instead of allocating
            // new native effects at 120 Hz; the real transient envelope chooses between them.
            val rgb = RuntimeShader(RGB)
            rgb.setFloatUniform("offset", .6f)
            quietCover = RenderEffect.createRuntimeShaderEffect(rgb, "image").asComposeRenderEffect()
            rgb.setFloatUniform("offset", 2.5f)
            activeCover = RenderEffect.createRuntimeShaderEffect(rgb, "image").asComposeRenderEffect()
        }
    }
    fun cover(glitch: Float) = if (glitch > .08f) activeCover else quietCover
    fun draw(scope: DrawScope, e: VisualizerEffectState, c: VisualizerEffectConfig, pulse: Float, reduced: Boolean) {
        val s = shader ?: return
        s.setFloatUniform("resolution", scope.size.width, scope.size.height)
        s.setFloatUniform("time", e.phase)
        s.setFloatUniform("bass", e.bass)
        s.setFloatUniform("treble", e.treble)
        s.setFloatUniform("pulse", if (reduced) pulse * .15f else pulse)
        s.setFloatUniform("intensity", c.globalIntensity * c.glowIntensity.coerceAtMost(1f))
        s.setFloatUniform("distortion", c.distortionIntensity * if (reduced) .15f else 1f)
        with(scope) { brush?.let { drawRect(it) } }
    }
    override fun close() { shader = null; brush = null; quietCover = null; activeCover = null }
    companion object {
        private const val UNIFORMS = """
            uniform float2 resolution;
            uniform float time;
            uniform float bass;
            uniform float treble;
            uniform float pulse;
            uniform float intensity;
            uniform float distortion;
        """
        private const val NEON = UNIFORMS + """
            half4 main(float2 coord) {
                float2 p = (coord - resolution * .5) / min(resolution.x, resolution.y);
                float r = length(p);
                float ring = exp(-abs(r - .34 - bass * .025) * 30.0);
                float flow = .5 + .5 * sin(p.x * 14.0 + p.y * 10.0 + time * 3.0);
                float a = clamp(ring * (.045 + bass * .08 + pulse * .08) * intensity, 0.0, .25);
                return half4(mix(float3(.18,.13,.8), float3(.05,.6,1.0), flow) * a, a);
            }
        """
        private const val RADIAL = UNIFORMS + """
            half4 main(float2 coord) {
                float2 p = (coord - resolution * .5) / min(resolution.x, resolution.y);
                float r = length(p);
                float angle = atan(p.y, p.x);
                float bend = sin(r * 32.0 - time * 4.0) * pulse * distortion * .08;
                float lines = pow(max(0.0, sin(angle * 38.0 + bend)), 18.0);
                float a = clamp(lines * smoothstep(.29,.55,r) * (.015 + pulse * .16) * intensity, 0.0, .2);
                return half4(float3(1.0,.48,.08) * a, a);
            }
        """
        private const val SCAN = UNIFORMS + """
            half4 main(float2 coord) {
                float2 p = coord / resolution;
                float scan = step(.8, fract(coord.y / 5.0));
                float noise = fract(sin(dot(floor(coord / 3.0),float2(12.9898,78.233)) + time) * 43758.5453);
                float a = min(.08, (scan * .012 + noise * treble * .025) * intensity);
                return half4(float3(.8,.05,.13) * a, a);
            }
        """
        private const val RGB = """
            uniform shader image;
            uniform float offset;
            half4 main(float2 p) {
                half4 base = image.eval(p);
                return half4(image.eval(p + float2(offset,0)).r, base.g, image.eval(p - float2(offset,0)).b, base.a);
            }
        """
    }
}
