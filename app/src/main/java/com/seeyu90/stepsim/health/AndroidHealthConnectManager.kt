package com.seeyu90.stepsim.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.seeyu90.stepsim.health.HealthConnectManager.Companion.PERMISSIONS
import com.seeyu90.stepsim.health.HealthConnectManager.Companion.PROVIDER_PACKAGE

/** 以 Health Connect SDK 實作的 [HealthConnectManager]。 */
class AndroidHealthConnectManager(context: Context) : HealthConnectManager {

    private val appContext = context.applicationContext

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(appContext) }

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_UPDATE
            else -> if (isProviderInstalled()) {
                HealthConnectAvailability.NOT_SUPPORTED
            } else {
                HealthConnectAvailability.NEEDS_INSTALL
            }
        }

    override suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    override suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(PERMISSIONS)

    override fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    override fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri()
            putExtra("overlay", true)
            putExtra("callerId", appContext.packageName)
        }

    override fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    private fun isProviderInstalled(): Boolean =
        try {
            appContext.packageManager.getPackageInfo(PROVIDER_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
}
