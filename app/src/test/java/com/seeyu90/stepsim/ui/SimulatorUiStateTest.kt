package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatorUiStateTest {

    private fun state(status: SimulationStatus, granted: Boolean?, available: Boolean?) = SimulatorUiState(
        permissionGranted = granted,
        healthConnectAvailable = available,
        simulation = SimulationState(status = status, targetSteps = 5_000),
    )

    @Test
    fun `unavailable shows only unavailable notice, not permission prompt`() {
        // 不可用時 ViewModel 會把 granted 設為 false
        for (status in listOf(SimulationStatus.READY, SimulationStatus.PAUSED, SimulationStatus.PERMISSION_REVOKED)) {
            assertEquals(
                "status $status",
                AccessNotice.HEALTH_CONNECT_UNAVAILABLE,
                state(status, granted = false, available = false).accessNotice,
            )
        }
    }

    @Test
    fun `unavailable while failed is explained by the failure card only`() {
        val ui = state(SimulationStatus.FAILED, granted = false, available = false)

        assertEquals(AccessNotice.NONE, ui.accessNotice)
        assertEquals(
            FailureMessage.HEALTH_CONNECT_UNAVAILABLE,
            failureMessageFor(ui.status, ui.simulation.failure, ui.permissionGranted, ui.healthConnectAvailable),
        )
    }

    @Test
    fun `end is always offered when failed`() {
        val tri = listOf(true, false, null)
        for (status in listOf(SimulationStatus.FAILED, SimulationStatus.PERMISSION_REVOKED)) {
            for (granted in tri) for (available in tri) {
                val ui = state(status, granted, available)
                assertTrue(
                    "status=$status granted=$granted available=$available",
                    SecondaryAction.END in ui.secondaryActions,
                )
            }
        }
    }

    @Test
    fun `unknown permission does not block retry when failed`() {
        for (status in listOf(SimulationStatus.FAILED, SimulationStatus.PERMISSION_REVOKED)) {
            val ui = state(status, granted = null, available = true)
            assertEquals(AccessNotice.NONE, ui.accessNotice)
            assertTrue(ui.showMainContent)
        }
    }

    @Test
    fun `unknown permission shows checking when not failed`() {
        val ui = state(SimulationStatus.READY, granted = null, available = null)
        assertEquals(AccessNotice.CHECKING, ui.accessNotice)
        assertTrue("not started: nothing to end", ui.secondaryActions.isEmpty())
    }

    @Test
    fun `denied permission shows permission prompt and offers end only once started`() {
        val ready = state(SimulationStatus.READY, granted = false, available = true)
        assertEquals(AccessNotice.PERMISSION_REQUIRED, ready.accessNotice)
        assertFalse(SecondaryAction.END in ready.secondaryActions)

        val revoked = state(SimulationStatus.PERMISSION_REVOKED, granted = false, available = true)
        assertEquals(AccessNotice.PERMISSION_REQUIRED, revoked.accessNotice)
        assertTrue(SecondaryAction.END in revoked.secondaryActions)
    }

    @Test
    fun `background unavailable hint only while a simulation is in progress`() {
        val inProgress = listOf(
            SimulationStatus.RUNNING,
            SimulationStatus.PAUSED,
            SimulationStatus.FAILED,
            SimulationStatus.PERMISSION_REVOKED,
        )
        for (status in SimulationStatus.entries) {
            val ui = state(status, granted = true, available = true).copy(backgroundAvailable = false)
            assertEquals("status $status", status in inProgress, ui.showBackgroundUnavailable)
        }
        val ok = state(SimulationStatus.RUNNING, granted = true, available = true)
        assertFalse("available: no hint", ok.showBackgroundUnavailable)
    }

    @Test
    fun `granted and available shows main content`() {
        val ui = state(SimulationStatus.RUNNING, granted = true, available = true)
        assertEquals(AccessNotice.NONE, ui.accessNotice)
        assertTrue(ui.showMainContent)
    }
}
