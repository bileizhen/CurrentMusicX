package io.github.currencortex.music.feature.profile

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.BannerVideoCache
import io.github.currencortex.music.data.profile.BannerCrop
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val PROFILE_BANNER_ASPECT = 3f / 2f

@Composable
internal fun DefaultProfileBannerBackdrop(modifier: Modifier = Modifier) {
    val colors = COUITheme.colorScheme
    val dark = colors.isDark
    val base = if (dark) colors.surface else colors.cardBackground
    val start = lerp(base, colors.primary, if (dark) 0.42f else 0.24f)
    val mid = lerp(base, colors.assist, if (dark) 0.28f else 0.17f)
    val end = lerp(base, colors.blue, if (dark) 0.18f else 0.10f)
    val primaryGlow = colors.primary.copy(alpha = if (dark) 0.30f else 0.22f)
    val secondaryGlow = colors.assist.copy(alpha = if (dark) 0.22f else 0.16f)
    val ringTint = if (dark) Color.White else colors.primary

    Box(modifier.background(Brush.linearGradient(listOf(start, mid, end)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(w * 0.84f, h * 0.22f),
                    radius = w * 0.48f,
                ),
                radius = w * 0.48f,
                center = Offset(w * 0.84f, h * 0.22f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = Offset(w * 0.16f, h * 0.86f),
                    radius = w * 0.42f,
                ),
                radius = w * 0.42f,
                center = Offset(w * 0.16f, h * 0.86f),
            )
            // Subtle concentric acoustic rings on the trailing side give the default header depth without clutter.
            val ringCenter = Offset(w * 0.88f, h * 0.56f)
            val stroke = 1.dp.toPx()
            listOf(0.22f to 0.09f, 0.36f to 0.065f, 0.50f to 0.04f).forEach { (radiusFactor, alpha) ->
                drawCircle(
                    color = ringTint.copy(alpha = if (dark) alpha * 1.15f else alpha),
                    radius = w * radiusFactor,
                    center = ringCenter,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

@Composable
internal fun ProfileBannerHero(
    model: Any?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isVideo: Boolean = false,
    httpClient: okhttp3.OkHttpClient? = null,
    videoCache: BannerVideoCache? = null,
    videoActive: Boolean = true,
    showVideo: Boolean = true,
    seamColor: Color = COUITheme.colorScheme.cardBackground,
) {
    val context = LocalContext.current
    val canBlur = Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated
    val actionDescription = stringResource(if (model != null) R.string.profile_banner_view else R.string.profile_banner_upload)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PROFILE_BANNER_ASPECT)
            .heightIn(min = 160.dp, max = 380.dp)
            .background(seamColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                        .semantics { contentDescription = actionDescription }
                } else Modifier,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .then(if (canBlur) Modifier.bannerRadialSeamBlur() else Modifier),
        ) {
            DefaultProfileBannerBackdrop(Modifier.matchParentSize())
            if (model != null) {
                if (isVideo) {
                    if (showVideo) BannerVideoSurface(
                        source = model,
                        httpClient = httpClient,
                        videoCache = videoCache,
                        active = videoActive,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    val imageRequest = remember(context, model) {
                        ImageRequest.Builder(context)
                            .data(model)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        BannerSeamMeltOverlay(
            seamColor = seamColor,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@RequiresApi(33)
@Composable
private fun Modifier.bannerRadialSeamBlur(): Modifier {
    val masks = remember { runCatching { List(2) { RuntimeShader(BANNER_SEAM_BLUR_SHADER) } }.getOrNull() }
        ?: return this
    return graphicsLayer {
        clip = true
        if (size.width < 1f || size.height < 1f) {
            renderEffect = null
            return@graphicsLayer
        }
        masks.forEachIndexed { index, shader ->
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("blurred", index.toFloat())
        }
        val radius = 14.dp.toPx().coerceAtLeast(1f)
        val sharp = RenderEffect.createRuntimeShaderEffect(masks[0], "content")
        val gaussian = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val soft = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(masks[1], "content"),
            gaussian,
        )
        renderEffect = RenderEffect.createBlendModeEffect(sharp, soft, android.graphics.BlendMode.PLUS)
            .asComposeRenderEffect()
    }
}

@Composable
private fun BannerSeamMeltOverlay(seamColor: Color, modifier: Modifier = Modifier) {
    val shader = if (Build.VERSION.SDK_INT >= 33) {
        remember { runCatching { RuntimeShader(BANNER_SEAM_MELT_SHADER) }.getOrNull() }
    } else {
        null
    }
    Box(
        modifier.drawWithCache {
            if (Build.VERSION.SDK_INT >= 33 && shader != null && size.width >= 1f && size.height >= 1f) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setColorUniform("fadeColor", seamColor.toArgb())
                val brush = ShaderBrush(shader)
                onDrawBehind { drawRect(brush) }
            } else {
                val verticalFade = Brush.verticalGradient(
                    0.66f to Color.Transparent,
                    0.85f to seamColor.copy(alpha = 0.42f),
                    1.00f to seamColor,
                )
                val radialFade = Brush.radialGradient(
                    0.0f to seamColor.copy(alpha = 0.65f),
                    0.65f to seamColor.copy(alpha = 0.22f),
                    1.0f to Color.Transparent,
                    center = Offset(size.width * 0.5f, size.height * 1.05f),
                    radius = (maxOf(size.width, size.height) * 0.54f).coerceAtLeast(1f),
                )
                onDrawBehind {
                    drawRect(radialFade)
                    drawRect(verticalFade)
                }
            }
        },
    )
}

// Blend sharp cover pixels smoothly into blurred lower seam pixels following an arched radial curve.
private const val BANNER_SEAM_BLUR_SHADER = """
uniform shader content;
uniform float2 size;
uniform float blurred;
half4 main(float2 p) {
    float w = max(size.x, 1.0);
    float h = max(size.y, 1.0);
    float u = p.x / w;
    float v = p.y / h;
    float dx = (u - 0.5) * 2.0;
    float arch = 1.0 - clamp(dx * dx, 0.0, 1.0);
    float band = mix(0.24, 0.38, arch);
    float progress = 1.0 - clamp((1.0 - v) / band, 0.0, 1.0);
    float blurMix = smoothstep(0.0, 0.58, progress);
    float weight = mix(1.0 - blurMix, blurMix, blurred);
    return content.eval(p) * weight;
}
"""

// Dissolve the blurred bottom edge into the card surface along the same radial arch so the junction has no hard step.
private const val BANNER_SEAM_MELT_SHADER = """
uniform float2 size;
layout(color) uniform half4 fadeColor;
half4 main(float2 p) {
    float w = max(size.x, 1.0);
    float h = max(size.y, 1.0);
    float u = p.x / w;
    float v = p.y / h;
    float dx = (u - 0.5) * 2.0;
    float arch = 1.0 - clamp(dx * dx, 0.0, 1.0);
    float band = mix(0.24, 0.38, arch);
    float progress = 1.0 - clamp((1.0 - v) / band, 0.0, 1.0);
    float linearFade = smoothstep(0.16, 1.0, progress);
    float softFade = linearFade * linearFade * (3.0 - 2.0 * linearFade);
    half4 fc = fadeColor;
    half alpha = clamp(fc.a * half(softFade), half(0.0), half(1.0));
    return half4(fc.rgb * alpha, alpha);
}
"""

@Composable
internal fun ProfileBannerCropDialog(
    uri: Uri,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: (BannerCrop) -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    var crop by remember(uri) { mutableStateOf(BannerCrop()) }
    var failed by remember(uri) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = try {
            withContext(Dispatchers.IO) { loadAvatarCropImage(resolver, uri) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
            null
        }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    MusicDialog("调整主页横幅", { if (!busy) onCancel() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PROFILE_BANNER_ASPECT)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("profile_banner_crop_preview")
                        .semantics { contentDescription = "横幅裁剪区域，可拖动和双指缩放" }
                        .pointerInput(image, busy) {
                            if (!busy) detectTransformGestures { focus, pan, zoom, _ ->
                                crop = crop.transform(
                                    width = image.width,
                                    height = image.height,
                                    viewportWidth = size.width.toFloat(),
                                    viewportHeight = size.height.toFloat(),
                                    aspectRatio = PROFILE_BANNER_ASPECT,
                                    panX = pan.x,
                                    panY = pan.y,
                                    zoomChange = zoom,
                                    focusX = focus.x,
                                    focusY = focus.y,
                                )
                            }
                        },
                ) {
                    val selected = crop.constrained(image.width, image.height, PROFILE_BANNER_ASPECT)
                    val bounds = selected.bounds(image.width, image.height, PROFILE_BANNER_ASPECT)
                    val scale = size.width / bounds.width
                    drawRect(Color(0xFF16171B))
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(
                            (-bounds.left * scale).roundToInt(),
                            (-bounds.top * scale).roundToInt(),
                        ),
                        dstSize = IntSize(
                            (image.width * scale).roundToInt(),
                            (image.height * scale).roundToInt(),
                        ),
                    )
                    val guideColor = Color.White.copy(alpha = 0.18f)
                    val guideStroke = 0.5.dp.toPx()
                    listOf(1f / 3f, 2f / 3f).forEach { fraction ->
                        drawLine(guideColor, Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height), guideStroke)
                        drawLine(guideColor, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), guideStroke)
                    }
                    drawRect(
                        color = Color.White.copy(alpha = 0.65f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            } else if (failed) {
                Text("无法读取图片，请重新选择", modifier = Modifier.padding(16.dp), fontSize = 14.sp)
            } else {
                CircularProgressIndicator()
            }
        }
        Text(
            "拖动调整位置，双指或滑动下方滑杆缩放",
            fontSize = 13.sp,
            color = COUITheme.colorScheme.labelSecondaryVariant,
        )
        Slider(
            value = crop.zoom,
            onValueChange = { zoom ->
                bitmap?.let {
                    crop = crop.copy(zoom = zoom).constrained(it.width, it.height, PROFILE_BANNER_ASPECT)
                }
            },
            valueRange = 1f..5f,
            enabled = image != null && !busy,
            modifier = Modifier.fillMaxWidth().testTag("profile_banner_crop_zoom"),
        )
        MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicDialogButton(
                "重置",
                { crop = BannerCrop() },
                enabled = image != null && !busy,
                modifier = Modifier.weight(1f).testTag("profile_banner_crop_reset"),
            )
            MusicDialogButton(
                if (busy) "保存中…" else "保存横幅",
                { onSave(crop) },
                enabled = image != null && !busy,
                modifier = Modifier.weight(1f).testTag("profile_banner_save"),
            )
        }
        MusicDialogButton(
            "取消",
            onCancel,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("profile_banner_crop_cancel"),
        )
    }
}
