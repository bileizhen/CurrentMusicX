package io.github.currencortex.music.core.network

enum class SourceAvailabilityStatus(val label: String) {
    UNKNOWN("未检测"), CHECKING("检测中"), AVAILABLE("可用"), LIMITED("限流"), TIMEOUT("超时"), UNAVAILABLE("不可用")
}
