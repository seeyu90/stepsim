package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.SimulationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAvailabilityTest {

    private val availability = BackgroundAvailability()

    @Test
    fun `initially not alive and available`() {
        assertEquals(ForegroundServiceStatus(alive = false, failedAt = null), availability.status.value)
        assertTrue(availability.available.value)
    }

    @Test
    fun `start requested marks the service alive`() {
        availability.reportStartRequested()

        assertTrue(availability.status.value.alive)
        assertTrue(availability.available.value)
    }

    @Test
    fun `start failure records the status kind and shows unavailable`() {
        availability.reportStartRequested()
        availability.reportStartFailed(SimulationStatus.RUNNING)

        assertEquals(ForegroundServiceStatus(alive = false, failedAt = SimulationStatus.RUNNING), availability.status.value)
        assertFalse(availability.available.value)
    }

    @Test
    fun `successful start clears the failure`() {
        availability.reportStartFailed(SimulationStatus.RUNNING)
        availability.reportStartRequested()
        availability.reportStarted()

        assertEquals(ForegroundServiceStatus(alive = true, failedAt = null), availability.status.value)
        assertTrue(availability.available.value)
    }

    @Test
    fun `stopped keeps the failure record`() {
        availability.reportStartFailed(SimulationStatus.PAUSED)
        availability.reportStopped()

        assertEquals(ForegroundServiceStatus(alive = false, failedAt = SimulationStatus.PAUSED), availability.status.value)
        assertFalse(availability.available.value)

        availability.reportStarted()
        availability.reportStopped()
        assertEquals(ForegroundServiceStatus(alive = false, failedAt = null), availability.status.value)
    }

    @Test
    fun `clearing the failure record hides the unavailable hint`() {
        availability.reportStartFailed(SimulationStatus.RUNNING)
        availability.updateFailedAt(nextFailedAt(SimulationStatus.READY, availability.status.value.failedAt))

        assertEquals(ForegroundServiceStatus(alive = false, failedAt = null), availability.status.value)
        assertTrue(availability.available.value)
    }
}
