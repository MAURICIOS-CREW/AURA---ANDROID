package com.mexadev.aura.core.network

import com.mexadev.aura.core.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()
            .header("Accept", "application/json")
        
        // Si es la ruta de refresh, enviamos el refresh_token
        if (request.url.encodedPath.contains("auth/refresh")) {
            val refreshToken = sessionManager.getRefreshTokenSync()
            if (refreshToken != null) {
                requestBuilder.header("Authorization", "Bearer $refreshToken")
            }
            return chain.proceed(requestBuilder.build())
        }
        
        val token = sessionManager.getAccessTokenSync()
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        return chain.proceed(requestBuilder.build())
    }
}
