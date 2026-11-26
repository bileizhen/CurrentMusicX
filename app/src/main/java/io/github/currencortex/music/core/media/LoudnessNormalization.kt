package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.MusicSettings
import io.github.currencortex.music.data.settings.VolumeNormalizationTarget
import io.github.currencortex.music.data.song.AudioSource
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable

/** ReplayGain 2.0 defines reference loudness at -18 LUFS (89 dB SPL). */
internal const val REPLAYGAIN_REFERENCE_LUFS = -18.0f

/** Opus R128_*_GAIN tags use Q7.8 dB relative to -23 LUFS. */
internal const val R128_REFERENCE_LUFS = -23.0f

/** Apple SoundCheck iTunNORM targets -16 LUFS. */
internal const val ITUNNORM_REFERENCE_LUFS = -16.0f

/** NetEase song/url `gain` field targets approximately -14 LUFS. */
internal const val NETEASE_URL_GAIN_REFERENCE_LUFS = -14.0f

@Serializable
data class TrackLoudnessMetadata(
    val trackLufs: Float? = null,
    val trackPeak: Float? = null,
    val albumLufs: Float? = null,
    val albumPeak: Float? = null,
    val authoritative: Boolean = true,
    val sourceLabel: String = "",
)

@Serializable
data class TrackLoudnessSpec(
    val trackKey: String = "",
    val metadata: TrackLoudnessMetadata? = null,
    val cachedLufs: Float? = null,
    val cachedPeak: Float? = null,
    val cachedGatedBlocks: Int = 0,
) {
    /**
     * Resolve the initial reference loudness before real-time estimation.
     * Pair is (lufs, peak) and whether that reference is authoritative enough not to blend.
     */
    fun preferredReference(preferAlbumGain: Boolean): ResolvedLoudnessReference? {
        if (preferAlbumGain && metadata?.albumLufs != null && metadata.albumLufs.isFinite()) {
            return ResolvedLoudnessReference(
                lufs = metadata.albumLufs,
                peak = metadata.albumPeak ?: metadata.trackPeak,
                authoritative = metadata.authoritative,
                sourceLabel = metadata.sourceLabel.ifBlank { "专辑响度标签" },
            )
        }
        val metaTrack = metadata?.trackLufs?.takeIf { it.isFinite() }
        if (metaTrack != null && metadata.authoritative) {
            return ResolvedLoudnessReference(
                lufs = metaTrack,
                peak = metadata.trackPeak,
                authoritative = true,
                sourceLabel = metadata.sourceLabel.ifBlank { "响度标签" },
            )
        }
        val cached = cachedLufs?.takeIf { it.isFinite() }
        if (cached != null) {
            return ResolvedLoudnessReference(
                lufs = cached,
                peak = cachedPeak,
                authoritative = true,
                sourceLabel = "已缓存响度",
            )
        }
        if (metaTrack != null) {
            return ResolvedLoudnessReference(
                lufs = metaTrack,
                peak = metadata.trackPeak,
                authoritative = false,
                sourceLabel = metadata.sourceLabel.ifBlank { "音源响度参考" },
            )
        }
        return null
    }
}

data class ResolvedLoudnessReference(
    val lufs: Float,
    val peak: Float?,
    val authoritative: Boolean,
    val sourceLabel: String,
)

data class VolumeNormalizationSettings(
    val enabled: Boolean = false,
    val targetLufs: Float = VolumeNormalizationTarget.STANDARD.targetLufs,
    val adaptive: Boolean = true,
    val preferAlbumGain: Boolean = false,
) {
    companion object {
        fun from(settings: MusicSettings, bypassProcessing: Boolean = false) = VolumeNormalizationSettings(
            enabled = settings.volumeNormalizationEnabled && !bypassProcessing,
            targetLufs = settings.volumeNormalizationTarget.targetLufs,
            adaptive = settings.volumeNormalizationAdaptive,
            preferAlbumGain = settings.volumeNormalizationAlbumGain,
        )
    }
}

data class VolumeNormalizationRuntime(
    val active: Boolean = false,
    val trackKey: String = "",
    val gainDb: Float = 0f,
    val measuredLufs: Float? = null,
    val sourceLabel: String = "",
    val message: String = "未开启",
)

