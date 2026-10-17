package com.seeyu90.stepsim.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.ui.components.InfoCard
import com.seeyu90.stepsim.ui.components.NoticeCard
import com.seeyu90.stepsim.ui.components.PrimaryButton
import com.seeyu90.stepsim.ui.components.SecondaryTextButton

/** 接 ViewModel 的外層：重新檢查、跳出系統授權視窗、開啟 Health Connect 或 Play 商店。 */
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = viewModel(factory = PermissionViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    fun startOrToast(intent: Intent, @StringRes failureMessage: Int) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
        }
    }

    // 被拒絕時不自動跳轉設定頁：改為提供「再試一次」與手動開啟的步驟
    val launcher = rememberLauncherForActivityResult(remember { viewModel.permissionContract() }) { granted ->
        viewModel.onPermissionResult(granted)
    }

    PermissionContent(
        state = state,
        actions = PermissionActions(
            request = { launcher.launch(viewModel.permissions) },
            recheck = viewModel::refresh,
            openHealthConnect = { startOrToast(viewModel.settingsIntent(), R.string.toast_settings_failed) },
            install = { startOrToast(viewModel.installIntent(), R.string.toast_install_failed) },
            back = onBack,
        ),
        modifier = modifier,
    )
}

/** 授權頁的操作；Preview 傳入空實作。 */
class PermissionActions(
    val request: () -> Unit,
    val recheck: () -> Unit,
    val openHealthConnect: () -> Unit,
    val install: () -> Unit,
    val back: () -> Unit,
) {
    companion object {
        val None = PermissionActions({}, {}, {}, {}, {})
    }
}

/** 無狀態授權頁：先說為什麼需要，再跳系統視窗；主要動作固定在底部。 */
@Composable
fun PermissionContent(state: PermissionUiState, actions: PermissionActions, modifier: Modifier = Modifier) {
    val stage = state.stage
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.perm_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            when (stage) {
                PermissionStage.CHECKING -> BodyText(R.string.checking)
                PermissionStage.CHECK_FAILED -> NoticeCard(stringResource(R.string.perm_check_failed))
                PermissionStage.NEEDS_INSTALL -> NoticeCard(stringResource(R.string.perm_need_install))
                PermissionStage.NEEDS_UPDATE -> NoticeCard(stringResource(R.string.perm_need_update))
                PermissionStage.NOT_SUPPORTED -> NoticeCard(stringResource(R.string.perm_not_supported))
                PermissionStage.NOT_GRANTED -> {
                    StatusLine(R.string.perm_status_denied, granted = false)
                    BodyText(R.string.perm_why)
                }
                PermissionStage.NEEDS_MANUAL -> ManualSteps()
                PermissionStage.GRANTED -> StatusLine(R.string.perm_status_granted, granted = true)
            }
            // 說明條目（含責任使用條文）只在使用者可以繼續允許的階段顯示
            Points(permissionPoints(stage))
            // 次要操作
            when (stage) {
                PermissionStage.NEEDS_MANUAL ->
                    SecondaryTextButton(stringResource(R.string.perm_open_hc), onClick = actions.openHealthConnect)
                PermissionStage.GRANTED ->
                    SecondaryTextButton(stringResource(R.string.perm_reset_perm), onClick = actions.openHealthConnect)
                else -> Unit
            }
        }
        BottomAction(stage, actions)
    }
}

@Composable
private fun BottomAction(stage: PermissionStage, actions: PermissionActions) {
    val (label, onClick) = when (permissionBottomAction(stage) ?: return) {
        PermissionBottomAction.RECHECK -> R.string.perm_retry to actions.recheck
        PermissionBottomAction.INSTALL -> R.string.btn_install_hc to actions.install
        PermissionBottomAction.UPDATE -> R.string.btn_update_hc to actions.install
        PermissionBottomAction.BACK -> R.string.perm_back to actions.back
        PermissionBottomAction.REQUEST -> R.string.perm_cta to actions.request
        PermissionBottomAction.REQUEST_AGAIN -> R.string.perm_retry to actions.request
        PermissionBottomAction.BACK_TO_SIMULATOR -> R.string.perm_back_to_sim to actions.back
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        PrimaryButton(
            text = stringResource(label),
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun BodyText(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

/** 授權狀態：圖示＋文字，不單靠顏色。 */
@Composable
private fun StatusLine(@StringRes text: Int, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painterResource(if (granted) R.drawable.ic_check else R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** 授權說明條目；第四點（RESPONSIBLE_USE）是責任使用。沒有條目的階段不顯示卡片。 */
@Composable
private fun Points(points: List<PermissionPoint>) {
    if (points.isEmpty()) return
    InfoCard {
        for (point in points.map { it.stringRes() }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(point), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

@StringRes
private fun PermissionPoint.stringRes(): Int = when (this) {
    PermissionPoint.ONLY_SIMULATED -> R.string.perm_point_1
    PermissionPoint.NO_UPLOAD -> R.string.perm_point_2
    PermissionPoint.REVOKE_ANYTIME -> R.string.perm_point_3
    PermissionPoint.RESPONSIBLE_USE -> R.string.perm_point_4
}

/** 請求後仍未授權：不斷言系統是否跳出視窗，提供再試一次與手動開啟的編號步驟。 */
@Composable
private fun ManualSteps() {
    Text(
        stringResource(R.string.perm_manual_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    BodyText(R.string.perm_manual_body)
    InfoCard {
        val steps = listOf(R.string.perm_manual_step_1, R.string.perm_manual_step_2, R.string.perm_manual_step_3)
        steps.forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                Text(stringResource(step), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}
