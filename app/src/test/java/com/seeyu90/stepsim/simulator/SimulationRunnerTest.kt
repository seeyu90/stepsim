package com.seeyu90.stepsim.simulator

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationRunnerTest {

    /** 迴圈類錯誤可能讓測試卡住；逾時即失敗，避免整個測試流程停住。 */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(20)

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val time = TimeSource { Instant.ofEpochMilli(dispatcher.scheduler.currentTime) }
    private val writer = RecordingWriter(time)
    private val runner = SimulationRunner(
        simulator = StepSimulator(time, writer, sessionId = "runner"),
        dispatcher = dispatcher,
        tickIntervalMillis = 10_000,
    )

    @After
    fun tearDown() {
        runner.close()
    }

    private val status get() = runner.state.value.status

    /** 在測試本體內關閉 runner：計時迴圈不會留到 runTest 收尾時繼續排程。 */
    private fun runnerTest(body: suspend TestScope.() -> Unit) = scope.runTest {
        try {
            body()
        } finally {
            runner.close()
        }
    }

    private fun TestScope.startRunning(target: Long = 10_000) {
        runner.setTarget(target)
        runner.start()
        runCurrent()
    }

    @Test
    fun `ticks every interval without any observer`() = runnerTest {
        startRunning()

        advanceTimeBy(10_001)
        assertEquals(1, writer.written.size)
        advanceTimeBy(20_000)
        assertEquals(3, writer.written.size)
    }

    @Test
    fun `pause stops ticking`() = runnerTest {
        startRunning()
        advanceTimeBy(10_001)

        runner.pause()
        runCurrent()
        val attempts = writer.attempts.size
        advanceTimeBy(60_000)

        assertEquals(SimulationStatus.PAUSED, status)
        assertEquals(attempts, writer.attempts.size)
    }

    @Test
    fun `pause then resume keeps exactly one tick loop`() = runnerTest {
        startRunning()
        assertEquals(1, runner.activeTickLoops)
        advanceTimeBy(10_001)
        runner.pause()
        runCurrent()
        assertEquals("paused: loop cancelled", 0, runner.activeTickLoops)
        advanceTimeBy(5_000)

        runner.resume()
        runCurrent()
        assertEquals("resumed: exactly one loop", 1, runner.activeTickLoops)
        val before = writer.written.size
        advanceTimeBy(30_001) // 含繼續後第 10、20、30 秒的 tick

        // 單一迴圈：30 秒內恰好 3 批；若舊迴圈未取消會多出約一倍
        assertEquals(3, writer.written.size - before)
    }

    @Test
    fun `tick loop restarts aligned with resume time`() = runnerTest {
        startRunning()
        advanceTimeBy(10_001)
        runner.pause()
        runCurrent()
        advanceTimeBy(5_000)

        runner.resume()
        runCurrent()
        val resumedAt = time.now()
        advanceTimeBy(10_001)

        // 新迴圈從繼續時刻起算；若沿用舊迴圈，批次會落在舊的節拍上
        val batch = writer.written.last()
        assertEquals(resumedAt, batch.start)
        assertEquals(resumedAt.plusMillis(10_000), batch.end)
    }

    @Test
    fun `failure stops ticking and retry resumes it`() = runnerTest {
        startRunning()
        writer.failWith = IOException("disk")
        advanceTimeBy(10_001)
        assertEquals(SimulationStatus.FAILED, status)
        assertEquals(0, runner.activeTickLoops)

        val attempts = writer.attempts.size
        advanceTimeBy(60_000)
        assertEquals("no ticks while failed", attempts, writer.attempts.size)

        writer.failWith = null
        runner.retry()
        runCurrent()
        assertEquals(SimulationStatus.RUNNING, status)

        advanceTimeBy(10_001)
        assertEquals(2, writer.written.size) // 重送的那批 + 重試後的一批
    }

    @Test
    fun `tick loop ends when target is reached`() = runnerTest {
        startRunning(target = 10)
        advanceTimeBy(10_001)
        assertEquals(SimulationStatus.COMPLETED, status)

        assertEquals(0, runner.activeTickLoops)
        advanceTimeBy(60_000)
        assertEquals(1, writer.attempts.size)
    }

    @Test
    fun `invalid commands are ignored without crashing`() = runnerTest {
        startRunning()
        runner.start() // 已在執行中
        runner.setTarget(0) // 超出範圍
        runner.setGait(Gait.RUN) // 執行中不可改
        runCurrent()

        assertEquals(SimulationStatus.RUNNING, status)
        advanceTimeBy(10_001)
        assertEquals(1, writer.written.size)
    }

    @Test
    fun `close stops the tick loop`() = runnerTest {
        startRunning()
        runner.close()

        advanceTimeBy(60_000)
        assertTrue(writer.attempts.isEmpty())
    }
}

private class RecordingWriter(private val time: TimeSource) : StepWriter {
    val attempts = mutableListOf<StepBatch>()
    val written = mutableListOf<StepBatch>()
    var failWith: Exception? = null

    override suspend fun write(batch: StepBatch) {
        attempts += batch
        failWith?.let { throw it }
        check(!batch.end.isAfter(time.now()))
        written += batch
    }
}
