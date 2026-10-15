package com.seeyu90.stepsim.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

/** 封裝所有 Health Connect SDK 存取；其他套件不得直接使用 HC 類別。 */
class HealthConnectManager(context: Context) {

    private val appContext = context.applicationContext

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(appContext) }

    fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_UPDATE
            else -> if (isProviderInstalled()) {
                HealthConnectAvailability.NOT_SUPPORTED
            } else {
                HealthConnectAvailability.NEEDS_INSTALL
            }
        }

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(PERMISSIONS)

    fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** 導向 Play Store 安裝 / 更新 Health Connect。 */
    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri()
            putExtra("overlay", true)
            putExtra("callerId", appContext.packageName)
        }

    private fun isProviderInstalled(): Boolean =
        try {
            appContext.packageManager.getPackageInfo(PROVIDER_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
        )
    }
}
