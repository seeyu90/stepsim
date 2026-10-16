package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus

// 前景服務與通知的決策（純 Kotlin，可單元測試）；SimulationService 只依此繪製通知。

/** 執行中、暫停、失敗時保留前景服務；完成與未開始時停止。 */
private val FOREGROUND_STATUSES = setOf(
    SimulationStatus.RUNNING,
    SimulationStatus.PAUSED,
    SimulationStatus.FAILED,
    SimulationStatus.PERMISSION_REVOKED,
)

fun shouldRunForeground(status: SimulationStatus): Boolean = status in FOREGROUND_STATUSES

enum class NotificationKind { RUNNING, PAUSED, FAILED, PERMISSION_REVOKED, COMPLETED }

enum class NotificationAction { PAUSE, RESUME, STOP }

data class NotificationModel(
    val kind: NotificationKind,
    val gait: Gait,
    val writtenSteps: Long,
    val targetSteps: Long,
    val percent: Int,
    val failedBatchNumber: Int?,
    val actions: List<NotificationAction>,
    /** 前景服務期間為常駐；完成後改為可滑掉。 */
    val ongoing: Boolean,
)

/** 依模擬狀態決定通知內容；不需要通知（未開始）時回傳 null。 */
fun notificationModel(state: SimulationState): NotificationModel? {
    val kind = when (state.status) {
        SimulationStatus.RUNNING -> NotificationKind.RUNNING
        SimulationStatus.PAUSED -> NotificationKind.PAUSED
        SimulationStatus.FAILED -> NotificationKind.FAILED
        SimulationStatus.PERMISSION_REVOKED -> NotificationKind.PERMISSION_REVOKED
        SimulationStatus.COMPLETED -> NotificationKind.COMPLETED
        SimulationStatus.IDLE, SimulationStatus.READY -> return null
    }
    val target = state.targetSteps ?: return null
    val actions = when (kind) {
        NotificationKind.RUNNING -> listOf(NotificationAction.PAUSE, NotificationAction.STOP)
        NotificationKind.PAUSED -> listOf(NotificationAction.RESUME, NotificationAction.STOP)
        // 失敗時一律保留「結束本次模擬」；重試請開啟 App
        NotificationKind.FAILED, NotificationKind.PERMISSION_REVOKED -> listOf(NotificationAction.STOP)
        NotificationKind.COMPLETED -> emptyList()
    }
    val percent = if (target > 0) ((state.writtenSteps * 100) / target).toInt().coerceIn(0, 100) else 0
    return NotificationModel(
        kind = kind,
        gait = state.gait,
        writtenSteps = state.writtenSteps,
        targetSteps = target,
        percent = percent,
        failedBatchNumber = state.failedBatchNumber,
        actions = actions,
        ongoing = kind != NotificationKind.COMPLETED,
    )
}

/**
 * 停止服務時要留下的通知，依「觸發停止當下的狀態快照」決定：只有 COMPLETED 留下可滑掉的完成通知，
 * 其他狀態回傳 null（不留通知，不可顯示成常駐的「啟動中」）。
 */
fun stopNotificationModel(snapshot: SimulationState): NotificationModel? =
    if (snapshot.status == SimulationStatus.COMPLETED) notificationModel(snapshot) else null

data class StartCommandOutcome(
    /** 要轉送給模擬的通知按鈕指令；不因前景啟動失敗而丟失。 */
    val command: NotificationAction?,
    val stopSelf: Boolean,
)

/** onStartCommand 的決策：按鈕指令一律轉送；前景啟動失敗或模擬不需要前景時停止服務。 */
fun startCommandOutcome(
    action: NotificationAction?,
    foregroundStarted: Boolean,
    status: SimulationStatus,
): StartCommandOutcome = StartCommandOutcome(
    command = action,
    stopSelf = !foregroundStarted || !shouldRunForeground(status),
)
