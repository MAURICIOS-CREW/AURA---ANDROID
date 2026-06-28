package com.mexadev.aura.data.model.auth

import com.google.gson.annotations.SerializedName
import com.mexadev.aura.data.model.User

data class LoginRequest(
    val login: String, 
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String, 
    @SerializedName("refresh_token") val refreshToken: String, 
    @SerializedName("token_type") val tokenType: String, 
    @SerializedName("expires_in") val expiresIn: Long, 
    val user: User
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class RefreshResponse(
    @SerializedName("access_token") val accessToken: String, 
    @SerializedName("token_type") val tokenType: String, 
    @SerializedName("expires_in") val expiresIn: Long
)
