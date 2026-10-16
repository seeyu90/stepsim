package com.seeyu90.stepsim.simulator

import java.time.Instant

/** 一批要寫入的步數，區間為 [start, end)。重送同一批時 [clientRecordId] 不變。 */
data class StepBatch(
    val clientRecordId: String,
    val start: Instant,
    val end: Instant,
    val steps: Long,
)

/** 步數寫入的抽象；Health Connect 實作在 health/，simulator 不得引用 HC SDK。 */
interface StepWriter {
    suspend fun write(batch: StepBatch)
}

/** 寫入因權限不足（例如被撤銷）而失敗時，[StepWriter] 應拋出此例外。 */
class StepWritePermissionException(cause: Throwable? = null) : Exception(cause)

/** 寫入因頻率限制而失敗時，[StepWriter] 應拋出此例外；simulator 視為一般可重試的失敗。 */
class StepWriteRateLimitException(cause: Throwable? = null) : Exception(cause)
