package io.github.currencortex.music.core.media

enum class AudioQuality(val value: String, val label: String) {
    AUTO("auto", "自动最高"), JYMASTER("jymaster", "超清母带"),
    SKY("sky", "沉浸环绕声"), VIVID("vivid", "臻音全景声"), DOLBY("dolby", "杜比全景声"),
    JYEFFECT("jyeffect", "高清臻音"), HIRES("hires", "Hi-Res"), LOSSLESS("lossless", "无损"),
    EXHIGH("exhigh", "极高"), HIGHER("higher", "较高"), STANDARD("standard", "标准");
    companion object { fun from(value: String) = entries.firstOrNull { it.value == value } ?: AUTO }
}
