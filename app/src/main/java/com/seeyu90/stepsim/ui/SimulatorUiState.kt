package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.service.shouldRunForeground
import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepSimulator

enum class TargetInputError { OUT_OF_RANGE }

/** 主要內容上方的存取提示；非 [NONE] 時主要內容（目標、步態、進度、操作）不顯示。 */
enum class AccessNotice {
    NONE,

    /** 權限檢查尚未結束。 */
    CHECKING,

    /** 權限檢查本身失敗（例如 Health Connect 服務暫時無法連線），可再試一次。 */
    CHECK_FAILED,
    HEALTH_CONNECT_UNAVAILABLE,
    PERMISSION_REQUIRED,
}

data class SimulatorUiState(
    /** null 代表尚在檢查。 */
    val permissionGranted: Boolean? = null,
    /** getSdkStatus 是否為 AVAILABLE；null 代表尚在檢查。 */
    val healthConnectAvailable: Boolean? = null,
    /** 最近一次權限檢查本身失敗（權限仍為未知）。 */
    val permissionCheckFailed: Boolean = false,
    val targetInput: String = "",
    val targetError: TargetInputError? = null,
    val simulation: SimulationState = SimulationState(),
    /** 前景服務是否成功啟動；false 時離開 App 後模擬可能中斷。 */
    val backgroundAvailable: Boolean = true,
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
            permissionGranted == null && !isFailed ->
                if (permissionCheckFailed) AccessNotice.CHECK_FAILED else AccessNotice.CHECKING
            else -> AccessNotice.NONE
        }

    val showMainContent: Boolean get() = accessNotice == AccessNotice.NONE

    /** 模擬進行中（含暫停、失敗）但前景服務無法啟動時，提示「無法在背景執行」。狀態範圍與前景服務一致。 */
    val showBackgroundUnavailable: Boolean
        get() = !backgroundAvailable && shouldRunForeground(status)

    /** 執行中提示「離開 App 會在背景繼續」；前景服務無法啟動時這句話不成立，由「無法在背景執行」取代。 */
    val showForegroundHint: Boolean
        get() = status == SimulationStatus.RUNNING && backgroundAvailable

    /** 模擬已開始（含暫停與失敗）：這時一律要能「結束本次模擬」。 */
    val hasStarted: Boolean
        get() = status == SimulationStatus.RUNNING || status == SimulationStatus.PAUSED || isFailed

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

    // ---- 以下為純顯示用的衍生屬性（階段 9.5） ----

    /** 進度百分比，無條件捨去（與通知同算法）：未達標前不會顯示 100。無目標時為 0。 */
    val progressPercent: Int
        get() {
            val target = displayTarget?.takeIf { it > 0 } ?: return 0
            return (simulation.writtenSteps * 100 / target).toInt().coerceIn(0, 100)
        }

    /** 剩餘時間只在執行中顯示（暫停、失敗、完成時不顯示）。 */
    val showRemaining: Boolean
        get() = status == SimulationStatus.RUNNING && remainingMinutes != null

    /** 準備狀態（尚無進度）用較小的圓環。 */
    val compactRing: Boolean get() = canEditTarget

    /** 目標步數只在準備狀態顯示；速度另外在暫停時也能切換。 */
    val showTargetInput: Boolean get() = canEditTarget

    val showGaitSelector: Boolean get() = canEditGait

    val startHint: StartHint?
        get() = if (bottomAction == BottomAction.START && displayTarget == null) StartHint.NEED_GOAL else null

    /**
     * 底部固定的主要動作；null 代表不顯示（例如權限檢查中）。
     * 失敗時依原因決定（對照表由 SimulatorActionsTest 逐列驗證）。
     */
    val bottomAction: BottomAction?
        get() = when {
            isFailed -> failedBottomAction
            accessNotice == AccessNotice.HEALTH_CONNECT_UNAVAILABLE -> BottomAction.GO_CHECK
            accessNotice == AccessNotice.PERMISSION_REQUIRED -> BottomAction.GO_ALLOW
            accessNotice == AccessNotice.CHECK_FAILED -> BottomAction.RECHECK
            accessNotice == AccessNotice.CHECKING -> null
            else -> when (status) {
                SimulationStatus.IDLE, SimulationStatus.READY -> BottomAction.START
                SimulationStatus.RUNNING -> BottomAction.PAUSE
                SimulationStatus.PAUSED -> BottomAction.RESUME
                SimulationStatus.COMPLETED -> BottomAction.START_NEW
                SimulationStatus.FAILED, SimulationStatus.PERMISSION_REVOKED -> null
            }
        }

    private val failedBottomAction: BottomAction
        get() = when {
            healthConnectAvailable == false -> BottomAction.GO_CHECK
            permissionGranted == false -> BottomAction.GO_ALLOW
            // 權限被撤銷後尚未確認目前狀態：先引導去允許，「再試一次」放在次要操作
            status == SimulationStatus.PERMISSION_REVOKED && permissionGranted == null -> BottomAction.GO_ALLOW
            else -> BottomAction.RETRY
        }

    /**
     * 次要操作：已開始的模擬（執行中、暫停、失敗）一律能「結束本次模擬」；
     * 權限狀態未知而主按鈕不是「再試一次」時，另外保留「再試一次」。
     */
    val secondaryActions: List<SecondaryAction>
        get() = buildList {
            if (isFailed && permissionGranted == null && bottomAction != BottomAction.RETRY) add(SecondaryAction.RETRY)
            if (hasStarted) add(SecondaryAction.END)
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

    /** 權限檢查本身失敗後，重新檢查。 */
    data object Recheck : SimulatorEvent
}
