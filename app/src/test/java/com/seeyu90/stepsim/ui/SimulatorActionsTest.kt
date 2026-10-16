package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepWritePermissionException
import com.seeyu90.stepsim.simulator.StepWriteRateLimitException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 底部主按鈕、次要操作與其他顯示用衍生屬性；失敗時的對照表見 docs/phase-9.5/decisions.md。 */
class SimulatorActionsTest {

    private fun ui(
        status: SimulationStatus,
        granted: Boolean? = true,
        available: Boolean? = true,
        failure: Throwable? = null,
        written: Long = 2_100,
        target: Long? = 5_000,
        input: String = "",
        checkFailed: Boolean = false,
    ) = SimulatorUiState(
        permissionGranted = granted,
        healthConnectAvailable = available,
        permissionCheckFailed = checkFailed,
        targetInput = input,
        simulation = SimulationState(
            status = status,
            targetSteps = target,
            gait = Gait.WALK,
            writtenSteps = written,
            failure = failure,
        ),
    )

    // ---- 失敗時的按鈕對照表 ----

    @Test
    fun `generic error retries and can end`() {
        val state = ui(SimulationStatus.FAILED, failure = IOException("disk"))
        assertEquals(BottomAction.RETRY, state.bottomAction)
        assertEquals(listOf(SecondaryAction.END), state.secondaryActions)
    }

    @Test
    fun `rate limited retries and can end`() {
        val state = ui(SimulationStatus.FAILED, failure = StepWriteRateLimitException())
        assertEquals(BottomAction.RETRY, state.bottomAction)
        assertEquals(listOf(SecondaryAction.END), state.secondaryActions)
    }

    @Test
    fun `permission unconfirmed goes to allow but keeps retry`() {
        val state = ui(SimulationStatus.PERMISSION_REVOKED, granted = null, failure = StepWritePermissionException())
        assertEquals(BottomAction.GO_ALLOW, state.bottomAction)
        assertEquals(listOf(SecondaryAction.RETRY, SecondaryAction.END), state.secondaryActions)
    }

    @Test
    fun `permission restored retries`() {
        val state = ui(SimulationStatus.PERMISSION_REVOKED, granted = true, failure = StepWritePermissionException())
        assertEquals(BottomAction.RETRY, state.bottomAction)
        assertEquals(listOf(SecondaryAction.END), state.secondaryActions)
    }

    @Test
    fun `permission confirmed denied goes to allow`() {
        val state = ui(SimulationStatus.PERMISSION_REVOKED, granted = false, failure = StepWritePermissionException())
        assertEquals(BottomAction.GO_ALLOW, state.bottomAction)
        assertEquals(listOf(SecondaryAction.END), state.secondaryActions)
    }

    @Test
    fun `health connect unavailable goes to check`() {
        // 不可用時 ViewModel 會把 granted 設為 false
        val state = ui(SimulationStatus.FAILED, granted = false, available = false, failure = IOException())
        assertEquals(BottomAction.GO_CHECK, state.bottomAction)
        assertEquals(listOf(SecondaryAction.END), state.secondaryActions)
    }

    @Test
    fun `every state has an exit and unknown permission always keeps retry when failed`() {
        val values = listOf(null, true, false)
        for (status in SimulationStatus.entries) {
            for (granted in values) for (available in values) for (checkFailed in listOf(false, true)) {
                val state = ui(status, granted = granted, available = available, failure = IOException(), checkFailed = checkFailed)
                val label = "status=$status granted=$granted available=$available checkFailed=$checkFailed"
                // 只有「檢查尚未結束」可以沒有主按鈕
                if (state.accessNotice != AccessNotice.CHECKING) assertNotNull(label, state.bottomAction)
                if (state.accessNotice == AccessNotice.CHECK_FAILED) assertEquals(label, BottomAction.RECHECK, state.bottomAction)
                if (state.hasStarted) {
                    assertTrue(label, SecondaryAction.END in state.secondaryActions)
                    assertTrue(label, state.showMainContent || state.showStandaloneReset)
                }
                if (state.isFailed && granted == null) {
                    assertTrue(
                        label,
                        state.bottomAction == BottomAction.RETRY || SecondaryAction.RETRY in state.secondaryActions,
                    )
                }
            }
        }
    }

    // ---- 權限檢查失敗與提示狀態下的出口 ----

    @Test
    fun `failed permission check offers recheck instead of an endless checking state`() {
        val failed = ui(SimulationStatus.READY, granted = null, available = null, input = "5000", checkFailed = true)
        assertEquals(AccessNotice.CHECK_FAILED, failed.accessNotice)
        assertEquals(BottomAction.RECHECK, failed.bottomAction)

        val checking = failed.copy(permissionCheckFailed = false)
        assertEquals("checking only means the check has not finished", AccessNotice.CHECKING, checking.accessNotice)
        assertNull(checking.bottomAction)
    }

