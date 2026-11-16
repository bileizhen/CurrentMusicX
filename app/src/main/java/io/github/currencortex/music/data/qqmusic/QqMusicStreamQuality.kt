package io.github.currencortex.music.data.qqmusic

/**
 * Clear stream filenames issued by QQ Music. A quality may have several compatible
 * encodings. For example, the app requests different containers for the HQ tier.
 */
internal enum class QqMusicStreamQuality(
    val level: String,
    val prefix: String,
    val format: String,
    val nominalBitrate: Long = 0,
) {
    MASTER("qq_master", "AI00", "flac"),
    STUDIO("qq_studio", "Q000", "flac"),
    SURROUND_51("qq_surround", "Q001", "flac"),
    SURROUND_714("qq_surround", "Q003", "ogg"),
    SURROUND_916("qq_surround", "Q005", "ogg"),
    DOLBY("dolby", "D008", "mp4"),
    DOLBY_COMPAT("dolby", "D004", "mp4"),
    DTS_X("qq_dts_x", "DT03", "mp4"),
    NAC("qq_nac", "TL01", "nac"),
    HI_RES("hires", "RS01", "flac"),
    LOSSLESS("lossless", "F000", "flac"),
    LOSSLESS_OGG_640("lossless", "O801", "ogg", 640_000),
    HQ_OGG_320("exhigh", "O800", "ogg", 320_000),
    HQ_MP3_320("exhigh", "M800", "mp3", 320_000),
    HQ_OGG_192("exhigh", "O601", "ogg", 192_000),
    HQ_AAC_192("exhigh", "C600", "m4a", 192_000),
    STANDARD_MP3("standard", "M500", "mp3", 128_000),
    STANDARD_AAC("standard", "C400", "m4a", 96_000);

    fun filename(songMid: String, mediaMid: String): String = "$prefix$songMid$mediaMid.$format"

    companion object {
        fun candidates(level: String, supportsDolby: Boolean, supportsDtsX: Boolean): List<QqMusicStreamQuality> =
            when (level) {
                "auto" -> listOf(MASTER, STUDIO, HI_RES, LOSSLESS, HQ_OGG_320, HQ_MP3_320, STANDARD_MP3)
                "higher" -> entries.filter { it.level == "exhigh" }
                "qq_master" -> listOf(MASTER)
                "qq_studio" -> listOf(STUDIO)
                "qq_surround" -> listOf(SURROUND_51, SURROUND_714, SURROUND_916)
                "dolby" -> if (supportsDolby) listOf(DOLBY, DOLBY_COMPAT) else emptyList()
                "qq_dts_x" -> if (supportsDtsX) listOf(DTS_X) else emptyList()
                "qq_nac" -> listOf(NAC)
                "hires" -> listOf(HI_RES, LOSSLESS)
                "lossless" -> listOf(LOSSLESS, LOSSLESS_OGG_640)
                else -> entries.filter { it.level == level }
            }
    }
}
