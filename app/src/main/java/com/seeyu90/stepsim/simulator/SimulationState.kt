package com.seeyu90.stepsim.simulator

import java.time.Instant

enum class SimulationStatus {
    /** 尚未設定目標。 */
    IDLE,

    /** 目標已設定，待開始。 */
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,

    /** 寫入失敗，等待重試。 */
    FAILED,

    /** 寫入時權限不足，重新授權後可重試。 */
    PERMISSION_REVOKED,
}

data class SimulationState(
    val status: SimulationStatus = SimulationStatus.IDLE,
    val targetSteps: Long? = null,
    val gait: Gait = Gait.WALK,
    val writtenSteps: Long = 0,
    val batchCount: Int = 0,
    val firstBatchStart: Instant? = null,
    val lastBatchEnd: Instant? = null,
    /** 失敗的是第幾批（從 1 起算），僅 FAILED / PERMISSION_REVOKED 時有值。 */
    val failedBatchNumber: Int? = null,
    val failure: Throwable? = null,
)
