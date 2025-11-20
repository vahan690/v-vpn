package com.vvpn.android.network

import android.content.Context
import com.vvpn.android.BuildConfig
import com.vvpn.android.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UpdateChecker checks for app updates from the API server
 * Compares current version with server version to determine if update is available
 */
object UpdateChecker {

    /**
     * Check if an update is available
     * @return UpdateInfo if update available, null otherwise
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val apiService = RetrofitClient.apiService
            val response = apiService.getAppVersion()

            if (response.isSuccessful) {
                val versionResponse = response.body()

                if (versionResponse?.success == true && versionResponse.version != null) {
                    val serverVersion = versionResponse.version
                    val currentVersionCode = BuildConfig.VERSION_CODE

                    Logs.d("UpdateChecker: Current version: $currentVersionCode")
                    Logs.d("UpdateChecker: Server version: ${serverVersion.versionCode}")

                    // Check if server version is newer
                    if (serverVersion.versionCode > currentVersionCode) {
                        Logs.d("UpdateChecker: Update available!")
                        return@withContext UpdateInfo(
                            versionCode = serverVersion.versionCode,
                            versionName = serverVersion.versionName,
                            apkUrl = serverVersion.apkUrl,
                            apkHash = serverVersion.apkHash,
                            apkSize = serverVersion.apkSize ?: 0,
                            releaseNotes = serverVersion.releaseNotes,
                            forceUpdate = serverVersion.forceUpdate,
                            releaseDate = serverVersion.releaseDate
                        )
                    } else {
                        Logs.d("UpdateChecker: App is up to date")
                        return@withContext null
                    }
                } else {
                    Logs.e("UpdateChecker: API returned success=false or missing version data")
                    return@withContext null
                }
            } else {
                Logs.e("UpdateChecker: API error: HTTP ${response.code()}")
                return@withContext null
            }
        } catch (e: Exception) {
            Logs.e("UpdateChecker: Exception during update check", e)
            return@withContext null
        }
    }

    /**
     * Get formatted file size
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }
}

/**
 * Information about available update
 */
@kotlinx.parcelize.Parcelize
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkHash: String?,
    val apkSize: Long,
    val releaseNotes: String,
    val forceUpdate: Boolean,
    val releaseDate: String
) : android.os.Parcelable
