package com.mexadev.aura.data.model.qr

import com.google.gson.annotations.SerializedName

data class QrTempResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: QrData?
) {
    fun getQrData(): String? {
        return data?.hash
    }
}

data class QrData(
    @SerializedName("hash") val hash: String,
    @SerializedName("expires_at") val expiresAt: String?
)
