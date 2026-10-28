package io.github.currencortex.music.core.media

/** A bounded copy keeps the latest attempt and, if necessary, both its beginning and final result. */
internal object AudioDiagnosticExcerpt {
    private const val NOTE = "[日志过长，已省略部分中间行；请导出完整诊断]"
    fun latest(header: String, lines: List<String>, maxBytes: Int = 3000): String {
        val marker = lines.indexOfLast { "decodedPcm.sink" in it }
        val selected = if (marker < 0) lines.takeLast(30) else {
            val previous = (marker - 1 downTo 0).firstOrNull { "decodedPcm.sink" in lines[it] } ?: -1
            val source = (previous + 1 until marker).filter { "source.declared" in lines[it] }.takeLast(2)
            val start = source.firstOrNull() ?: marker
            val failure = (marker until lines.size).lastOrNull { "finalState=FAILED" in lines[it] || "player.finalState=ERROR" in lines[it] }
            lines.subList(start, failure?.plus(1) ?: lines.size)
        }
        fun size(value: String) = value.toByteArray(Charsets.UTF_8).size
        val full = (listOf(header) + selected).joinToString("\n")
        if (size(full) <= maxBytes) return full
        val budget = (maxBytes - size(header) - size(NOTE) - 4).coerceAtLeast(0)
        val head = mutableListOf<String>(); val tail = mutableListOf<String>()
        var used = 0
        for (line in selected) {
            val bytes = size(line) + 1
            if (used + bytes > budget * 2 / 3) break
            head += line; used += bytes
        }
        for (index in selected.lastIndex downTo head.size) {
            val line = selected[index]; val bytes = size(line) + 1
            if (used + bytes > budget) break
            tail.add(0, line); used += bytes
        }
        return (listOf(header) + head + NOTE + tail).joinToString("\n")
    }
}
