package io.github.currencortex.music.data.profile

import kotlin.math.min

/** Image-relative coordinates keep the preview and uploaded square identical at any screen size. */
data class AvatarCrop(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f) {
    fun constrained(width: Int, height: Int): AvatarCrop {
        require(width > 0 && height > 0)
        val scale = zoom.coerceIn(1f, 5f)
        val half = min(width, height) / (2f * scale)
        return AvatarCrop(scale, centerX.coerceIn(half / width, 1f - half / width),
            centerY.coerceIn(half / height, 1f - half / height))
    }

    fun transform(width: Int, height: Int, viewport: Float, panX: Float, panY: Float,
        zoomChange: Float, focusX: Float = viewport / 2f, focusY: Float = viewport / 2f): AvatarCrop {
        require(viewport > 0)
        val before = constrained(width, height)
        val nextZoom = (before.zoom * zoomChange).coerceIn(1f, 5f)
        val beforeScale = viewport / min(width, height) * before.zoom
        val afterScale = viewport / min(width, height) * nextZoom
        return AvatarCrop(nextZoom,
            before.centerX + ((focusX - viewport / 2f) * (1f / beforeScale - 1f / afterScale) - panX / afterScale) / width,
            before.centerY + ((focusY - viewport / 2f) * (1f / beforeScale - 1f / afterScale) - panY / afterScale) / height)
            .constrained(width, height)
    }

    fun bounds(width: Int, height: Int): AvatarCropBounds {
        val crop = constrained(width, height)
        val size = min(width, height) / crop.zoom
        return AvatarCropBounds(crop.centerX * width - size / 2f, crop.centerY * height - size / 2f, size)
    }
}

data class AvatarCropBounds(val left: Float, val top: Float, val size: Float)
