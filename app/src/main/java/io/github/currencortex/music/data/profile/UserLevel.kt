package io.github.currencortex.music.data.profile

/** Missing fields on older servers stay unknown; privileges never derive from a level. */
fun userLevelLabel(level: Int?): String? = when (level) {
    null -> null
    in 1..6 -> "Lv.$level"
    7 -> "Lv.6 Plus"
    else -> "等级 $level"
}
