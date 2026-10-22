package io.github.currencortex.music.ui.component

/** Each arm approaches along its own tip-to-tip axis, keeping its line through the junction. */
internal enum class HaloStarArm(
    val points: FloatArray,
    val startMs: Float,
    val endMs: Float,
    travelX: Float? = null,
    travelY: Float? = null,
) {
    UPRIGHT(floatArrayOf(1075f, 145f, 958f, 438f, 800f, 765f, 932f, 445f),
        2233f, 2467f, travelY = -800f),
    CROSSBAR(floatArrayOf(655f, 285f, 949f, 429f, 1178f, 590f, 938f, 456f),
        2367f, 2633f, travelX = -820f);

    private val axisX = points[0] - points[4]
    private val axisY = points[1] - points[5]
    val travelX: Float = travelX ?: requireNotNull(travelY) * axisX / axisY
    val travelY: Float = travelY ?: requireNotNull(travelX) * axisY / axisX

    fun progress(timeMs: Float): Float = ((timeMs - startMs) / (endMs - startMs)).coerceIn(0f, 1f)
}
