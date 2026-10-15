package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPolicyTest {

    private fun state(status: SimulationStatus, written: Long = 2_100, target: Long? = 5_000) = SimulationState(
        status = status,
        targetSteps = target,
        gait = Gait.WALK,
        writtenSteps = written,
        failedBatchNumber = if (status == SimulationStatus.FAILED) 7 else null,
    )

    @Test
    fun `foreground is kept while running, paused or failed`() {
        for (status in listOf(
            SimulationStatus.RUNNING,
            SimulationStatus.PAUSED,
            SimulationStatus.FAILED,
            SimulationStatus.PERMISSION_REVOKED,
        )) {
            assertTrue("status $status", shouldRunForeground(status))
        }
    }

    @Test
    fun `foreground stops when completed or not started`() {
        for (status in listOf(SimulationStatus.COMPLETED, SimulationStatus.IDLE, SimulationStatus.READY)) {
            assertFalse("status $status", shouldRunForeground(status))
        }
    }

    @Test
    fun `running shows progress with pause and stop`() {
        val model = notificationModel(state(SimulationStatus.RUNNING))!!

        assertEquals(NotificationKind.RUNNING, model.kind)
        assertEquals(42, model.percent)
        assertEquals(listOf(NotificationAction.PAUSE, NotificationAction.STOP), model.actions)
        assertTrue(model.ongoing)
    }

    @Test
    fun `paused shows resume and stop`() {
        val model = notificationModel(state(SimulationStatus.PAUSED))!!
        assertEquals(listOf(NotificationAction.RESUME, NotificationAction.STOP), model.actions)
        assertTrue(model.ongoing)
    }

    @Test
    fun `failures always keep the stop action`() {
        for (status in listOf(SimulationStatus.FAILED, SimulationStatus.PERMISSION_REVOKED)) {
            val model = notificationModel(state(status))!!
            assertEquals("status $status", listOf(NotificationAction.STOP), model.actions)
            assertTrue(model.ongoing)
        }
        assertEquals(7, notificationModel(state(SimulationStatus.FAILED))!!.failedBatchNumber)
    }

    @Test
    fun `completed is dismissible without actions`() {
        val model = notificationModel(state(SimulationStatus.COMPLETED, written = 5_000))!!

        assertEquals(NotificationKind.COMPLETED, model.kind)
        assertEquals(100, model.percent)
        assertTrue(model.actions.isEmpty())
        assertFalse(model.ongoing)
    }

    @Test
    fun `no notification before start`() {
        assertNull(notificationModel(state(SimulationStatus.IDLE, written = 0, target = null)))
        assertNull(notificationModel(state(SimulationStatus.READY, written = 0)))
    }

    @Test
    fun `percent is clamped and does not overflow for large values`() {
        assertEquals(0, notificationModel(state(SimulationStatus.RUNNING, written = 0))!!.percent)
        assertEquals(99, notificationModel(state(SimulationStatus.RUNNING, written = 99_999, target = 100_000))!!.percent)
    }
}
