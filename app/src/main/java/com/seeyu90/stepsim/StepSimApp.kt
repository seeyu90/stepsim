package com.seeyu90.stepsim

import android.app.Application
import com.seeyu90.stepsim.health.HealthConnectStepWriter
import com.seeyu90.stepsim.service.BackgroundAvailability
import com.seeyu90.stepsim.service.SimulationService
import com.seeyu90.stepsim.service.shouldRunForeground
import com.seeyu90.stepsim.simulator.SimulationController
import com.seeyu90.stepsim.simulator.SimulationRunner
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.SystemTimeSource
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
            // 單一執行緒：所有指令與計時依序執行
            dispatcher = Dispatchers.Default.limitedParallelism(1),
        )
    }

    val backgroundAvailability = BackgroundAvailability()

    override fun onCreate() {
        super.onCreate()
        // 模擬進入需要前景服務的狀態時啟動服務（實務上只會由畫面按「開始」觸發，App 在前景）。
        // 停止由服務自己觀察狀態處理。
        MainScope().launch {
            simulation.state
                .map { shouldRunForeground(it.status) }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect { SimulationService.start(this@StepSimApp, backgroundAvailability) }
        }
    }
}
