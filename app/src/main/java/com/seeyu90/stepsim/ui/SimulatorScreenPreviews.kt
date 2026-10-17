package com.seeyu90.stepsim.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepWritePermissionException
import com.seeyu90.stepsim.simulator.StepWriteRateLimitException
import com.seeyu90.stepsim.ui.theme.StepSimTheme
import java.io.IOException
import java.time.Instant

// Preview 專用：只使用無狀態的 SimulatorScreen 與寫死的範例 state，不依賴 ViewModel、Health Connect、Context。

/** 版面壓力變體：一般、深色、字體 1.5／2.0 倍、小螢幕、小螢幕＋2.0 倍。 */
@Preview(name = "一般", showBackground = true, heightDp = 1100)
@Preview(name = "深色", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "字體 1.5 倍", showBackground = true, heightDp = 1100, fontScale = 1.5f)
@Preview(name = "字體 2.0 倍", showBackground = true, heightDp = 1100, fontScale = 2f)
@Preview(name = "小螢幕 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Preview(name = "小螢幕＋字體 2.0 倍", showBackground = true, widthDp = 320, heightDp = 568, fontScale = 2f)
annotation class StepSimPreviews

private val sessionStart: Instant = Instant.parse("2026-10-15T06:05:00Z")

private fun running(gait: Gait, written: Long, target: Long, batches: Int) = SimulationState(
    status = SimulationStatus.RUNNING,
    targetSteps = target,
    gait = gait,
    writtenSteps = written,
    batchCount = batches,
    firstBatchStart = sessionStart,
    lastBatchEnd = sessionStart.plusSeconds(batches * 10L),
)

private object PreviewStates {
    val empty = SimulatorUiState(permissionGranted = true, healthConnectAvailable = true)

    val ready = SimulatorUiState(
        permissionGranted = true,
        healthConnectAvailable = true,
        targetInput = "5000",
        simulation = SimulationState(status = SimulationStatus.READY, targetSteps = 5_000),
    )

    val targetOutOfRange = SimulatorUiState(
        permissionGranted = true,
        healthConnectAvailable = true,
        targetInput = "250000",
        targetError = TargetInputError.OUT_OF_RANGE,
    )

    /** 42% */
    val walking = SimulatorUiState(
        permissionGranted = true,
        healthConnectAvailable = true,
        simulation = running(Gait.WALK, written = 2_100, target = 5_000, batches = 126),
    )

    /** 78% */
    val runningFast = walking.copy(simulation = running(Gait.RUN, written = 3_900, target = 5_000, batches = 146))

    /** 55% */
    val paused = walking.copy(
        simulation = running(Gait.RUN, written = 2_750, target = 5_000, batches = 165).copy(status = SimulationStatus.PAUSED),
    )

    /** 100% */
    val completed = walking.copy(
        simulation = SimulationState(
            status = SimulationStatus.COMPLETED,
            targetSteps = 5_000,
            writtenSteps = 5_000,
            batchCount = 300,
            firstBatchStart = sessionStart,
            lastBatchEnd = sessionStart.plusSeconds(50 * 60),
        ),
    )

    private val failedSimulation = running(Gait.WALK, written = 3_050, target = 5_000, batches = 183).copy(
        status = SimulationStatus.FAILED,
        failedBatchNumber = 184,
        failure = IOException("No space left on device"),
    )

    /** 61% */
    val writeFailed = walking.copy(simulation = failedSimulation)

    val rateLimited = walking.copy(
        simulation = failedSimulation.copy(failure = StepWriteRateLimitException(IllegalStateException("rate limit"))),
    )

    private val revokedSimulation = failedSimulation.copy(
        status = SimulationStatus.PERMISSION_REVOKED,
        failure = StepWritePermissionException(SecurityException("missing WRITE_STEPS")),
    )

    val permissionUnconfirmed = walking.copy(permissionGranted = null, simulation = revokedSimulation)

    val permissionRestored = walking.copy(simulation = revokedSimulation)

    /** 權限確認未授權：主內容被提示取代，仍可結束本次模擬。 */
    val permissionDeniedWhileFailed = walking.copy(permissionGranted = false, simulation = revokedSimulation)

    val permissionDenied = SimulatorUiState(permissionGranted = false, healthConnectAvailable = true)

    val unavailable = SimulatorUiState(permissionGranted = false, healthConnectAvailable = false)

    val backgroundUnavailable = walking.copy(backgroundAvailable = false)

    /** 完成後權限被撤銷：主要內容被提示取代，次要操作仍有「再模擬一次」。 */
    val completedPermissionDenied = completed.copy(permissionGranted = false)

    /** 寫入失敗後重新檢查發現沒有權限：失敗卡片仍在，底部是前往允許。 */
    val failedPermissionDenied = writeFailed.copy(permissionGranted = false)

    val checking = SimulatorUiState()

    /** 壓力測試：目標 100,000、已存入 99,999。 */
    val stress = walking.copy(simulation = running(Gait.WALK, written = 99_999, target = 100_000, batches = 6_000))
}

@Composable
private fun PreviewFrame(state: SimulatorUiState) {
    StepSimTheme {
        Surface {
            SimulatorScreen(state = state, onEvent = {}, onOpenGuide = {})
        }
    }
}

@Preview(name = "01 準備（空）", showBackground = true, heightDp = 1100)
@Composable
private fun EmptyPreview() = PreviewFrame(PreviewStates.empty)

@StepSimPreviews
@Composable
private fun ReadyPreview() = PreviewFrame(PreviewStates.ready)

@Preview(name = "03 目標超出範圍", showBackground = true, heightDp = 1100)
@Composable
private fun TargetOutOfRangePreview() = PreviewFrame(PreviewStates.targetOutOfRange)

@StepSimPreviews
@Composable
private fun WalkingPreview() = PreviewFrame(PreviewStates.walking)

@Preview(name = "05 跑步中（78%）", showBackground = true, heightDp = 1100)
@Composable
private fun RunningPreview() = PreviewFrame(PreviewStates.runningFast)

@Preview(name = "06 暫停（55%）", showBackground = true, heightDp = 1100)
@Composable
private fun PausedPreview() = PreviewFrame(PreviewStates.paused)

@Preview(name = "07 完成（100%）", showBackground = true, heightDp = 1100)
@Composable
private fun CompletedPreview() = PreviewFrame(PreviewStates.completed)

@StepSimPreviews
@Composable
private fun WriteFailedPreview() = PreviewFrame(PreviewStates.writeFailed)

@Preview(name = "09 失敗 · 太頻繁", showBackground = true, heightDp = 1100)
@Composable
private fun RateLimitedPreview() = PreviewFrame(PreviewStates.rateLimited)

@Preview(name = "10 失敗 · 權限未確認", showBackground = true, heightDp = 1100)
@Composable
private fun PermissionUnconfirmedPreview() = PreviewFrame(PreviewStates.permissionUnconfirmed)

@Preview(name = "11 失敗 · 權限已恢復", showBackground = true, heightDp = 1100)
@Composable
private fun PermissionRestoredPreview() = PreviewFrame(PreviewStates.permissionRestored)

@Preview(name = "12 失敗 · 權限確認未授權", showBackground = true, heightDp = 1100)
@Composable
private fun PermissionDeniedWhileFailedPreview() = PreviewFrame(PreviewStates.permissionDeniedWhileFailed)

@Preview(name = "13 尚未允許", showBackground = true, heightDp = 1100)
@Composable
private fun PermissionDeniedPreview() = PreviewFrame(PreviewStates.permissionDenied)

@Preview(name = "14 Health Connect 不可用", showBackground = true, heightDp = 1100)
@Composable
private fun UnavailablePreview() = PreviewFrame(PreviewStates.unavailable)

@Preview(name = "15 背景不可用", showBackground = true, heightDp = 1100)
@Composable
private fun BackgroundUnavailablePreview() = PreviewFrame(PreviewStates.backgroundUnavailable)

@Preview(name = "17 完成 · 權限被撤銷", showBackground = true, heightDp = 1100)
@Composable
private fun CompletedPermissionDeniedPreview() = PreviewFrame(PreviewStates.completedPermissionDenied)

@Preview(name = "18 失敗 · 之後發現沒有權限", showBackground = true, heightDp = 1100)
@Composable
private fun FailedPermissionDeniedPreview() = PreviewFrame(PreviewStates.failedPermissionDenied)

@Preview(name = "16 檢查中", showBackground = true, heightDp = 1100)
@Composable
private fun CheckingPreview() = PreviewFrame(PreviewStates.checking)

@StepSimPreviews
@Composable
private fun StressPreview() = PreviewFrame(PreviewStates.stress)

@StepSimPreviews
@Composable
private fun GuidePreview() = StepSimTheme { Surface { GuideScreen(onBack = {}) } }

@Preview(name = "結束本次模擬確認", showBackground = true)
@Preview(name = "結束本次模擬確認 · 字體 2.0 倍", showBackground = true, fontScale = 2f)
@Composable
private fun EndDialogPreview() = StepSimTheme { EndSimulationDialog(onConfirm = {}, onDismiss = {}) }
