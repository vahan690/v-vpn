package com.vvpn.android.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.vvpn.android.ktx.Logs
import com.vvpn.android.payment.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DeviceManager handles device registration and single-connection enforcement
 * with the V-VPN License API
 */
object DeviceManager {

    private var currentDeviceId: String? = null
    private var isDeviceConnected: Boolean = false
    private var heartbeatJob: Job? = null
    private var heartbeatContext: Context? = null

    // Heartbeat interval in milliseconds (30 seconds)
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    /**
     * Get the unique device ID for this device
     * Uses Android's ANDROID_ID which is unique per app and persists across app reinstalls
     */
    fun getDeviceId(context: Context): String {
        if (currentDeviceId != null) {
            return currentDeviceId!!
        }

        currentDeviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return currentDeviceId!!
    }

    /**
     * Get device name (model name)
     */
    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model.capitalize()
        } else {
            "$manufacturer $model".capitalize()
        }
    }

    /**
     * Register device connection with the API
     * This MUST be called before starting VPN to enforce single-connection rule
     *
     * @return true if connection allowed, false if rejected (409 - another device already connected)
     * @throws Exception if API error occurs
     */
    suspend fun connectDevice(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId(context)
            val deviceName = getDeviceName()
            val authManager = AuthManager(context)
            val token = authManager.getToken()

            if (token.isNullOrEmpty()) {
                Logs.e("❌ DeviceManager: No auth token found, cannot register device")
                throw Exception("Not authenticated. Please login first.")
            }

            val apiService = RetrofitClient.apiService
            val request = ConnectDeviceRequest(deviceId, deviceName)

            Logs.d("📱 DeviceManager: Registering device connection...")
            Logs.d("   Device ID: $deviceId")
            Logs.d("   Device Name: $deviceName")

            val response = apiService.connectDevice("Bearer $token", request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    isDeviceConnected = true
                    heartbeatContext = context.applicationContext
                    startHeartbeat(context.applicationContext)
                    Logs.d("✅ DeviceManager: Device connected successfully")
                    return@withContext true
                } else {
                    Logs.e("❌ DeviceManager: API returned success=false: ${body?.error}")
                    throw Exception(body?.error ?: "Failed to connect device")
                }
            } else if (response.code() == 409) {
                // HTTP 409 Conflict - Another device is already connected
                val errorBody = response.errorBody()?.string()
                Logs.w("⚠️ DeviceManager: Connection rejected (409) - another device is active")
                Logs.w("   Response: $errorBody")
                isDeviceConnected = false
                return@withContext false
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Logs.e("❌ DeviceManager: API error: $errorMsg")
                throw Exception("Failed to register device: $errorMsg")
            }
        } catch (e: Exception) {
            Logs.e("❌ DeviceManager: Exception during device connection", e)
            isDeviceConnected = false
            throw e
        }
    }

    /**
     * Unregister device connection from the API
     * This should be called when VPN disconnects
     */
    suspend fun disconnectDevice(context: Context) = withContext(Dispatchers.IO) {
        try {
            // Stop heartbeat first
            stopHeartbeat()

            if (!isDeviceConnected) {
                Logs.d("📱 DeviceManager: Device not marked as connected, skipping disconnect")
                return@withContext
            }

            val deviceId = getDeviceId(context)
            val authManager = AuthManager(context)
            val token = authManager.getToken()

            if (token.isNullOrEmpty()) {
                Logs.w("⚠️ DeviceManager: No auth token, cannot disconnect device properly")
                isDeviceConnected = false
                return@withContext
            }

            val apiService = RetrofitClient.apiService
            val request = DisconnectDeviceRequest(deviceId)

            Logs.d("📱 DeviceManager: Disconnecting device...")
            Logs.d("   Device ID: $deviceId")

            val response = apiService.disconnectDevice("Bearer $token", request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    isDeviceConnected = false
                    Logs.d("✅ DeviceManager: Device disconnected successfully")
                } else {
                    Logs.w("⚠️ DeviceManager: Disconnect returned success=false: ${body?.message}")
                    isDeviceConnected = false
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Logs.w("⚠️ DeviceManager: Disconnect API error (continuing anyway): $errorMsg")
                isDeviceConnected = false
            }
        } catch (e: Exception) {
            Logs.w("⚠️ DeviceManager: Exception during disconnect (continuing anyway)", e)
            isDeviceConnected = false
        }
    }

    /**
     * Check if this device is currently marked as connected
     */
    fun isConnected(): Boolean = isDeviceConnected

    /**
     * Reset connection state (for testing/debugging)
     */
    fun resetState() {
        stopHeartbeat()
        isDeviceConnected = false
    }

    /**
     * Start sending periodic heartbeats to keep the connection alive
     */
    private fun startHeartbeat(context: Context) {
        // Cancel any existing heartbeat job
        heartbeatJob?.cancel()

        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            Logs.d("💓 DeviceManager: Starting heartbeat service (interval: ${HEARTBEAT_INTERVAL_MS}ms)")

            while (isDeviceConnected) {
                try {
                    delay(HEARTBEAT_INTERVAL_MS)

                    if (!isDeviceConnected) {
                        Logs.d("💓 DeviceManager: Device no longer connected, stopping heartbeat")
                        break
                    }

                    sendHeartbeat(context)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Logs.d("💓 DeviceManager: Heartbeat job cancelled")
                        break
                    }
                    Logs.e("💓 DeviceManager: Heartbeat error", e)
                }
            }

            Logs.d("💓 DeviceManager: Heartbeat service stopped")
        }
    }

    /**
     * Stop sending heartbeats
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        heartbeatContext = null
        Logs.d("💓 DeviceManager: Heartbeat stopped")
    }

    /**
     * Send a single heartbeat to the server
     */
    private suspend fun sendHeartbeat(context: Context) {
        try {
            val deviceId = getDeviceId(context)
            val authManager = AuthManager(context)
            val token = authManager.getToken()

            if (token.isNullOrEmpty()) {
                Logs.w("💓 DeviceManager: No auth token for heartbeat")
                return
            }

            val apiService = RetrofitClient.apiService
            val request = HeartbeatRequest(deviceId)

            val response = apiService.sendHeartbeat("Bearer $token", request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    Logs.d("💓 DeviceManager: Heartbeat sent successfully")
                } else {
                    Logs.w("💓 DeviceManager: Heartbeat failed: ${body?.message}")
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Logs.w("💓 DeviceManager: Heartbeat API error: $errorMsg")
            }
        } catch (e: Exception) {
            Logs.e("💓 DeviceManager: Exception sending heartbeat", e)
        }
    }
}
