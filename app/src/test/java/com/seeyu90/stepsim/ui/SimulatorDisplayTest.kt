package com.seeyu90.stepsim.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatorDisplayTest {

    private val tw = Locale.TRADITIONAL_CHINESE

    @Test
    fun `steps use grouping separators of the locale`() {
        assertEquals("0", formatSteps(0, tw))
        assertEquals("1,000", formatSteps(1_000, tw))
        assertEquals("100,000", formatSteps(100_000, tw))
        assertEquals("1.000", formatSteps(1_000, Locale.GERMANY))
    }

    @Test
    fun `remaining time picks minutes, whole hours or hours and minutes`() {
        assertEquals(RemainingTime.Soon, RemainingTime.of(0))
        assertEquals(RemainingTime.Minutes(1), RemainingTime.of(1))
        assertEquals(RemainingTime.Minutes(59), RemainingTime.of(59))
        assertEquals(RemainingTime.Hours(1), RemainingTime.of(60))
        assertEquals(RemainingTime.HoursMinutes(1, 1), RemainingTime.of(61))
        assertEquals(RemainingTime.Hours(2), RemainingTime.of(120))
        // 100,000 步走路（每分鐘 100 步）
        assertEquals(RemainingTime.HoursMinutes(16, 40), RemainingTime.of(1_000))
    }

    @Test
    fun `negative remaining is treated as soon`() {
        assertEquals(RemainingTime.Soon, RemainingTime.of(-5))
    }

    @Test
    fun `clock time uses the given zone and locale`() {
        val instant = Instant.parse("2026-10-15T06:32:00Z")
        assertEquals("14:32", formatClockTime(instant, ZoneId.of("Asia/Taipei"), Locale.GERMANY))
        // 跨日：UTC 前一天 23:30 在台北已是隔天 07:30
        assertEquals("07:30", formatClockTime(Instant.parse("2026-10-14T23:30:00Z"), ZoneId.of("Asia/Taipei"), Locale.GERMANY))
    }
}
