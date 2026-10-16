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

/**
 * 只有執行中（計時迴圈在跑）才需要保持 CPU 喚醒；暫停、失敗時沒有計時工作，不持有以免白白耗電。
 * 必為 [shouldRunForeground] 的子集：沒有前景服務就不會有人持有 wake lock。
 */
fun shouldHoldWakeLock(status: SimulationStatus): Boolean = status == SimulationStatus.RUNNING

/** wake lock 的保險逾時：執行中每次狀態更新（約每 10 秒）都會續約，服務異常中止時最久只多撐這麼久。 */
const val WAKE_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1000L

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

enum class StopForegroundMode { DETACH, REMOVE }

/**
 * 停止前景時前景通知的去留：只有 [leftover]（[stopNotificationModel]）存在且確實發出（[posted]）才 DETACH，
 * 讓位給可滑掉的完成通知；其餘一律 REMOVE。完成通知沒發出（例如通知被關閉）卻還 DETACH，
 * 會把當初 startForeground 的「執行中」通知（附暫停／結束按鈕）過期遺留在通知列。
 */
fun stopForegroundMode(leftover: NotificationModel?, posted: Boolean): StopForegroundMode =
    if (leftover != null && posted) StopForegroundMode.DETACH else StopForegroundMode.REMOVE

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

enum class ServiceAction { START, STOP, NONE }

/**
 * 前景服務的對帳：以「目前的值」比較目標（現在需不需要前景）與實際（服務是否存活），不一致就修正。
 * 只有 App 端使用，且只執行 START；服務端的停止（stopSelf(startId)）直接依 [shouldRunForeground] 判斷。
 *
 * [failedAt]：上次啟動失敗時的狀態種類（null 代表沒有失敗紀錄）。同一種狀態不重試，避免迴圈；
 * 狀態種類改變（暫停、繼續、重試等）才再嘗試。
 */
fun reconcileForeground(
    status: SimulationStatus,
    serviceAlive: Boolean,
    failedAt: SimulationStatus?,
): ServiceAction {
    val needed = shouldRunForeground(status)
    return when {
        needed && !serviceAlive -> if (failedAt == status) ServiceAction.NONE else ServiceAction.START
        !needed && serviceAlive -> ServiceAction.STOP
        else -> ServiceAction.NONE
    }
}

/** 離開前景需求時清掉失敗紀錄：下一輪模擬要重新嘗試啟動，「無法在背景執行」提示也要消失。 */
fun nextFailedAt(status: SimulationStatus, failedAt: SimulationStatus?): SimulationStatus? =
    if (shouldRunForeground(status)) failedAt else null

data class AppReconcile(val failedAt: SimulationStatus?, val start: Boolean)

/** App 端的一次對帳：先套用 [nextFailedAt]，再依 [reconcileForeground] 決定是否啟動服務。 */
fun appReconcile(status: SimulationStatus, serviceAlive: Boolean, failedAt: SimulationStatus?): AppReconcile {
    val next = nextFailedAt(status, failedAt)
    return AppReconcile(
        failedAt = next,
        start = reconcileForeground(status, serviceAlive, next) == ServiceAction.START,
    )
}
