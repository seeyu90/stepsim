package com.seeyu90.stepsim.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seeyu90.stepsim.health.AndroidHealthConnectManager
import com.seeyu90.stepsim.health.HealthConnectAvailability
import com.seeyu90.stepsim.health.HealthConnectManager
import com.seeyu90.stepsim.StepSimApp
import com.seeyu90.stepsim.simulator.SimulationController
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 只負責觀察模擬狀態、送出指令與畫面相關狀態（輸入、權限）；模擬與計時迴圈由程序層級的
 * [SimulationController] 持有。
 */
class SimulatorViewModel(
    private val controller: SimulationController,
    private val healthConnect: HealthConnectManager,
    backgroundAvailable: StateFlow<Boolean> = MutableStateFlow(true),
) : ViewModel() {

    private data class Form(val targetInput: String = "", val targetError: TargetInputError? = null)

    /** null 代表尚在檢查。 */
    private data class Access(val available: Boolean? = null, val granted: Boolean? = null)

    private val form = MutableStateFlow(Form())
    private val access = MutableStateFlow(Access())

    val state: StateFlow<SimulatorUiState> =
        combine(form, access, controller.state, backgroundAvailable) { form, access, simulation, background ->
            SimulatorUiState(
                permissionGranted = access.granted,
                healthConnectAvailable = access.available,
                targetInput = form.targetInput,
                targetError = form.targetError,
                simulation = simulation,
                backgroundAvailable = background,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SimulatorUiState())

    private var permissionJob: Job? = null

    init {
        // 每發生一次寫入失敗（不論狀態是否改變）就重新檢查可用性與權限，
        // 畫面才能顯示正確的失敗原因與授權提示
        viewModelScope.launch {
            controller.state.map { it.failureCount }.distinctUntilChanged().drop(1)
                .collect { onWriteFailed() }
        }
    }

    fun onEvent(event: SimulatorEvent) {
        when (event) {
            is SimulatorEvent.TargetInputChanged -> updateTarget(event.text)
            is SimulatorEvent.QuickTargetSelected -> updateTarget(event.steps.toString())
            is SimulatorEvent.GaitSelected -> controller.setGait(event.gait)
            SimulatorEvent.Start -> if (state.value.canStart) controller.start()
            SimulatorEvent.Pause -> controller.pause()
            SimulatorEvent.Resume -> controller.resume()
            SimulatorEvent.Retry -> controller.retry()
            SimulatorEvent.Reset -> reset()
            SimulatorEvent.OpenPermission -> Unit // 由畫面處理導覽
        }
    }

    /** 指令為非同步：等模擬確實回到 READY / IDLE 後再檢查一次權限。 */
    private fun reset() {
        controller.reset()
        viewModelScope.launch {
            controller.state.first { it.status == SimulationStatus.READY || it.status == SimulationStatus.IDLE }
            refreshPermission()
        }
    }

    /** 失敗當下權限狀態已不可信：先設為未知，待重新檢查完成才更新。 */
    private fun onWriteFailed() {
        access.update { it.copy(granted = null) }
        refreshPermission()
    }

    fun refreshPermission() {
        permissionJob?.cancel()
        permissionJob = viewModelScope.launch {
            try {
                val available = healthConnect.availability() == HealthConnectAvailability.AVAILABLE
                access.value = Access(available = available, granted = available && healthConnect.hasAllPermissions())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 檢查本身失敗：權限維持未知，不保留可能已過時的舊值
                access.update { it.copy(granted = null) }
            }
        }
    }

    private fun updateTarget(raw: String) {
        val text = raw.filter(Char::isDigit).take(MAX_INPUT_LENGTH)
        val parsed = text.toLongOrNull()
        val error = if (text.isNotEmpty() && (parsed == null || !StepSimulator.isValidTarget(parsed))) {
            TargetInputError.OUT_OF_RANGE
        } else {
            null
        }
        form.update { Form(text, error) }
        if (error == null && parsed != null && state.value.canEditTarget) {
            controller.setTarget(parsed)
        }
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 7

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as StepSimApp
                SimulatorViewModel(
                    controller = app.simulation,
                    healthConnect = AndroidHealthConnectManager(app),
                    backgroundAvailable = app.backgroundAvailability.available,
                )
            }
        }
    }
}
