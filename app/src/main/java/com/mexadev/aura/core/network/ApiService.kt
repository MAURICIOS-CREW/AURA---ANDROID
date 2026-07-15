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

    @retrofit2.http.GET("api/mobile/vehicles")
    suspend fun getVehicles(): Response<List<com.mexadev.aura.data.model.Vehicle>>

    @retrofit2.http.POST("api/mobile/vehicles")
    suspend fun createVehicle(@Body request: com.mexadev.aura.data.model.VehicleCreateRequest): Response<com.mexadev.aura.data.model.Vehicle>

    @retrofit2.http.GET("api/mobile/vehicles/{id}")
    suspend fun getVehicle(@retrofit2.http.Path("id") id: Long): Response<com.mexadev.aura.data.model.Vehicle>

    @retrofit2.http.PUT("api/mobile/vehicles/{id}")
    suspend fun updateVehicle(@retrofit2.http.Path("id") id: Long, @Body request: com.mexadev.aura.data.model.VehicleUpdateRequest): Response<com.mexadev.aura.data.model.Vehicle>

    @retrofit2.http.DELETE("api/mobile/vehicles/{id}")
    suspend fun deleteVehicle(@retrofit2.http.Path("id") id: Long): Response<Unit>
}