    @Test
    fun `paused simulation can be ended while a notice replaces the main content`() {
        val notices = listOf(
            ui(SimulationStatus.PAUSED, granted = false),
            ui(SimulationStatus.PAUSED, granted = false, available = false),
            ui(SimulationStatus.PAUSED, granted = null),
            ui(SimulationStatus.PAUSED, granted = null, checkFailed = true),
        )
        for (state in notices) {
            val label = "notice=${state.accessNotice}"
            assertFalse(label, state.showMainContent)
            assertTrue(label, state.showStandaloneReset)
            assertTrue(label, SecondaryAction.END in state.secondaryActions)
        }
    }

    @Test
    fun `running simulation can be ended while permission is revoked`() {
        val state = ui(SimulationStatus.RUNNING, granted = false)
        assertEquals(BottomAction.GO_ALLOW, state.bottomAction)
        assertTrue(state.showStandaloneReset)
        assertTrue(SecondaryAction.END in state.secondaryActions)
    }

    // ---- 一般狀態 ----

    @Test
    fun `normal states map to one primary action`() {
        assertEquals(BottomAction.START, ui(SimulationStatus.READY, written = 0, input = "5000").bottomAction)
        assertEquals(BottomAction.PAUSE, ui(SimulationStatus.RUNNING).bottomAction)
        assertEquals(BottomAction.RESUME, ui(SimulationStatus.PAUSED).bottomAction)
        assertEquals(BottomAction.START_NEW, ui(SimulationStatus.COMPLETED, written = 5_000).bottomAction)
    }

    @Test
    fun `end is offered only once the simulation has started`() {
        assertEquals(listOf(SecondaryAction.END), ui(SimulationStatus.RUNNING).secondaryActions)
        assertEquals(listOf(SecondaryAction.END), ui(SimulationStatus.PAUSED).secondaryActions)
        assertTrue(ui(SimulationStatus.READY, written = 0, input = "5000").secondaryActions.isEmpty())
        assertTrue(ui(SimulationStatus.COMPLETED, written = 5_000).secondaryActions.isEmpty())
    }

    @Test
    fun `access notices replace the primary action`() {
        assertEquals(BottomAction.GO_ALLOW, ui(SimulationStatus.READY, granted = false, input = "5000").bottomAction)
        assertEquals(
            BottomAction.GO_CHECK,
            ui(SimulationStatus.READY, granted = false, available = false, input = "5000").bottomAction,
        )
        assertNull("checking", ui(SimulationStatus.READY, granted = null, input = "5000").bottomAction)
    }

    @Test
    fun `start hint asks for a goal when the input is empty or invalid`() {
        assertEquals(StartHint.NEED_GOAL, ui(SimulationStatus.IDLE, written = 0, target = null).startHint)
        assertEquals(StartHint.NEED_GOAL, ui(SimulationStatus.READY, written = 0, input = "0").startHint)
        assertNull(ui(SimulationStatus.READY, written = 0, input = "5000").startHint)
    }

    // ---- 其他顯示用屬性 ----

    @Test
    fun `progress percent rounds down and never shows 100 before the goal`() {
        assertEquals(0, ui(SimulationStatus.RUNNING, written = 0).progressPercent)
        assertEquals(42, ui(SimulationStatus.RUNNING, written = 2_100).progressPercent)
        assertEquals(99, ui(SimulationStatus.RUNNING, written = 99_999, target = 100_000).progressPercent)
        assertEquals(100, ui(SimulationStatus.COMPLETED, written = 5_000).progressPercent)
        assertEquals(0, ui(SimulationStatus.IDLE, written = 0, target = null).progressPercent)
    }

    @Test
    fun `remaining time is shown only while running`() {
        for (status in SimulationStatus.entries) {
            assertEquals("status $status", status == SimulationStatus.RUNNING, ui(status, input = "5000").showRemaining)
        }
    }

    @Test
    fun `ready shows target and gait, paused shows gait only`() {
        val ready = ui(SimulationStatus.READY, written = 0, input = "5000")
        assertTrue(ready.showTargetInput)
        assertTrue(ready.showGaitSelector)
        assertTrue(ready.compactRing)

        val paused = ui(SimulationStatus.PAUSED)
        assertFalse(paused.showTargetInput)
        assertTrue(paused.showGaitSelector)
        assertFalse(paused.compactRing)

        val running = ui(SimulationStatus.RUNNING)
        assertFalse(running.showTargetInput)
        assertFalse(running.showGaitSelector)
    }
}
