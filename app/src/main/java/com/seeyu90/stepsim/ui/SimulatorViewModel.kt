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
import com.seeyu90.stepsim.health.HealthConnectStepWriter
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.SystemTimeSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 模擬只在 App 前景執行：計時迴圈跑在 [viewModelScope]，離開前景時由畫面呼叫 [onAppStopped] 暫停。 */
class SimulatorViewModel(
    private val simulator: StepSimulator,
    private val healthConnect: HealthConnectManager,
    private val tickIntervalMillis: Long = TICK_INTERVAL_MILLIS,
) : ViewModel() {

    private data class Form(val targetInput: String = "", val targetError: TargetInputError? = null)

    /** null 代表尚在檢查。 */
    private data class Access(val available: Boolean? = null, val granted: Boolean? = null)

    private val form = MutableStateFlow(Form())
    private val access = MutableStateFlow(Access())

    val state: StateFlow<SimulatorUiState> =
        combine(form, access, simulator.state) { form, access, simulation ->
            SimulatorUiState(access.granted, access.available, form.targetInput, form.targetError, simulation)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SimulatorUiState())

    private var tickJob: Job? = null
    private var permissionJob: Job? = null

    init {
        // 每發生一次寫入失敗（不論狀態是否改變）就重新檢查可用性與權限，
        // 畫面才能顯示正確的失敗原因與授權提示
        viewModelScope.launch {
            simulator.state.map { it.failureCount }.distinctUntilChanged().drop(1)
                .collect { onWriteFailed() }
        }
    }

    fun onEvent(event: SimulatorEvent) {
        when (event) {
            is SimulatorEvent.TargetInputChanged -> updateTarget(event.text)
            is SimulatorEvent.QuickTargetSelected -> updateTarget(event.steps.toString())
            is SimulatorEvent.GaitSelected -> act { simulator.setGait(event.gait) }
            SimulatorEvent.Start -> if (state.value.canStart) act { simulator.start() }
            SimulatorEvent.Pause -> act { simulator.pause() }
            SimulatorEvent.Resume -> act { simulator.resume() }
            SimulatorEvent.Retry -> act { simulator.retry() }
            SimulatorEvent.Reset -> act {
                simulator.reset()
                refreshPermission()
            }
            SimulatorEvent.OpenPermission -> Unit // 由畫面處理導覽
        }
    }

    /** 畫面離開前景（含螢幕關閉）時暫停；旋轉等設定變更不暫停。 */
    fun onAppStopped(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations) return
        act { simulator.pause() }
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
            act { simulator.setTarget(parsed) }
        }
    }

    /** 執行模擬器操作；狀態已變（例如連點）而不合法的操作直接忽略。執行中則確保計時迴圈在跑。 */
    private fun act(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (_: IllegalStateException) {
            }
            ensureTicking()
        }
    }

    private fun ensureTicking() {
        if (tickJob?.isActive == true) return
        if (simulator.state.value.status != SimulationStatus.RUNNING) return
        tickJob = viewModelScope.launch {
            while (simulator.state.value.status == SimulationStatus.RUNNING) {
                delay(tickIntervalMillis)
                simulator.tick()
            }
        }
    }

    companion object {
        const val TICK_INTERVAL_MILLIS = 10_000L
        private const val MAX_INPUT_LENGTH = 7

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SimulatorViewModel(
                    simulator = StepSimulator(
                        timeSource = SystemTimeSource,
                        writer = HealthConnectStepWriter(app),
                        sessionId = UUID.randomUUID().toString(),
                    ),
                    healthConnect = AndroidHealthConnectManager(app),
                )
            }
        }
    }
}
