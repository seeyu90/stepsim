package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepWritePermissionException
import com.seeyu90.stepsim.simulator.StepWriteRateLimitException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailureMessageTest {

    private fun failed(failure: Throwable?, available: Boolean? = true, granted: Boolean? = true) =
        failureMessageFor(SimulationStatus.FAILED, failure, granted, available)

    @Test
    fun `IllegalStateException shows generic message, not unavailable`() {
        val message = failed(IllegalStateException("platform error"))

        assertEquals(FailureMessage.GENERIC, message)
        assertNotEquals(FailureMessage.HEALTH_CONNECT_UNAVAILABLE, message)
    }

    @Test
    fun `unavailable is shown only when sdk status is not available`() {
        assertEquals(FailureMessage.HEALTH_CONNECT_UNAVAILABLE, failed(IOException(), available = false))
        assertEquals(FailureMessage.IO, failed(IOException(), available = true))
        assertEquals("unknown availability is not unavailable", FailureMessage.IO, failed(IOException(), available = null))
    }

    @Test
    fun `rate limit shows its own message`() {
        assertEquals(FailureMessage.RATE_LIMITED, failed(StepWriteRateLimitException(IllegalStateException())))
    }

    @Test
    fun `io error shows io message`() {
        assertEquals(FailureMessage.IO, failed(IOException("disk")))
    }

    @Test
    fun `other unclassified errors show generic message`() {
        assertEquals(FailureMessage.GENERIC, failed(IllegalArgumentException()))
        assertEquals(FailureMessage.GENERIC, failed(RuntimeException()))
        assertEquals(FailureMessage.GENERIC, failed(null))
    }

    @Test
    fun `permission revoked shows restored message only after regranting`() {
        val failure = StepWritePermissionException(SecurityException())
        assertEquals(
            FailureMessage.PERMISSION_RESTORED,
            failureMessageFor(SimulationStatus.PERMISSION_REVOKED, failure, permissionGranted = true, healthConnectAvailable = true),
        )
        assertEquals(
            "unknown permission is not shown as restored",
            FailureMessage.PERMISSION_UNCONFIRMED,
            failureMessageFor(SimulationStatus.PERMISSION_REVOKED, failure, permissionGranted = null, healthConnectAvailable = true),
        )
        assertNull(
            failureMessageFor(SimulationStatus.PERMISSION_REVOKED, failure, permissionGranted = false, healthConnectAvailable = true),
        )
    }

    @Test
    fun `no card when not failed`() {
        for (status in listOf(SimulationStatus.IDLE, SimulationStatus.RUNNING, SimulationStatus.PAUSED, SimulationStatus.COMPLETED)) {
            assertNull(failureMessageFor(status, IOException(), permissionGranted = true, healthConnectAvailable = true))
        }
    }
}
