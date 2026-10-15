package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.health.HealthConnectAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionAutoReturnTest {

    private val unchecked = PermissionUiState()
    private val notGranted = PermissionUiState(HealthConnectAvailability.AVAILABLE, granted = false, checked = true)
    private val granted = PermissionUiState(HealthConnectAvailability.AVAILABLE, granted = true, checked = true)
    private val failed = granted.copy(refreshFailed = true)

    private fun decisions(vararg states: PermissionUiState): List<Boolean> {
        val tracker = PermissionAutoReturn()
        return states.map(tracker::shouldReturn)
    }

    @Test
    fun `returns when permission changes from not granted to granted`() {
        assertEquals(listOf(false, false, true), decisions(unchecked, notGranted, granted))
    }

    @Test
    fun `does not return when already granted on entry`() {
        // 進來重新授權：一直是已授權，不自動跳走
        assertEquals(listOf(false, false, false), decisions(unchecked, granted, granted))
    }

    @Test
    fun `stays while still not granted`() {
        assertEquals(listOf(false, false), decisions(notGranted, notGranted))
    }

    @Test
    fun `ignores unchecked and failed refresh states`() {
        // 讀取失敗時的 granted 不可信，不算「已授權」也不算「未授權」
        assertEquals(listOf(false, false), decisions(failed, granted))
        assertEquals(listOf(false, false, true), decisions(notGranted, failed, granted))
    }
}
