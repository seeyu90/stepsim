package com.seeyu90.stepsim.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 授權頁的階段判斷，以及「請求後仍未授權」→ 需要手動開啟的流程。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionStageTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val manager = StageFakeHealthConnect()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val available = HealthConnectAvailability.AVAILABLE

    @Test
    fun `stage follows check, availability and permission`() {
        assertEquals(PermissionStage.CHECKING, PermissionUiState().stage)
        assertEquals(PermissionStage.CHECK_FAILED, PermissionUiState(checked = true, refreshFailed = true).stage)
        assertEquals(
            PermissionStage.NEEDS_INSTALL,
            PermissionUiState(HealthConnectAvailability.NEEDS_INSTALL, checked = true).stage,
        )
        assertEquals(
            PermissionStage.NEEDS_UPDATE,
            PermissionUiState(HealthConnectAvailability.NEEDS_UPDATE, checked = true).stage,
        )
        assertEquals(
            PermissionStage.NOT_SUPPORTED,
            PermissionUiState(HealthConnectAvailability.NOT_SUPPORTED, checked = true).stage,
        )
        assertEquals(PermissionStage.NOT_GRANTED, PermissionUiState(available, granted = false, checked = true).stage)
        assertEquals(PermissionStage.GRANTED, PermissionUiState(available, granted = true, checked = true).stage)
        assertEquals(
            PermissionStage.NEEDS_MANUAL,
            PermissionUiState(available, granted = false, checked = true, deniedAfterRequest = true).stage,
        )
    }

    @Test
    fun `responsible use is shown only where the user can continue to allow`() {
        val withPoints = setOf(PermissionStage.NOT_GRANTED, PermissionStage.NEEDS_MANUAL)
        for (stage in PermissionStage.entries) {
            assertEquals("stage=$stage", stage in withPoints, showsResponsibleUse(stage))
            val expected = if (stage in withPoints) PermissionPoint.entries else emptyList()
            assertEquals("stage=$stage", expected, permissionPoints(stage))
        }
    }

    @Test
    fun `every stage whose primary button requests permission shows responsible use`() {
        val requesting = PermissionStage.entries.filter { permissionBottomAction(it)?.requestsPermission == true }
        assertEquals(setOf(PermissionStage.NOT_GRANTED, PermissionStage.NEEDS_MANUAL), requesting.toSet())
        for (stage in requesting) {
            assertTrue("stage=$stage", showsResponsibleUse(stage))
        }
    }

    @Test
    fun `empty result after a request leads to manual steps`() = runTest(dispatcher) {
        manager.granted = false
        val viewModel = PermissionViewModel(manager)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(PermissionStage.NOT_GRANTED, viewModel.state.value.stage)

        viewModel.onPermissionResult(emptySet())
        advanceUntilIdle()

        assertEquals(PermissionStage.NEEDS_MANUAL, viewModel.state.value.stage)
    }

    @Test
    fun `manual stage survives later rechecks such as returning to the app`() = runTest(dispatcher) {
        manager.granted = false
        val viewModel = PermissionViewModel(manager)
        viewModel.onPermissionResult(emptySet())
        advanceUntilIdle()

        // 例如從 Health Connect 回來觸發 ON_RESUME，但仍未打開權限
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(PermissionStage.NEEDS_MANUAL, viewModel.state.value.stage)
    }

    @Test
    fun `granting later clears the manual stage`() = runTest(dispatcher) {
        manager.granted = false
        val viewModel = PermissionViewModel(manager)
        viewModel.onPermissionResult(emptySet())
        advanceUntilIdle()

        manager.granted = true
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(PermissionStage.GRANTED, viewModel.state.value.stage)
        assertFalse(viewModel.state.value.deniedAfterRequest)

        // 之後在 Health Connect 撤銷，回到「尚未允許」而不是殘留的手動步驟
        manager.granted = false
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(PermissionStage.NOT_GRANTED, viewModel.state.value.stage)
    }

    @Test
    fun `full grant from the dialog does not mark a denial`() = runTest(dispatcher) {
        manager.granted = true
        val viewModel = PermissionViewModel(manager)
        viewModel.onPermissionResult(HealthConnectManager.PERMISSIONS)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.deniedAfterRequest)
        assertEquals(PermissionStage.GRANTED, viewModel.state.value.stage)
    }
}

private class StageFakeHealthConnect : HealthConnectManager {
    var granted = false

    override fun availability() = HealthConnectAvailability.AVAILABLE

    override suspend fun grantedPermissions(): Set<String> = error("not used")

    override suspend fun hasAllPermissions(): Boolean = granted

    override fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        error("not used")

    override fun installIntent(): Intent = error("not used")

    override fun settingsIntent(): Intent = error("not used")
}
