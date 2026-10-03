package io.github.currencortex.music.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException

enum class ErrorKind(val message: String) {
    Network("网络连接失败"), Timeout("请求超时，请重试"), Unauthorized("登录已过期"),
    Forbidden("没有访问权限"), NotFound("内容不存在"), RateLimited("请求过于频繁"),
    Server("服务器异常"), Parse("数据格式异常"), Unknown("操作失败，请重试")
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
