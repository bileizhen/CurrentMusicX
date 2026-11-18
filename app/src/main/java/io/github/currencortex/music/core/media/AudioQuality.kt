package io.github.currencortex.music.core.media

enum class AudioQuality(val value: String, val label: String) {
    AUTO("auto", "自动最高"), JYMASTER("jymaster", "超清母带"),
    SKY("sky", "沉浸环绕声"), VIVID("vivid", "臻音全景声"), DOLBY("dolby", "杜比全景声"),
    JYEFFECT("jyeffect", "高清臻音"), HIRES("hires", "Hi-Res"), LOSSLESS("lossless", "无损"),
    EXHIGH("exhigh", "极高"), HIGHER("higher", "较高"), STANDARD("standard", "标准"),
    QQ_NAC("qq_nac", "NAC 品质"),
    QQ_MASTER("qq_master", "臻品母带 4.0"),
    QQ_SURROUND("qq_surround", "臻品全景声 3.0"),
    QQ_STUDIO("qq_studio", "臻品音质 2.0"),
    QQ_DTS_X("qq_dts_x", "DTS:X");

    val qqOnly: Boolean get() = value.startsWith("qq_")
    companion object { fun from(value: String) = entries.firstOrNull { it.value == value } ?: AUTO }
}
