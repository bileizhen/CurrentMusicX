package io.github.currencortex.music.feature.update

/** Match the installed base version; never present another version's notes as its own. */
internal fun installedReleaseNotes(history: String, version: String): String {
    val base = version.removePrefix("v").substringBefore('-')
    val sections = Regex("(?m)^## (\\d+\\.\\d+\\.\\d+)[^\\n]*$").findAll(history).toList()
    val index = sections.indexOfFirst { it.groupValues[1] == base }
    if (index < 0) return "此版本未附带更新说明，可从右上角查看完整发布记录。"
    return history.substring(sections[index].range.last + 1,
        sections.getOrNull(index + 1)?.range?.first ?: history.length).trim()
}
