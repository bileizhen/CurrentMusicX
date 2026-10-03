package io.github.currencortex.music.core.network

import okhttp3.Interceptor
import okhttp3.Response

class RequestSession(val server: String, val token: String?) {
    override fun toString() = "RequestSession(redacted)"
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
