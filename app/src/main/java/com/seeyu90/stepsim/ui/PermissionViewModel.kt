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

/** 授權頁的說明條目；[RESPONSIBLE_USE] 是責任使用條文（perm_point_4）。 */
enum class PermissionPoint { ONLY_SIMULATED, NO_UPLOAD, REVOKE_ANYTIME, RESPONSIBLE_USE }

/**
 * 各階段顯示的說明條目：只在使用者可以按主按鈕繼續允許的階段（尚未允許、需要手動開啟）顯示完整四點，
 * 責任使用條文因此一定出現在允許之前；其他階段不顯示說明條目。
 */
fun permissionPoints(stage: PermissionStage): List<PermissionPoint> = when (stage) {
    PermissionStage.NOT_GRANTED, PermissionStage.NEEDS_MANUAL -> PermissionPoint.entries
    else -> emptyList()
}

fun showsResponsibleUse(stage: PermissionStage): Boolean = PermissionPoint.RESPONSIBLE_USE in permissionPoints(stage)

/** 授權頁底部的主要動作；[requestsPermission] 為 true 的動作會跳出系統的允許視窗。 */
enum class PermissionBottomAction(val requestsPermission: Boolean = false) {
    RECHECK,
    INSTALL,
    UPDATE,
    BACK,
    REQUEST(requestsPermission = true),
    REQUEST_AGAIN(requestsPermission = true),
    BACK_TO_SIMULATOR,
}

/** 各階段的主要動作；null 代表不顯示（檢查中）。不支援時沒有可做的事，只能返回。 */
fun permissionBottomAction(stage: PermissionStage): PermissionBottomAction? = when (stage) {
    PermissionStage.CHECKING -> null
    PermissionStage.CHECK_FAILED -> PermissionBottomAction.RECHECK
    PermissionStage.NEEDS_INSTALL -> PermissionBottomAction.INSTALL
    PermissionStage.NEEDS_UPDATE -> PermissionBottomAction.UPDATE
    PermissionStage.NOT_SUPPORTED -> PermissionBottomAction.BACK
    PermissionStage.NOT_GRANTED -> PermissionBottomAction.REQUEST
    PermissionStage.NEEDS_MANUAL -> PermissionBottomAction.REQUEST_AGAIN
    PermissionStage.GRANTED -> PermissionBottomAction.BACK_TO_SIMULATOR
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
