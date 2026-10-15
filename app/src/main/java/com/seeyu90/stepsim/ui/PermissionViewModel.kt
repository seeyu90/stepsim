package com.seeyu90.stepsim.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.NOT_SUPPORTED,
    val granted: Boolean = false,
    val checked: Boolean = false,
)

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = HealthConnectManager(application)

    private val _state = MutableStateFlow(PermissionUiState())
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    val permissions: Set<String> get() = HealthConnectManager.PERMISSIONS

    fun permissionContract() = manager.createPermissionRequestContract()

    fun installIntent() = manager.installIntent()

    /** 重新查詢可用性與授權狀態（onResume、授權結果回來後都會呼叫）。 */
    fun refresh() {
        viewModelScope.launch {
            val availability = manager.availability()
            val granted = availability == HealthConnectAvailability.AVAILABLE &&
                manager.hasAllPermissions()
            _state.value = PermissionUiState(availability, granted, checked = true)
        }
    }
}
