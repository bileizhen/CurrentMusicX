package io.github.currencortex.music.feature.share

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.currencortex.music.ui.component.ArtworkGlowPalette

/** Fixed export coordinates keep the saved image identical to the preview at any screen/font scale. */
internal object SongSharePoster {
    const val WIDTH = 1080
    const val HEIGHT = 1440

    fun render(content: SongShareContent, cover: Bitmap?): Bitmap {
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val palette = if (cover == null) ArtworkGlowPalette.Neutral else {
            val sample = Bitmap.createScaledBitmap(cover, 64, 64, true)
            val pixels = IntArray(4096)
            sample.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            ArtworkGlowPalette.fromPixels(pixels).also { if (sample !== cover) sample.recycle() }
        }
        fun dark(color: Int, amount: Float) = Color.rgb((Color.red(color) * amount).toInt(),
            (Color.green(color) * amount).toInt(), (Color.blue(color) * amount).toInt())
        paint.shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            intArrayOf(dark(palette.primary, .22f), Color.rgb(17, 17, 23), dark(palette.secondary, .26f)),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        fun glow(color: Int, x: Float, y: Float, radius: Float) {
            paint.shader = RadialGradient(x, y, radius, intArrayOf((color and 0xffffff) or 0x32000000,
                color and 0xffffff), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        }
        glow(palette.primary, 1040f, 160f, 900f)
        glow(palette.secondary, 40f, 1400f, 950f)
        paint.shader = null
        fun label(text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
            paint.color = color; paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(text, x, y, paint)
        }
        label("CurrentMusic", 90f, 112f, 40f, Color.WHITE, true)
        label("MUSIC SHARE", 90f, 154f, 22f, 0x88ffffff.toInt(), true)
        val artwork = RectF(230f, 180f, 850f, 800f)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(artwork, 34f, 34f, Path.Direction.CW) })
        paint.color = Color.WHITE
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val left = (cover.width - side) / 2; val top = (cover.height - side) / 2
            paint.isFilterBitmap = true
            canvas.drawBitmap(cover, Rect(left, top, left + side, top + side), artwork, paint)
        } else {
            paint.color = dark(palette.primary, .5f)
            canvas.drawRect(artwork, paint)
            label("♪", 438f, 592f, 230f, 0xccffffff.toInt())
        }
        canvas.restore()
        var titleSize = 54f
        while (titleSize > 40f && paragraph(content.title, titleSize, 900, 20, Color.WHITE, true).lineCount > 3) titleSize -= 2f
        val title = paragraph(content.title, titleSize, 900, 3, Color.WHITE, true)
        drawParagraph(canvas, title, 90f, 830f)
        val artistTop = 830f + title.height + 24f
        val artists = paragraph(content.artists, 32f, 900, if (artistTop < 990f) 2 else 1, 0xbfffffff.toInt())
        drawParagraph(canvas, artists, 90f, artistTop)
        paint.color = 0x33ffffff; paint.strokeWidth = 1.5f
        canvas.drawLine(90f, 1100f, 990f, 1100f, paint)
        val url = content.publicUrl
        val footerWidth = if (url == null) 900 else 590
        drawParagraph(canvas, paragraph(if (url == null) "由 CurrentMusicX 分享" else "在 CurrentMusic 收听",
            26f, footerWidth, 1, 0xafffffff.toInt()), 90f, 1150f)
        if (url != null) {
            val qr = qr(url)
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(736f, 1136f, 984f, 1384f), 20f, 20f, paint)
            paint.isFilterBitmap = false
            canvas.drawBitmap(qr, 752f, 1152f, paint)
            qr.recycle()
            drawParagraph(canvas, paragraph(url.removePrefix("https://"), 24f, footerWidth, 2,
                0xddffffff.toInt(), monospace = true), 90f, 1198f)
            label("扫码或访问链接", 90f, 1290f, 22f, 0x99ffffff.toInt())
            drawParagraph(canvas, paragraph("也可打开 CurrentMusicX 搜索歌名收听", 21f, footerWidth, 2,
                0x88ffffff.toInt()), 90f, 1320f)
        } else {
            drawParagraph(canvas, paragraph("${content.title} · ${content.artists}", 26f,
                footerWidth, 2, 0xccffffff.toInt()), 90f, 1204f)
            label("打开音乐应用，搜索歌曲名收听", 90f, 1332f, 23f, 0x99ffffff.toInt())
        }
        return output
    }

    internal fun qr(url: String): Bitmap {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 216, 216,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    private fun paragraph(text: String, size: Float, width: Int, lines: Int, color: Int,
        bold: Boolean = false, monospace: Boolean = false): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = if (monospace) Typeface.MONOSPACE else if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(4f, 1.04f)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
    }

    private fun drawParagraph(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
    }
}
