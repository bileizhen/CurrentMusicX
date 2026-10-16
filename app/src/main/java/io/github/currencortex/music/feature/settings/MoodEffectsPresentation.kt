package io.github.currencortex.music.feature.settings

/** Original 9.5.30 JSK 967 keyword table; labels and videos are from the original RN constant.ts. */
internal data class MoodEmotionPresentation(val label: String, val name: String, val keyword: String) {
    val video get() = "emotion_video_map_${label}_dark_video.mp4"
    val poster get() = "emotion_${label}_dark_poster.png"
}

internal object MoodEffectsPresentation {
    val emotions = listOf(
        MoodEmotionPresentation("default", "默认", "每种心情都有回响"),
        MoodEmotionPresentation("ecstatic", "狂欢", "狂欢是迪斯科节奏"),
        MoodEmotionPresentation("passionate", "热血", "热血是音浪翻滚"),
        MoodEmotionPresentation("energetic", "活力", "活力是向上的光"),
        MoodEmotionPresentation("joyful", "欢快", "愉悦是跳跃的鼓点"),
        MoodEmotionPresentation("sweet", "甜蜜", "甜蜜悬浮在粉色中"),
        MoodEmotionPresentation("confident", "自信", "自信放光芒"),
        MoodEmotionPresentation("romantic", "浪漫", "浪漫是绽放的星"),
        MoodEmotionPresentation("relaxed", "放松", "放松让时间融化"),
        MoodEmotionPresentation("healing", "治愈", "治愈是一抹绿"),
        MoodEmotionPresentation("calm", "平静", "平静是风止步"),
        MoodEmotionPresentation("nostalgic", "思念", "思念藏在歌里"),
        MoodEmotionPresentation("lonely", "孤独", "孤单是徘徊的身影"),
        MoodEmotionPresentation("sad", "悲伤", "悲伤是像素的眼泪"),
        MoodEmotionPresentation("bitter", "苦情", "深情是一杯咖啡"),
        MoodEmotionPresentation("depressed", "抑郁", "忧郁是深蓝的房间"),
    )
    fun emotion(label: String?) = emotions.firstOrNull { it.label == label } ?: emotions.first()

    // The original RN page's generic fallback. It is not presented as a measured per-song result.
    const val introduction = "按歌曲情绪智能调音，读懂每首歌背后的情感温度，让音乐不只是播放，而是情绪的共鸣"
    const val fallbackDescription = "按歌曲情绪智能调音，让每首歌情感更强烈，放大心动与共鸣"
    val explanation = listOf(
        "1.功能说明" to "通过AI算法分析歌曲的旋律、节奏等音频特征，自动识别当前歌曲的主导情绪，实时叠加与情绪精准匹配的专属音效参数，强化歌曲的情感氛围与沉浸感，无需手动调节即可获得与歌曲情绪高度契合的听感体验",
        "2.歌曲覆盖范围" to "系统持续为更多歌曲完成情绪深度解析，已解析歌曲可获得精准情绪调音；其余歌曲将自动应用通用情绪优化方案，持续完善中",
        "3.情绪音效" to "①情绪调音师针对不同情绪类型对应独特的调音策略，放大快乐、沉淀悲伤、温暖治愈，让每首歌的情感表达更加鲜明\n②情绪音效可与音质权益叠加使用：当同时使用空间音频（沉浸环绕声、臻音全景声）和情绪调音师时，为保障好的听歌体验，情绪调音效果将有所降权",
        "4.版权限制" to "部分版权方限制的特殊歌曲（如付费数字专辑）禁用音效调节功能",
        "5.数据隐私" to "用户的音效使用记录或歌曲情绪分析数据仅用于本地音效优化，不会上传或关联个人身份信息",
    )
}
