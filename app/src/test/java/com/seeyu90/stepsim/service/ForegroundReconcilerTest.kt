package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * App 端的對帳迴圈，守住 b58536c／eb40f6b 的修正：以「目前的值」對帳，而不是依賴每次變化都送到。
 * 每個測試各守一個保護，拿掉對應的那行程式碼就會變紅：
 * - 與服務狀態合併（combine）：服務自己死掉而模擬狀態沒變時，要重新啟動
 * - distinctUntilChanged：進度更新（狀態種類不變）不可重複啟動
 * - 失敗紀錄的更新接線：離開前景需求時要作廢，下一輪才會重新嘗試
 *
 * 來源 Flow 與正式環境相同：模擬的完整狀態 map 成 status（map 不會去重，進度更新也會送出）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundReconcilerTest {

    private val availability = BackgroundAvailability()
    private val simulation = MutableStateFlow(SimulationState(status = SimulationStatus.READY, targetSteps = 5_000))
    private val started = mutableListOf<SimulationStatus>()

    /** 如同 SimulationService.start：先把服務記為存活（之後只有服務死掉才會變回不存活）。 */
    private fun startAlive(status: SimulationStatus) {
        started += status
        availability.reportStartRequested()
    }

    /** 啟動失敗：服務沒起來，記下失敗時的狀態種類（如同 SimulationService.start 的失敗路徑）。 */
    private fun startFailing(status: SimulationStatus) {
        started += status
        availability.reportStartRequested()
        availability.reportStartFailed(status)
    }

    /** 完全不回報服務狀態：此時只有 distinctUntilChanged 能避免重複啟動。 */
    private fun startSilently(status: SimulationStatus) {
        started += status
    }

    private fun TestScope.launchReconciler(start: (SimulationStatus) -> Unit) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ForegroundReconciler(simulation.map { it.status }, availability, start).run()
        }
        runCurrent()
    }

    private fun TestScope.moveTo(status: SimulationStatus) {
        simulation.value = simulation.value.copy(status = status)
        runCurrent()
    }

    private fun TestScope.progressTo(writtenSteps: Long) {
        simulation.value = simulation.value.copy(writtenSteps = writtenSteps)
        runCurrent()
    }

    @Test
    fun `does not start before foreground is needed`() = runTest {
        launchReconciler(::startAlive)

        moveTo(SimulationStatus.IDLE)
        moveTo(SimulationStatus.READY)

        assertEquals(emptyList<SimulationStatus>(), started)
    }

    @Test
    fun `starts once and does not start again while the service stays alive`() = runTest {
        launchReconciler(::startAlive)

        moveTo(SimulationStatus.RUNNING)
        progressTo(16)
        moveTo(SimulationStatus.PAUSED)
        moveTo(SimulationStatus.RUNNING)

        assertEquals(listOf(SimulationStatus.RUNNING), started)
    }

    @Test
    fun `progress updates that keep the status kind do not start the service again`() = runTest {
        // 服務不回報存活：若沒有 distinctUntilChanged，每次進度更新都會再啟動一次
        launchReconciler(::startSilently)

        moveTo(SimulationStatus.RUNNING)
        progressTo(16)
        progressTo(33)
        progressTo(50)

        assertEquals(listOf(SimulationStatus.RUNNING), started)
    }

    @Test
    fun `restarts when the service dies while the simulation is still running`() = runTest {
        launchReconciler(::startAlive)
        moveTo(SimulationStatus.RUNNING)
        assertEquals(listOf(SimulationStatus.RUNNING), started)

        // 服務被系統回收或自己停止：模擬狀態沒變，只有服務狀態變了。必須依「目前的值」重新對帳
        availability.reportStopped()
        runCurrent()

        assertEquals(listOf(SimulationStatus.RUNNING, SimulationStatus.RUNNING), started)
    }

    @Test
    fun `failed start is not retried within the same status kind but is when it changes`() = runTest {
        launchReconciler(::startFailing)

        moveTo(SimulationStatus.RUNNING)
        progressTo(16)
        progressTo(33)
        assertEquals("no retry loop in the same status kind", listOf(SimulationStatus.RUNNING), started)

        moveTo(SimulationStatus.PAUSED)
        assertEquals(listOf(SimulationStatus.RUNNING, SimulationStatus.PAUSED), started)
    }

    @Test
    fun `leaving the foreground requirement clears the failure record so the next run retries`() = runTest {
        launchReconciler(::startFailing)
        moveTo(SimulationStatus.RUNNING)
        assertEquals(SimulationStatus.RUNNING, availability.status.value.failedAt)
        assertFalse("hint is shown while the failure stands", availability.available.first())

        moveTo(SimulationStatus.READY)
        assertNull("failure record is cleared", availability.status.value.failedAt)
        assertEquals("hint disappears", true, availability.available.first())

        moveTo(SimulationStatus.RUNNING)
        assertEquals(listOf(SimulationStatus.RUNNING, SimulationStatus.RUNNING), started)
    }
}
