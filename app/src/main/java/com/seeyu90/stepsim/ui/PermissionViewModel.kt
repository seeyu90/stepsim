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

/** 授權頁要顯示的階段；由 [PermissionUiState.stage] 決定。 */
enum class PermissionStage {
    CHECKING,
    CHECK_FAILED,
    NEEDS_INSTALL,
    NEEDS_UPDATE,
    NOT_SUPPORTED,
    NOT_GRANTED,

    /**
     * 請求後仍未授權。程式分不出「使用者這次拒絕」與「系統不再跳出視窗」，
     * 所以同時提供再試一次與手動開啟的步驟，文案不得斷言系統沒有跳出視窗。
     */
    NEEDS_MANUAL,
    GRANTED,
}

data class PermissionUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.NOT_SUPPORTED,
    val granted: Boolean = false,
    val checked: Boolean = false,
    val refreshFailed: Boolean = false,
    /**
     * 曾請求權限且結果仍未授權。標記存在 Activity 範圍的 ViewModel：使用者離開授權頁再回來仍在，
     * 重新檢查時也保留，直到取得授權才清掉。
     */
    val deniedAfterRequest: Boolean = false,
) {
    val stage: PermissionStage
        get() = when {
            refreshFailed -> PermissionStage.CHECK_FAILED
            !checked -> PermissionStage.CHECKING
            availability == HealthConnectAvailability.NEEDS_INSTALL -> PermissionStage.NEEDS_INSTALL
            availability == HealthConnectAvailability.NEEDS_UPDATE -> PermissionStage.NEEDS_UPDATE
            availability == HealthConnectAvailability.NOT_SUPPORTED -> PermissionStage.NOT_SUPPORTED
            granted -> PermissionStage.GRANTED
            deniedAfterRequest -> PermissionStage.NEEDS_MANUAL
            else -> PermissionStage.NOT_GRANTED
        }

    /** 可以（再次）跳出系統授權視窗。 */
    val canRequest: Boolean
        get() = stage == PermissionStage.NOT_GRANTED || stage == PermissionStage.NEEDS_MANUAL
}

class PermissionViewModel(private val manager: HealthConnectManager) : ViewModel() {

    private val _state = MutableStateFlow(PermissionUiState())
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    val permissions: Set<String> get() = HealthConnectManager.PERMISSIONS

    fun permissionContract() = manager.createPermissionRequestContract()

    fun installIntent() = manager.installIntent()

    fun settingsIntent() = manager.settingsIntent()

    private var refreshJob: Job? = null

    /**
     * 系統授權視窗的結果。沒有取得全部權限時記下「請求後仍未授權」，畫面改為提供再試一次與手動開啟；
     * 不再自動跳轉 Health Connect 設定頁。之後一律以重新檢查的結果為準。
     */
    fun onPermissionResult(granted: Set<String>) {
        if (!granted.containsAll(permissions)) {
            _state.value = _state.value.copy(deniedAfterRequest = true)
        }
        refresh()
    }

    /** 重新查詢可用性與授權狀態（onResume、授權結果回來後都會呼叫）；新的呼叫會取消尚未完成的前一次。 */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val availability = manager.availability()
                val granted = availability == HealthConnectAvailability.AVAILABLE &&
                    manager.hasAllPermissions()
                _state.value = PermissionUiState(
                    availability,
                    granted,
                    checked = true,
                    // 重新檢查不會讓「請求後仍未授權」消失；取得授權後才清掉
                    deniedAfterRequest = _state.value.deniedAfterRequest && !granted,
                )
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
