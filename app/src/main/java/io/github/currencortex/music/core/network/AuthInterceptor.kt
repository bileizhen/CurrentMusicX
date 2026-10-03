package io.github.currencortex.music.core.network

import okhttp3.Interceptor
import okhttp3.Response

class RequestSession(val server: String, val token: String?) {
    override fun toString() = "RequestSession(redacted)"
    override fun equals(other: Any?) = other is RequestSession && server == other.server && token == other.token
    override fun hashCode() = 31 * server.hashCode() + (token?.hashCode() ?: 0)
}
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = request.tag(RequestSession::class.java)?.token
        return chain.proceed(request.newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build())
    }
}
