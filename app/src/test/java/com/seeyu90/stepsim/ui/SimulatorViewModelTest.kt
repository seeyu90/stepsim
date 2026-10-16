package com.seeyu90.stepsim.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import com.seeyu90.stepsim.simulator.SimulationRunner
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepBatch
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.StepWritePermissionException
import com.seeyu90.stepsim.simulator.StepWriter
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
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
    private val runner = SimulationRunner(
        simulator = StepSimulator(time, writer, sessionId = "vm"),
        dispatcher = dispatcher,
        tickIntervalMillis = 10_000,
    )
    private val backgroundAvailable = MutableStateFlow(true)
    private lateinit var viewModel: SimulatorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SimulatorViewModel(
            controller = runner,
            healthConnect = healthConnect,
            backgroundAvailable = backgroundAvailable,
        )
    }

    /** 在測試本體內關閉 runner，計時迴圈不會留到 runTest 收尾。 */
    private fun vmTest(body: suspend TestScope.() -> Unit): TestResult = scope.runTest {
        try {
            body()
        } finally {
            runner.close()
        }
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
    fun `ui reflects progress from the controller`() = vmTest {
        startSimulation()
        assertEquals(SimulationStatus.RUNNING, ui.status)

        advanceTimeBy(10_001)
        assertEquals(16L, ui.simulation.writtenSteps)

        advanceTimeBy(10_000)
        assertEquals(33L, ui.simulation.writtenSteps)
        assertEquals(2, writer.written.size)
    }


    @Test
    fun `background unavailable is reflected in ui state`() = vmTest {
        startSimulation()
        assertTrue(ui.backgroundAvailable)
        assertFalse(ui.showBackgroundUnavailable)

        backgroundAvailable.value = false
        runCurrent()

        assertFalse(ui.backgroundAvailable)
        assertTrue(ui.showBackgroundUnavailable)
        assertEquals("simulation keeps running", SimulationStatus.RUNNING, ui.status)
    }

    @Test
    fun `recreated view model restores the target held by the process-level simulation`() = vmTest {
        viewModel.refreshPermission()
        viewModel.onEvent(SimulatorEvent.TargetInputChanged("5000"))
        runCurrent()
        assertEquals(SimulationStatus.READY, ui.status)

        // 按 Back 後重開：ViewModel 重建，程序層級的模擬器仍存著目標
        val recreated = SimulatorViewModel(
            controller = runner,
            healthConnect = healthConnect,
            backgroundAvailable = backgroundAvailable,
        )
        recreated.refreshPermission()
        runCurrent()

        assertEquals("5000", recreated.state.value.targetInput)
        assertTrue("start must stay enabled", recreated.state.value.canStart)
    }

    @Test
    fun `permission denied blocks start`() = vmTest {
        healthConnect.granted = false
        startSimulation()

        assertEquals(false, ui.permissionGranted)
        assertFalse(ui.canStart)
        assertEquals(SimulationStatus.READY, ui.status)
    }

    @Test
    fun `write failure shows failed state and retry resumes ticking`() = vmTest {
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
    fun `health connect unavailable is reported separately from permission`() = vmTest {
        healthConnect.availability = HealthConnectAvailability.NEEDS_INSTALL
        viewModel.refreshPermission()
        runCurrent()

        assertEquals(false, ui.healthConnectAvailable)
        assertEquals(false, ui.permissionGranted)
    }

    @Test
    fun `write failure rechecks availability`() = vmTest {
        startSimulation()
        assertEquals(true, ui.healthConnectAvailable)

        healthConnect.availability = HealthConnectAvailability.NOT_SUPPORTED
        writer.failWith = IllegalStateException("platform error")
        advanceTimeBy(10_001)

        assertEquals(SimulationStatus.FAILED, ui.status)
        assertEquals(false, ui.healthConnectAvailable)
        assertEquals(
            FailureMessage.HEALTH_CONNECT_UNAVAILABLE,
            failureMessageFor(ui.status, ui.simulation.failure, ui.permissionGranted, ui.healthConnectAvailable),
        )
    }

    @Test
    fun `permission revoked during write rechecks permission`() = vmTest {
        startSimulation()
        assertEquals(true, ui.permissionGranted)

        healthConnect.granted = false
        writer.failWith = StepWritePermissionException(SecurityException("revoked"))
        advanceTimeBy(10_001)

        assertEquals(SimulationStatus.PERMISSION_REVOKED, ui.status)
        assertEquals(false, ui.permissionGranted)
    }

    @Test
    fun `repeated failure with the same status rechecks permission`() = vmTest {
        startSimulation()
        writer.failWith = IOException("disk")
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(SimulationStatus.FAILED, ui.status)
        val calls = healthConnect.hasAllPermissionsCalls

        viewModel.onEvent(SimulatorEvent.Retry) // 重試又失敗，狀態仍是 FAILED
        runCurrent()

        assertEquals(SimulationStatus.FAILED, ui.status)
        assertEquals(calls + 1, healthConnect.hasAllPermissionsCalls)
    }

    @Test
    fun `permission restored is not shown before the recheck completes`() = vmTest {
        startSimulation()
        assertEquals(true, ui.permissionGranted)

        val check = CompletableDeferred<Boolean>()
        healthConnect.hasAllPermissionsHandler = { check.await() }
        writer.failWith = StepWritePermissionException(SecurityException("revoked"))
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(SimulationStatus.PERMISSION_REVOKED, ui.status)
        assertNull("permission is unknown while checking", ui.permissionGranted)
        assertEquals(FailureMessage.PERMISSION_UNCONFIRMED, currentFailureMessage())

        check.complete(true)
        runCurrent()
        assertEquals(FailureMessage.PERMISSION_RESTORED, currentFailureMessage())
    }

    @Test
    fun `failed permission check does not keep the stale value`() = vmTest {
        startSimulation()
        assertEquals(true, ui.permissionGranted)

        healthConnect.hasAllPermissionsHandler = { throw IllegalStateException("service unbound") }
        viewModel.refreshPermission()
        runCurrent()

        assertNull(ui.permissionGranted)
    }

    @Test
    fun `reset rechecks permission`() = vmTest {
        startSimulation()
        val calls = healthConnect.hasAllPermissionsCalls

        viewModel.onEvent(SimulatorEvent.Reset)
        runCurrent()

        assertEquals(SimulationStatus.READY, ui.status)
        assertEquals(calls + 1, healthConnect.hasAllPermissionsCalls)
    }

    private fun currentFailureMessage() =
        failureMessageFor(ui.status, ui.simulation.failure, ui.permissionGranted, ui.healthConnectAvailable)

    @Test
    fun `clearing a valid target disables start`() = vmTest {
        viewModel.refreshPermission()
        viewModel.onEvent(SimulatorEvent.TargetInputChanged("5000"))
        runCurrent()
        assertTrue(ui.canStart)

        viewModel.onEvent(SimulatorEvent.TargetInputChanged(""))
        runCurrent()

        assertNull(ui.targetError)
        assertFalse(ui.canStart)
        viewModel.onEvent(SimulatorEvent.Start)
        runCurrent()
        assertEquals("start is ignored", SimulationStatus.READY, ui.status)
    }

    @Test
    fun `out of range target after a valid one disables start`() = vmTest {
        viewModel.refreshPermission()
        viewModel.onEvent(SimulatorEvent.TargetInputChanged("5000"))
        runCurrent()
        assertTrue(ui.canStart)

        for (input in listOf("0", "100001")) {
            viewModel.onEvent(SimulatorEvent.TargetInputChanged(input))
            runCurrent()
            assertEquals(TargetInputError.OUT_OF_RANGE, ui.targetError)
            assertFalse("canStart for $input", ui.canStart)
        }
        viewModel.onEvent(SimulatorEvent.Start)
        runCurrent()
        assertEquals("start is ignored", SimulationStatus.READY, ui.status)
    }

    @Test
    fun `target input validation`() = vmTest {
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
    var availability = HealthConnectAvailability.AVAILABLE

    /** 預設回傳 [granted]；測試可替換成暫停或拋例外。 */
    var hasAllPermissionsHandler: suspend () -> Boolean = { granted }

    var hasAllPermissionsCalls = 0
        private set

    override fun availability() = availability

    override suspend fun grantedPermissions(): Set<String> = error("not used")

    override suspend fun hasAllPermissions(): Boolean {
        hasAllPermissionsCalls++
        return hasAllPermissionsHandler()
    }

    override fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        error("not used")

    override fun installIntent(): Intent = error("not used")

    override fun settingsIntent(): Intent = error("not used")
}
