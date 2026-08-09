package com.mexadev.aura.ui.payments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.data.model.PaymentHistoryItem
import com.mexadev.aura.data.model.PaymentsData
import com.mexadev.aura.data.model.PendingPaymentItem
import kotlinx.coroutines.launch
import java.io.IOException

sealed class PaymentsUiState {
    object Loading : PaymentsUiState()
    data class Success(val data: PaymentsData) : PaymentsUiState()
    data class Error(val message: String) : PaymentsUiState()
}

/**
 * ViewModel para gestionar el estado de Pagos.
 * Cumple con principios SOLID (SRP).
 */
class PaymentsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)

    private val _uiState = MutableLiveData<PaymentsUiState>()
    val uiState: LiveData<PaymentsUiState> = _uiState

    private val _historyItems = MutableLiveData<List<PaymentHistoryItem>>()
    val historyItems: LiveData<List<PaymentHistoryItem>> = _historyItems

    private val _isLoadingMore = MutableLiveData<Boolean>(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    private val _hasMorePages = MutableLiveData<Boolean>(false)
    val hasMorePages: LiveData<Boolean> = _hasMorePages

    private val _pendingItems = MutableLiveData<List<PendingPaymentItem>>(emptyList())
    val pendingItems: LiveData<List<PendingPaymentItem>> = _pendingItems

    private val _currentBalance = MutableLiveData<String>("0.00")
    val currentBalance: LiveData<String> = _currentBalance

    var currentPage = 1
        private set
    var lastPage = 1
        private set

    fun fetchPayments(isPullToRefresh: Boolean = false) {
        if (!isPullToRefresh && _historyItems.value.isNullOrEmpty()) {
            _uiState.value = PaymentsUiState.Loading
        }

        currentPage = 1
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getPaymentsSummary(page = 1)
                if (response.isSuccessful && response.body()?.status == "success") {
                    val paymentsData = response.body()?.data
                    if (paymentsData != null) {
                        _currentBalance.value = paymentsData.saldoPendiente ?: "0.00"
                        _pendingItems.value = paymentsData.pagosPendientes ?: emptyList()

                        val historico = paymentsData.historicoPagos
                        if (historico != null) {
                            currentPage = historico.currentPage
                            lastPage = historico.lastPage
                            _hasMorePages.value = (currentPage < lastPage) && (historico.nextPageUrl != null)
                            _historyItems.value = historico.data

                            if (historico.data.isNotEmpty()) {
                                prefsManager.paymentsHistoryCount = historico.data.size
                            }
                        } else {
                            _historyItems.value = emptyList()
                            _hasMorePages.value = false
                        }

                        _uiState.value = PaymentsUiState.Success(paymentsData)
                    } else {
                        _uiState.value = PaymentsUiState.Error("Respuesta vacía del servidor")
                    }
                } else {
                    _uiState.value = PaymentsUiState.Error(response.body()?.status ?: "Error al obtener pagos")
                }
            } catch (_: IOException) {
                _uiState.value = PaymentsUiState.Error("Sin conexión a internet")
            } catch (e: Exception) {
                _uiState.value = PaymentsUiState.Error("Error inesperado: ${e.localizedMessage}")
            }
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value == true || currentPage >= lastPage) return

        _isLoadingMore.value = true
        val nextPage = currentPage + 1

        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getPaymentsSummary(page = nextPage)
                _isLoadingMore.value = false

                if (response.isSuccessful && response.body()?.status == "success") {
                    val historico = response.body()?.data?.historicoPagos
                    if (historico != null) {
                        currentPage = historico.currentPage
                        lastPage = historico.lastPage
                        _hasMorePages.value = (currentPage < lastPage) && (historico.nextPageUrl != null)

                        val currentList = _historyItems.value.orEmpty().toMutableList()
                        currentList.addAll(historico.data)
                        _historyItems.value = currentList
                    }
                }
            } catch (_: Exception) {
                _isLoadingMore.value = false
            }
        }
    }
}
