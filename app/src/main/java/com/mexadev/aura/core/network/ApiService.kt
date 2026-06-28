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
}
