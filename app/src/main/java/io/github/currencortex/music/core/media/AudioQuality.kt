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
    QQ_DTS_X("qq_dts_x", "DTS:X"),
    KUGOU_VIPER_MASTER("kugou_viper_master", "蝰蛇母带 2.0"),
    KUGOU_VIPER_CLEAR("kugou_viper_clear", "蝰蛇超清"),
    KUGOU_VIPER_ATMOS("kugou_viper_atmos", "蝰蛇全景声 2.0");

    val qqOnly: Boolean get() = value.startsWith("qq_")
    val kugouOnly: Boolean get() = value.startsWith("kugou_")
    companion object { fun from(value: String) = entries.firstOrNull { it.value == value } ?: AUTO }
}
