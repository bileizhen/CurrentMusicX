package io.github.currencortex.music.feature.room

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.currencortex.music.data.netease.listen.ListenTogetherRecordReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates a local report only. The caller opens the system chooser after an explicit share tap. */
internal object ListenTogetherReportSharing {
    suspend fun create(context: Context, report: ListenTogetherRecordReport): File = withContext(Dispatchers.Default) {
        val portraits = arrayOfNulls<Bitmap>(2)
        var output: Bitmap? = null
        try {
            coroutineScope {
                listOf(report.self?.avatarUrl, report.peer?.avatarUrl).forEachIndexed { index, url ->
                    launch { portraits[index] = avatar(context, url) }
                }
            }
            ensureActive()
            val bitmap = render(report, portraits[0], portraits[1])
            output = bitmap
            withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "song-shares").apply { check(mkdirs() || isDirectory) }
                val cutoff = System.currentTimeMillis() - 86_400_000L
                directory.listFiles()?.filter { it.isFile && it.name.startsWith("listen-report-") && it.lastModified() < cutoff }
                    ?.forEach { it.delete() }
                val file = File.createTempFile("listen-report-", ".png", directory)
                try {
                    ensureActive()
                    file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    ensureActive()
                    file
                } catch (failure: Exception) { file.delete(); throw failure }
            }
        } finally {
            output?.recycle()
            portraits.forEach { it?.recycle() }
        }
    }

    fun intent(context: Context, report: ListenTogetherRecordReport, file: File): Intent {
        require(file.isFile && file.name.startsWith("listen-report-") &&
            file.canonicalFile.parentFile == File(context.cacheDir, "song-shares").canonicalFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "一起听报告 · CurrentMusicX")
            putExtra(Intent.EXTRA_TEXT, "和好友一起听的这段时光，由 CurrentMusicX 分享。\n${report.officialUrl()}")
            clipData = ClipData.newUri(context.contentResolver, "一起听报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun avatar(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val result = withTimeoutOrNull(8_000) {
                SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url)
                    .size(256).allowHardware(false).build())
            }
            // Coil may own/cache the source bitmap. Only this independent export copy is recycled.
            (result as? SuccessResult)?.image?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { null }
    }

    private fun render(report: ListenTogetherRecordReport, self: Bitmap?, peer: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val coral = 0xFFE75D55.toInt()
            val ink = 0xFF532C2A.toInt()
            val muted = 0xFF9A7772.toInt()
            paint.shader = LinearGradient(0f, 0f, 1080f, 1440f,
                intArrayOf(0xFFFF8A76.toInt(), 0xFFF36A61.toInt(), 0xFFE74C4D.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 1080f, 1440f, paint)
            paint.shader = null
            fun label(text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
                paint.color = color; paint.textSize = size; paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(text, x, y, paint)
            }
            label("CurrentMusicX", 72f, 100f, 38f, Color.WHITE, true)
            label("LISTEN TOGETHER", 74f, 142f, 21f, 0xDDFFFFFF.toInt())
            label("一起听的这段时光", 72f, 245f, 58f, Color.WHITE, true)
            report.endTimeMs?.takeIf { it > 0 }?.let {
                label(SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(it)), 76f, 296f, 26f, 0xEEFFFFFF.toInt())
            }
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(64f, 350f, 1016f, 1000f), 40f, 40f, paint)
            portrait(canvas, self, 460f, 433f, 74f, paint)
            portrait(canvas, peer, 610f, 433f, 74f, paint)
            val names = listOf(report.self?.nickname?.ifBlank { "我" } ?: "我",
                report.peer?.nickname?.ifBlank { "好友" } ?: "好友").joinToString("  ·  ")
            paragraph(canvas, names, 96f, 535f, 888, 32f, ink, 2, bold = true, centered = true)

            fun stat(labelText: String, value: String, x: Float, top: Float, emphasized: Boolean) {
                paragraph(canvas, labelText, x, top, 400, 25f, muted, 1)
                var font = if (emphasized) 42f else 34f
                val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
                while (font > 27f && run { measure.textSize = font; measure.measureText(value) > 400 }) font -= 1f
                paragraph(canvas, value, x, top + 44f, 400, font, if (emphasized) coral else ink, 1, bold = true)
            }
            stat("本次一起听了", count(report.songCount), 110f, 662f, true)
            stat("本次陪伴彼此", duration(report.durationMs), 578f, 662f, true)
            paint.color = 0xFFEFE4E0.toInt(); paint.strokeWidth = 2f
            canvas.drawLine(110f, 786f, 970f, 786f, paint)
            stat("累计一起听过", count(report.totalSongCount), 110f, 820f, false)
            stat("累计陪伴彼此", duration(report.totalDurationMs), 578f, 820f, false)
            if (listOf(report.songCount, report.durationMs, report.totalSongCount, report.totalDurationMs).any { it == null }) {
                paragraph(canvas, "— 表示网易云未提供数据，部分统计需会员权限。", 110f, 945f, 860, 20f, muted, 1)
            }
            label("每一首歌，都有彼此", 72f, 1110f, 34f, Color.WHITE, true)
            paragraph(canvas, "数据来自网易云一起听\n扫码查看网易云官方报告", 74f, 1144f, 570, 25f, 0xEEFFFFFF.toInt(), 3)
            paragraph(canvas, "由 CurrentMusicX 生成并分享", 74f, 1310f, 570, 22f, 0xDDFFFFFF.toInt(), 2)
            val qr = qr(report.officialUrl())
            try {
                paint.color = Color.WHITE
                canvas.drawRoundRect(RectF(716f, 1080f, 984f, 1348f), 20f, 20f, paint)
                paint.isFilterBitmap = false
                canvas.drawBitmap(qr, 726f, 1090f, paint)
            } finally { qr.recycle() }
            return bitmap
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }

    private fun portrait(canvas: Canvas, bitmap: Bitmap?, x: Float, y: Float, radius: Float, paint: Paint) {
        canvas.save()
        try {
            canvas.clipPath(Path().apply { addCircle(x, y, radius, Path.Direction.CW) })
            paint.color = 0xFFFFE6DC.toInt()
            canvas.drawCircle(x, y, radius, paint)
            if (bitmap != null) {
                val side = minOf(bitmap.width, bitmap.height)
                val left = (bitmap.width - side) / 2; val top = (bitmap.height - side) / 2
                paint.isFilterBitmap = true
                canvas.drawBitmap(bitmap, Rect(left, top, left + side, top + side), RectF(x - radius, y - radius, x + radius, y + radius), paint)
            } else {
                paint.color = 0xFFD49988.toInt()
                canvas.drawCircle(x, y - radius * .23f, radius * .25f, paint)
                canvas.drawOval(RectF(x - radius * .55f, y + radius * .12f, x + radius * .55f, y + radius * 1.15f), paint)
            }
        } finally { canvas.restore() }
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
        canvas.drawCircle(x, y, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun paragraph(canvas: Canvas, text: String, x: Float, y: Float, width: Int, size: Float,
        color: Int, maxLines: Int, bold: Boolean = false, centered: Boolean = false) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setIncludePad(false).setLineSpacing(5f, 1f).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).build()
        canvas.save()
        try { canvas.translate(x, y); layout.draw(canvas) } finally { canvas.restore() }
    }

    private fun qr(url: String): Bitmap {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 248, 248,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    private fun count(value: Long?) = value?.takeIf { it >= 0 }?.let { "$it 首歌曲" } ?: "—"
    private fun duration(value: Long?): String {
        if (value == null || value < 0) return "—"
        val minutes = value / 60_000
        return when { value > 0 && minutes == 0L -> "小于 1 分钟"; minutes < 60 -> "$minutes 分钟"; else -> "${minutes / 60} 小时 ${minutes % 60} 分钟" }
    }
}
