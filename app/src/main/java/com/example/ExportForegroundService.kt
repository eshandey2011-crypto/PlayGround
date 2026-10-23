package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process alive (foreground service + progress notification) while a
 * video export runs, so switching apps or locking the screen does not kill the
 * mix/encode work. The export itself still runs in the ViewModel.
 */
class ExportForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        when (intent?.action) {
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            else -> startInForeground(0, "Preparing export…")
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(progress: Int, status: String) {
        val notification = buildNotification(this, progress, status)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // If the OS refuses (e.g. notification permission), keep running
            // without the foreground promotion rather than crashing.
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "video_export_progress"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_START = "com.example.export.START"
        private const val ACTION_STOP = "com.example.export.STOP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ExportForegroundService::class.java).setAction(ACTION_START)
                )
            } catch (_: Exception) {
            }
        }

        /**
         * Updates the existing foreground notification directly via the
         * NotificationManager. This avoids a second startForegroundService()
         * call, which Android rejects once the app is in the background.
         */
        fun update(context: Context, progressFraction: Float, status: String) {
            try {
                ensureChannel(context)
                val progress = (progressFraction.coerceIn(0f, 1f) * 100).toInt()
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, buildNotification(context, progress, status))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ExportForegroundService::class.java))
            } catch (_: Exception) {
            }
        }

        private fun buildNotification(context: Context, progress: Int, status: String): Notification {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Exporting video")
                .setContentText(status)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress <= 0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows video export progress in the background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
