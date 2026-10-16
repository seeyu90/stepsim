package com.seeyu90.stepsim.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val manager = FakeHealthConnectManager()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh updates state when permissions are granted`() = runTest(dispatcher) {
        manager.hasAllPermissionsResponses += { true }
        val viewModel = PermissionViewModel(manager)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            PermissionUiState(HealthConnectAvailability.AVAILABLE, granted = true, checked = true),
            viewModel.state.value,
        )
    }

    @Test
    fun `refresh skips permission check when Health Connect is unavailable`() = runTest(dispatcher) {
        manager.availability = HealthConnectAvailability.NEEDS_INSTALL
        val viewModel = PermissionViewModel(manager)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            PermissionUiState(HealthConnectAvailability.NEEDS_INSTALL, granted = false, checked = true),
            viewModel.state.value,
        )
        assertEquals(0, manager.hasAllPermissionsCalls)
    }

    @Test
    fun `refresh enters error state when Health Connect throws`() = runTest(dispatcher) {
        manager.hasAllPermissionsResponses += { throw IllegalStateException("provider died") }
        val viewModel = PermissionViewModel(manager)

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.refreshFailed)
        assertTrue(viewModel.state.value.checked)
    }

    @Test
    fun `newer refresh cancels older one so stale result cannot overwrite`() = runTest(dispatcher) {
        val first = CompletableDeferred<Boolean>()
        val second = CompletableDeferred<Boolean>()
        manager.hasAllPermissionsResponses += { first.await() }
        manager.hasAllPermissionsResponses += { second.await() }
        val viewModel = PermissionViewModel(manager)

        viewModel.refresh()
        viewModel.refresh()

        // 新的一次先完成：已授權
        second.complete(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.granted)

        // 舊的一次較晚完成（讀到授權前的狀態），不得覆蓋新結果
        first.complete(false)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.granted)
    }

    @Test
    fun `cancelled refresh is not treated as an error`() = runTest(dispatcher) {
        val never = CompletableDeferred<Boolean>()
        manager.hasAllPermissionsResponses += { never.await() }
        manager.hasAllPermissionsResponses += { never.await() }
        val viewModel = PermissionViewModel(manager)

        viewModel.refresh()
        viewModel.refresh() // 取消前一次，前一次的 await 會拋出 CancellationException
        advanceUntilIdle()

        assertFalse(viewModel.state.value.refreshFailed)
        assertFalse(viewModel.state.value.checked)
    }
}

private class FakeHealthConnectManager : HealthConnectManager {

    var availability = HealthConnectAvailability.AVAILABLE

    /** 每次呼叫 hasAllPermissions() 依序取出一個回應。 */
    val hasAllPermissionsResponses = ArrayDeque<suspend () -> Boolean>()

    var hasAllPermissionsCalls = 0
        private set

    override fun availability(): HealthConnectAvailability = availability

    override suspend fun grantedPermissions(): Set<String> = error("not used")

    override suspend fun hasAllPermissions(): Boolean {
        hasAllPermissionsCalls++
        return hasAllPermissionsResponses.removeFirst().invoke()
    }

    override fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        error("not used")

    override fun installIntent(): Intent = error("not used")

    override fun settingsIntent(): Intent = error("not used")
}
