package com.mexadev.aura.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.data.model.auth.LoginRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val apiService = ApiClient.apiService

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val response = apiService.login(LoginRequest(email, pass))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    sessionManager.saveTokens(body.accessToken, body.refreshToken)

                    com.mexadev.aura.fcm.FcmHelper.fetchAndSyncToken(sessionManager, apiService)

                    _loginState.value = LoginState.Success
                } else {
                    _loginState.value = LoginState.Error("Credenciales incorrectas o error en el servidor.")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Error de red desconocido")
            }
        }
    }
}
