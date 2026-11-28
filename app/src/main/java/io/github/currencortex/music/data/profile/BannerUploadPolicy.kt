package io.github.currencortex.music.data.profile

internal enum class BannerUploadPolicy(val maxVideoDurationMs: Long) {
    STANDARD(5_000L),
    EXTENDED(10_000L);

    val allowsOriginalMedia: Boolean get() = this == EXTENDED

    companion object {
        fun forUser(role: UserRole?, level: Int?): BannerUploadPolicy =
            if (role != null || (level ?: 0) >= 3) EXTENDED else STANDARD
    }
}
