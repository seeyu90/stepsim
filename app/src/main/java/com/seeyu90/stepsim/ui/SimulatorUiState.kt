package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepSimulator

enum class TargetInputError { OUT_OF_RANGE }

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
