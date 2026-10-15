package com.seeyu90.stepsim.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前景服務是否成功啟動的可觀察狀態。啟動失敗（例如 SecurityException、
 * ForegroundServiceStartNotAllowedException）時為 false，畫面據此提示「無法在背景執行」；
 * 模擬本身不中斷。
 */
class BackgroundAvailability {
    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun reportStarted() {
        _available.value = true
    }

    fun reportStartFailed() {
        _available.value = false
    }
}
