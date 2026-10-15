package com.seeyu90.stepsim

import android.app.Application
import com.seeyu90.stepsim.health.HealthConnectStepWriter
import com.seeyu90.stepsim.simulator.SimulationController
import com.seeyu90.stepsim.simulator.SimulationRunner
import com.seeyu90.stepsim.simulator.StepSimulator
import com.seeyu90.stepsim.simulator.SystemTimeSource
import java.util.UUID
import kotlinx.coroutines.Dispatchers

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
}
