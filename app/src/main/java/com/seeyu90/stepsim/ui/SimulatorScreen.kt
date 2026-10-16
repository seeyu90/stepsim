package com.seeyu90.stepsim.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.ui.components.ErrorCard
import com.seeyu90.stepsim.ui.components.ExpandableDetails
import com.seeyu90.stepsim.ui.components.IconTextRow
import com.seeyu90.stepsim.ui.components.InfoCard
import com.seeyu90.stepsim.ui.components.NoticeCard
import com.seeyu90.stepsim.ui.components.PrimaryButton
import com.seeyu90.stepsim.ui.components.SecondaryTextButton
import com.seeyu90.stepsim.ui.components.SimulatedDataBadge
import com.seeyu90.stepsim.ui.components.StepRing
import com.seeyu90.stepsim.ui.components.StepRingSize
import java.time.ZoneId
import java.util.Locale

/** 接 ViewModel 的外層：負責權限檢查，以及開始前請求通知權限（Android 13+）。 */
@Composable
fun SimulatorRoute(
    onOpenPermission: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SimulatorViewModel = viewModel(factory = SimulatorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 進入畫面（含從授權頁返回）與回到前景時重新檢查權限
    LaunchedEffect(Unit) { viewModel.refreshPermission() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }
    // 前景服務無法啟動時，離開畫面就暫停（沒有前景服務保護，繼續跑只會被凍結或終止）；旋轉等設定變更不算離開
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onAppStopped(context.findActivity()?.isChangingConfigurations == true)
    }

    // 不論允許或拒絕都開始模擬：拒絕時前景服務照常執行，通知只出現在工作管理員
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onEvent(SimulatorEvent.Start)
    }

    SimulatorScreen(
        state = state,
        onEvent = { event ->
            when {
                event == SimulatorEvent.OpenPermission -> onOpenPermission()
                event == SimulatorEvent.Start && needsNotificationPermission(context) ->
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> viewModel.onEvent(event)
            }
        },
        onOpenGuide = onOpenGuide,
        modifier = modifier,
    )
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/**
 * 無狀態畫面：只依 [state] 繪製，所有操作透過 [onEvent] 回傳。Preview 只使用這一層。
 *
 * 版面：可捲動的內容在上，主要動作固定在底部（捲動區之外）。鍵盤打開時底部按鈕跟著上移；
 * 系統導覽列的 insets 已由外層 Scaffold 處理，這裡只補鍵盤高到導覽列以上的部分。
 */
