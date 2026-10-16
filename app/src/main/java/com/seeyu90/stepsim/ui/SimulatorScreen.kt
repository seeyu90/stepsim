package com.seeyu90.stepsim.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepSimulator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 接 ViewModel 的外層：負責權限檢查、離開前景暫停、執行中保持螢幕常亮。 */
@Composable
fun SimulatorRoute(
    onOpenPermission: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SimulatorViewModel = viewModel(factory = SimulatorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 進入畫面（含從授權頁返回）與回到前景時重新檢查權限
    LaunchedEffect(Unit) { viewModel.refreshPermission() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onAppStopped(context.findActivity()?.isChangingConfigurations == true)
    }

    val running = state.status == SimulationStatus.RUNNING
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    SimulatorScreen(
        state = state,
        onEvent = { event ->
            if (event == SimulatorEvent.OpenPermission) onOpenPermission() else viewModel.onEvent(event)
        },
        modifier = modifier,
    )
}

/** 無狀態畫面：只依 [state] 繪製，所有操作透過 [onEvent] 回傳。Preview 只使用這一層。 */
@Composable
fun SimulatorScreen(
    state: SimulatorUiState,
    onEvent: (SimulatorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.sim_title), style = MaterialTheme.typography.headlineSmall)

        // 放在權限檢查之前：Health Connect 不可用時，失敗原因仍要顯示
        FailureCard(state)

        val openPermission = { onEvent(SimulatorEvent.OpenPermission) }
        when (state.accessNotice) {
            AccessNotice.CHECKING -> Text(stringResource(R.string.sim_checking_permission))
            AccessNotice.HEALTH_CONNECT_UNAVAILABLE -> AccessRequired(
                title = R.string.sim_unavailable_title,
                body = R.string.sim_unavailable_body,
                onOpenPermission = openPermission,
            )
            AccessNotice.PERMISSION_REQUIRED -> AccessRequired(
                title = R.string.sim_permission_title,
                body = R.string.sim_permission_body,
                onOpenPermission = openPermission,
            )
            AccessNotice.NONE -> Unit
        }
        if (state.showStandaloneReset) {
            OutlinedButton(onClick = { onEvent(SimulatorEvent.Reset) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sim_action_reset))
            }
        }
        if (!state.showMainContent) return@Column

        StatusLabel(state)
        TargetSection(state, onEvent)
        GaitSection(state, onEvent)
        ProgressSection(state)
        Actions(state, onEvent)
    }
}

@Composable
private fun AccessRequired(@StringRes title: Int, @StringRes body: Int, onOpenPermission: () -> Unit) {
    WarningCard(
        title = stringResource(title),
        body = stringResource(body),
    )
    Button(onClick = onOpenPermission, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sim_action_open_permission))
    }
}

