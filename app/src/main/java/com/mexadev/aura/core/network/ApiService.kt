package com.mexadev.aura.core.network

import com.mexadev.aura.data.model.auth.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/mobile/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/mobile/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<RefreshResponse>

    @retrofit2.http.GET("api/mobile/qr/temp")
    suspend fun getTempQrHash(): Response<com.mexadev.aura.data.model.qr.QrTempResponse>

    @retrofit2.http.GET("api/mobile/profile")
    suspend fun getProfile(): Response<com.mexadev.aura.data.model.User>
}
