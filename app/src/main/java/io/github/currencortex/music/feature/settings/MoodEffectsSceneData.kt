package io.github.currencortex.music.feature.settings

// Keywords/highlight colors follow the original detail; emoji media follows the recorded RN light scenes.
// Hero edge colors are sampled from the corresponding original light posters, never replacement artwork.
internal data class MoodDetailScene(val label: String, val color: Long, val background: Long, val redWord: String,
    val heroBackground: Long) {
    val video get() = when (label) {
        "unUsed" -> "emotion_light_unuse_video_bg_url.mp4"
        "default" -> "emotion_video_map_default_light_video.mp4"
        else -> "mood_card_${label}_light_video.mp4"
    }
    val poster get() = when (label) {
        "unUsed" -> "emotion_light_unuse_bg_url.png"
        "default" -> "emotion_default_light_poster.png"
        else -> "mood_card_${label}_light_poster.png"
    }
}
internal object MoodDetailScenes {
    private val values = mapOf(
        "unUsed" to MoodDetailScene("unUsed", 0xFFFFFFFF, 0xFF000000, "", 0xFFAECDFB),
        "sad" to MoodDetailScene("sad", 0xFF859FB6, 0xFF17212E, "悲伤", 0xFF4A84C5),
        "confident" to MoodDetailScene("confident", 0xFF2CDDC2, 0xFF012225, "自信", 0xFF5AC0DE),
        "joyful" to MoodDetailScene("joyful", 0xFFFFC83F, 0xFF170E00, "愉悦", 0xFFEAA712),
        "passionate" to MoodDetailScene("passionate", 0xFFFF491B, 0xFF260801, "热血", 0xFFE86360),
        "sweet" to MoodDetailScene("sweet", 0xFFF9547E, 0xFF4E021D, "甜蜜", 0xFFF96FA4),
        "romantic" to MoodDetailScene("romantic", 0xFFDF97F0, 0xFF291139, "浪漫", 0xFFC497E8),
        "ecstatic" to MoodDetailScene("ecstatic", 0xE6FFB039, 0xFF802F04, "狂欢", 0xFFEA8565),
        "energetic" to MoodDetailScene("energetic", 0xE664FFAC, 0xFF0A6333, "活力", 0xFF64CC99),
        "relaxed" to MoodDetailScene("relaxed", 0xFF8EE1FF, 0xFF036284, "放松", 0xFF66B7D4),
        "healing" to MoodDetailScene("healing", 0xFF86FCD4, 0xFF1C6850, "治愈", 0xFF67C0A8),
        "calm" to MoodDetailScene("calm", 0xFFA9E1FD, 0xFF5F98AE, "平静", 0xFF74AFCC),
        "nostalgic" to MoodDetailScene("nostalgic", 0xE6D9A4FF, 0xFF58319D, "思念", 0xFF9977E7),
        "lonely" to MoodDetailScene("lonely", 0xFFA7C3DF, 0xFF2C3E54, "孤单", 0xFF7191AE),
        "bitter" to MoodDetailScene("bitter", 0xFFB4977E, 0xFF533B27, "深情", 0xFF95877C),
        "depressed" to MoodDetailScene("depressed", 0xFF79A6E7, 0xFF265697, "忧郁", 0xFF3A77C3),
        "default" to MoodDetailScene("default", 0xE6FFFFFF, 0xFF571407, "音乐", 0xFFDC5544),
    )
    fun forLabel(label: String?) = values[label] ?: values.getValue("default")
}
