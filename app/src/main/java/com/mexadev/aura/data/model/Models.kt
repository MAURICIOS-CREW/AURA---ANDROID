package com.mexadev.aura.data.model

import com.google.gson.annotations.SerializedName

data class Tenant(
    val id: Long,
    val name: String,
    @SerializedName("db_host") val dbHost: String?,
    @SerializedName("db_name") val dbName: String?,
    @SerializedName("db_user") val dbUser: String?,
    @SerializedName("db_password") val dbPassword: String?,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Module(
    val id: Long,
    val name: String,
    val slug: String,
    @SerializedName("is_core") val isCore: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class TenantModule(
    @SerializedName("tenant_id") val tenantId: Long,
    @SerializedName("module_id") val moduleId: Long,
    @SerializedName("is_active") val isActive: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Permission(
    val id: Long,
    val name: String,
    val slug: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Role(
    val id: Long,
    val name: String,
    @SerializedName("hierarchy_level") val hierarchyLevel: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class RolePermission(
    @SerializedName("role_id") val roleId: Long,
    @SerializedName("permission_id") val permissionId: Long,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class User(
    val id: Long,
    @SerializedName("tenant_id") val tenantId: Long?,
    val name: String,
    val username: String,
    val email: String,
    val password: String?,
    val phone: String?,
    @SerializedName("role_id") val roleId: Long?,
    @SerializedName("is_active") val isActive: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class UserSession(
    val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("token_id") val tokenId: String,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("expired_at") val expiredAt: String?,
    @SerializedName("is_closed") val isClosed: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class BannedUser(
    val id: Long,
    @SerializedName("user_id") val userId: Long,
    val reason: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Address(
    val id: Long,
    val name: String,
    val cp: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Residence(
    val id: Long,
    @SerializedName("address_id") val addressId: Long?,
    val block: Int,
    val number: String,
    @SerializedName("intercom_number") val intercomNumber: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class UserResidence(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("residence_id") val residenceId: Long,
    @SerializedName("is_primary_owner") val isPrimaryOwner: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Vehicle(
    val id: Long,
    @SerializedName("residence_id") val residenceId: Long,
    val plate: String,
    val brand: String?,
    val color: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class DeliveryPackage(
    val id: Long,
    @SerializedName("residence_id") val residenceId: Long,
    @SerializedName("receiver_guard_id") val receiverGuardId: Long?,
    @SerializedName("courier_company") val courierCompany: String?,
    val status: String,
    @SerializedName("delivery_evidence") val deliveryEvidence: String?,
    @SerializedName("received_at") val receivedAt: String?,
    @SerializedName("delivered_at") val deliveredAt: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class AccessCode(
    val id: Long,
    @SerializedName("residence_id") val residenceId: Long,
    @SerializedName("guest_name") val guestName: String?,
    val code: String,
    val type: String,
    @SerializedName("valid_from") val validFrom: String?,
    @SerializedName("valid_until") val validUntil: String?,
    val uses: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class AccessLog(
    val id: Long,
    @SerializedName("access_type") val accessType: String,
    val method: String,
    @SerializedName("residence_id") val residenceId: Long?,
    @SerializedName("vehicle_id") val vehicleId: Long?,
    @SerializedName("access_code_id") val accessCodeId: Long?,
    @SerializedName("guard_user_id") val guardUserId: Long?,
    val status: String,
    @SerializedName("ai_confidence") val aiConfidence: Float?,
    val timestamp: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class FinancialCharge(
    val id: Long,
    @SerializedName("residence_id") val residenceId: Long,
    val amount: Double,
    val month: Int,
    val year: Int,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Payment(
    val id: Long,
    @SerializedName("charge_id") val chargeId: Long,
    @SerializedName("user_id") val userId: Long,
    val amount: Double,
    @SerializedName("payment_method") val paymentMethod: String,
    val receipt: String?,
    @SerializedName("validator_admin_id") val validatorAdminId: Long?,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class Incident(
    val id: Long,
    @SerializedName("reporter_user_id") val reporterUserId: Long,
    val title: String,
    val description: String?,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)
