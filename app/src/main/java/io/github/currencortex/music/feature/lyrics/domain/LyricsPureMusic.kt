// Adapted from Folia f96c65b27f7ca351e8db781301cedfa0a52252e2,
// src/utils/lyrics/pureMusic.ts (AGPL-3.0); original contributors retain copyright.
package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

/** An explicit whole-line notice is evidence; missing lyrics or a passing mention are not. */
internal fun LyricsDocument.isExplicitPureMusic(): Boolean {
    if (metadata.values["pureMusic"] == listOf("true")) return true
    val notice = Regex("^(?:[此本](?:首)?(?:歌曲|曲目|作品|乐曲|曲)?[为是]?)?(?:(?:没有|无)(?:填词|歌词)的)?纯音乐敬?请[您你]?欣赏$")
    val credit = Regex("^(作词|作曲|编曲|制作|录音|混音|母带|吉他|贝斯|和声|OP|SP)\\s*[:：]", RegexOption.IGNORE_CASE)
    var found = false
    for (line in lines) {
        val text = line.text.trim()
        if (text.isEmpty()) continue
        val normalized = FoliaLyricsMatchPolicy.normalize(text).replace(" ", "")
        if (notice.matches(normalized)) { found = true; continue }
        if (normalized.isEmpty() || credit.containsMatchIn(text) || text.contains(Regex("[:：]|\\s[-–—]\\s"))) continue
        return false
    }
    return found
}
