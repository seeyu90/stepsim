package com.seeyu90.stepsim.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepBatch
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.StepWriter
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    /** 時鐘綁定測試排程器的虛擬時間。 */
    private val time = { Instant.ofEpochMilli(dispatcher.scheduler.currentTime) }
    private val writer = FakeStepWriter()
    private val healthConnect = FakeHealthConnect()
    private lateinit var viewModel: SimulatorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SimulatorViewModel(
            simulator = StepSimulator(time, writer, sessionId = "vm"),
            healthConnect = healthConnect,
            tickIntervalMillis = 10_000,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.startSimulation(target: String = "5000") {
        viewModel.refreshPermission()
        viewModel.onEvent(SimulatorEvent.TargetInputChanged(target))
        runCurrent()
        viewModel.onEvent(SimulatorEvent.Start)
        runCurrent()
    }

    private val ui get() = viewModel.state.value

    @Test
    fun `ticks every interval and updates progress`() = scope.runTest {
        startSimulation()
        assertEquals(SimulationStatus.RUNNING, ui.status)

        advanceTimeBy(10_001)
        assertEquals(16L, ui.simulation.writtenSteps)

        advanceTimeBy(10_000)
        assertEquals(33L, ui.simulation.writtenSteps)
        assertEquals(2, writer.written.size)
    }

    @Test
    fun `leaving foreground pauses and stops writing`() = scope.runTest {
        startSimulation()
        advanceTimeBy(10_001)

        viewModel.onAppStopped(isChangingConfigurations = false)
        runCurrent()
        assertEquals(SimulationStatus.PAUSED, ui.status)

        val writes = writer.written.size
        advanceTimeBy(60_000)
        assertEquals(writes, writer.written.size)
    }

    @Test
    fun `configuration change does not pause`() = scope.runTest {
        startSimulation()

        viewModel.onAppStopped(isChangingConfigurations = true)
        runCurrent()
        assertEquals(SimulationStatus.RUNNING, ui.status)

        advanceTimeBy(10_001)
        assertEquals(1, writer.written.size)
    }

    @Test
    fun `permission denied blocks start`() = scope.runTest {
        healthConnect.granted = false
        startSimulation()

        assertEquals(false, ui.permissionGranted)
        assertFalse(ui.canStart)
        assertEquals(SimulationStatus.READY, ui.status)
    }

    @Test
    fun `write failure shows failed state and retry resumes ticking`() = scope.runTest {
        startSimulation()
        writer.failWith = IOException("disk")
        advanceTimeBy(10_001)
        assertEquals(SimulationStatus.FAILED, ui.status)

        writer.failWith = null
        viewModel.onEvent(SimulatorEvent.Retry)
        runCurrent()
        assertEquals(SimulationStatus.RUNNING, ui.status)
        assertEquals(16L, ui.simulation.writtenSteps)

        advanceTimeBy(10_001)
        assertEquals(2, writer.written.size)
    }

    @Test
    fun `target input validation`() = scope.runTest {
        viewModel.refreshPermission()

        viewModel.onEvent(SimulatorEvent.TargetInputChanged("0"))
        runCurrent()
        assertEquals(TargetInputError.OUT_OF_RANGE, ui.targetError)
        assertFalse(ui.canStart)

        viewModel.onEvent(SimulatorEvent.TargetInputChanged("100001"))
        runCurrent()
        assertEquals(TargetInputError.OUT_OF_RANGE, ui.targetError)

        viewModel.onEvent(SimulatorEvent.TargetInputChanged("5a0b0"))
        runCurrent()
        assertEquals("non-digits are filtered", "500", ui.targetInput)
        assertNull(ui.targetError)
        assertTrue(ui.canStart)
    }
}

private class FakeStepWriter : StepWriter {
    val written = mutableListOf<StepBatch>()
    var failWith: Exception? = null

    override suspend fun write(batch: StepBatch) {
        failWith?.let { throw it }
        written += batch
    }
}

private class FakeHealthConnect : HealthConnectManager {
    var granted = true

    override fun availability() = HealthConnectAvailability.AVAILABLE

    override suspend fun grantedPermissions(): Set<String> = error("not used")

    override suspend fun hasAllPermissions(): Boolean = granted

    override fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        error("not used")

    override fun installIntent(): Intent = error("not used")

    override fun settingsIntent(): Intent = error("not used")
}
