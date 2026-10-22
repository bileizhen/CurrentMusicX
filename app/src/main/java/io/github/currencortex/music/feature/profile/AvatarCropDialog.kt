package io.github.currencortex.music.feature.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.profile.AvatarCrop
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.min
import kotlin.math.roundToInt

@Composable internal fun AvatarCropDialog(uri: Uri, busy: Boolean, onDismiss: () -> Unit,
    onUpload: (AvatarCrop) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var crop by remember(uri) { mutableStateOf(AvatarCrop()) }
    var failed by remember(uri) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = try { withContext(Dispatchers.IO) { loadAvatarCropImage(resolver, uri) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; null }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val previewSize = minOf(280, (LocalConfiguration.current.screenHeightDp * .36f).roundToInt()).coerceAtLeast(120).dp
    MusicDialog("裁剪头像", onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(previewSize).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (image != null) Canvas(Modifier.fillMaxSize().testTag("avatar_crop_preview")
                    .semantics { contentDescription = "头像裁剪区域，可拖动和双指缩放" }
                    .pointerInput(image, busy) {
                        if (!busy) detectTransformGestures { focus, pan, zoom, _ ->
                            crop = crop.transform(image.width, image.height, size.width.toFloat(),
                                pan.x, pan.y, zoom, focus.x, focus.y)
                        }
                    }) {
                    val selected = crop.constrained(image.width, image.height)
                    val scale = size.width / min(image.width, image.height) * selected.zoom
                    drawRect(Color(0xFF16171B))
                    drawImage(image, dstOffset = IntOffset(
                        (size.width / 2f - selected.centerX * image.width * scale).roundToInt(),
                        (size.height / 2f - selected.centerY * image.height * scale).roundToInt()),
                        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()))
                    val mask = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(Offset.Zero, size))
                    }
                    drawPath(mask, Color.Black.copy(alpha = .55f))
                    drawCircle(Color.White.copy(alpha = .9f), radius = size.width / 2f - 1.dp.toPx(),
                        style = Stroke(1.5.dp.toPx()))
                }
                else if (failed) Text("无法读取图片，请重新选择", modifier = Modifier.padding(16.dp), fontSize = 14.sp)
                else CircularProgressIndicator()
            }
        }
        Text("拖动调整位置，双指或滑动下方滑杆缩放", fontSize = 13.sp)
        Slider(value = crop.zoom, onValueChange = { zoom ->
            bitmap?.let { crop = crop.copy(zoom = zoom).constrained(it.width, it.height) }
        }, valueRange = 1f..5f, enabled = image != null && !busy, modifier = Modifier.fillMaxWidth().testTag("avatar_crop_zoom"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicDialogButton("重置", { crop = AvatarCrop() }, enabled = image != null && !busy,
                modifier = Modifier.weight(1f).testTag("avatar_crop_reset"))
            MusicDialogButton(if (busy) "正在上传…" else "裁剪并上传", { onUpload(crop) }, enabled = image != null && !busy,
                modifier = Modifier.weight(1f).testTag("confirm_avatar_upload"))
        }
        MusicDialogButton("取消", onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("avatar_crop_cancel"))
    }
}
