package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.SimulationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 前景服務的實際狀態。
 *
 * @property alive 已要求啟動或已在前景，尚未銷毀。
 * @property failedAt 上次啟動失敗時的模擬狀態種類；null 代表沒有失敗紀錄。
 */
data class ForegroundServiceStatus(
    val alive: Boolean = false,
    val failedAt: SimulationStatus? = null,
)

/**
 * 前景服務狀態的唯一持有者（程序層級，由 StepSimApp 持有）。
 * 啟動失敗（例如 SecurityException、ForegroundServiceStartNotAllowedException）時 [available] 為 false，
 * 畫面據此提示「無法在背景執行」；模擬本身不中斷。
 *
 * 只能在主執行緒呼叫（App 的對帳迴圈與服務的生命週期回呼都在主執行緒）。
 */
class BackgroundAvailability {
    private val _status = MutableStateFlow(ForegroundServiceStatus())
    val status: StateFlow<ForegroundServiceStatus> = _status.asStateFlow()

    /** 前景服務是否可用（沒有啟動失敗紀錄）。由 [status] 推導，不另存狀態，兩者不可能不一致。 */
    val available: Flow<Boolean> = status.map { it.failedAt == null }.distinctUntilChanged()

    /** App 呼叫 startForegroundService 之前：視為存活，避免重複啟動。 */
    fun reportStartRequested() = set(_status.value.copy(alive = true))

    /** 服務的 startForeground 成功：清掉失敗紀錄。 */
    fun reportStarted() = set(ForegroundServiceStatus(alive = true, failedAt = null))

    /** startForegroundService 或 startForeground 失敗，記下失敗時的狀態種類。 */
    fun reportStartFailed(status: SimulationStatus) = set(ForegroundServiceStatus(alive = false, failedAt = status))

    /** 服務 onDestroy。 */
    fun reportStopped() = set(_status.value.copy(alive = false))

    /** 對帳時依 [nextFailedAt] 更新失敗紀錄（離開前景需求時清掉）。 */
    fun updateFailedAt(failedAt: SimulationStatus?) = set(_status.value.copy(failedAt = failedAt))

    private fun set(value: ForegroundServiceStatus) {
        _status.value = value
    }
}
