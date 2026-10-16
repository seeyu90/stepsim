package com.seeyu90.stepsim.service

import com.seeyu90.stepsim.simulator.SimulationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * App 端的前景服務對帳迴圈（純 Kotlin，不依賴 Android，可單元測試）。
 *
 * 每次以「目前的值」比較模擬狀態與服務實際狀態，需要前景而服務不在就啟動；不依賴每次變化都送到
 * （StateFlow 會合併）。停止由服務自己以 stopSelf(startId) 處理。收集與 [BackgroundAvailability] 的更新
 * 都應在同一個執行緒（主執行緒）。
 *
 * - 與服務狀態合併：服務自己死掉（alive 變 false）而模擬狀態沒變時，也要重新啟動。
 * - distinctUntilChanged：進度更新不改變狀態種類時，不可重複啟動。
 * - 依 [appReconcile] 的結果更新失敗紀錄：離開前景需求時作廢，下一輪才會重新嘗試。
 *
 * @param startService 啟動前景服務；實作需自行向 [availability] 回報（見 SimulationService.start）。
 */
class ForegroundReconciler(
    private val simulationStatus: Flow<SimulationStatus>,
    private val availability: BackgroundAvailability,
    private val startService: (SimulationStatus) -> Unit,
) {
    /** 持續對帳，直到被取消。 */
    suspend fun run() {
        combine(simulationStatus, availability.status) { status, service -> status to service }
            .distinctUntilChanged()
            .collect { (status, service) ->
                val step = appReconcile(status, service.alive, service.failedAt)
                if (step.failedAt != service.failedAt) availability.updateFailedAt(step.failedAt)
                if (step.start) startService(status)
            }
    }
}
