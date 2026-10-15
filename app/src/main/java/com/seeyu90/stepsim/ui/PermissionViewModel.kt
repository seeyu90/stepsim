package com.seeyu90.stepsim.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seeyu90.stepsim.health.AndroidHealthConnectManager
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.NOT_SUPPORTED,
    val granted: Boolean = false,
    val checked: Boolean = false,
    val refreshFailed: Boolean = false,
)

class PermissionViewModel(private val manager: HealthConnectManager) : ViewModel() {

    private val _state = MutableStateFlow(PermissionUiState())
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    val permissions: Set<String> get() = HealthConnectManager.PERMISSIONS

    fun permissionContract() = manager.createPermissionRequestContract()

    fun installIntent() = manager.installIntent()

    fun settingsIntent() = manager.settingsIntent()

    private var refreshJob: Job? = null

    /** 重新查詢可用性與授權狀態（onResume、授權結果回來後都會呼叫）；新的呼叫會取消尚未完成的前一次。 */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val availability = manager.availability()
                val granted = availability == HealthConnectAvailability.AVAILABLE &&
                    manager.hasAllPermissions()
                _state.value = PermissionUiState(availability, granted, checked = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(checked = true, refreshFailed = true)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PermissionViewModel(AndroidHealthConnectManager(checkNotNull(this[APPLICATION_KEY])))
            }
        }
    }
}
