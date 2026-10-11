package io.github.currencortex.music.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException

enum class ErrorKind(val message: String) {
    Network("网络连接失败"), Timeout("请求超时，请重试"), Unauthorized("登录已过期"),
    Forbidden("没有访问权限"), NotFound("内容不存在"), RateLimited("请求过于频繁"),
    Server("服务器异常"), Parse("数据格式异常"), Unknown("操作失败，请重试"),
    AudioKeyRequired("请在网络与播放设置中填写 LeiZ API Key"),
    AudioKeyRejected("LeiZ API Key 无效或没有访问权限，请检查音源设置"),
    AudioUnavailable("LeiZ 未提供这首歌的音源，可能受 VIP 权限限制"),
    AudioSourceChanged("音源设置已改变，请重新播放"),
    NeteaseBindingRequired("请先绑定网易云账号；绑定失效时请重新登录网易云"),
    NeteaseSessionChanged("网易云账号已切换，请重试"),
    NeteaseLikedPlaylistUnavailable("无法读取网易云“我喜欢的音乐”歌单，请检查网易云绑定"),
    NeteaseHeartNoRecommendations("暂时没有心动推荐，请换一首歌再试")
}
class ApiException(val kind: ErrorKind, val status: Int = 0) : IOException(kind.message)
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val kind: ErrorKind, val status: Int = 0) : AppResult<Nothing>
}
suspend fun <T> appResult(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) { throw e
} catch (e: Exception) {
    AppResult.Failure(when (e) {
        is ApiException -> e.kind
        is SocketTimeoutException -> ErrorKind.Timeout
        is SerializationException -> ErrorKind.Parse
        is IOException -> ErrorKind.Network
        else -> ErrorKind.Unknown
    }, status = (e as? ApiException)?.status ?: 0)
}
