package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationRunner
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
    fun `wake lock is held only while running`() {
        for (status in SimulationStatus.entries) {
            assertEquals("status $status", status == SimulationStatus.RUNNING, shouldHoldWakeLock(status))
        }
    }

    @Test
    fun `wake lock is never held without the foreground service`() {
        for (status in SimulationStatus.entries.filter(::shouldHoldWakeLock)) {
            assertTrue("status $status", shouldRunForeground(status))
        }
    }

    @Test
    fun `wake lock timeout outlasts several tick intervals so renewals can keep it alive`() {
        assertTrue(WAKE_LOCK_TIMEOUT_MILLIS >= 10 * SimulationRunner.TICK_INTERVAL_MILLIS)
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

    @Test
    fun `stop notification comes from the completed snapshot and is dismissible`() {
        val model = stopNotificationModel(state(SimulationStatus.COMPLETED, written = 5_000))!!

        assertEquals(NotificationKind.COMPLETED, model.kind)
        assertEquals(5_000, model.writtenSteps)
        assertFalse(model.ongoing)
    }

    @Test
    fun `stop leaves no notification for any snapshot other than completed`() {
        // 例如完成後立刻重設：快照已是 READY，不可留下常駐的「啟動中」
        for (status in SimulationStatus.entries - SimulationStatus.COMPLETED) {
            assertNull("status $status", stopNotificationModel(state(status)))
        }
        assertNull(stopNotificationModel(state(SimulationStatus.IDLE, written = 0, target = null)))
    }

    @Test
    fun `stop detaches only when the completion notification was actually posted`() {
        val completed = stopNotificationModel(state(SimulationStatus.COMPLETED, written = 5_000))

        assertEquals(StopForegroundMode.DETACH, stopForegroundMode(completed, posted = true))
        // 通知被關閉：完成通知沒發出，不可 DETACH，否則「執行中」的通知會過期遺留
        assertEquals(StopForegroundMode.REMOVE, stopForegroundMode(completed, posted = false))
    }

    @Test
    fun `stop removes the foreground notification when nothing is left behind`() {
        for (status in SimulationStatus.entries - SimulationStatus.COMPLETED) {
            val leftover = stopNotificationModel(state(status))
            for (posted in listOf(true, false)) {
                assertEquals(
                    "status=$status posted=$posted",
                    StopForegroundMode.REMOVE,
                    stopForegroundMode(leftover, posted),
                )
            }
        }
    }

    @Test
    fun `button command is forwarded even when foreground start fails`() {
        for (action in NotificationAction.entries) {
            for (started in listOf(true, false)) {
                assertEquals(
                    "action=$action started=$started",
                    action,
                    startCommandOutcome(action, started, SimulationStatus.RUNNING).command,
                )
            }
        }
        assertNull(startCommandOutcome(null, true, SimulationStatus.RUNNING).command)
    }

    @Test
    fun `service stops when foreground start fails`() {
        for (status in SimulationStatus.entries) {
            assertTrue("status $status", startCommandOutcome(NotificationAction.STOP, false, status).stopSelf)
        }
    }

    @Test
    fun `reconcile starts the service when foreground is needed and the service is not alive`() {
        for (status in SimulationStatus.entries.filter(::shouldRunForeground)) {
            assertEquals("status $status", ServiceAction.START, reconcileForeground(status, serviceAlive = false, failedAt = null))
        }
    }

    @Test
    fun `reconcile does not start twice while the service is alive`() {
        for (status in SimulationStatus.entries.filter(::shouldRunForeground)) {
            assertEquals("status $status", ServiceAction.NONE, reconcileForeground(status, serviceAlive = true, failedAt = null))
        }
    }

    @Test
    fun `reconcile stops the service when foreground is no longer needed`() {
        for (status in SimulationStatus.entries.filterNot(::shouldRunForeground)) {
            assertEquals("status $status", ServiceAction.STOP, reconcileForeground(status, serviceAlive = true, failedAt = null))
            assertEquals("status $status", ServiceAction.NONE, reconcileForeground(status, serviceAlive = false, failedAt = null))
        }
    }

    @Test
    fun `failed start is not retried in the same status kind`() {
        assertEquals(
            ServiceAction.NONE,
            reconcileForeground(SimulationStatus.RUNNING, serviceAlive = false, failedAt = SimulationStatus.RUNNING),
        )
    }

    @Test
    fun `failed start is retried when the status kind changes`() {
        // 失敗於 RUNNING → 暫停；失敗於 PAUSED → 繼續；失敗於 RUNNING → 寫入失敗
        val cases = listOf(
            SimulationStatus.RUNNING to SimulationStatus.PAUSED,
            SimulationStatus.PAUSED to SimulationStatus.RUNNING,
            SimulationStatus.RUNNING to SimulationStatus.FAILED,
            SimulationStatus.FAILED to SimulationStatus.RUNNING,
        )
        for ((failedAt, now) in cases) {
            val step = appReconcile(now, serviceAlive = false, failedAt = failedAt)
            assertTrue("failedAt=$failedAt now=$now", step.start)
        }
    }

    @Test
    fun `failure record is kept while foreground is needed and cleared when it is not`() {
        for (status in SimulationStatus.entries) {
            val expected = if (shouldRunForeground(status)) SimulationStatus.RUNNING else null
            assertEquals("status $status", expected, nextFailedAt(status, SimulationStatus.RUNNING))
        }
    }

    @Test
    fun `new run after a failed start tries to start again`() {
        // 開始時啟動失敗（failedAt = RUNNING）→ 結束（READY）→ 再開始（RUNNING）：第二次 RUNNING 要再嘗試
        var failedAt: SimulationStatus? = SimulationStatus.RUNNING

        val ended = appReconcile(SimulationStatus.READY, serviceAlive = false, failedAt = failedAt)
        assertFalse(ended.start)
        assertNull("leaving foreground clears the failure", ended.failedAt)
        failedAt = ended.failedAt

        val restarted = appReconcile(SimulationStatus.RUNNING, serviceAlive = false, failedAt = failedAt)
        assertTrue(restarted.start)
    }

    @Test
    fun `service keeps running only when started and foreground is needed`() {
        for (status in SimulationStatus.entries) {
            assertEquals(
                "status $status",
                !shouldRunForeground(status),
                startCommandOutcome(null, true, status).stopSelf,
            )
        }
    }
}
