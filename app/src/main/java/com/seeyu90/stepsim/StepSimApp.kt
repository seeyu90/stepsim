package com.seeyu90.stepsim

import android.app.Application
import com.seeyu90.stepsim.health.HealthConnectStepWriter
import com.seeyu90.stepsim.service.BackgroundAvailability
import com.seeyu90.stepsim.service.ForegroundReconciler
import com.seeyu90.stepsim.service.SimulationService
import com.seeyu90.stepsim.simulator.SimulationController
import com.seeyu90.stepsim.simulator.SimulationRunner
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.SystemTimeSource
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.map
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
            // 序列化（不固定執行緒）：所有指令與計時依序執行
            dispatcher = Dispatchers.Default.limitedParallelism(1),
        )
    }

    /** 前景服務狀態；只能在主執行緒更新。 */
    val backgroundAvailability = BackgroundAvailability()

    override fun onCreate() {
        super.onCreate()
        // 前景服務的對帳迴圈（邏輯見 ForegroundReconciler）；收集與 BackgroundAvailability 的更新都在主執行緒（MainScope）
        MainScope().launch {
            ForegroundReconciler(
                simulationStatus = simulation.state.map { it.status },
                availability = backgroundAvailability,
                startService = { status -> SimulationService.start(this@StepSimApp, backgroundAvailability, status) },
            ).run()
        }
    }
}
