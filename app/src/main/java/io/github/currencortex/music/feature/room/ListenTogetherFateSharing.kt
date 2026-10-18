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
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateReport
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

/** Offline export and share intent only; no chooser, room writes or account profile lookups happen here. */
internal object ListenTogetherFateSharing {
    suspend fun create(context: Context, report: ListenTogetherFateReport): File {
        var destination: File? = null
        try {
            return withContext(Dispatchers.Default) {
                val portraits = arrayOfNulls<Bitmap>(2)
                var output: Bitmap? = null
                try {
                    coroutineScope {
                        listOf(report.self.avatarUrl, report.peer.avatarUrl).forEachIndexed { index, url ->
                            launch { portraits[index] = avatar(context, url) }
                        }
                    }
                    ensureActive()
                    val bitmap = render(report, portraits[0], portraits[1])
                    output = bitmap
                    withContext(Dispatchers.IO) {
                        val directory = File(context.cacheDir, "song-shares").apply { check(mkdirs() || isDirectory) }
                        val cutoff = System.currentTimeMillis() - 86_400_000L
                        directory.listFiles()?.filter { it.isFile && it.name.startsWith(PREFIX) && it.lastModified() < cutoff }
                            ?.forEach { it.delete() }
                        val file = File.createTempFile(PREFIX, ".png", directory)
                        destination = file
                        ensureActive()
                        file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        ensureActive()
                        file
                    }
                } finally {
                    output?.recycle()
                    portraits.forEach { it?.recycle() }
                }
            }
        } catch (failure: Throwable) {
            // Includes cancellation while the completed IO result is dispatched back to its caller.
            destination?.delete()
            throw failure
        }
    }

