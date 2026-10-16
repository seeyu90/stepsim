package com.seeyu90.stepsim

import android.app.Application
import com.seeyu90.stepsim.health.HealthConnectStepWriter
import com.seeyu90.stepsim.service.BackgroundAvailability
import com.seeyu90.stepsim.service.SimulationService
import com.seeyu90.stepsim.service.appReconcile
import com.seeyu90.stepsim.simulator.SimulationController
import com.seeyu90.stepsim.simulator.SimulationRunner
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.SystemTimeSource
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 持有程序層級的模擬：不隨畫面建立或銷毀，App 離開前景後仍持續存在。 */
class StepSimApp : Application() {

    val simulation: SimulationController by lazy {
        SimulationRunner(
            simulator = StepSimulator(
                timeSource = SystemTimeSource,
                writer = HealthConnectStepWriter(this),
                sessionId = UUID.randomUUID().toString(),
            ),
            // 單一執行緒：所有指令與計時依序執行
            dispatcher = Dispatchers.Default.limitedParallelism(1),
        )
    }

    /** 前景服務狀態；只能在主執行緒更新。 */
    val backgroundAvailability = BackgroundAvailability()

    override fun onCreate() {
        super.onCreate()
        // 前景服務的對帳迴圈：每次以「目前的值」比較模擬狀態與服務實際狀態，需要前景而服務不在就啟動。
        // 不依賴每次變化都送到（StateFlow 會合併）。停止由服務自己以 stopSelf(startId) 處理。
        // 收集與 BackgroundAvailability 的更新都在主執行緒（MainScope）。
        MainScope().launch {
            combine(simulation.state, backgroundAvailability.status) { state, service -> state.status to service }
                .distinctUntilChanged()
                .collect { (status, service) ->
                    val step = appReconcile(status, service.alive, service.failedAt)
                    if (step.failedAt != service.failedAt) backgroundAvailability.updateFailedAt(step.failedAt)
                    if (step.start) SimulationService.start(this@StepSimApp, backgroundAvailability, status)
                }
        }
    }
}
