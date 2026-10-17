package com.seeyu90.stepsim.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectPermissionsTest {

    @Test
    fun `only the write steps permission is requested`() {
        assertEquals(setOf("android.permission.health.WRITE_STEPS"), HealthConnectManager.PERMISSIONS)
    }
}