    fun intent(context: Context, report: ListenTogetherFateReport, file: File): Intent {
        require(file.isFile && file.name.startsWith(PREFIX) && file.extension == "png" &&
            file.canonicalFile.parentFile == File(context.cacheDir, "song-shares").canonicalFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "缘分报告 · CurrentMusicX")
            putExtra(Intent.EXTRA_TEXT, "一起听的音乐时光，由 CurrentMusicX 分享。\n网易云官方入口（需登录及访问权限）：\n${report.officialUrl()}")
            clipData = ClipData.newUri(context.contentResolver, "一起听缘分报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun avatar(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val result = withTimeoutOrNull(8_000) {
                SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url)
                    .size(256).allowHardware(false).build())
            }
            // The original may belong to Coil's memory cache. Recycle only the independent copy.
            (result as? SuccessResult)?.image?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { null }
    }

    private fun render(report: ListenTogetherFateReport, self: Bitmap?, peer: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val primary = 0xFF327BEE.toInt()
            val ink = 0xFF1D2C43.toInt()
            val muted = 0xFF78869B.toInt()
            paint.shader = LinearGradient(0f, 0f, 1080f, 1800f,
                intArrayOf(0xFFE7F0FF.toInt(), 0xFFF4F7FB.toInt(), 0xFFE5EDF9.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 1080f, 1800f, paint)
            paint.shader = null
            fun text(value: String, x: Float, top: Float, width: Int, size: Float, color: Int = ink,
                lines: Int = 1, bold: Boolean = false, centered: Boolean = false) {
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
                    .setIncludePad(false).setLineSpacing(5f, 1f).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).build()
                canvas.save()
                try { canvas.translate(x, top); layout.draw(canvas) } finally { canvas.restore() }
            }
            fun fitted(value: String, x: Float, top: Float, width: Int, size: Float, color: Int = ink) {
                var targetSize = size
                val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
                while (targetSize > 26f && run { measure.textSize = targetSize; measure.measureText(value) > width }) targetSize -= 1f
                text(value, x, top, width, targetSize, color, bold = true)
            }
            fun card(bounds: RectF) { paint.color = Color.WHITE; canvas.drawRoundRect(bounds, 36f, 36f, paint) }
            text("CurrentMusicX", 72f, 62f, 936, 38f, primary, bold = true)
            text("和 TA 的缘分报告", 72f, 145f, 936, 56f, bold = true)
            text("那些一起听过的歌，记录着彼此的陪伴。", 76f, 225f, 928, 25f, muted)

            card(RectF(64f, 286f, 1016f, 818f))
            portrait(canvas, self, 465f, 382f, 68f, paint)
            portrait(canvas, peer, 605f, 382f, 68f, paint)
            val names = listOf(report.self.nickname, report.peer.nickname).joinToString("  ·  ") { it.ifBlank { "网易云用户" } }
            text(names, 96f, 483f, 888, 31f, lines = 2, bold = true, centered = true)
            text("累计陪伴彼此", 110f, 594f, 860, 24f, muted)
            fitted(duration(report.totalDurationMs), 110f, 637f, 860, 46f, primary)
            text("一起听过", 110f, 724f, 400, 23f, muted)
            text("共同红心", 578f, 724f, 400, 23f, muted)
            fitted(count(report.totalSongCount), 110f, 760f, 400, 30f)
            fitted(count(report.commonRedSongCount), 578f, 760f, 400, 30f)

            card(RectF(64f, 846f, 1016f, 1452f))
            text("首次一起听", 110f, 882f, 400, 24f, muted)
            text("相识天数", 578f, 882f, 400, 24f, muted)
            fitted(date(report.firstListenTimeMs, "yyyy.MM.dd HH:mm"), 110f, 920f, 400, 34f)
            fitted(report.daysSinceFirstListen?.let { "$it 天" } ?: "—", 578f, 920f, 400, 34f)
            paint.color = 0xFFEBEFF5.toInt(); paint.strokeWidth = 2f
            canvas.drawLine(110f, 985f, 970f, 985f, paint)
            text("一起听到最晚的那天", 110f, 1012f, 860, 24f, muted)
            fitted(date(report.latestLateNightTimeMs, "yyyy.MM.dd  HH:mm"), 110f, 1050f, 860, 34f)
            text("一起听得最多的歌", 110f, 1130f, 860, 24f, muted)
            text(report.mostPlayedSongName.ifBlank { "—" }, 110f, 1168f, 860, 36f, lines = 2, bold = true)
            text("一次最长的陪伴", 110f, 1300f, 860, 24f, muted)
            fitted(duration(report.longestDurationMs), 110f, 1340f, 860, 40f, primary)
            text("— 表示网易云未提供该项数据。", 110f, 1410f, 860, 20f, muted)

            text("音乐，让陪伴有迹可循。", 74f, 1530f, 570, 32f, bold = true)
            text("数据来自网易云一起听\n网易云官方入口\n需登录及访问权限", 76f, 1590f, 570, 24f, muted, lines = 3)
            text("由 CurrentMusicX 生成并分享", 76f, 1725f, 570, 21f, muted)
            val qr = qr(report.officialUrl())
            try {
                paint.color = Color.WHITE
                canvas.drawRoundRect(RectF(716f, 1500f, 984f, 1768f), 20f, 20f, paint)
                paint.isFilterBitmap = false
                canvas.drawBitmap(qr, 726f, 1510f, paint)
            } finally { qr.recycle() }
            return bitmap
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }

    private fun portrait(canvas: Canvas, bitmap: Bitmap?, x: Float, y: Float, radius: Float, paint: Paint) {
        canvas.save()
        try {
            canvas.clipPath(Path().apply { addCircle(x, y, radius, Path.Direction.CW) })
            paint.color = 0xFFE8EFFA.toInt(); canvas.drawCircle(x, y, radius, paint)
            if (bitmap != null) {
                val side = minOf(bitmap.width, bitmap.height)
                val left = (bitmap.width - side) / 2; val top = (bitmap.height - side) / 2
                paint.isFilterBitmap = true
                canvas.drawBitmap(bitmap, Rect(left, top, left + side, top + side), RectF(x - radius, y - radius, x + radius, y + radius), paint)
            } else {
                paint.color = 0xFFA4B4CD.toInt()
                canvas.drawCircle(x, y - radius * .23f, radius * .25f, paint)
                canvas.drawOval(RectF(x - radius * .55f, y + radius * .12f, x + radius * .55f, y + radius * 1.15f), paint)
            }
        } finally { canvas.restore() }
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
        canvas.drawCircle(x, y, radius, paint); paint.style = Paint.Style.FILL
    }

    private fun qr(url: String): Bitmap {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 248, 248,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    private fun count(value: Long?): String = value?.let { "$it 首歌曲" } ?: "—"
    private fun duration(value: Long?): String {
        if (value == null) return "—"
        val minutes = value / 60_000
        return when { value > 0 && minutes == 0L -> "小于 1 分钟"; minutes < 60 -> "$minutes 分钟"; else -> "${minutes / 60} 小时 ${minutes % 60} 分钟" }
    }
    private fun date(value: Long?, pattern: String): String = value?.takeIf { it > 0 }
        ?.let { SimpleDateFormat(pattern, Locale.CHINA).format(Date(it)) } ?: "—"
    private const val PREFIX = "fate-report-"
}
