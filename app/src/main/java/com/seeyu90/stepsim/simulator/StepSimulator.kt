package com.seeyu90.stepsim.simulator

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 步數模擬狀態機。
 *
 * - 步數 = 步頻 × 經過時間；小數餘數累積到下一批，最後一批截到剛好達標，總數不多不少。
 * - 每批區間為 [上一批結束, 現在)，只寫已過去的時間；不滿 1 步時不寫，下一批從同一起點重算。
 * - 暫停、寫入失敗期間留空：繼續或重試成功後，下一批從當下開始，不補寫空窗。
 * - 所有操作以 [mutex] 串行，避免 tick 與暫停同時寫入同一段時間。
 */
class StepSimulator(
    private val timeSource: TimeSource,
    private val writer: StepWriter,
    private val sessionId: String,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    /** 下一批的起點；null 代表目前不在計時（未開始、暫停、失敗）。 */
    private var segmentStart: Instant? = null

    /**
     * 尚未寫出的小數步數，以整數單位保存避免浮點誤差：1 步 = [UNITS_PER_STEP] 單位
     * （步頻 × 毫秒 = 單位），永遠小於 1 步。
     */
    private var carryUnits = 0L

    private var nextSequence = 1

    /** 寫入途中被取消、結果未知的一批；下次先以相同 clientRecordId 重送。 */
    private var pending: PlannedBatch? = null

    /** 寫入失敗、等待重試的一批。 */
    private var failed: PlannedBatch? = null

    /** 失敗發生在暫停補寫時；重試成功後應回到暫停而非執行中。 */
    private var failedWhilePausing = false

    private class PlannedBatch(val batch: StepBatch, val carryUnitsAfter: Long, val completes: Boolean)

    suspend fun setTarget(steps: Long) = mutex.withLock {
        require(isValidTarget(steps)) { "target must be in 1..$MAX_TARGET_STEPS" }
        val status = _state.value.status
        check(status == SimulationStatus.IDLE || status == SimulationStatus.READY) { "cannot change target while $status" }
        _state.update { it.copy(targetSteps = steps, status = SimulationStatus.READY) }
    }

    suspend fun setGait(gait: Gait) = mutex.withLock {
        check(_state.value.status != SimulationStatus.RUNNING) { "cannot change gait while running" }
        _state.update { it.copy(gait = gait) }
    }

    suspend fun start() = mutex.withLock {
        check(_state.value.status == SimulationStatus.READY) { "cannot start while ${_state.value.status}" }
        segmentStart = now()
        _state.update { it.copy(status = SimulationStatus.RUNNING) }
    }

    /** 由外部定時呼叫：寫出 [上一批結束, 現在) 的整數步數。 */
    suspend fun tick() = mutex.withLock {
        if (_state.value.status != SimulationStatus.RUNNING) return@withLock
        if (!resendPending()) return@withLock
        val planned = planBatch(now(), keepFractionOnShortfall = false) ?: return@withLock
        send(planned)
    }

    /** 暫停前先寫到暫停時刻；不滿 1 步的部分以餘數保留。 */
    suspend fun pause() = mutex.withLock {
        if (_state.value.status != SimulationStatus.RUNNING) return@withLock
        if (!resendPending()) {
            failedWhilePausing = true
            return@withLock
        }
        val planned = planBatch(now(), keepFractionOnShortfall = true)
        if (planned != null && !send(planned)) {
            failedWhilePausing = true
            return@withLock
        }
        if (_state.value.status == SimulationStatus.RUNNING) {
            segmentStart = null
            _state.update { it.copy(status = SimulationStatus.PAUSED) }
        }
    }

    /** 從當下重新開始計時，暫停期間不補寫。 */
    suspend fun resume() = mutex.withLock {
        if (_state.value.status != SimulationStatus.PAUSED) return@withLock
        segmentStart = now()
        _state.update { it.copy(status = SimulationStatus.RUNNING) }
    }

    /**
     * 以相同 clientRecordId 重送失敗的那一批；失敗期間不補寫。
     * 成功後從當下繼續；若失敗發生在暫停補寫時，則維持暫停，待使用者按「繼續」。
     */
    suspend fun retry() = mutex.withLock {
        val status = _state.value.status
        if (status != SimulationStatus.FAILED && status != SimulationStatus.PERMISSION_REVOKED) return@withLock
        val planned = failed ?: return@withLock
        if (!write(planned)) return@withLock
        failed = null
        commit(planned)
        val wasPausing = failedWhilePausing
        failedWhilePausing = false
        if (_state.value.status == SimulationStatus.COMPLETED) return@withLock
        if (wasPausing) {
            segmentStart = null
            _state.update { it.copy(status = SimulationStatus.PAUSED) }
        } else {
            segmentStart = now()
            _state.update { it.copy(status = SimulationStatus.RUNNING) }
        }
    }

    /** 結束本次模擬並清空進度，保留目標與步態。 */
    suspend fun reset() = mutex.withLock {
        segmentStart = null
        carryUnits = 0L
        pending = null
        failed = null
        failedWhilePausing = false
        _state.update {
            SimulationState(
                status = if (it.targetSteps == null) SimulationStatus.IDLE else SimulationStatus.READY,
                targetSteps = it.targetSteps,
                gait = it.gait,
                failureCount = it.failureCount,
            )
        }
    }

    private fun now(): Instant = timeSource.now().truncatedTo(ChronoUnit.MILLIS)

    /** 回傳 true 代表沒有待重送的批次（或已重送成功），可以繼續。 */
    private suspend fun resendPending(): Boolean {
        val planned = pending ?: return true
        pending = null
        return send(planned)
    }

    private fun planBatch(now: Instant, keepFractionOnShortfall: Boolean): PlannedBatch? {
        val start = segmentStart ?: return null
        val state = _state.value
        val target = state.targetSteps ?: return null
        if (!now.isAfter(start)) return null

        val cadence = state.gait.stepsPerMinute.toLong()
        val rawUnits = cadence * Duration.between(start, now).toMillis() + carryUnits
        val whole = rawUnits / UNITS_PER_STEP
        if (whole < 1) {
            if (keepFractionOnShortfall) carryUnits = rawUnits
            return null
        }

        val remaining = target - state.writtenSteps
        val id = "stepsim-$sessionId-${nextSequence++}"
        if (whole >= remaining) {
            // 最後一批：結束時間算到剛好達標的時刻（不晚於現在）
            val unitsNeeded = remaining * UNITS_PER_STEP - carryUnits
            val millisNeeded = ((unitsNeeded + cadence - 1) / cadence).coerceAtLeast(1)
            val end = minOf(start.plusMillis(millisNeeded), now)
            return PlannedBatch(StepBatch(id, start, end, remaining), carryUnitsAfter = 0L, completes = true)
        }
        return PlannedBatch(
            StepBatch(id, start, now, whole),
            carryUnitsAfter = rawUnits - whole * UNITS_PER_STEP,
            completes = false,
        )
    }

    /** 寫入並提交；回傳是否成功。失敗時狀態轉為 FAILED 或 PERMISSION_REVOKED。 */
    private suspend fun send(planned: PlannedBatch): Boolean {
        if (!write(planned)) return false
        commit(planned)
        return true
    }

    private suspend fun write(planned: PlannedBatch): Boolean =
        try {
            writer.write(planned.batch)
            true
        } catch (e: CancellationException) {
            // 結果未知（可能已寫入）：保留下來，下次以相同 clientRecordId 重送，由 HC 去重
            pending = planned
            throw e
        } catch (e: Exception) {
            failed = planned
            segmentStart = null
            _state.update {
                it.copy(
                    status = if (e is StepWritePermissionException) {
                        SimulationStatus.PERMISSION_REVOKED
                    } else {
                        SimulationStatus.FAILED
                    },
                    failedBatchNumber = it.batchCount + 1,
                    failure = e,
                    failureCount = it.failureCount + 1,
                )
            }
            false
        }

    private fun commit(planned: PlannedBatch) {
        // 同一批可能同時被記為待重送與失敗（例如重試時寫入被取消），提交後兩者都要清掉，避免重複提交
        if (pending === planned) pending = null
        if (failed === planned) failed = null
        val batch = planned.batch
        carryUnits = planned.carryUnitsAfter
        segmentStart = batch.end
        _state.update {
            it.copy(
                status = if (planned.completes) SimulationStatus.COMPLETED else it.status,
                writtenSteps = it.writtenSteps + batch.steps,
                batchCount = it.batchCount + 1,
                firstBatchStart = it.firstBatchStart ?: batch.start,
                lastBatchEnd = batch.end,
                failedBatchNumber = null,
                failure = null,
            )
        }
        if (planned.completes) segmentStart = null
    }

    companion object {
        const val MAX_TARGET_STEPS = 100_000L

        /** 1 步對應的單位數：步頻（步/分）× 毫秒 ÷ 60,000 = 步數。 */
        private const val UNITS_PER_STEP = 60_000L

        fun isValidTarget(steps: Long): Boolean = steps in 1..MAX_TARGET_STEPS
    }
}
