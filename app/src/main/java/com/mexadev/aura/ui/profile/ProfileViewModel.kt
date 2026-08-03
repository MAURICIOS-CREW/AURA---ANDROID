package com.mexadev.aura.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.ProfileUpdateRequest
import com.mexadev.aura.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val user: User) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

sealed class ProfileUpdateState {
    object Idle : ProfileUpdateState()
    object Loading : ProfileUpdateState()
    data class Success(val user: User, val message: String) : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}

class ProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val updateState: StateFlow<ProfileUpdateState> = _updateState.asStateFlow()

    private val apiService = ApiClient.apiService

    fun fetchProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val response = apiService.getProfile()
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = ProfileUiState.Success(response.body()!!)
                } else {
                    val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                    _uiState.value = ProfileUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Sin conexión a internet o servidor inaccesible.")
            }
        }
    }

    fun updateProfile(name: String?, username: String?, email: String?, phone: String?) {
        viewModelScope.launch {
            _updateState.value = ProfileUpdateState.Loading
            try {
                val req = ProfileUpdateRequest(
                    name = name?.takeIf { it.isNotBlank() },
                    username = username?.takeIf { it.isNotBlank() },
                    email = email?.takeIf { it.isNotBlank() },
                    phone = phone?.takeIf { it.isNotBlank() }
                )
                val response = apiService.updateProfile(req)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _uiState.value = ProfileUiState.Success(body.user)
                    _updateState.value = ProfileUpdateState.Success(body.user, body.message)
                } else {
                    val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                    _updateState.value = ProfileUpdateState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _updateState.value = ProfileUpdateState.Error("Sin conexión a internet o servidor inaccesible.")
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = ProfileUpdateState.Idle
    }

    private fun parseErrorMessage(code: Int, errorBody: String?): String {
        var customMessage: String? = null
        try {
            if (!errorBody.isNullOrEmpty()) {
                val jsonObject = JSONObject(errorBody)
                if (jsonObject.has("message")) {
                    customMessage = jsonObject.getString("message")
                } else if (jsonObject.has("error")) {
                    customMessage = jsonObject.getString("error")
                }
                
                // Handle Laravel 422 errors dictionary if available
                if (jsonObject.has("errors")) {
                    val errorsObj = jsonObject.getJSONObject("errors")
                    val keys = errorsObj.keys()
                    if (keys.hasNext()) {
                        val firstKey = keys.next()
                        val arr = errorsObj.getJSONArray(firstKey)
                        if (arr.length() > 0) {
                            customMessage = arr.getString(0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore JSON parsing failure
        }

        return when (code) {
            500 -> "Error interno en el servidor."
            400 -> customMessage ?: "Petición inválida. Verifica los datos."
            401 -> "Tu sesión ha expirado o no tienes acceso."
            403 -> customMessage ?: "No tienes permiso para realizar esta acción."
            422 -> customMessage ?: "Datos de perfil inválidos o ya registrados."
            else -> customMessage ?: "Ocurrió un error inesperado ($code)."
        }
    }
}
