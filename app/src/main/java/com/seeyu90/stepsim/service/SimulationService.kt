package com.seeyu90.stepsim.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.seeyu90.stepsim.MainActivity
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.StepSimApp
import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.SimulationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 模擬執行中保持程序存活並顯示常駐通知（type = health）。模擬本身由 [StepSimApp.simulation] 持有，
 * 此服務只依 [shouldRunForeground] 與 [notificationModel] 顯示通知、轉送通知按鈕的指令。
 */
class SimulationService : Service() {

    private val app get() = application as StepSimApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先呼叫 startForeground：以 startForegroundService 啟動後必須盡快進入前景
        val state = app.simulation.state.value
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(notificationModel(state)), foregroundType())
            app.backgroundAvailability.reportStarted()
        } catch (e: Exception) {
            // 例如未符合 health 類型的啟動前提（SecurityException）或背景啟動限制
            Log.w(TAG, "startForeground failed", e)
            app.backgroundAvailability.reportStartFailed()
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PAUSE -> app.simulation.pause()
            ACTION_RESUME -> app.simulation.resume()
            ACTION_STOP -> app.simulation.reset()
        }

        // 程序被殺後重啟等情況：模擬不在需要前景的狀態，立刻停止，不留空通知
        if (!shouldRunForeground(state.status)) {
            stopForegroundAndSelf(state.status)
            return START_NOT_STICKY
        }

        if (observeJob == null) {
            observeJob = scope.launch {
                app.simulation.state.collect { current ->
                    if (shouldRunForeground(current.status)) {
                        notify(buildNotification(notificationModel(current)))
                    } else {
                        stopForegroundAndSelf(current.status)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf(status: SimulationStatus) {
        if (status == SimulationStatus.COMPLETED) {
            // 完成：結束前景，但留下一則可滑掉的「已完成」通知
            notify(buildNotification(notificationModel(app.simulation.state.value)))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notify(notification: Notification) {
        // 通知權限被拒時不顯示於通知欄（前景服務仍會出現在工作管理員），不可因此崩潰
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify failed", e)
        }
    }

    private fun buildNotification(model: NotificationModel?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        if (model == null) {
            return builder.setContentTitle(getString(R.string.notif_title_starting)).setOngoing(true).build()
        }
        val progress = getString(
            R.string.notif_progress,
            "%,d".format(model.writtenSteps),
            "%,d".format(model.targetSteps),
            model.percent,
        )
        val (title, text) = when (model.kind) {
            NotificationKind.RUNNING -> getString(
                R.string.notif_title_running,
                getString(if (model.gait == Gait.WALK) R.string.sim_gait_walk else R.string.sim_gait_run),
                model.gait.stepsPerMinute,
            ) to progress
            NotificationKind.PAUSED -> getString(R.string.notif_title_paused) to progress
            NotificationKind.FAILED ->
                getString(R.string.notif_title_failed, model.failedBatchNumber ?: 0) to getString(R.string.notif_text_failed)
            NotificationKind.PERMISSION_REVOKED ->
                getString(R.string.notif_title_permission) to getString(R.string.notif_text_permission)
            NotificationKind.COMPLETED -> getString(R.string.notif_title_completed) to progress
        }
        builder.setContentTitle(title)
            .setContentText(text)
            .setOngoing(model.ongoing)
            .setAutoCancel(!model.ongoing)
        if (model.kind == NotificationKind.RUNNING || model.kind == NotificationKind.PAUSED) {
            builder.setProgress(100, model.percent, false)
        }
        for (action in model.actions) {
            val label = when (action) {
                NotificationAction.PAUSE -> R.string.notif_action_pause
                NotificationAction.RESUME -> R.string.notif_action_resume
                NotificationAction.STOP -> R.string.notif_action_stop
            }
            builder.addAction(0, getString(label), actionIntent(action))
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun actionIntent(action: NotificationAction): PendingIntent {
        val intentAction = when (action) {
            NotificationAction.PAUSE -> ACTION_PAUSE
            NotificationAction.RESUME -> ACTION_RESUME
            NotificationAction.STOP -> ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            action.ordinal + 1,
            Intent(this, SimulationService::class.java).setAction(intentAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "SimulationService"
        private const val CHANNEL_ID = "simulation"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PAUSE = "com.seeyu90.stepsim.action.PAUSE"
        private const val ACTION_RESUME = "com.seeyu90.stepsim.action.RESUME"
        private const val ACTION_STOP = "com.seeyu90.stepsim.action.STOP"

        /** 啟動前景服務；失敗時記錄為無法在背景執行，不崩潰。 */
        fun start(context: Context, availability: BackgroundAvailability) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SimulationService::class.java))
            } catch (e: Exception) {
                // Android 12+ 背景啟動限制為 ForegroundServiceStartNotAllowedException（IllegalStateException 子類）
                Log.w(TAG, "startForegroundService failed", e)
                availability.reportStartFailed()
            }
        }
    }
}
