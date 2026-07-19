package com.mexadev.aura.core.network

import com.mexadev.aura.data.model.auth.*
import com.mexadev.aura.data.model.CommentCreateRequest
import com.mexadev.aura.data.model.Incident
import com.mexadev.aura.data.model.IncidentComment
import com.mexadev.aura.data.model.IncidentCreateRequest
import com.mexadev.aura.data.model.IncidentDetail
import com.mexadev.aura.data.model.IncidentUpdateRequest
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

    // ── Incidents ─────────────────────────────────────────────────────
    @retrofit2.http.GET("api/mobile/incidents")
    suspend fun getIncidents(): Response<List<Incident>>

    @retrofit2.http.POST("api/mobile/incidents")
    suspend fun createIncident(@Body request: IncidentCreateRequest): Response<Incident>

    @retrofit2.http.GET("api/mobile/incidents/{id}")
    suspend fun getIncidentDetail(@retrofit2.http.Path("id") id: Long): Response<IncidentDetail>

    @retrofit2.http.PATCH("api/mobile/incidents/{id}")
    suspend fun updateIncident(@retrofit2.http.Path("id") id: Long, @Body request: IncidentUpdateRequest): Response<Incident>

    // ── Incident Comments ─────────────────────────────────────────────
    @retrofit2.http.GET("api/mobile/incidents/{id}/comments")
    suspend fun getIncidentComments(@retrofit2.http.Path("id") incidentId: Long): Response<List<IncidentComment>>

    @retrofit2.http.POST("api/mobile/incidents/{id}/comments")
    suspend fun addIncidentComment(
        @retrofit2.http.Path("id") incidentId: Long,
        @Body request: CommentCreateRequest
    ): Response<IncidentComment>

    // ── Access Codes ──────────────────────────────────────────────────
    @retrofit2.http.GET("api/mobile/access-codes")
    suspend fun getAccessCodes(): Response<com.mexadev.aura.data.model.AccessCodeListResponse>

    @retrofit2.http.POST("api/mobile/access-codes")
    suspend fun createAccessCode(@Body request: com.mexadev.aura.data.model.AccessCodeCreateRequest): Response<com.mexadev.aura.data.model.SingleAccessCodeResponse>

    @retrofit2.http.GET("api/mobile/access-codes/{id}")
    suspend fun getAccessCode(@retrofit2.http.Path("id") id: Long): Response<com.mexadev.aura.data.model.SingleAccessCodeResponse>

    @retrofit2.http.PUT("api/mobile/access-codes/{id}")
    suspend fun updateAccessCode(@retrofit2.http.Path("id") id: Long, @Body request: com.mexadev.aura.data.model.AccessCodeUpdateRequest): Response<com.mexadev.aura.data.model.SingleAccessCodeResponse>

    @retrofit2.http.DELETE("api/mobile/access-codes/{id}")
    suspend fun deleteAccessCode(@retrofit2.http.Path("id") id: Long): Response<Unit>
}

