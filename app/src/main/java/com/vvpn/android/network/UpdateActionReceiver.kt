package com.vvpn.android.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.vvpn.android.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * UpdateActionReceiver handles notification action button clicks
 * Processes Update, Skip, and Cancel Download actions
 */
class UpdateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Logs.d("UpdateActionReceiver: Received action: ${intent.action}")

        when (intent.action) {
            UpdateNotificationManager.ACTION_UPDATE -> {
                handleUpdateAction(context, intent)
            }
            UpdateNotificationManager.ACTION_SKIP -> {
                handleSkipAction(context)
            }
            UpdateNotificationManager.ACTION_CANCEL_DOWNLOAD -> {
                handleCancelDownloadAction(context)
            }
        }
    }

    /**
     * Handle Update button click - start download and installation
     */
    private fun handleUpdateAction(context: Context, intent: Intent) {
        val updateInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UpdateNotificationManager.EXTRA_UPDATE_INFO, UpdateInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UpdateNotificationManager.EXTRA_UPDATE_INFO)
        }

        if (updateInfo == null) {
            Logs.e("UpdateActionReceiver: UpdateInfo is null")
            Toast.makeText(context, "Update information not available", Toast.LENGTH_SHORT).show()
            return
        }

        Logs.d("UpdateActionReceiver: Starting update download for ${updateInfo.versionName}")
        Toast.makeText(context, "Starting update download...", Toast.LENGTH_SHORT).show()

        // Cancel the update available notification
        UpdateNotificationManager.cancelNotification(context)

        // Use application context to avoid lifecycle issues
        val appContext = context.applicationContext

        // Start download in background using GlobalScope to survive receiver lifecycle
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            try {
                Logs.d("UpdateActionReceiver: Coroutine started, calling downloadAndInstall")
                val result = UpdateManager.downloadAndInstall(
                    context = appContext,
                    updateInfo = updateInfo,
                    onProgress = { progress ->
                        // Update notification with progress
                        UpdateNotificationManager.showDownloadProgressNotification(appContext, progress)
                    }
                )

                if (result.first) {
                    Logs.d("UpdateActionReceiver: Update download completed successfully")
                    UpdateNotificationManager.showDownloadCompleteNotification(appContext)
                } else {
                    Logs.e("UpdateActionReceiver: Update download failed: ${result.second}")
                    UpdateNotificationManager.showDownloadErrorNotification(
                        appContext,
                        result.second ?: "Download failed"
                    )
                }
            } catch (e: Exception) {
                Logs.e("UpdateActionReceiver: Exception during update", e)
                UpdateNotificationManager.showDownloadErrorNotification(
                    appContext,
                    e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Handle Skip button click - dismiss notification
     */
    private fun handleSkipAction(context: Context) {
        Logs.d("UpdateActionReceiver: User skipped update")
        UpdateNotificationManager.cancelNotification(context)
        Toast.makeText(context, "Update skipped", Toast.LENGTH_SHORT).show()
    }

    /**
     * Handle Cancel Download button click - cancel ongoing download
     */
    private fun handleCancelDownloadAction(context: Context) {
        Logs.d("UpdateActionReceiver: User cancelled download")
        UpdateManager.cancelDownload(context)
        UpdateNotificationManager.cancelNotification(context)
        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
    }
}
