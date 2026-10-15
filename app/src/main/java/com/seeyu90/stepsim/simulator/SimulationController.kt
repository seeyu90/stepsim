package com.seeyu90.stepsim.simulator

import kotlinx.coroutines.flow.StateFlow

/**
 * 模擬的對外介面：畫面、通知等只觀察 [state] 並送出指令，不持有計時迴圈。
 * 指令為非同步執行；在目前狀態不合法的指令（例如重複開始）會被忽略。
 */
interface SimulationController {
    val state: StateFlow<SimulationState>

    fun setTarget(steps: Long)
    fun setGait(gait: Gait)
    fun start()
    fun pause()
    fun resume()
    fun retry()

    /** 結束本次模擬並清空進度（畫面的「結束並重設」與通知的「停止」）。 */
    fun reset()
}