@Composable
private fun StatusLabel(state: SimulatorUiState) {
    val cadence = state.simulation.gait.stepsPerMinute
    val text = when (state.status) {
        SimulationStatus.RUNNING -> stringResource(
            if (state.simulation.gait == Gait.WALK) R.string.sim_status_walking else R.string.sim_status_running,
            cadence,
        ).let { "● $it" }
        SimulationStatus.PAUSED -> "❚❚ " + stringResource(R.string.sim_status_paused)
        SimulationStatus.COMPLETED -> "✓ " + stringResource(R.string.sim_status_completed)
        else -> return
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun FailureCard(state: SimulatorUiState) {
    val failure = state.simulation.failure
    val message = failureMessageFor(
        status = state.status,
        failure = failure,
        permissionGranted = state.permissionGranted,
        healthConnectAvailable = state.healthConnectAvailable,
    ) ?: return
    WarningCard(
        title = stringResource(R.string.sim_failed_title, state.simulation.failedBatchNumber ?: 0),
        body = stringResource(message.stringRes()) + "\n\n" +
            stringResource(R.string.sim_failed_retry_note),
        detail = failure?.let { stringResource(R.string.sim_failed_detail, technicalDetail(it)) },
    )
}

@Composable
private fun WarningCard(title: String, body: String, detail: String? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠ $title", style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TargetSection(state: SimulatorUiState, onEvent: (SimulatorEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sim_target_label), style = MaterialTheme.typography.titleSmall)
        val value = if (state.canEditTarget) state.targetInput else state.displayTarget?.toString().orEmpty()
        OutlinedTextField(
            value = value,
            onValueChange = { onEvent(SimulatorEvent.TargetInputChanged(it)) },
            enabled = state.canEditTarget,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.sim_target_placeholder)) },
            suffix = { Text(stringResource(R.string.sim_steps_suffix)) },
            isError = state.targetError != null,
            supportingText = state.targetError?.let {
                { Text(stringResource(R.string.sim_target_out_of_range, formatSteps(StepSimulator.MAX_TARGET_STEPS))) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.canEditTarget) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (steps in QUICK_TARGETS) {
                    FilterChip(
                        selected = state.targetInput == steps.toString(),
                        onClick = { onEvent(SimulatorEvent.QuickTargetSelected(steps)) },
                        label = { Text(formatSteps(steps)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GaitSection(state: SimulatorUiState, onEvent: (SimulatorEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sim_gait_label), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Gait.entries.forEachIndexed { index, gait ->
                SegmentedButton(
                    selected = state.simulation.gait == gait,
                    onClick = { onEvent(SimulatorEvent.GaitSelected(gait)) },
                    enabled = state.canEditGait,
                    shape = SegmentedButtonDefaults.itemShape(index, Gait.entries.size),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (gait == Gait.WALK) R.string.sim_gait_walk else R.string.sim_gait_run))
                        Text(
                            stringResource(R.string.sim_cadence, gait.stepsPerMinute),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(state: SimulatorUiState) {
    val simulation = state.simulation
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.sim_progress_label) + "  " + stringResource(
                    R.string.sim_progress_steps,
                    formatSteps(simulation.writtenSteps),
                    state.displayTarget?.let(::formatSteps) ?: "—",
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.sim_progress_percent, (state.progress * 100).toInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

        val first = simulation.firstBatchStart
        val last = simulation.lastBatchEnd
        when {
            state.status == SimulationStatus.COMPLETED && first != null && last != null ->
                InfoText(stringResource(R.string.sim_batches_range, simulation.batchCount, formatTime(first), formatTime(last)))
            last != null ->
                InfoText(stringResource(R.string.sim_batches_last, simulation.batchCount, formatTime(last)))
        }

        val minutes = state.remainingMinutes
        when {
            state.canEditTarget && minutes == null -> InfoText(stringResource(R.string.sim_need_target))
            state.canEditTarget && minutes != null -> InfoText(stringResource(R.string.sim_estimate_total, minutes))
            state.status == SimulationStatus.RUNNING && minutes != null ->
                InfoText(stringResource(R.string.sim_estimate_remaining, minutes))
        }
        when (state.status) {
            SimulationStatus.RUNNING -> InfoText("ⓘ " + stringResource(R.string.sim_foreground_hint))
            SimulationStatus.PAUSED -> InfoText(stringResource(R.string.sim_paused_hint))
            else -> Unit
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Actions(state: SimulatorUiState, onEvent: (SimulatorEvent) -> Unit) {
    when (state.status) {
        SimulationStatus.IDLE, SimulationStatus.READY ->
            PrimaryButton(R.string.sim_action_start, enabled = state.canStart) { onEvent(SimulatorEvent.Start) }
        SimulationStatus.RUNNING ->
            PrimaryButton(R.string.sim_action_pause) { onEvent(SimulatorEvent.Pause) }
        SimulationStatus.PAUSED ->
            ButtonPair(R.string.sim_action_resume, SimulatorEvent.Resume, onEvent)
        SimulationStatus.COMPLETED ->
            PrimaryButton(R.string.sim_action_new) { onEvent(SimulatorEvent.Reset) }
        SimulationStatus.FAILED, SimulationStatus.PERMISSION_REVOKED ->
            ButtonPair(R.string.sim_action_retry, SimulatorEvent.Retry, onEvent)
    }
}

@Composable
private fun PrimaryButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label))
    }
}

/** 主要操作加上「結束並重設」。 */
@Composable
private fun ButtonPair(@StringRes primary: Int, primaryEvent: SimulatorEvent, onEvent: (SimulatorEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEvent(primaryEvent) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(primary))
        }
        OutlinedButton(onClick = { onEvent(SimulatorEvent.Reset) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.sim_action_reset))
        }
    }
}

/** 主訊息用人話；技術細節另外以次要文字顯示。對應邏輯見 [failureMessageFor]。 */
@StringRes
private fun FailureMessage.stringRes(): Int = when (this) {
    FailureMessage.PERMISSION_RESTORED -> R.string.sim_failed_permission_restored
    FailureMessage.PERMISSION_UNCONFIRMED -> R.string.sim_failed_permission_unconfirmed
    FailureMessage.HEALTH_CONNECT_UNAVAILABLE -> R.string.sim_failed_unavailable
    FailureMessage.RATE_LIMITED -> R.string.sim_failed_rate_limited
    FailureMessage.IO -> R.string.sim_failed_io
    FailureMessage.REMOTE -> R.string.sim_failed_remote
    FailureMessage.GENERIC -> R.string.sim_failed_generic
}

private fun technicalDetail(failure: Throwable): String {
    val root = failure.cause ?: failure
    return listOfNotNull(root::class.simpleName, root.message).joinToString(": ")
}

private val QUICK_TARGETS = listOf(1_000L, 5_000L, 10_000L)

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatSteps(steps: Long): String = "%,d".format(steps)

private fun formatTime(instant: Instant): String = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
