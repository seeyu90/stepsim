package com.seeyu90.stepsim.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.seeyu90.stepsim.simulator.StepBatch
import com.seeyu90.stepsim.simulator.StepWritePermissionException
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
        }
    }

    private fun offsetAt(instant: Instant): ZoneOffset = zoneId.rules.getOffset(instant)

    private companion object {
        /** 固定版本：同一批重送時版本相同，HC 會忽略重送而非重複寫入。 */
        const val CLIENT_RECORD_VERSION = 1L
    }
}
