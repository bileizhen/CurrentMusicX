package io.github.currencortex.music.feature.library

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** Bevel normals, a fixed area light, and environment reflection on the clear moulded shell.
 * Kept in its own API-33 class; older devices use the Canvas material underneath it. */
@RequiresApi(33)
internal class RecordShelfLighting {
    val rim = RuntimeShader(MATERIAL).apply { setFloatUniform("part", 0f) }
    val top = RuntimeShader(MATERIAL).apply { setFloatUniform("part", 1f) }
    val side = RuntimeShader(MATERIAL).apply { setFloatUniform("part", 2f) }

    fun prepare(offset: Float) {
        rim.setFloatUniform("offset", offset)
        top.setFloatUniform("offset", offset)
        side.setFloatUniform("offset", offset)
    }

    companion object {
        private const val MATERIAL = """
            uniform float part;
            uniform float offset;
            float box(float2 p, float2 c, float2 b, float r) {
                float2 d = abs(p-c) - b + r;
                return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
            }
            float outer(float2 p) { return box(p, float2(.5), float2(.5), .008); }
            float inner(float2 p) { return box(p, float2(.526, .502), float2(.448, .471), .003); }
            float3 environment(float3 r, float2 p) {
                float3 lower = float3(.10, .16, .23);
                float3 upper = float3(.77, .85, .93);
                float3 base = mix(lower, upper, smoothstep(-.7, .8, -r.y));
                float softbox = pow(max(0.0, dot(r, normalize(float3(-.55,-.65,.7)))), 10.0);
                float strip = exp(-pow((r.x + .22 + .04 * offset) / .075, 2.0));
                float variation = .92 + .08 * sin(p.x * 19.0 + p.y * 3.0 + offset);
                return base * variation + float3(softbox * .8 + strip * .28);
            }
            half4 main(float2 p) {
                float3 n;
                float shade = 1.0;
                if (part < .5) {
                    float d = outer(p), hole = inner(p);
                    if (d > 0.0 || hole < 0.0) return half4(0.0);
                    float2 go = normalize(float2(outer(p+float2(.001,0))-outer(p-float2(.001,0)),
                        outer(p+float2(0,.001))-outer(p-float2(0,.001))) + .00001);
                    float2 gi = normalize(float2(inner(p+float2(.001,0))-inner(p-float2(.001,0)),
                        inner(p+float2(0,.001))-inner(p-float2(0,.001))) + .00001);
                    float bevel = 1.0-smoothstep(0.0,.010,-d);
                    float recess = 1.0-smoothstep(0.0,.007,hole);
                    float2 slope = go * bevel * 2.4 - gi * recess * 1.9;
                    // The hinge is a rounded, ribbed piece of plastic rather than a painted line.
                    if (p.x < .076 && p.y > .04 && p.y < .96) {
                        slope.x += sin(p.x * 345.0) * .8;
                        shade *= .86 + .14 * cos(p.x * 345.0);
                    }
                    n = normalize(float3(slope, 1.0));
                    shade *= 1.0 - recess * .24;
                } else if (part < 1.5) {
                    n = normalize(float3(.12, -.87, .48));
                    shade = .98 + .02 * sin(p.x * 75.0);
                } else {
                    n = normalize(float3(.88, .08, .44));
                    shade = .76 + .18 * pow(.5+.5*cos(p.x*410.0), 3.0);
                }
                float3 v = normalize(float3(.20,-.28,1.0));
                float3 l = normalize(float3(-.48,-.72,.78));
                float diffuse = max(dot(n,l), 0.0);
                float spec = pow(max(dot(n,normalize(l+v)),0.0), 44.0);
                float fresnel = .09 + .91 * pow(1.0-max(dot(n,v),0.0),5.0);
                float3 reflection = environment(reflect(-v,n),p);
                float3 body = float3(.53,.56,.58) + float3(.29,.28,.26) * diffuse;
                float3 lit = mix(body, reflection, .24 + fresnel*.55) * shade + spec * .55;
                // Narrow bright seams are kept distinct from the darker side walls.
                float alpha = part < .5 ? .97 : .99;
                return half4(half3(clamp(lit,0.0,1.0) * alpha), half(alpha));
            }
        """
    }
}
