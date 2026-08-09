package com.mexadev.aura.data.model

import com.google.gson.annotations.SerializedName

data class Service(
    val id: Long,
    val title: String,
    val description: String?,
    val price: String,
    @SerializedName("image_urls") val imageUrls: List<String>? = null,
    @SerializedName("is_active") val isActive: Boolean? = true
)

data class ServiceListResponse(
    val status: String,
    val data: List<Service>
)

data class SingleServiceResponse(
    val status: String,
    val data: Service
)

data class ServiceContractRequest(
    @SerializedName("residence_id") val residenceId: Long,
    @SerializedName("preferred_date") val preferredDate: String,
    @SerializedName("visit_time_from") val visitTimeFrom: String,
    @SerializedName("visit_time_to") val visitTimeTo: String,
    @SerializedName("is_recurrent") val isRecurrent: Boolean = false,
    @SerializedName("suggested_schedule") val suggestedSchedule: List<String>? = null,
    val notes: String? = null,
    @SerializedName("payment_method") val paymentMethod: String = "stripe",
    @SerializedName("stripe_payment_intent_id") val stripePaymentIntentId: String? = null,
    @SerializedName("payment_method_id") val paymentMethodId: String? = null
)

data class FinancialChargeMinimal(
    val id: Long,
    val amount: String?,
    val status: String?
)

data class ContractedService(
    val id: Long,
    @SerializedName("service_id") val serviceId: Long?,
    val service: Service?,
    @SerializedName("residence_id") val residenceId: Long?,
    @SerializedName("charge_id") val chargeId: Long?,
    @SerializedName("preferred_date") val preferredDate: String?,
    @SerializedName("visit_time_from") val visitTimeFrom: String?,
    @SerializedName("visit_time_to") val visitTimeTo: String?,
    @SerializedName("exact_scheduled_at") val exactScheduledAt: String?,
    val amount: String?,
    val status: String, // "created", "scheduled", "completed", "cancelled"
    @SerializedName("is_recurrent") val isRecurrent: Boolean? = false,
    @SerializedName("suggested_schedule") val suggestedSchedule: List<String>? = null,
    val notes: String?,
    @SerializedName("payment_method") val paymentMethod: String?,
    @SerializedName("financial_charge") val financialCharge: FinancialChargeMinimal?,
    @SerializedName("access_code") val accessCode: AccessCode?
)

data class ContractedServiceListResponse(
    val status: String,
    val data: List<ContractedService>
)

data class ContractServiceResponse(
    val status: String,
    val message: String?,
    val data: ContractedService?
)

data class CompleteServiceResponse(
    val status: String,
    val message: String?,
    val data: ContractedService?
)