internal fun trackLoudnessKey(song: Song, source: AudioSource? = null): String {
    if (song.video) return ""
    val base = when (song.musicSource) {
        MusicSource.QQ_MUSIC -> "qq:${song.qqMusic?.mid?.takeIf { it.isNotBlank() } ?: song.externalIds.qqMusicId ?: song.id}"
        MusicSource.NAVIDROME -> "navidrome:${song.navidrome?.identity.orEmpty()}:${song.navidrome?.id.orEmpty()}"
        MusicSource.EMBY -> "emby:${song.emby?.identity.orEmpty()}:${song.emby?.id.orEmpty()}"
        MusicSource.LOCAL -> "local:${song.id}"
        else -> if (song.neteaseCloud) "netease:cloud:${song.id}" else "netease:${song.id}"
    }
    val variant = source?.takeIf { it.matched || it.source.startsWith("lx:") }
        ?.cacheIdentity?.takeIf { it.isNotBlank() }?.take(16)
    return if (variant != null) "$base:$variant" else base
}

internal object LoudnessMetadataMapper {
    fun fromReplayGain(
        trackGainDb: Float? = null,
        trackPeak: Float? = null,
        albumGainDb: Float? = null,
        albumPeak: Float? = null,
        sourceLabel: String = "ReplayGain 标签",
    ): TrackLoudnessMetadata? {
        val validTrackGain = trackGainDb?.takeIf { it.isFinite() && it in -40f..40f }
        val validAlbumGain = albumGainDb?.takeIf { it.isFinite() && it in -40f..40f }
        if (validTrackGain == null && validAlbumGain == null) return null
        return TrackLoudnessMetadata(
            trackLufs = validTrackGain?.let { REPLAYGAIN_REFERENCE_LUFS - it },
            trackPeak = trackPeak?.takeIf { it.isFinite() && it > 0f && it <= 4f },
            albumLufs = validAlbumGain?.let { REPLAYGAIN_REFERENCE_LUFS - it },
            albumPeak = albumPeak?.takeIf { it.isFinite() && it > 0f && it <= 4f },
            authoritative = true,
            sourceLabel = sourceLabel,
        )
    }

    fun fromR128Gain(
        trackGainQ78: Int? = null,
        albumGainQ78: Int? = null,
        sourceLabel: String = "R128 标签",
    ): TrackLoudnessMetadata? {
        val validTrack = trackGainQ78?.let { it / 256f }?.takeIf { it.isFinite() && it in -40f..40f }
        val validAlbum = albumGainQ78?.let { it / 256f }?.takeIf { it.isFinite() && it in -40f..40f }
        if (validTrack == null && validAlbum == null) return null
        return TrackLoudnessMetadata(
            trackLufs = validTrack?.let { R128_REFERENCE_LUFS - it },
            albumLufs = validAlbum?.let { R128_REFERENCE_LUFS - it },
            authoritative = true,
            sourceLabel = sourceLabel,
        )
    }

    fun fromSongAndSource(song: Song, source: AudioSource?): TrackLoudnessMetadata? {
        song.navidrome?.let { nav ->
            val hasGain = (nav.trackGainDb != null && (nav.trackGainDb != 0f || (nav.trackPeak != null && nav.trackPeak > 0f))) ||
                (nav.albumGainDb != null && (nav.albumGainDb != 0f || (nav.albumPeak != null && nav.albumPeak > 0f)))
            if (hasGain) {
                fromReplayGain(
                    trackGainDb = nav.trackGainDb,
                    trackPeak = nav.trackPeak,
                    albumGainDb = nav.albumGainDb,
                    albumPeak = nav.albumPeak,
                    sourceLabel = "Navidrome ReplayGain",
                )?.let { return it }
            }
        }
        song.emby?.normalizationGainDb?.takeIf { it.isFinite() && it in -40f..40f }?.let { gainDb ->
            if (gainDb != 0f) {
                fromReplayGain(
                    trackGainDb = gainDb,
                    sourceLabel = "Emby 响度元数据",
                )?.let { return it }
            }
        }
        source?.let { audio ->
            val gain = audio.gainDb?.takeIf { it.isFinite() && it in -40f..40f }
            val peak = audio.peak?.takeIf { it.isFinite() && it > 0f && it <= 4f }
            if (gain != null && (gain != 0f || peak != null)) {
                return TrackLoudnessMetadata(
                    trackLufs = NETEASE_URL_GAIN_REFERENCE_LUFS - gain,
                    trackPeak = peak,
                    authoritative = false,
                    sourceLabel = "网易云音源参考",
                )
            }
        }
        return null
    }
}
