package com.mexadev.aura.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelos de datos para el módulo de pagos de AURA Móvil.
 * Sigue principios SOLID y mapea la API /api/mobile/payments.
 */

data class PaymentsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: PaymentsData?
)

data class PaymentsData(
    @SerializedName("saldo_pendiente") val saldoPendiente: String?,
    @SerializedName("pagos_pendientes") val pagosPendientes: List<PendingPaymentItem>?,
    @SerializedName("historico_pagos") val historicoPagos: PaymentHistoryPaged?
)

data class PendingPaymentItem(
    @SerializedName("id") val id: Long?,
    @SerializedName("type") val type: String, // "monthly_fee", "contracted_service", "financial_charge"
    @SerializedName("title") val title: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("month") val month: Int?,
    @SerializedName("year") val year: Int?,
    @SerializedName("status") val status: String,
    @SerializedName("is_recurrent") val isRecurrent: Boolean?,
    @SerializedName("contracted_service_id") val contractedServiceId: Long?,
    @SerializedName("created_at") val createdAt: String?
)

data class PaymentHistoryPaged(
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("data") val data: List<PaymentHistoryItem>,
    @SerializedName("first_page_url") val firstPageUrl: String?,
    @SerializedName("from") val from: Int?,
    @SerializedName("last_page") val lastPage: Int,
    @SerializedName("last_page_url") val lastPageUrl: String?,
    @SerializedName("next_page_url") val nextPageUrl: String?,
    @SerializedName("path") val path: String?,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("prev_page_url") val prevPageUrl: String?,
    @SerializedName("to") val to: Int?,
    @SerializedName("total") val total: Int
)

data class PaymentHistoryItem(
    @SerializedName("id") val id: Long,
    @SerializedName("payment_id") val paymentId: Long?,
    @SerializedName("title") val title: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("payment_method") val paymentMethod: String?,
    @SerializedName("receipt_url") val receiptUrl: String?,
    @SerializedName("status") val status: String,
    @SerializedName("month") val month: Int?,
    @SerializedName("year") val year: Int?,
    @SerializedName("date") val date: String?
)

data class PaymentProcessItemRequest(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long?,
    @SerializedName("residence_id") val residenceId: Long? = null
)

data class StripeCreateIntentRequest(
    @SerializedName("items") val items: List<PaymentProcessItemRequest>
)

data class StripeCreateIntentResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: StripeIntentData? = null
)

data class StripeIntentData(
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("publishable_key") val publishableKey: String? = null,
    @SerializedName("payment_intent_id") val paymentIntentId: String,
    @SerializedName("amount") val amount: String? = null,
    @SerializedName("currency") val currency: String? = null
)

data class PaymentProcessRequest(
    @SerializedName("items") val items: List<PaymentProcessItemRequest>,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("stripe_payment_intent_id") val stripePaymentIntentId: String? = null,
    @SerializedName("payment_method_id") val paymentMethodId: String? = null
)

data class PaymentProcessResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("decline_code") val declineCode: String? = null,
    @SerializedName("failure_reason") val failureReason: String? = null,
    @SerializedName("data") val data: PaymentProcessData? = null
)

data class PaymentProcessData(
    @SerializedName("total_paid") val totalPaid: String?,
    @SerializedName("payment_method") val paymentMethod: String?,
    @SerializedName("payments") val payments: List<PaymentResultItem>?
)

data class PaymentResultItem(
    @SerializedName("payment_id") val paymentId: Long,
    @SerializedName("charge_id") val chargeId: Long?,
    @SerializedName("amount") val amount: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("failure_code") val failureCode: String? = null,
    @SerializedName("failure_reason") val failureReason: String? = null
)
