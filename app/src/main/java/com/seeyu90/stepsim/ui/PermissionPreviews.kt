package com.seeyu90.stepsim.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.ui.theme.StepSimTheme

// Preview 專用：只使用無狀態的 PermissionContent 與 RationaleContent，不依賴 ViewModel、Health Connect。

private val available = HealthConnectAvailability.AVAILABLE

@Composable
private fun Frame(state: PermissionUiState) = StepSimTheme {
    Surface { PermissionContent(state = state, actions = PermissionActions.None) }
}

@StepSimPreviews
@Composable
private fun NotGrantedPreview() = Frame(PermissionUiState(available, granted = false, checked = true))

@StepSimPreviews
@Composable
private fun ManualPreview() =
    Frame(PermissionUiState(available, granted = false, checked = true, deniedAfterRequest = true))

@Preview(name = "授權 · 已允許", showBackground = true, heightDp = 1000)
@Composable
private fun GrantedPreview() = Frame(PermissionUiState(available, granted = true, checked = true))

@Preview(name = "授權 · 檢查中", showBackground = true)
@Composable
private fun CheckingPreview() = Frame(PermissionUiState())

@Preview(name = "授權 · 檢查失敗", showBackground = true)
@Composable
private fun CheckFailedPreview() = Frame(PermissionUiState(checked = true, refreshFailed = true))

@Preview(name = "授權 · 未安裝", showBackground = true)
@Composable
private fun NeedsInstallPreview() = Frame(PermissionUiState(HealthConnectAvailability.NEEDS_INSTALL, checked = true))

@Preview(name = "授權 · 需要更新", showBackground = true)
@Composable
private fun NeedsUpdatePreview() = Frame(PermissionUiState(HealthConnectAvailability.NEEDS_UPDATE, checked = true))

@Preview(name = "授權 · 不支援", showBackground = true)
@Composable
private fun NotSupportedPreview() = Frame(PermissionUiState(HealthConnectAvailability.NOT_SUPPORTED, checked = true))

@StepSimPreviews
@Composable
private fun RationalePreview() = StepSimTheme { Surface { RationaleContent() } }
