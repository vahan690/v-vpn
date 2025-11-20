package com.vvpn.android.network

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.vvpn.android.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * UpdateManager handles APK download and installation
 * Downloads the APK using Android's DownloadManager and triggers installation
 */
object UpdateManager {

    private var downloadId: Long = -1
    private var onDownloadComplete: ((Boolean, String?) -> Unit)? = null

    /**
     * Download and install APK
     * @param context Application context
     * @param updateInfo Update information with APK URL
     * @param onProgress Progress callback (0-100)
     * @return Pair<Boolean, String?> - Success status and error message if failed
     */
    suspend fun downloadAndInstall(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: ((Int) -> Unit)? = null
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            Logs.d("UpdateManager: Starting APK download from ${updateInfo.apkUrl}")

            // Create downloads directory
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null) {
                Logs.e("UpdateManager: Cannot access downloads directory")
                return@withContext Pair(false, "Cannot access storage")
            }

            // Delete old APK if exists
            val apkFile = File(downloadsDir, "vvpn-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
                Logs.d("UpdateManager: Deleted old APK file")
            }

            // Start download using DownloadManager
            val downloadResult = startDownload(context, updateInfo, apkFile, onProgress)

            if (!downloadResult.first) {
                return@withContext downloadResult
            }

            // Verify hash if provided
            if (!updateInfo.apkHash.isNullOrEmpty()) {
                Logs.d("UpdateManager: Verifying APK hash...")
                val calculatedHash = calculateFileHash(apkFile)
                if (calculatedHash != updateInfo.apkHash) {
                    Logs.e("UpdateManager: Hash mismatch! Expected: ${updateInfo.apkHash}, Got: $calculatedHash")
                    apkFile.delete()
                    return@withContext Pair(false, "APK verification failed - corrupted download")
                }
                Logs.d("UpdateManager: Hash verification passed")
            }

            // Install APK
            Logs.d("UpdateManager: Starting APK installation")
            val installResult = installApk(context.applicationContext, apkFile)

            return@withContext installResult

        } catch (e: Exception) {
            Logs.e("UpdateManager: Error during download/install", e)
            return@withContext Pair(false, e.localizedMessage ?: "Unknown error")
        }
    }

    /**
     * Start APK download using DownloadManager
     */
    private suspend fun startDownload(
        context: Context,
        updateInfo: UpdateInfo,
        targetFile: File,
        onProgress: ((Int) -> Unit)?
    ): Pair<Boolean, String?> = suspendCancellableCoroutine { continuation ->
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
                .setTitle("V-VPN Update")
                .setDescription("Downloading V-VPN ${updateInfo.versionName}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(targetFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            downloadId = downloadManager.enqueue(request)
            Logs.d("UpdateManager: Download started, ID: $downloadId")

            // Register broadcast receiver for download completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        Logs.d("UpdateManager: Download completed")
                        context.unregisterReceiver(this)

                        // Check download status
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)

                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(statusIndex)

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    Logs.d("UpdateManager: Download successful")
                                    if (continuation.isActive) {
                                        continuation.resume(Pair(true, null))
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = cursor.getInt(reasonIndex)
                                    Logs.e("UpdateManager: Download failed, reason: $reason")
                                    if (continuation.isActive) {
                                        continuation.resume(Pair(false, "Download failed (code: $reason)"))
                                    }
                                }
                                else -> {
                                    Logs.e("UpdateManager: Unknown download status: $status")
                                    if (continuation.isActive) {
                                        continuation.resume(Pair(false, "Download incomplete"))
                                    }
                                }
                            }
                        } else {
                            Logs.e("UpdateManager: Download query returned no results")
                            if (continuation.isActive) {
                                continuation.resume(Pair(false, "Download tracking failed"))
                            }
                        }
                        cursor.close()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            // Optional: Monitor progress
            onProgress?.let { progressCallback ->
                Thread {
                    var downloading = true
                    while (downloading) {
                        try {
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = downloadManager.query(query)

                            if (cursor.moveToFirst()) {
                                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                                val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                                val bytesTotal = cursor.getLong(bytesTotalIndex)
                                val status = cursor.getInt(statusIndex)
                                val reason = cursor.getInt(reasonIndex)

                                Logs.d("UpdateManager: Status=$status, Reason=$reason, Downloaded=$bytesDownloaded/$bytesTotal")

                                if (bytesTotal > 0) {
                                    val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                                    progressCallback(progress)
                                    Logs.d("UpdateManager: Download progress: $progress%")
                                } else if (status == DownloadManager.STATUS_RUNNING) {
                                    // Server didn't provide content-length, show indeterminate
                                    Logs.d("UpdateManager: Download running but size unknown")
                                }

                                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                    downloading = false
                                    if (status == DownloadManager.STATUS_FAILED) {
                                        Logs.e("UpdateManager: Download failed with reason: $reason")
                                    }
                                }
                            } else {
                                Logs.e("UpdateManager: Cursor empty - download may have been removed")
                                downloading = false
                            }
                            cursor.close()
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            downloading = false
                        }
                    }
                }.start()
            }

            continuation.invokeOnCancellation {
                Logs.d("UpdateManager: Download cancelled")
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Receiver might not be registered
                }
                downloadManager.remove(downloadId)
            }

        } catch (e: Exception) {
            Logs.e("UpdateManager: Error starting download", e)
            if (continuation.isActive) {
                continuation.resume(Pair(false, e.localizedMessage ?: "Failed to start download"))
            }
        }
    }

    /**
     * Install APK using package installer
     * @return Pair<Boolean, String?> - Success status and error/info message
     */
    private fun installApk(context: Context, apkFile: File): Pair<Boolean, String?> {
        try {
            Logs.d("UpdateManager: Installing APK from ${apkFile.absolutePath}")
            Logs.d("UpdateManager: File exists: ${apkFile.exists()}, size: ${apkFile.length()}")

            if (!apkFile.exists()) {
                Logs.e("UpdateManager: APK file does not exist!")
                return Pair(false, "APK file not found")
            }

            // Check if we can install packages
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canInstall = context.packageManager.canRequestPackageInstalls()
                Logs.d("UpdateManager: canRequestPackageInstalls: $canInstall")
                if (!canInstall) {
                    Logs.w("UpdateManager: App doesn't have install permission, opening settings")
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    settingsIntent.data = Uri.parse("package:${context.packageName}")
                    settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(settingsIntent)
                    return Pair(false, "Please enable 'Install unknown apps' permission and try again")
                }
            }

            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For Android 7.0+ use FileProvider
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.cache",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            Logs.d("UpdateManager: APK URI: $apkUri")

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            intent.data = apkUri
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, false)

            context.startActivity(intent)
            Logs.d("UpdateManager: Installation intent launched successfully")
            return Pair(true, null)
        } catch (e: Exception) {
            Logs.e("UpdateManager: Error launching installer", e)
            return Pair(false, e.localizedMessage ?: "Failed to launch installer")
        }
    }

    /**
     * Calculate SHA-256 hash of a file
     */
    private fun calculateFileHash(file: File): String {
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logs.e("UpdateManager: Error calculating file hash", e)
            throw e
        }
    }

    /**
     * Cancel ongoing download
     */
    fun cancelDownload(context: Context) {
        if (downloadId != -1L) {
            try {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(downloadId)
                Logs.d("UpdateManager: Download cancelled")
                downloadId = -1L
            } catch (e: Exception) {
                Logs.e("UpdateManager: Error cancelling download", e)
            }
        }
    }
}
