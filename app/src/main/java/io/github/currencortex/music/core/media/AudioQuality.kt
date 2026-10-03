package io.github.currencortex.music.core.media

enum class AudioQuality(val value: String, val label: String) {
    AUTO("auto", "自动最高"), JYMASTER("jymaster", "超清母带"), JYEFFECT("jyeffect", "臻音全景声"),
    SKY("sky", "沉浸环绕声"), HIRES("hires", "高清臻音"), LOSSLESS("lossless", "无损"),
    EXHIGH("exhigh", "极高"), STANDARD("standard", "标准");
    companion object { fun from(value: String) = entries.firstOrNull { it.value == value } ?: AUTO }
}
