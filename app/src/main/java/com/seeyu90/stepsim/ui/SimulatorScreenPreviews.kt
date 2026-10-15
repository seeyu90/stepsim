package com.seeyu90.stepsim.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.ui.theme.StepSimTheme
import java.io.IOException
import java.time.Instant

// Preview 專用：只使用無狀態的 SimulatorScreen 與寫死的範例 state，不依賴 ViewModel、Health Connect、Context。

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
    val initial = SimulatorUiState(permissionGranted = true)

    val ready = SimulatorUiState(
        permissionGranted = true,
        targetInput = "5000",
        simulation = SimulationState(status = SimulationStatus.READY, targetSteps = 5_000),
    )

    val targetOutOfRange = SimulatorUiState(
        permissionGranted = true,
        targetInput = "250000",
        targetError = TargetInputError.OUT_OF_RANGE,
    )

    /** 42% */
    val walking = SimulatorUiState(
        permissionGranted = true,
        simulation = running(Gait.WALK, written = 2_100, target = 5_000, batches = 126),
    )

    /** 78% */
    val running = SimulatorUiState(
        permissionGranted = true,
        simulation = running(Gait.RUN, written = 3_900, target = 5_000, batches = 146),
    )

    /** 55% */
    val paused = SimulatorUiState(
        permissionGranted = true,
        simulation = running(Gait.RUN, written = 2_750, target = 5_000, batches = 165)
            .copy(status = SimulationStatus.PAUSED),
    )

    /** 100% */
    val completed = SimulatorUiState(
        permissionGranted = true,
        simulation = SimulationState(
            status = SimulationStatus.COMPLETED,
            targetSteps = 5_000,
            writtenSteps = 5_000,
            batchCount = 300,
            firstBatchStart = sessionStart,
            lastBatchEnd = sessionStart.plusSeconds(50 * 60),
        ),
    )

    /** 61% */
    val writeFailed = SimulatorUiState(
        permissionGranted = true,
        simulation = running(Gait.WALK, written = 3_050, target = 5_000, batches = 183).copy(
            status = SimulationStatus.FAILED,
            failedBatchNumber = 184,
            failure = IOException("No space left on device"),
        ),
    )

    val permissionDenied = SimulatorUiState(permissionGranted = false)
}

@Composable
private fun PreviewFrame(state: SimulatorUiState) {
    StepSimTheme {
        Surface {
            SimulatorScreen(state = state, onEvent = {})
        }
    }
}

@Preview(name = "1. 初始（尚未設定）", showBackground = true)
@Composable
private fun InitialPreview() = PreviewFrame(PreviewStates.initial)

@Preview(name = "2. 設定完成待開始（0%）", showBackground = true)
@Composable
private fun ReadyPreview() = PreviewFrame(PreviewStates.ready)

@Preview(name = "3. 目標超出範圍", showBackground = true)
@Composable
private fun TargetOutOfRangePreview() = PreviewFrame(PreviewStates.targetOutOfRange)

@Preview(name = "4. 走路中（42%）", showBackground = true)
@Composable
private fun WalkingPreview() = PreviewFrame(PreviewStates.walking)

@Preview(name = "5. 跑步中（78%）", showBackground = true)
@Composable
private fun RunningPreview() = PreviewFrame(PreviewStates.running)

@Preview(name = "6. 暫停（55%）", showBackground = true)
@Composable
private fun PausedPreview() = PreviewFrame(PreviewStates.paused)

@Preview(name = "7. 已完成（100%）", showBackground = true)
@Composable
private fun CompletedPreview() = PreviewFrame(PreviewStates.completed)

@Preview(name = "8. 寫入失敗（61%）", showBackground = true)
@Composable
private fun WriteFailedPreview() = PreviewFrame(PreviewStates.writeFailed)

@Preview(name = "9. 權限未授權", showBackground = true)
@Composable
private fun PermissionDeniedPreview() = PreviewFrame(PreviewStates.permissionDenied)

// 以下以最複雜的「寫入失敗」狀態檢查版面

@Preview(name = "寫入失敗 · 深色模式", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WriteFailedDarkPreview() = PreviewFrame(PreviewStates.writeFailed)

@Preview(name = "寫入失敗 · 字體 1.5 倍", showBackground = true, fontScale = 1.5f)
@Composable
private fun WriteFailedLargeFontPreview() = PreviewFrame(PreviewStates.writeFailed)

@Preview(name = "寫入失敗 · 小螢幕", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun WriteFailedSmallScreenPreview() = PreviewFrame(PreviewStates.writeFailed)
