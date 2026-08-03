package com.mexadev.aura.data.model.auth

import com.google.gson.annotations.SerializedName

data class FcmRequest(
    @SerializedName("fcm")
    val fcmToken: String
)
