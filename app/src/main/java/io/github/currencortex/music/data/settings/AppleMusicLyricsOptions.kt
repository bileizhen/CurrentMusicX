package io.github.currencortex.music.data.settings

/** Apple Music presentation only: these values never alter lyric timestamps or audio speed. */
data class AppleMusicLyricsOptions(
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val animationSpeed: Float = DEFAULT_ANIMATION_SPEED,
    val glowIntensity: Float = DEFAULT_GLOW_INTENSITY,
    val blurStrength: Float = DEFAULT_BLUR_STRENGTH,
) {
    fun normalized() = copy(
        lineSpacing = lineSpacing.normalized(LINE_SPACING_RANGE, DEFAULT_LINE_SPACING),
        lineHeight = lineHeight.normalized(LINE_HEIGHT_RANGE, DEFAULT_LINE_HEIGHT),
        animationSpeed = animationSpeed.normalized(ANIMATION_SPEED_RANGE, DEFAULT_ANIMATION_SPEED),
        glowIntensity = glowIntensity.normalized(GLOW_INTENSITY_RANGE, DEFAULT_GLOW_INTENSITY),
        blurStrength = blurStrength.normalized(BLUR_STRENGTH_RANGE, DEFAULT_BLUR_STRENGTH),
    )

    companion object {
        // Relative to the original 0.4 em padding on each side of a lyric group.
        const val DEFAULT_LINE_SPACING = .65f
        const val MIN_LINE_SPACING = .25f
        const val MAX_LINE_SPACING = 1.5f
        const val DEFAULT_LINE_HEIGHT = 1.2f
        const val MIN_LINE_HEIGHT = 1.1f
        const val MAX_LINE_HEIGHT = 1.6f
        const val DEFAULT_ANIMATION_SPEED = 1f
        const val MIN_ANIMATION_SPEED = .5f
        const val MAX_ANIMATION_SPEED = 2f
        const val DEFAULT_GLOW_INTENSITY = 1f
        const val MIN_GLOW_INTENSITY = 0f
        const val MAX_GLOW_INTENSITY = 2f
        const val DEFAULT_BLUR_STRENGTH = 1f
        const val MIN_BLUR_STRENGTH = 0f
        const val MAX_BLUR_STRENGTH = 2f

        val LINE_SPACING_RANGE = MIN_LINE_SPACING..MAX_LINE_SPACING
        val LINE_HEIGHT_RANGE = MIN_LINE_HEIGHT..MAX_LINE_HEIGHT
        val ANIMATION_SPEED_RANGE = MIN_ANIMATION_SPEED..MAX_ANIMATION_SPEED
        val GLOW_INTENSITY_RANGE = MIN_GLOW_INTENSITY..MAX_GLOW_INTENSITY
        val BLUR_STRENGTH_RANGE = MIN_BLUR_STRENGTH..MAX_BLUR_STRENGTH

        private fun Float.normalized(range: ClosedFloatingPointRange<Float>, default: Float) =
            if (isFinite()) coerceIn(range) else default
    }
}
