package com.seeyu90.stepsim.simulator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 程序層級的模擬持有者：擁有自己的 CoroutineScope 與計時迴圈，不依賴任何畫面。
 *
 * 計時迴圈由狀態驅動：單一位置觀察狀態，RUNNING 時有且只有一個迴圈，離開 RUNNING 即取消。
 * [dispatcher] 須讓工作序列化執行（例如 `Dispatchers.Default.limitedParallelism(1)`：同一時間只有一個工作，
 * 但不保證固定在同一條執行緒），測試時注入測試用 dispatcher。
 */
class SimulationRunner(
    private val simulator: StepSimulator,
    dispatcher: CoroutineDispatcher,
    private val tickIntervalMillis: Long = TICK_INTERVAL_MILLIS,
) : SimulationController {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override val state: StateFlow<SimulationState> = simulator.state

    /** 目前執行中的計時迴圈數（只在 runner 的 dispatcher 上讀寫，工作序列化執行），供測試驗證 RUNNING 時有且只有一個。 */
    internal var activeTickLoops = 0
        private set

    init {
        scope.launch {
            simulator.state
                .map { it.status == SimulationStatus.RUNNING }
                .distinctUntilChanged()
                .collectLatest { running ->
                    if (!running) return@collectLatest
                    activeTickLoops++
                    try {
                        while (true) {
                            delay(tickIntervalMillis)
                            simulator.tick()
                        }
                    } finally {
                        activeTickLoops--
                    }
                }
        }
    }

    override fun setTarget(steps: Long) = command { simulator.setTarget(steps) }
    override fun setGait(gait: Gait) = command { simulator.setGait(gait) }
    override fun start() = command { simulator.start() }
    override fun pause() = command { simulator.pause() }
    override fun resume() = command { simulator.resume() }
    override fun retry() = command { simulator.retry() }
    override fun reset() = command { simulator.reset() }

    /** 停止迴圈並取消所有工作；正式環境隨程序存在，主要供測試收尾。 */
    fun close() {
        scope.cancel()
    }

    private fun command(action: suspend () -> Unit) {
        scope.launch { ignoreInvalidCommand(action) }
    }

    companion object {
        const val TICK_INTERVAL_MILLIS = 10_000L
    }
}

/**
 * 狀態已變（例如連點、通知與畫面同時操作）而不合法的指令直接忽略。
 * CancellationException 在 JVM 上是 IllegalStateException 的子類別，必須先重新拋出，取消才會傳遞出去。
 */
internal suspend fun ignoreInvalidCommand(action: suspend () -> Unit) {
    try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IllegalStateException) {
    } catch (_: IllegalArgumentException) {
    }
}
