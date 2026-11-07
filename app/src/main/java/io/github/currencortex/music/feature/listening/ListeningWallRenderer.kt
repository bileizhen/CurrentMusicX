package io.github.currencortex.music.feature.listening

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.currencortex.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One chooser owns its bitmaps and temporary previews. Share copies outlive that chooser. */
internal class ListeningWallRenderSession(
    context: Context,
    private val covers: List<String>,
    private val title: String,
    private val info: ListeningWallExportInfo,
    /** Returned bitmap is owned by this session. Defaults to a copy of Coil cache data. */
    private val loadBitmap: (suspend (String, Int) -> Bitmap)? = null,
) {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val images = linkedMapOf<String, Bitmap>()
    private val root = File(context.cacheDir, "song-shares")
    private val directory = File(root, "listening-export-${UUID.randomUUID()}")
    private val font by lazy { ResourcesCompat.getFont(this.context, R.font.listening_misans_semibold) ?: Typeface.DEFAULT_BOLD }
    private val titleFont by lazy { ResourcesCompat.getFont(this.context, R.font.listening_misans_bold) ?: Typeface.DEFAULT_BOLD }
    private val bodyFont = Typeface.create("sans-serif", Typeface.NORMAL)
    private val nicknameFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val numberFont by lazy { ResourcesCompat.getFont(this.context, R.font.listening_outfit) ?: Typeface.DEFAULT }

    fun markClosed() { closed.set(true) }
    suspend fun close() = withContext(Dispatchers.IO) {
        closed.set(true)
        mutex.withLock {
            images.values.forEach { if (!it.isRecycled) it.recycle() }
            images.clear()
            directory.deleteRecursively()
        }
    }

    suspend fun render(template: ListeningWallTemplate, indices: List<Int>, outputWidth: Int = 1125): File =
        withContext(Dispatchers.IO) { mutex.withLock {
            check(!closed.get()) { "Export session closed" }
            require(covers.isNotEmpty() && indices.size == listeningExportSlots(template).size)
            require(indices.all { it in covers.indices })
            require(outputWidth in 160..2250)
            check(directory.mkdirs() || directory.isDirectory)
            val file = File(directory, "${template.kind}-${template.index}-$outputWidth-${indices.joinToString("_")}.png")
            if (file.isFile) return@withLock file
            val required = indices.distinct().associateWith { image(covers[it], 750) }
            val assets = assetsFor(template).associateWith { image(it, 1125) }
            currentCoroutineContext().ensureActive()
            check(!closed.get())
            val bitmap = Bitmap.createBitmap(outputWidth, (template.height.toLong() * outputWidth / 1125).toInt(), Bitmap.Config.ARGB_8888)
            try {
                draw(bitmap, template, indices, required, assets)
                currentCoroutineContext().ensureActive()
                file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                currentCoroutineContext().ensureActive()
                check(!closed.get())
                val prefix = "${template.kind}-${template.index}-$outputWidth-"
                directory.listFiles()?.filter { it != file && it.name.startsWith(prefix) }?.forEach { it.delete() }
                file
            } catch (error: Throwable) {
                file.delete()
                throw error
            } finally { bitmap.recycle(); trimImages() }
        } }

    suspend fun shareCopy(template: ListeningWallTemplate, indices: List<Int>): File {
        check(!template.locked(info)) { "Membership required" }
        val rendered = render(template, indices)
        return withContext(Dispatchers.IO) { mutex.withLock {
            check(!closed.get())
            check(root.mkdirs() || root.isDirectory)
            cleanOldListeningExports(root)
            val target = File.createTempFile("listening-export-share-", ".png", root)
            try {
                rendered.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                currentCoroutineContext().ensureActive()
                target
            } catch (error: Throwable) { target.delete(); throw error }
        } }
    }

    private suspend fun image(url: String, size: Int): Bitmap {
        images.remove(url)?.let { images[url] = it; return it }
        currentCoroutineContext().ensureActive()
        loadBitmap?.let { loader ->
            val owned = loader(url, size)
            images[url] = owned
            return owned
        }
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).size(size).allowHardware(false).build())
        val loaded = checkNotNull((result as? SuccessResult)?.image?.toBitmap()) { "图片加载失败" }
        val owned = checkNotNull(loaded.copy(Bitmap.Config.ARGB_8888, false))
        images[url] = owned
        return owned
    }

    /** A long editing session must not retain every cover the user has cycled through. */
    private fun trimImages() {
        var bytes = images.values.sumOf { it.allocationByteCount.toLong() }
        val iterator = images.entries.iterator()
        while (bytes > 48L * 1024 * 1024 && iterator.hasNext()) {
            val image = iterator.next().value
            bytes -= image.allocationByteCount
            iterator.remove()
            image.recycle()
        }
    }

    private fun assetsFor(template: ListeningWallTemplate): List<String> = buildList {
        val wallpaper = template.kind == ListeningExportKind.WALLPAPER
        if (template.layout == ListeningExportLayout.SCATTER) {
            add(if (wallpaper) if (template.dark) WallScatterDark else WallScatterLight else if (template.dark) PosterScatterDark else PosterScatterLight)
            if (!wallpaper && info.isCloud) add(Media + when {
                info.superVip && template.dark -> "preview_svip_bg_pic.91bfb9b5.png"
                info.superVip -> "preview_svip_bg_pic_dark.5d285c24.png"
                template.dark -> "preview_vip_bg_pic.5fc1089b.png"
                else -> "preview_vip_bg_pic_dark.22ac13cf.png"
            })
        } else add(Media + if (wallpaper) {
            if (template.dark) "screensaver_top_bg_black.649f1fa2.png" else "screensaver_top_bg_white.80f552cb.png"
        } else if (template.dark) "poster_top_bg_black.64bbc210.png" else "poster_top_bg_white.f416646e.png")
        if (info.isCloud) add(Media + if (wallpaper) "screensaver_logo_black.6c5a3dbd.png"
            else if (template.dark) "poster_logo_white.b6009d0e.png" else "poster_logo_black.2700c799.png")
    }

    private suspend fun draw(output: Bitmap, template: ListeningWallTemplate, indices: List<Int>, coverImages: Map<Int, Bitmap>, assets: Map<String, Bitmap>) {
        val canvas = Canvas(output)
        try {
            canvas.scale(output.width / 1125f, output.width / 1125f)
            drawContent(canvas, template, indices, coverImages, assets, preview = output.width < 1125)
        } finally {
            // End the raster target's Canvas lifetime before PNG encoding or bitmap recycling,
            // including when drawing is cancelled or fails. Do not rely on Canvas finalization.
            canvas.setBitmap(null)
        }
    }

    private suspend fun drawContent(canvas: Canvas, template: ListeningWallTemplate, indices: List<Int>, coverImages: Map<Int, Bitmap>, assets: Map<String, Bitmap>, preview: Boolean) {
        canvas.drawColor(Color.parseColor(if (template.dark) "#232323" else "#E4E4E4"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val wallpaper = template.kind == ListeningExportKind.WALLPAPER
        val scatter = template.layout == ListeningExportLayout.SCATTER
        val assetList = assetsFor(template)
        fun asset(url: String, left: Float, top: Float, width: Float, height: Float) {
            val bitmap = requireNotNull(assets[url])
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
        }
        if (scatter) {
            val height = if (wallpaper) 2125f else 1660f
            asset(assetList[0], 0f, template.height - height, 1125f, height)
        }
        val slots = listeningExportSlots(template)
        slots.withIndex().sortedWith(compareBy<IndexedValue<ListeningExportSlot>> { it.value.z }.thenBy { it.index }).forEach { (index, slot) ->
            currentCoroutineContext().ensureActive()
            val save = canvas.save()
            canvas.translate(slot.x, slot.y)
            canvas.rotate(slot.angle)
            if (scatter && index == 0) {
                paint.color = Color.BLACK
                paint.setShadowLayer(80f, -20f, 58f, 0x4D000000)
                canvas.drawRect(0f, 0f, slot.size, slot.size, paint)
                paint.clearShadowLayer()
            }
            val source = requireNotNull(coverImages[indices[index]])
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            canvas.drawBitmap(source, Rect(left, top, left + side, top + side), RectF(0f, 0f, slot.size, slot.size), paint)
            if (slot.pinLogo && info.isCloud) asset(Media + "screensaver_logo_black.6c5a3dbd.png", slot.size - 333f, slot.size + 5f, 336f, 42f)
            canvas.restoreToCount(save)
        }
        if (!scatter) asset(assetList[0], -3f, 0f, 1131f, if (wallpaper) 1055f else 820f)
        if (wallpaper) {
            if (!scatter) {
                val top = if (template.layout == ListeningExportLayout.GRID) 1968f else 1897f
                if (info.isCloud) asset(Media + "screensaver_logo_black.6c5a3dbd.png", 787f, top, 338f, 42f)
                else text(canvas, "CurrentMusicX", 1080f, top + 35, 31f, Color.DKGRAY, align = Paint.Align.RIGHT)
            }
        } else {
            val ink = Color.parseColor(if (template.dark) "#E4E4E4" else "#232323")
            if (info.nickname.isNotBlank()) text(canvas, "@${info.nickname}", 60f, lineBaseline(81f,54.6f,39f,nicknameFont), 39f, ink, typeface = nicknameFont, maxWidth = 690f)
            text(canvas, "${title}唱片墙", 60f, lineBaseline(145f,109.2f,78f,titleFont), 78f, ink, typeface = titleFont, maxWidth = 780f)
            val titleWidth = Paint().apply { typeface = titleFont; textSize = 78f }.measureText("${title}唱片墙").coerceAtMost(780f)
            val badgeWidth = Paint().apply { typeface = numberFont; textSize = 21f }.measureText("TOP最爱") + 34.2f
            paint.color = ink; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.1f
            canvas.drawRoundRect(RectF(80f + titleWidth, 179f, 80f + titleWidth + badgeWidth, 222.6f), 30f, 30f, paint)
            paint.style = Paint.Style.FILL
            text(canvas, "TOP最爱", 97.1f + titleWidth, lineBaseline(186.1f,29.4f,21f,numberFont), 21f, ink, typeface = numberFont)
            if (info.songTitles.isNotEmpty()) {
                val names = info.songTitles.take(if (scatter) 3 else 5)
                val bottom = if (scatter) 448f else 545f
                val rowHeight = minOf(51.7f, (if (scatter) 148f else 245f)/names.size)
                names.forEachIndexed { index, name -> text(canvas, name, 60f,
                    lineBaseline(bottom-46.2f-(names.size-1-index)*rowHeight,46.2f,33f,bodyFont), 33f,
                    (ink and 0x00FFFFFF) or 0xCC000000.toInt(), typeface = bodyFont, maxWidth = 820f) }
            }
            if (info.timeline.isNotBlank()) text(canvas, info.timeline, 1065f,
                lineBaseline((if (scatter) 448f else 545f)-46.2f,46.2f,33f,numberFont), 33f,
                (ink and 0x00FFFFFF) or 0xCC000000.toInt(), Paint.Align.RIGHT, numberFont)
            if (info.isCloud) {
                if (!scatter || !preview) asset(Media + if (template.dark) "poster_logo_white.b6009d0e.png" else "poster_logo_black.2700c799.png", 810f, 81f, 255f, 43.53f)
            } else text(canvas, "CurrentMusicX", 1065f, 119f, 34f, ink, Paint.Align.RIGHT)
            if (scatter && info.isCloud) asset(assetList[1], 383.8f, if (info.superVip) 193.8f else 234f, 741.2f, 248.7f)
            info.qrUrl?.takeIf { it.startsWith("https://") }?.let { link ->
                val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 129, 129, mapOf(EncodeHintType.MARGIN to 0))
                val x = 960f; val y = template.height - 165f
                paint.color = Color.WHITE
                canvas.drawRoundRect(RectF(x, y, x + 141, y + 141), 6f, 6f, paint)
                paint.color = Color.BLACK
                repeat(matrix.height) { row -> repeat(matrix.width) { column -> if (matrix[column,row]) canvas.drawRect(x + 6 + column, y + 6 + row, x + 7 + column, y + 7 + row, paint) } }
            }
        }
    }

    private fun lineBaseline(top: Float, lineHeight: Float, size: Float, face: Typeface): Float {
        val metrics = Paint().apply { typeface = face; textSize = size }.fontMetrics
        return top + (lineHeight - metrics.descent + metrics.ascent)/2 - metrics.ascent
    }

    private fun text(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.LEFT, typeface: Typeface = font, maxWidth: Float = 1125f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = size; this.color = color; textAlign = align }
        val count = paint.breakText(value, true, maxWidth, null)
        val shown = if (count < value.length) value.take((count - 1).coerceAtLeast(0)) + "…" else value
        canvas.drawText(shown, x, baseline, paint)
    }

    companion object {
        private const val Media = "https://sg.music.163.com/st/listen-home/static/media/"
        private const val PosterScatterLight = "https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/30522275058/d6e7/b8b6/97a5/a92f2f82f4dac32c6fd98089e9a7d317.png"
        private const val PosterScatterDark = "https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/30481586796/b513/c65f/e010/4b135d67fb7a5e6bb4bb15310d63cea2.png"
        private const val WallScatterLight = "https://p5.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/30522293494/483f/df04/f481/6b620920fdf4fbc57490a3e79ad0a041.png"
        private const val WallScatterDark = "https://p6.music.126.net/obj/wonDlsKUwrLClGjCm8Kx/30481649395/a2af/ceda/6dfb/f9cf599be02452d442730aebd4a3dbfa.png"
    }
}

internal suspend fun saveListeningExportToGallery(context: Context, file: File, name: String) = withContext(Dispatchers.IO) {
    check(Build.VERSION.SDK_INT >= 29)
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CurrentMusicX")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    try {
        checkNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
        currentCoroutineContext().ensureActive()
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
    } catch (error: Throwable) { resolver.delete(uri, null, null); throw error }
    uri
}

private fun cleanOldListeningExports(root: File) {
    val cutoff = System.currentTimeMillis() - 86_400_000L
    root.listFiles()?.filter { it.name.startsWith("listening-export-") && it.lastModified() < cutoff }?.forEach {
        if (it.isDirectory) it.deleteRecursively() else it.delete()
    }
}
