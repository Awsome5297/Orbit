package com.ai.assistance.operit.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ForegroundServiceCompat

/**
 * 媒体生成期间保活。任务本身由 MediaGenerationRunner 的应用级作用域执行。
 */
class MediaGenerationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceCompat.startForeground(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = createNotification(),
            types = ForegroundServiceCompat.buildTypes(dataSync = true),
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_generation_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.media_generation_notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.media_generation_notification_title))
            .setContentText(getString(R.string.media_generation_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_simple_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "media_generation"
        private const val NOTIFICATION_ID = 4211

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MediaGenerationService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaGenerationService::class.java))
        }
    }
}
