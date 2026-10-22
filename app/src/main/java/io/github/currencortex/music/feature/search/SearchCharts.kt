package io.github.currencortex.music.feature.search

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.json.*

/** Search-home chart IDs are opaque strings such as HOT_SEARCH_STYLE_ACG#@#, never playlist IDs. */
data class SearchChart(val id: String, val name: String)
data class SearchChartPage(val words: List<HotSearchWord> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val error: String? = null)
data class SearchChartsState(val charts: List<SearchChart> = emptyList(), val pages: Map<String, SearchChartPage> = emptyMap(),
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null)

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun chartData(raw: JsonElement): JsonObject {
    val root = raw as? JsonObject ?: throw ApiException(ErrorKind.Parse)
    if ((root["code"] as? JsonPrimitive)?.intOrNull?.let { it != 200 } == true) throw ApiException(ErrorKind.Server)
    return root["data"] as? JsonObject ?: root
}
private fun chartWords(raw: JsonElement?): List<HotSearchWord> = (raw as? JsonArray).orEmpty().mapNotNull { value ->
    val item = value as? JsonObject ?: return@mapNotNull null
    val keyword = item.text("searchWord").trim()
    keyword.takeIf(String::isNotBlank)?.let { HotSearchWord(it, item.text("content"), item.text("toUserWord").ifBlank { it }) }
}.distinctBy { it.text }

internal fun parseSearchCharts(raw: JsonElement): SearchChartsState {
    val data = chartData(raw)
    val list = (data["tabInfo"] as? JsonObject)?.get("list") as? JsonArray ?: throw ApiException(ErrorKind.Parse)
    val charts = list.mapNotNull { value ->
        val item = value as? JsonObject ?: return@mapNotNull null
        val id = item.text("id"); val name = item.text("name").trim()
        if (id.isBlank() || name.isBlank()) null else SearchChart(id, name)
    }.distinctBy { it.id }
    val items = data["items"] as? JsonObject
    val pages = charts.mapNotNull { chart ->
        val words = chartWords(items?.get(chart.id)).ifEmpty {
            if (chart == charts.firstOrNull()) chartWords((data["itemInfo"] as? JsonObject)?.get("itemList")) else emptyList()
        }
        words.takeIf { it.isNotEmpty() }?.let { chart.id to SearchChartPage(words = it, loaded = true) }
    }.toMap()
    return SearchChartsState(charts = charts, pages = pages, loaded = true)
}

internal fun parseSearchChartWords(raw: JsonElement): List<HotSearchWord> = chartWords(chartData(raw)["itemList"])
