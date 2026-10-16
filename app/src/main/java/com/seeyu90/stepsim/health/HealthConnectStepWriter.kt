package com.seeyu90.stepsim.health

import android.content.Context
import android.health.connect.HealthConnectException
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.seeyu90.stepsim.simulator.StepBatch
import com.seeyu90.stepsim.simulator.StepWritePermissionException
import com.seeyu90.stepsim.simulator.StepWriteRateLimitException
import com.seeyu90.stepsim.simulator.StepWriter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 以 Health Connect 實作的 [StepWriter]。
 *
 * - 紀錄方式標為手動輸入（manualEntry），不帶裝置資訊，避免偽裝成感測器記錄；來源 App 由 HC 自動記錄。
 * - 每批帶 clientRecordId：重送同一批時，HC 以 clientRecordVersion 較高者為準，
 *   版本相同時忽略重送，因此重試不會重複計算步數。
 */
class HealthConnectStepWriter(
    context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : StepWriter {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context.applicationContext) }

    override suspend fun write(batch: StepBatch) {
        val record = StepsRecord(
            startTime = batch.start,
            startZoneOffset = offsetAt(batch.start),
            endTime = batch.end,
            endZoneOffset = offsetAt(batch.end),
            count = batch.steps,
            metadata = Metadata.manualEntry(
                clientRecordId = batch.clientRecordId,
                clientRecordVersion = CLIENT_RECORD_VERSION,
            ),
        )
        try {
            client.insertRecords(listOf(record))
        } catch (e: SecurityException) {
            throw StepWritePermissionException(e)
        } catch (e: IllegalStateException) {
            if (isRateLimited(e)) throw StepWriteRateLimitException(e)
            throw e
        }
    }

    /**
     * Android 14+ 由平台 Health Connect 處理時，connect-client 的
     * HealthConnectClientUpsideDownImpl 會把平台的 HealthConnectException 經
     * ExceptionConverter.kt 的 toKtException() 轉換；ERROR_RATE_LIMIT_EXCEEDED 沒有對應類別，
     * 會成為 cause 為該平台例外的 IllegalStateException。Android 13 以下的行為未確認，不在此判斷。
     */
    private fun isRateLimited(e: IllegalStateException): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return Api34.isRateLimitError(e.cause)
    }

    private fun offsetAt(instant: Instant): ZoneOffset = zoneId.rules.getOffset(instant)

    private companion object {
        /** 固定版本：同一批重送時版本相同，HC 會忽略重送而非重複寫入。 */
        const val CLIENT_RECORD_VERSION = 1L
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        fun isRateLimitError(cause: Throwable?): Boolean =
            cause is HealthConnectException &&
                cause.errorCode == HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED
    }
}
