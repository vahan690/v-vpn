package com.vvpn.android.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vvpn.android.R

/**
 * UpdateNotificationManager handles update notifications
 * Shows notifications with "Update" and "Skip" action buttons
 */
object UpdateNotificationManager {

    private const val CHANNEL_ID = "vvpn_updates"
    private const val CHANNEL_NAME = "App Updates"
    private const val NOTIFICATION_ID = 1001

    const val ACTION_UPDATE = "com.vvpn.android.ACTION_UPDATE"
    const val ACTION_SKIP = "com.vvpn.android.ACTION_SKIP"
    const val ACTION_CANCEL_DOWNLOAD = "com.vvpn.android.ACTION_CANCEL_DOWNLOAD"

    const val EXTRA_UPDATE_INFO = "update_info"

    /**
     * Create notification channel for updates (Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about app updates"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show update available notification with action buttons
     */
    fun showUpdateAvailableNotification(context: Context, updateInfo: UpdateInfo) {
        createNotificationChannel(context)

        // Create intents for action buttons
        val updateIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_UPDATE
            putExtra(EXTRA_UPDATE_INFO, updateInfo)
        }
        val updatePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_SKIP
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format file size
        val sizeStr = UpdateChecker.formatFileSize(updateInfo.apkSize)

        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_enhanced_encryption)
            .setContentTitle("V-VPN Update Available")
            .setContentText("Version ${updateInfo.versionName} is ready to install")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Version ${updateInfo.versionName} (${updateInfo.versionCode})\n\n" +
                        "Size: $sizeStr\n" +
                        "Released: ${formatDate(updateInfo.releaseDate)}\n\n" +
                        "${updateInfo.releaseNotes}"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(updateInfo.forceUpdate) // Cannot dismiss if forced update
            .addAction(
                R.drawable.ic_baseline_download_24,
                "Update",
                updatePendingIntent
            )
            .apply {
                // Only show Skip button if not a forced update
                if (!updateInfo.forceUpdate) {
                    addAction(
                        R.drawable.ic_navigation_close,
                        "Skip",
                        skipPendingIntent
                    )
                }
            }
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Show download progress notification
     */
    fun showDownloadProgressNotification(context: Context, progress: Int) {
        createNotificationChannel(context)

        val cancelIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_enhanced_encryption)
            .setContentTitle("Downloading V-VPN Update")
            .setContentText("Download in progress: $progress%")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_navigation_close,
                "Cancel",
                cancelPendingIntent
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Show download complete notification (ready to install)
     */
    fun showDownloadCompleteNotification(context: Context) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_enhanced_encryption)
            .setContentTitle("Update Ready")
            .setContentText("Tap to install V-VPN update")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Show download error notification
     */
    fun showDownloadErrorNotification(context: Context, errorMessage: String) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_enhanced_encryption)
            .setContentTitle("Update Failed")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Cancel notification
     */
    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Format date for display
     */
    private fun formatDate(dateStr: String): String {
        return try {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(dateStr)
            java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(date ?: return dateStr)
        } catch (e: Exception) {
            dateStr
        }
    }
}
