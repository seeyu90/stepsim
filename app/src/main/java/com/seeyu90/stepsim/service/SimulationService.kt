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
import com.seeyu90.stepsim.simulator.SimulationState
import com.seeyu90.stepsim.simulator.SimulationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 模擬執行中保持程序存活並顯示常駐通知（type = health）。模擬本身由 [StepSimApp.simulation] 持有，
 * 此服務只依 [reconcileForeground] 與 [notificationModel] 顯示通知、轉送通知按鈕的指令、停止自己；
 * 啟動由 [StepSimApp] 的對帳迴圈負責。
 */
class SimulationService : Service() {

    private val app get() = application as StepSimApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    /** 最近一次 onStartCommand 的 startId；只在主執行緒讀寫，於呼叫 stopSelf 的當下讀取。 */
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先呼叫 startForeground：以 startForegroundService 啟動後必須盡快進入前景
        lastStartId = startId
        val state = app.simulation.state.value
        val foregroundStarted = try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(notificationModel(state)), foregroundType())
            app.backgroundAvailability.reportStarted()
            true
        } catch (e: Exception) {
            // 例如未符合 health 類型的啟動前提（SecurityException）或背景啟動限制
            Log.w(TAG, "startForeground failed", e)
            app.backgroundAvailability.reportStartFailed(state.status)
            false
        }

        val outcome = startCommandOutcome(notificationAction(intent?.action), foregroundStarted, state.status)
        when (outcome.command) {
            NotificationAction.PAUSE -> app.simulation.pause()
            NotificationAction.RESUME -> app.simulation.resume()
            NotificationAction.STOP -> app.simulation.reset()
            null -> Unit
        }

        // 前景啟動失敗，或模擬不在需要前景的狀態（例如程序被殺後重啟）：立刻停止，不留空通知
        if (outcome.stopSelf) {
            if (foregroundStarted) stopForegroundAndSelf(state) else stopSelf(startId)
            return START_NOT_STICKY
        }

        if (observeJob == null) {
            observeJob = scope.launch {
                app.simulation.state.collect { current ->
                    val action = reconcileForeground(
                        current.status,
                        serviceAlive = true,
                        failedAt = app.backgroundAvailability.status.value.failedAt,
                    )
                    if (action == ServiceAction.STOP) {
                        stopForegroundAndSelf(current)
                    } else {
                        notify(buildNotification(notificationModel(current)))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        app.backgroundAvailability.reportStopped()
        super.onDestroy()
    }

    /**
     * [snapshot] 為觸發停止當下的狀態；不可在此重新讀取目前狀態（可能已被重設）。
     * 以最新的 startId 停止：App 若已送出新的啟動、而對應的 onStartCommand 尚未執行，stopSelf 不會生效，
     * 由新的 onStartCommand 重新進入前景後再判斷。
     */
    private fun stopForegroundAndSelf(snapshot: SimulationState) {
        val model = stopNotificationModel(snapshot)
        if (model != null) {
            // 完成：結束前景，但留下一則可滑掉的「已完成」通知
            notify(buildNotification(model))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf(lastStartId)
    }

    private fun notificationAction(intentAction: String?): NotificationAction? = when (intentAction) {
        ACTION_PAUSE -> NotificationAction.PAUSE
        ACTION_RESUME -> NotificationAction.RESUME
        ACTION_STOP -> NotificationAction.STOP
        else -> null
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

        /** 啟動前景服務；失敗時記錄為無法在背景執行，不崩潰。只能在主執行緒呼叫。 */
        fun start(context: Context, availability: BackgroundAvailability, status: SimulationStatus) {
            availability.reportStartRequested()
            try {
                ContextCompat.startForegroundService(context, Intent(context, SimulationService::class.java))
            } catch (e: Exception) {
                // Android 12+ 背景啟動限制為 ForegroundServiceStartNotAllowedException（IllegalStateException 子類）
                Log.w(TAG, "startForegroundService failed", e)
                availability.reportStartFailed(status)
            }
        }
    }
}
