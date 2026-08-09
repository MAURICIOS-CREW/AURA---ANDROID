package com.mexadev.aura.core.network

import com.mexadev.aura.data.model.auth.*
import com.mexadev.aura.data.model.CommentCreateRequest
import com.mexadev.aura.data.model.Incident
import com.mexadev.aura.data.model.IncidentComment
import com.mexadev.aura.data.model.IncidentCreateRequest
import com.mexadev.aura.data.model.IncidentDetail
import com.mexadev.aura.data.model.IncidentUpdateRequest
import com.mexadev.aura.data.model.PaymentsResponse
import com.mexadev.aura.data.model.PaymentProcessRequest
import com.mexadev.aura.data.model.PaymentProcessResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/mobile/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/mobile/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("api/mobile/auth/fcm")
    suspend fun updateFcmToken(@Body request: FcmRequest): Response<Unit>

    @GET("api/mobile/qr/temp")
    suspend fun getTempQrHash(): Response<com.mexadev.aura.data.model.qr.QrTempResponse>

    @GET("api/mobile/profile")
    suspend fun getProfile(): Response<com.mexadev.aura.data.model.User>

    @PUT("api/mobile/profile")
    suspend fun updateProfile(@Body request: com.mexadev.aura.data.model.ProfileUpdateRequest): Response<com.mexadev.aura.data.model.ProfileUpdateResponse>

    @GET("api/mobile/vehicles")
    suspend fun getVehicles(): Response<List<com.mexadev.aura.data.model.Vehicle>>

    @POST("api/mobile/vehicles")
    suspend fun createVehicle(@Body request: com.mexadev.aura.data.model.VehicleCreateRequest): Response<com.mexadev.aura.data.model.Vehicle>

    @Suppress("unused")
    @GET("api/mobile/vehicles/{id}")
    suspend fun getVehicle(@Path("id") id: Long): Response<com.mexadev.aura.data.model.Vehicle>

    @PUT("api/mobile/vehicles/{id}")
    suspend fun updateVehicle(@Path("id") id: Long, @Body request: com.mexadev.aura.data.model.VehicleUpdateRequest): Response<com.mexadev.aura.data.model.Vehicle>

    @DELETE("api/mobile/vehicles/{id}")
    suspend fun deleteVehicle(@Path("id") id: Long): Response<Unit>

    // ── Incidents ─────────────────────────────────────────────────────
    @GET("api/mobile/incidents")
    suspend fun getIncidents(): Response<List<Incident>>

    @POST("api/mobile/incidents")
    suspend fun createIncident(@Body request: IncidentCreateRequest): Response<Incident>

    @GET("api/mobile/incidents/{id}")
    suspend fun getIncidentDetail(@Path("id") id: Long): Response<IncidentDetail>

    @PATCH("api/mobile/incidents/{id}")
    suspend fun updateIncident(@Path("id") id: Long, @Body request: IncidentUpdateRequest): Response<Incident>

    // ── Incident Comments ─────────────────────────────────────────────
    @Suppress("unused")
    @GET("api/mobile/incidents/{id}/comments")
    suspend fun getIncidentComments(@Path("id") incidentId: Long): Response<List<IncidentComment>>

    @POST("api/mobile/incidents/{id}/comments")
    suspend fun addIncidentComment(
        @Path("id") incidentId: Long,
        @Body request: CommentCreateRequest
    ): Response<IncidentComment>

    // ── Access Codes ──────────────────────────────────────────────────
    @GET("api/mobile/access-codes")
    suspend fun getAccessCodes(): Response<com.mexadev.aura.data.model.AccessCodeListResponse>

    @POST("api/mobile/access-codes")
    suspend fun createAccessCode(@Body request: com.mexadev.aura.data.model.AccessCodeCreateRequest): Response<com.mexadev.aura.data.model.SingleAccessCodeResponse>

    @GET("api/mobile/access-codes/{id}")
    suspend fun getAccessCode(@Path("id") id: Long): Response<com.mexadev.aura.data.model.SingleAccessCodeResponse>

    @PUT("api/mobile/access-codes/{id}")
    suspend fun updateAccessCode(@Path("id") id: Long, @Body request: com.mexadev.aura.data.model.AccessCodeUpdateRequest): Response<com.mexadev.aura.data.model.SingleAccessCodeResponse>

    @DELETE("api/mobile/access-codes/{id}")
    suspend fun deleteAccessCode(@Path("id") id: Long): Response<Unit>

    // ── Access Logs (Historial) ───────────────────────────────────────
    @GET("api/mobile/access-logs")
    suspend fun getAccessLogs(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<com.mexadev.aura.data.model.AccessLogPagedResponse>

    // ── Services ──────────────────────────────────────────────────────
    @GET("api/mobile/services")
    suspend fun getServices(): Response<com.mexadev.aura.data.model.ServiceListResponse>

    @Suppress("unused")
    @GET("api/mobile/services/{id}")
    suspend fun getServiceDetail(@Path("id") id: Long): Response<com.mexadev.aura.data.model.SingleServiceResponse>

    @POST("api/mobile/services/{id}/contract")
    suspend fun contractService(
        @Path("id") id: Long,
        @Body request: com.mexadev.aura.data.model.ServiceContractRequest
    ): Response<com.mexadev.aura.data.model.ContractServiceResponse>

    @GET("api/mobile/contracted-services")
    suspend fun getContractedServices(): Response<com.mexadev.aura.data.model.ContractedServiceListResponse>

    @PATCH("api/mobile/contracted-services/{id}/complete")
    suspend fun completeContractedService(@Path("id") id: Long): Response<com.mexadev.aura.data.model.CompleteServiceResponse>

    // ── Payments ──────────────────────────────────────────────────────
    @GET("api/mobile/payments")
    suspend fun getPaymentsSummary(
        @Query("page") page: Int = 1
    ): Response<PaymentsResponse>

    @POST("api/mobile/payments/stripe/create-intent")
    suspend fun createStripeIntent(
        @Body request: com.mexadev.aura.data.model.StripeCreateIntentRequest
    ): Response<com.mexadev.aura.data.model.StripeCreateIntentResponse>

    @POST("api/mobile/payments/pay")
    suspend fun payPendingItemsJson(
        @Body request: PaymentProcessRequest
    ): Response<PaymentProcessResponse>

    @Multipart
    @POST("api/mobile/payments/pay")
    suspend fun payPendingItemsMultipart(
        @Part("items") items: RequestBody,
        @Part("payment_method") paymentMethod: RequestBody,
        @Part("stripe_payment_intent_id") stripePaymentIntentId: RequestBody? = null,
        @Part("payment_method_id") paymentMethodId: RequestBody? = null,
        @Part receipt: MultipartBody.Part? = null
    ): Response<PaymentProcessResponse>
}