@Composable
fun SimulatorScreen(
    state: SimulatorUiState,
    onEvent: (SimulatorEvent) -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    // 讀 LocalConfiguration 才會在使用者切換語言時更新（不要用 Locale.getDefault()）
    val locale: Locale = LocalConfiguration.current.locales[0]

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()
            when {
                state.showMainContent -> MainContent(state, locale, onEvent)
                else -> AccessContent(state)
            }
            SecondaryActions(state, onEvent, onEnd = { confirmEnd = true })
            Tips(state)
            Disclaimer(onOpenGuide)
            if (state.showMainContent) Details(state, locale)
        }
        BottomBar(state, onEvent)
    }

    if (confirmEnd) {
        EndSimulationDialog(
            onConfirm = {
                confirmEnd = false
                onEvent(SimulatorEvent.Reset)
            },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun Header() {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        SimulatedDataBadge(stringResource(R.string.chip_simulated))
    }
}

/** 主要內容被存取提示取代時（檢查中、檢查失敗、Health Connect 不可用、尚未允許）。 */
@Composable
private fun AccessContent(state: SimulatorUiState) {
    when (state.accessNotice) {
        AccessNotice.CHECKING -> Text(
            stringResource(R.string.checking),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccessNotice.CHECK_FAILED -> NoticeCard(stringResource(R.string.perm_check_failed))
        AccessNotice.HEALTH_CONNECT_UNAVAILABLE -> NoticeCard(stringResource(R.string.err_unavailable))
        AccessNotice.PERMISSION_REQUIRED -> NoticeCard(stringResource(R.string.cta_hint_need_permission))
        AccessNotice.NONE -> Unit
    }
}

@Composable
private fun MainContent(state: SimulatorUiState, locale: Locale, onEvent: (SimulatorEvent) -> Unit) {
    Ring(state, locale)
    Status(state, locale)
    if (state.showBackgroundUnavailable) NoticeCard(stringResource(R.string.bg_unavailable))
    if (state.showTargetInput) {
        SettingsCard(state, locale, onEvent)
    } else if (state.showGaitSelector) {
        InfoCard { GaitSelector(state, onEvent) }
    }
}

@Composable
private fun Ring(state: SimulatorUiState, locale: Locale) {
    val written = formatSteps(state.simulation.writtenSteps, locale)
    val target = state.displayTarget
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StepRing(
            progress = state.progress,
            value = written,
            caption = stringResource(R.string.unit_steps),
            contentDescription = if (target != null) {
                stringResource(R.string.ring_a11y, state.progressPercent, written, formatSteps(target, locale))
            } else {
                stringResource(R.string.ring_a11y_no_target, written)
            },
            size = if (state.compactRing) StepRingSize.COMPACT else StepRingSize.FULL,
            failed = state.isFailed,
        )
        Text(
            text = if (target != null) {
                stringResource(R.string.ring_goal, formatSteps(target, locale)) + " · " + gaitName(state.simulation.gait)
            } else {
                stringResource(R.string.hero_empty)
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** 狀態說明：第一行（含圖示）在狀態改變時才變動，以 liveRegion 通知 TalkBack；第二行是細節。 */
@Composable
private fun Status(state: SimulatorUiState, locale: Locale) {
    val simulation = state.simulation
    when (simulation.status) {
        SimulationStatus.IDLE, SimulationStatus.READY -> state.remainingMinutes?.let { minutes ->
            Text(
                stringResource(R.string.estimate, durationText(RemainingTime.of(minutes))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SimulationStatus.RUNNING -> {
            val walking = simulation.gait == Gait.WALK
            val rate = stringResource(R.string.status_rate, simulation.gait.stepsPerMinute)
            val minutes = state.remainingMinutes
            StatusBlock(
                icon = if (walking) R.drawable.ic_walk else R.drawable.ic_run,
                title = stringResource(if (walking) R.string.status_walking else R.string.status_running),
                detail = if (state.showRemaining && minutes != null) "$rate · ${remainingText(minutes)}" else rate,
            )
        }
        SimulationStatus.PAUSED -> StatusBlock(
            icon = R.drawable.ic_pause,
            title = stringResource(R.string.status_paused),
            detail = stringResource(R.string.paused_hint),
        )
        SimulationStatus.COMPLETED -> StatusBlock(
            icon = R.drawable.ic_check,
            title = stringResource(R.string.status_done),
            detail = stringResource(R.string.done_title, formatSteps(simulation.writtenSteps, locale)) + "\n" +
                stringResource(R.string.done_body),
        )
        SimulationStatus.FAILED, SimulationStatus.PERMISSION_REVOKED -> FailureCard(state)
    }
}

@Composable
private fun StatusBlock(@DrawableRes icon: Int, title: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 失敗卡片：一句白話＋可展開的詳細資訊；不放按鈕（主要動作在底部）。 */
@Composable
private fun FailureCard(state: SimulatorUiState) {
    val failure = state.simulation.failure
    val message = failureMessageFor(
        status = state.status,
        failure = failure,
        permissionGranted = state.permissionGranted,
        healthConnectAvailable = state.healthConnectAvailable,
    ) ?: return
    ErrorCard(
        label = stringResource(R.string.status_failed),
        title = stringResource(R.string.err_generic_title),
        body = stringResource(message.stringRes()),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val batch = state.simulation.failedBatchNumber
        if (batch != null || failure != null) {
            ExpandableDetails(stringResource(R.string.err_details_label)) {
                if (batch != null) Text(stringResource(R.string.err_details_batch, batch), style = MaterialTheme.typography.bodyMedium)
                if (failure != null) Text(technicalDetail(failure), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsCard(state: SimulatorUiState, locale: Locale, onEvent: (SimulatorEvent) -> Unit) {
    InfoCard {
        OutlinedTextField(
            value = state.targetInput,
            onValueChange = { onEvent(SimulatorEvent.TargetInputChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.goal_label)) },
            suffix = { Text(stringResource(R.string.unit_steps)) },
            isError = state.targetError != null,
            supportingText = {
                Text(stringResource(if (state.targetError != null) R.string.goal_error else R.string.goal_hint))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (steps in QUICK_TARGETS) {
                val selected = state.targetInput == steps.toString()
                FilterChip(
                    selected = selected,
                    onClick = { onEvent(SimulatorEvent.QuickTargetSelected(steps)) },
                    label = { Text(formatSteps(steps, locale), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GaitSelector(state, onEvent)
    }
}

/** 速度：兩張垂直排列的選項卡（≥56dp），長標籤在大字體下也不會爆版。 */
@Composable
private fun GaitSelector(state: SimulatorUiState, onEvent: (SimulatorEvent) -> Unit) {
    Text(stringResource(R.string.speed_label), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (gait in Gait.entries) {
            val selected = state.simulation.gait == gait
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = selected,
                        enabled = state.canEditGait,
                        role = Role.RadioButton,
                        onClick = { onEvent(SimulatorEvent.GaitSelected(gait)) },
                    ),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Icon(
                        painterResource(if (gait == Gait.WALK) R.drawable.ic_walk else R.drawable.ic_run),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        stringResource(if (gait == Gait.WALK) R.string.speed_walk else R.string.speed_run),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryActions(state: SimulatorUiState, onEvent: (SimulatorEvent) -> Unit, onEnd: () -> Unit) {
    val actions = state.secondaryActions
    if (actions.isEmpty()) return
    Column {
        for (action in actions) {
            when (action) {
                SecondaryAction.RETRY -> SecondaryTextButton(stringResource(R.string.btn_retry), onClick = { onEvent(SimulatorEvent.Retry) })
                SecondaryAction.END -> SecondaryTextButton(stringResource(R.string.cta_end), onClick = onEnd)
            }
        }
    }
}

@Composable
private fun Tips(state: SimulatorUiState) {
    InfoCard(title = stringResource(R.string.tips_title)) {
        IconTextRow(stringResource(R.string.tip_realtime))
        IconTextRow(stringResource(R.string.tip_leave))
        // 前景服務無法啟動時「會在背景繼續」不成立，改由 bg_unavailable 提醒
        if (state.backgroundAvailable) IconTextRow(stringResource(R.string.tip_background))
    }
}

@Composable
private fun Disclaimer(onOpenGuide: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.disclaimer_short),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.disclaimer_use),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryTextButton(stringResource(R.string.disclaimer_link), onClick = onOpenGuide)
    }
}

/** 詳細資訊（預設收合）：已存入批次與時間；只用畫面上的狀態，不持久化。 */
@Composable
private fun Details(state: SimulatorUiState, locale: Locale) {
    val simulation = state.simulation
    if (simulation.batchCount == 0) return
    val zone = ZoneId.systemDefault()
    InfoCard {
        ExpandableDetails(stringResource(R.string.err_details_label)) {
            Text(stringResource(R.string.detail_batches, simulation.batchCount), style = MaterialTheme.typography.bodyMedium)
            val first = simulation.firstBatchStart
            val last = simulation.lastBatchEnd
            when {
                simulation.status == SimulationStatus.COMPLETED && first != null && last != null -> Text(
                    stringResource(
                        R.string.detail_range,
                        formatClockTime(first, zone, locale),
                        formatClockTime(last, zone, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                last != null -> Text(
                    stringResource(R.string.detail_last, formatClockTime(last, zone, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 底部固定的主要動作（捲動區之外）；沒有主要動作時（例如權限檢查中）整列不顯示。 */
@Composable
private fun BottomBar(state: SimulatorUiState, onEvent: (SimulatorEvent) -> Unit) {
    val action = state.bottomAction ?: return
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (label, icon, event) = when (action) {
                BottomAction.START -> Triple(R.string.cta_start, R.drawable.ic_play, SimulatorEvent.Start)
                BottomAction.PAUSE -> Triple(R.string.cta_pause, R.drawable.ic_pause, SimulatorEvent.Pause)
                BottomAction.RESUME -> Triple(R.string.cta_resume, R.drawable.ic_play, SimulatorEvent.Resume)
                BottomAction.RETRY -> Triple(R.string.btn_retry, null, SimulatorEvent.Retry)
                BottomAction.GO_ALLOW -> Triple(R.string.btn_go_allow, null, SimulatorEvent.OpenPermission)
                BottomAction.GO_CHECK -> Triple(R.string.btn_go_check, null, SimulatorEvent.OpenPermission)
                BottomAction.START_NEW -> Triple(R.string.cta_new, null, SimulatorEvent.Reset)
                BottomAction.RECHECK -> Triple(R.string.btn_retry, null, SimulatorEvent.Recheck)
            }
            PrimaryButton(
                text = stringResource(label),
                onClick = { onEvent(event) },
                enabled = action != BottomAction.START || state.canStart,
                icon = icon?.let { painterResource(it) },
            )
            if (state.startHint == StartHint.NEED_GOAL) {
                Text(
                    stringResource(R.string.cta_hint_need_goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun gaitName(gait: Gait): String =
    stringResource(if (gait == Gait.WALK) R.string.sim_gait_walk else R.string.sim_gait_run)

@Composable
private fun durationText(time: RemainingTime): String = when (time) {
    RemainingTime.Soon -> stringResource(R.string.remaining_soon)
    is RemainingTime.Minutes -> stringResource(R.string.duration_min, time.minutes.toInt())
    is RemainingTime.Hours -> stringResource(R.string.duration_hour, time.hours.toInt())
    is RemainingTime.HoursMinutes -> stringResource(R.string.duration_hour_min, time.hours.toInt(), time.minutes.toInt())
}

/** 剩餘分鐘為 0 時顯示「即將完成」，其餘為「還剩約 …」。 */
@Composable
private fun remainingText(minutes: Long): String = when (val time = RemainingTime.of(minutes)) {
    RemainingTime.Soon -> stringResource(R.string.remaining_soon)
    else -> stringResource(R.string.remaining, durationText(time))
}

/** 主訊息用白話；技術細節只放在「詳細資訊」。對應邏輯見 [failureMessageFor]。 */
@StringRes
private fun FailureMessage.stringRes(): Int = when (this) {
    FailureMessage.PERMISSION_RESTORED -> R.string.err_permission_regranted
    FailureMessage.PERMISSION_UNCONFIRMED -> R.string.err_permission
    FailureMessage.HEALTH_CONNECT_UNAVAILABLE -> R.string.err_unavailable
    FailureMessage.RATE_LIMITED -> R.string.err_rate_limit
    FailureMessage.IO, FailureMessage.REMOTE -> R.string.err_io
    FailureMessage.GENERIC -> R.string.err_generic_body
}

private fun technicalDetail(failure: Throwable): String {
    val root = failure.cause ?: failure
    return listOfNotNull(root::class.simpleName, root.message).joinToString(": ")
}

private val QUICK_TARGETS = listOf(1_000L, 5_000L, 10_000L)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
