package com.seeyu90.stepsim.health

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

/** 封裝所有 Health Connect SDK 存取；其他套件不得直接使用 HC 類別。實作見 [AndroidHealthConnectManager]。 */
interface HealthConnectManager {

    fun availability(): HealthConnectAvailability

    suspend fun grantedPermissions(): Set<String>

    suspend fun hasAllPermissions(): Boolean

    fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>>

    /** 導向 Play Store 安裝 / 更新 Health Connect。 */
    fun installIntent(): Intent

    /** 開啟 Health Connect 設定頁，讓使用者手動管理本 App 的權限。 */
    fun settingsIntent(): Intent

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
        )
    }
}
