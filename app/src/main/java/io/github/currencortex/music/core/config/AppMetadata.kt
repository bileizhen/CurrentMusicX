package io.github.currencortex.music.core.config

/**
 * Product metadata shared by About, update checking and diagnostics.
 * scripts/init_template.py rewrites APP_NAME and GITHUB_REPO for a new project.
 */
object AppMetadata {
    const val APP_NAME = "CurrentMusic"
    const val APP_DESCRIPTION = "原生 Android 音乐客户端"
    const val AUTHOR = "CurrentMusic"
    const val GITHUB_OWNER = "backrooms-yrc"
    const val GITHUB_REPO = "CurrentMusic"
    const val LICENSE = "GPL-3.0-only"

    const val PROJECT_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    // Native releases live in a dedicated repository so they never mix with the
    // web client's releases published on backrooms-yrc/CurrentMusic.
    const val RELEASES_OWNER = "bileizhen"
    const val RELEASES_REPO = "CurrentMusicX"
    const val RELEASES_PROJECT_URL = "https://github.com/$RELEASES_OWNER/$RELEASES_REPO"
    const val RELEASES_URL = "$RELEASES_PROJECT_URL/releases"
    const val ISSUES_URL = "$PROJECT_URL/issues"
    const val AUTHOR_URL = "https://github.com/$GITHUB_OWNER"
    // Optional product-specific URLs. Leave blank to use bundled documents.
    // Optional HTTPS prefix for GitHub asset mirrors; blank keeps the official source only.
    const val UPDATE_MIRROR_PREFIX = ""
    const val WEBSITE_URL = ""
    const val PRIVACY_URL = ""
}
