package io.github.currencortex.music.data.profile

/** Image-relative coordinates keep the preview and uploaded square identical at any screen size. */
data class AvatarCrop(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f) {
    fun constrained(width: Int, height: Int): AvatarCrop =
        fromAspect(constrainAspectCrop(zoom, centerX, centerY, width, height, 1f))

    fun transform(width: Int, height: Int, viewport: Float, panX: Float, panY: Float,
        zoomChange: Float, focusX: Float = viewport / 2f, focusY: Float = viewport / 2f): AvatarCrop =
        fromAspect(transformAspectCrop(zoom, centerX, centerY, width, height, viewport, viewport,
            1f, panX, panY, zoomChange, focusX, focusY))

    fun bounds(width: Int, height: Int): AvatarCropBounds {
        val crop = aspectCropBounds(zoom, centerX, centerY, width, height, 1f)
        return AvatarCropBounds(crop.left, crop.top, crop.width)
    }

    private companion object {
        fun fromAspect(crop: BannerCrop) = AvatarCrop(crop.zoom, crop.centerX, crop.centerY)
    }
}

data class AvatarCropBounds(val left: Float, val top: Float, val size: Float)

/** Image-relative coordinates for pan/zoom banner cropping at a target aspect ratio. */
data class BannerCrop(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f) {
    fun constrained(width: Int, height: Int, aspectRatio: Float): BannerCrop =
        constrainAspectCrop(zoom, centerX, centerY, width, height, aspectRatio)

    fun transform(width: Int, height: Int, viewportWidth: Float, viewportHeight: Float,
        aspectRatio: Float, panX: Float, panY: Float, zoomChange: Float,
        focusX: Float = viewportWidth / 2f, focusY: Float = viewportHeight / 2f): BannerCrop =
        transformAspectCrop(zoom, centerX, centerY, width, height, viewportWidth, viewportHeight,
            aspectRatio, panX, panY, zoomChange, focusX, focusY)

    fun bounds(width: Int, height: Int, aspectRatio: Float): BannerCropBounds =
        aspectCropBounds(zoom, centerX, centerY, width, height, aspectRatio)
}

data class BannerCropBounds(val left: Float, val top: Float, val width: Float, val height: Float)

private fun baseCropDimensions(width: Int, height: Int, aspectRatio: Float): Pair<Float, Float> {
    require(width > 0 && height > 0 && aspectRatio > 0f)
    val baseWidth = if (width.toFloat() / height > aspectRatio) height * aspectRatio else width.toFloat()
    return baseWidth to (baseWidth / aspectRatio)
}

private fun constrainAspectCrop(zoom: Float, centerX: Float, centerY: Float,
    width: Int, height: Int, aspectRatio: Float): BannerCrop {
    val (baseWidth, baseHeight) = baseCropDimensions(width, height, aspectRatio)
    val scale = zoom.coerceIn(1f, 5f)
    val halfX = (baseWidth / (2f * scale * width)).coerceAtMost(0.5f)
    val halfY = (baseHeight / (2f * scale * height)).coerceAtMost(0.5f)
    return BannerCrop(scale, centerX.coerceIn(halfX, 1f - halfX), centerY.coerceIn(halfY, 1f - halfY))
}

private fun transformAspectCrop(zoom: Float, centerX: Float, centerY: Float,
    width: Int, height: Int, viewportWidth: Float, viewportHeight: Float, aspectRatio: Float,
    panX: Float, panY: Float, zoomChange: Float, focusX: Float, focusY: Float): BannerCrop {
    require(viewportWidth > 0f && viewportHeight > 0f)
    val (baseWidth, baseHeight) = baseCropDimensions(width, height, aspectRatio)
    val before = constrainAspectCrop(zoom, centerX, centerY, width, height, aspectRatio)
    val nextZoom = (before.zoom * zoomChange).coerceIn(1f, 5f)
    val beforeScaleX = viewportWidth / baseWidth * before.zoom
    val afterScaleX = viewportWidth / baseWidth * nextZoom
    val beforeScaleY = viewportHeight / baseHeight * before.zoom
    val afterScaleY = viewportHeight / baseHeight * nextZoom
    return BannerCrop(
        nextZoom,
        before.centerX + ((focusX - viewportWidth / 2f) * (1f / beforeScaleX - 1f / afterScaleX) - panX / afterScaleX) / width,
        before.centerY + ((focusY - viewportHeight / 2f) * (1f / beforeScaleY - 1f / afterScaleY) - panY / afterScaleY) / height,
    ).let { constrainAspectCrop(it.zoom, it.centerX, it.centerY, width, height, aspectRatio) }
}

private fun aspectCropBounds(zoom: Float, centerX: Float, centerY: Float,
    width: Int, height: Int, aspectRatio: Float): BannerCropBounds {
    val (baseWidth, baseHeight) = baseCropDimensions(width, height, aspectRatio)
    val crop = constrainAspectCrop(zoom, centerX, centerY, width, height, aspectRatio)
    val cropWidth = baseWidth / crop.zoom
    val cropHeight = baseHeight / crop.zoom
    return BannerCropBounds(crop.centerX * width - cropWidth / 2f, crop.centerY * height - cropHeight / 2f,
        cropWidth, cropHeight)
}
