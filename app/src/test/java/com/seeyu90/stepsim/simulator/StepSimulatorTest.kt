package com.seeyu90.stepsim.simulator

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StepSimulatorTest {

    private val t0: Instant = Instant.parse("2026-01-01T08:00:00Z")
    private val time = FakeTimeSource(t0)
    private val writer = FakeStepWriter(time)
    private val simulator = StepSimulator(time, writer, sessionId = "test")

    private suspend fun startWith(target: Long, gait: Gait = Gait.WALK) {
        simulator.setTarget(target)
        simulator.setGait(gait)
        simulator.start()
    }

    private suspend fun advanceAndTick(seconds: Double) {
        time.advance(seconds)
        simulator.tick()
    }

    private val status get() = simulator.state.value.status

    // ---- 區間 ----

    @Test
    fun `batches are contiguous and non-overlapping`() = runTest {
        startWith(10_000)

        for (seconds in listOf(7.0, 10.0, 13.5, 3.0, 20.0)) advanceAndTick(seconds)

        val batches = writer.written
        assertEquals(5, batches.size)
        assertEquals(t0, batches.first().start)
        batches.forEach { assertTrue("start < end: $it", it.start.isBefore(it.end)) }
        batches.zipWithNext { a, b -> assertEquals("contiguous", a.end, b.start) }
    }

    @Test
    fun `never writes future time`() = runTest {
        startWith(10_000)

        repeat(6) { advanceAndTick(10.0) }

        writer.written.zip(writer.writtenAt).forEach { (batch, at) ->
            assertFalse("end ${batch.end} is after write time $at", batch.end.isAfter(at))
        }
    }

    // ---- 步數計算 ----

    @Test
    fun `fractional remainder carries over to next batch`() = runTest {
        startWith(10_000)

        // 100 步/分 × 7 秒 = 11.67 步
        repeat(4) { advanceAndTick(7.0) }

        assertEquals(listOf(11L, 12L, 12L, 11L), writer.written.map { it.steps })
        // floor(100 × 28 / 60) = 46
        assertEquals(46L, simulator.state.value.writtenSteps)
    }

    @Test
    fun `total equals target exactly after many uneven batches`() = runTest {
        startWith(1_234, Gait.RUN)

        while (status == SimulationStatus.RUNNING) advanceAndTick(7.3)

        assertEquals(SimulationStatus.COMPLETED, status)
        assertEquals(1_234L, writer.written.sumOf { it.steps })
        assertEquals(1_234L, simulator.state.value.writtenSteps)
    }

    @Test
    fun `less than one step writes nothing and next batch keeps original start`() = runTest {
        startWith(10_000)

        advanceAndTick(0.3) // 0.5 步
        assertTrue(writer.attempts.isEmpty())

        advanceAndTick(0.4) // 累計 0.7 秒 = 1.17 步
        assertEquals(1, writer.written.size)
        assertEquals(t0, writer.written.single().start)
        assertEquals(1L, writer.written.single().steps)
    }

    @Test
    fun `walking writes 100 steps per minute`() = runTest {
        startWith(10_000, Gait.WALK)
        advanceAndTick(60.0)
        assertEquals(100L, writer.written.single().steps)
    }

    @Test
    fun `running writes 160 steps per minute`() = runTest {
        startWith(10_000, Gait.RUN)
        advanceAndTick(60.0)
        assertEquals(160L, writer.written.single().steps)
    }

    // ---- 暫停／繼續 ----

    @Test
    fun `pause flushes up to pause time and resume restarts from resume time`() = runTest {
        startWith(10_000)
        advanceAndTick(30.0)

        time.advance(6.0)
        val pausedAt = time.now()
        simulator.pause()
        assertEquals(SimulationStatus.PAUSED, status)
        assertEquals(pausedAt, writer.written.last().end)

        time.advance(300.0)
        simulator.tick()
        assertEquals("paused ticks write nothing", 2, writer.written.size)

        val resumedAt = time.now()
        simulator.resume()
        advanceAndTick(12.0)

        assertEquals("gap is left empty", resumedAt, writer.written.last().start)
        assertEquals(20L, writer.written.last().steps)
    }

    @Test
    fun `fraction left at pause is kept after resume`() = runTest {
        startWith(10_000)
        time.advance(0.3) // 0.5 步，不滿 1 步
        simulator.pause()
        assertTrue(writer.attempts.isEmpty())

        time.advance(600.0)
        simulator.resume()
        val resumedAt = time.now()
        advanceAndTick(0.3) // 0.5 + 0.5 = 1 步

        assertEquals(1L, writer.written.single().steps)
        assertEquals(resumedAt, writer.written.single().start)
    }

    @Test
    fun `gait changed while paused applies after resume`() = runTest {
        startWith(10_000, Gait.WALK)
        advanceAndTick(60.0)
        simulator.pause()

        simulator.setGait(Gait.RUN)
        simulator.resume()
        advanceAndTick(60.0)

        assertEquals(listOf(100L, 160L), writer.written.map { it.steps })
    }

    @Test(expected = IllegalStateException::class)
    fun `gait cannot change while running`() = runTest {
        startWith(10_000)
        simulator.setGait(Gait.RUN)
    }

    // ---- 達標 ----

    @Test
    fun `last batch is capped at target and ends when target is reached`() = runTest {
        startWith(10, Gait.WALK)

        advanceAndTick(10.0) // 原本可寫 16 步

        val last = writer.written.single()
        assertEquals(10L, last.steps)
        // 100 步/分 走 10 步需 6 秒
        assertEquals(t0.plusSeconds(6), last.end)
        assertEquals(SimulationStatus.COMPLETED, status)

        advanceAndTick(60.0)
        assertEquals("no writes after completion", 1, writer.attempts.size)
    }

    // ---- 目標驗證 ----

    @Test
    fun `invalid targets are rejected`() = runTest {
        for (target in listOf(0L, -1L, StepSimulator.MAX_TARGET_STEPS + 1)) {
            try {
                simulator.setTarget(target)
                fail("target $target should be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(SimulationStatus.IDLE, status)
    }

    @Test(expected = IllegalStateException::class)
    fun `cannot start without target`() = runTest {
        simulator.start()
    }

    // ---- 寫入失敗與重試 ----

    @Test
    fun `write failure enters failed state without counting the batch`() = runTest {
        startWith(10_000)
        advanceAndTick(10.0)

        writer.failWith = IOException("disk")
        advanceAndTick(10.0)

        val state = simulator.state.value
        assertEquals(SimulationStatus.FAILED, state.status)
        assertEquals(16L, state.writtenSteps)
        assertEquals(2, state.failedBatchNumber)
        assertTrue(state.failure is IOException)

        writer.failWith = null
        advanceAndTick(10.0)
        assertEquals("failed state does not tick", 2, writer.attempts.size)
    }

    @Test
    fun `retry resends the identical batch then continues from retry time`() = runTest {
        startWith(10_000)
        writer.failWith = IOException("disk")
        advanceAndTick(10.0)
        val failedBatch = writer.attempts.single()

        writer.failWith = null
        time.advance(5.0)
        val retriedAt = time.now()
        simulator.retry()

        assertEquals("same id, interval and steps", failedBatch, writer.written.first())
        assertEquals(SimulationStatus.RUNNING, status)

        advanceAndTick(10.0)
        assertEquals(retriedAt, writer.written.last().start)
    }

    @Test
    fun `waiting in failed state then retrying does not backfill the gap`() = runTest {
        startWith(10_000)
        writer.failWith = IOException("disk")
        advanceAndTick(10.0) // 失敗的批次 [0s, 10s)

        writer.failWith = null
        time.advance(120.0) // 失敗後停留 2 分鐘
        simulator.retry()
        advanceAndTick(10.0)

        val failedEnd = t0.plusSeconds(10)
        val retriedAt = t0.plusSeconds(130)
        writer.written.forEach { batch ->
            val overlapsGap = batch.start.isBefore(retriedAt) && batch.end.isAfter(failedEnd)
            assertFalse("batch $batch backfills the failure gap", overlapsGap)
        }
        // 10 秒（重送）+ 10 秒（重試後）= 共 20 秒的步數，不含 120 秒空窗
        assertEquals(33L, simulator.state.value.writtenSteps)
    }

    @Test
    fun `failure during pause flush stays paused after successful retry`() = runTest {
        startWith(10_000)
        advanceAndTick(10.0)

        time.advance(5.0)
        writer.failWith = IOException("disk")
        simulator.pause() // 補寫 [10s, 15s) 失敗
        assertEquals(SimulationStatus.FAILED, status)

        writer.failWith = null
        time.advance(30.0)
        simulator.retry()
        assertEquals(SimulationStatus.PAUSED, status)
        assertEquals(t0.plusSeconds(15), writer.written.last().end)

        val attempts = writer.attempts.size
        advanceAndTick(60.0)
        assertEquals("paused after retry: ticks write nothing", attempts, writer.attempts.size)

        val resumedAt = time.now()
        simulator.resume()
        advanceAndTick(10.0)
        assertEquals(resumedAt, writer.written.last().start)
    }

    @Test
    fun `failure count increments on every failure and is never reset`() = runTest {
        startWith(10_000)
        writer.failWith = IOException("disk")
        advanceAndTick(10.0)
        assertEquals(1, simulator.state.value.failureCount)

        simulator.retry() // 同狀態再失敗
        assertEquals(SimulationStatus.FAILED, status)
        assertEquals(2, simulator.state.value.failureCount)

        writer.failWith = null
        simulator.retry()
        assertEquals("retry success keeps count", 2, simulator.state.value.failureCount)

        simulator.reset()
        assertEquals("reset keeps count", 2, simulator.state.value.failureCount)
    }

    @Test
    fun `retry that fails again stays failed`() = runTest {
        startWith(10_000)
        writer.failWith = IOException("disk")
        advanceAndTick(10.0)

        simulator.retry()

        assertEquals(SimulationStatus.FAILED, status)
        assertEquals(0L, simulator.state.value.writtenSteps)
    }

    @Test
    fun `client record ids are unique and only reused on retry`() = runTest {
        startWith(10_000)
        advanceAndTick(10.0)
        writer.failWith = IOException("disk")
        advanceAndTick(10.0)
        writer.failWith = null
        simulator.retry()
        advanceAndTick(10.0)

        assertEquals(
            listOf("stepsim-test-1", "stepsim-test-2", "stepsim-test-2", "stepsim-test-3"),
            writer.attempts.map { it.clientRecordId },
        )
        assertEquals(3, writer.written.map { it.clientRecordId }.toSet().size)
    }

    // ---- 權限 ----

    @Test
    fun `permission error enters permission revoked state instead of failed`() = runTest {
        startWith(10_000)
        writer.failWith = StepWritePermissionException(SecurityException("revoked"))
        advanceAndTick(10.0)

        assertEquals(SimulationStatus.PERMISSION_REVOKED, status)
        assertEquals(0L, simulator.state.value.writtenSteps)

        writer.failWith = null
        simulator.retry()
        assertEquals(SimulationStatus.RUNNING, status)
        assertEquals(16L, simulator.state.value.writtenSteps)
    }

    // ---- 取消與並行 ----

    @Test
    fun `cancellation during write is not a failure and the batch is resent with the same id`() = runTest {
        startWith(10_000)
        time.advance(10.0)
        writer.blockNext = { awaitCancellation() }
        val job = launch { simulator.tick() }
        runCurrent()
        job.cancel()
        runCurrent()

        assertEquals(SimulationStatus.RUNNING, status)
        assertNull(simulator.state.value.failure)

        advanceAndTick(10.0)
        val ids = writer.attempts.map { it.clientRecordId }
        assertEquals(listOf("stepsim-test-1", "stepsim-test-1", "stepsim-test-2"), ids)
        writer.written.zipWithNext { a, b -> assertEquals(a.end, b.start) }
    }

    @Test
    fun `retry cancelled mid-write then retried successfully is not committed twice`() = runTest {
        startWith(10_000)
        writer.failWith = IOException("disk")
        advanceAndTick(10.0) // 第 1 批失敗
        assertEquals(SimulationStatus.FAILED, status)

        // 重試時寫入被取消
        writer.failWith = null
        writer.blockNext = { awaitCancellation() }
        val job = launch { simulator.retry() }
        runCurrent()
        job.cancel()
        runCurrent()

        // 再重試成功
        time.advance(5.0)
        val retriedAt = time.now()
        simulator.retry()
        assertEquals(SimulationStatus.RUNNING, status)
        assertEquals(16L, simulator.state.value.writtenSteps)

        val attemptsBeforeTick = writer.attempts.size
        advanceAndTick(10.0)

        val newAttempts = writer.attempts.drop(attemptsBeforeTick)
        assertEquals("tick 不會重送已提交的批次", listOf("stepsim-test-2"), newAttempts.map { it.clientRecordId })
        // 第 1 批 16 步（餘 0.67 步）+ 重試後 10 秒 17 步；若重複提交會再多 16 步
        assertEquals("writtenSteps 不重複累加", 16L + 17L, simulator.state.value.writtenSteps)
        assertEquals("下一批起點為重試成功時刻", retriedAt, writer.written.last().start)
    }

    @Test
    fun `pause during an in-flight write waits instead of writing an overlapping batch`() = runTest {
        startWith(10_000)
        time.advance(10.0)
        val gate = CompletableDeferred<Unit>()
        writer.blockNext = { gate.await() }
        launch { simulator.tick() }
        runCurrent() // tick 寫入中

        time.advance(5.0)
        launch { simulator.pause() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(SimulationStatus.PAUSED, status)
        assertEquals(2, writer.written.size)
        writer.written.zipWithNext { a, b -> assertEquals("no overlap", a.end, b.start) }
    }

    // ---- 重設 ----

    @Test
    fun `reset clears progress but keeps target and gait`() = runTest {
        startWith(500, Gait.RUN)
        advanceAndTick(10.0)
        simulator.pause()

        simulator.reset()

        val state = simulator.state.value
        assertEquals(SimulationStatus.READY, state.status)
        assertEquals(500L, state.targetSteps)
        assertEquals(Gait.RUN, state.gait)
        assertEquals(0L, state.writtenSteps)
    }
}

private class FakeTimeSource(private var current: Instant) : TimeSource {
    override fun now(): Instant = current

    fun advance(seconds: Double) {
        current = current.plus(Duration.ofMillis((seconds * 1000).toLong()))
    }
}

private class FakeStepWriter(private val time: TimeSource) : StepWriter {
    /** 每次 write 呼叫（含失敗）。 */
    val attempts = mutableListOf<StepBatch>()

    /** 成功寫入的批次。 */
    val written = mutableListOf<StepBatch>()

    /** 每批成功寫入時的時鐘時間，與 [written] 一一對應。 */
    val writtenAt = mutableListOf<Instant>()

    /** 不為 null 時，每次寫入都拋出此例外。 */
    var failWith: Exception? = null

    /** 下一次寫入前先執行（只生效一次），用於模擬寫入中途暫停或被取消。 */
    var blockNext: (suspend () -> Unit)? = null

    override suspend fun write(batch: StepBatch) {
        attempts += batch
        failWith?.let { throw it }
        blockNext?.let { block ->
            blockNext = null
            block()
        }
        written += batch
        writtenAt += time.now()
    }
}
