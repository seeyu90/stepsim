package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepSimulator

enum class TargetInputError { OUT_OF_RANGE }

/** 主要內容上方的存取提示；非 [NONE] 時主要內容（目標、步態、進度、操作）不顯示。 */
enum class AccessNotice { NONE, CHECKING, HEALTH_CONNECT_UNAVAILABLE, PERMISSION_REQUIRED }

data class SimulatorUiState(
    /** null 代表尚在檢查。 */
    val permissionGranted: Boolean? = null,
    /** getSdkStatus 是否為 AVAILABLE；null 代表尚在檢查。 */
    val healthConnectAvailable: Boolean? = null,
    val targetInput: String = "",
    val targetError: TargetInputError? = null,
    val simulation: SimulationState = SimulationState(),
) {
    val status: SimulationStatus get() = simulation.status

    val isFailed: Boolean
        get() = status == SimulationStatus.FAILED || status == SimulationStatus.PERMISSION_REVOKED

    /**
     * - Health Connect 不可用：只提示「無法使用」，不提示授權；FAILED 時由失敗卡片說明，不重複提示。
     * - 權限確認未授權：提示授權。
     * - 權限未知：失敗時不擋（仍可重試、重設），其他狀態顯示檢查中。
     */
    val accessNotice: AccessNotice
        get() = when {
            healthConnectAvailable == false ->
                if (status == SimulationStatus.FAILED) AccessNotice.NONE else AccessNotice.HEALTH_CONNECT_UNAVAILABLE
            permissionGranted == false -> AccessNotice.PERMISSION_REQUIRED
            permissionGranted == null && !isFailed -> AccessNotice.CHECKING
            else -> AccessNotice.NONE
        }

    val showMainContent: Boolean get() = accessNotice == AccessNotice.NONE

    /** 失敗時一律能「結束並重設」：主要內容被提示取代時，另外單獨顯示重設按鈕。 */
    val showStandaloneReset: Boolean get() = isFailed && !showMainContent

    /** 目標與步態只能在尚未開始時修改；步態另外可在暫停時修改。 */
    val canEditTarget: Boolean
        get() = status == SimulationStatus.IDLE || status == SimulationStatus.READY

    val canEditGait: Boolean get() = canEditTarget || status == SimulationStatus.PAUSED

    /** 畫面上顯示的目標：開始後以模擬器為準，開始前以輸入框為準。 */
    val displayTarget: Long?
        get() = if (canEditTarget) parsedTarget else simulation.targetSteps

    val canStart: Boolean
        get() = permissionGranted == true && canEditTarget && parsedTarget != null

    val progress: Float
        get() {
            val target = displayTarget ?: return 0f
            return (simulation.writtenSteps.toFloat() / target).coerceIn(0f, 1f)
        }

    /** 依目前步頻估算的剩餘分鐘數（無條件進位）；無目標時為 null。 */
    val remainingMinutes: Long?
        get() {
            val target = displayTarget ?: return null
            val remaining = (target - simulation.writtenSteps).coerceAtLeast(0)
            val cadence = simulation.gait.stepsPerMinute
            return (remaining + cadence - 1) / cadence
        }

    private val parsedTarget: Long?
        get() = targetInput.toLongOrNull()?.takeIf(StepSimulator::isValidTarget)
}

sealed interface SimulatorEvent {
    data class TargetInputChanged(val text: String) : SimulatorEvent
    data class QuickTargetSelected(val steps: Long) : SimulatorEvent
    data class GaitSelected(val gait: Gait) : SimulatorEvent
    data object Start : SimulatorEvent
    data object Pause : SimulatorEvent
    data object Resume : SimulatorEvent
    data object Retry : SimulatorEvent
    data object Reset : SimulatorEvent
    data object OpenPermission : SimulatorEvent
}
