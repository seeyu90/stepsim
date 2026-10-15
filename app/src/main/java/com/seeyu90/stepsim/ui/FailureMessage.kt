package com.seeyu90.stepsim.ui

import android.os.RemoteException
import com.seeyu90.stepsim.simulator.SimulationStatus
import com.seeyu90.stepsim.simulator.StepWriteRateLimitException
import java.io.IOException

/** 寫入失敗卡片的主訊息種類；技術細節另外顯示原始例外。 */
enum class FailureMessage {
    PERMISSION_RESTORED,
    HEALTH_CONNECT_UNAVAILABLE,
    RATE_LIMITED,
    IO,
    REMOTE,
    GENERIC,
}

/**
 * 決定失敗卡片的主訊息；不需顯示卡片時回傳 null。
 *
 * - 「Health Connect 無法使用」只依可用性檢查（getSdkStatus）判斷，不由例外類型推測。
 * - 權限不足且尚未重新授權時不顯示卡片，由權限提示處理。
 * - 未分類的例外（含 IllegalStateException）一律顯示通用訊息。
 */
fun failureMessageFor(
    status: SimulationStatus,
    failure: Throwable?,
    permissionGranted: Boolean?,
    healthConnectAvailable: Boolean?,
): FailureMessage? = when {
    status != SimulationStatus.FAILED && status != SimulationStatus.PERMISSION_REVOKED -> null
    status == SimulationStatus.PERMISSION_REVOKED ->
        if (permissionGranted == true) FailureMessage.PERMISSION_RESTORED else null
    healthConnectAvailable == false -> FailureMessage.HEALTH_CONNECT_UNAVAILABLE
    failure is StepWriteRateLimitException -> FailureMessage.RATE_LIMITED
    failure is IOException -> FailureMessage.IO
    failure is RemoteException -> FailureMessage.REMOTE
    else -> FailureMessage.GENERIC
}
