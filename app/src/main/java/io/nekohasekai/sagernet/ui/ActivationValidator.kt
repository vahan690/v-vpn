package io.nekohasekai.sagernet.ui

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ActivationValidator(private val context: Context) {

    companion object {
        private const val ACTIVATION_CHECK_URL = "https://vpn-api.vahansahakyan.com/api/check-activation"
        private const val TAG = "ActivationValidator"
    }

    data class ActivationCheckResponse(
        val isValid: Boolean,
        val message: String,
        val expiresAt: String? = null,
        val remainingDays: Int = 0
    )

    suspend fun validateActivationWithServer(): ActivationCheckResponse {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = generateDeviceId()
                Log.d(TAG, "Checking activation for device: $deviceId")

                val url = URL(ACTIVATION_CHECK_URL)
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000

                // Create JSON payload
                val jsonPayload = """
                    {
                        "device_id": "$deviceId"
                    }
                """.trimIndent()

                // Send request
                connection.outputStream.use { os ->
                    os.write(jsonPayload.toByteArray())
                }

                // Read response
                val responseCode = connection.responseCode
                val responseText = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Server error"
                }

                Log.d(TAG, "Server response: $responseCode - $responseText")

                if (responseCode == 200) {
                    parseActivationCheckResponse(responseText)
                } else {
                    ActivationCheckResponse(
                        isValid = false,
                        message = "Server error: $responseCode"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Network error during activation check: ${e.message}")
                ActivationCheckResponse(
                    isValid = false,
                    message = "Network error: ${e.message}"
                )
            }
        }
    }

    private fun parseActivationCheckResponse(json: String): ActivationCheckResponse {
        return try {
            val isValid = json.contains("\"is_valid\":true")
            
            val messageStart = json.indexOf("\"message\":\"") + 11
            val messageEnd = json.indexOf("\"", messageStart)
            val message = if (messageStart > 10 && messageEnd > messageStart) {
                json.substring(messageStart, messageEnd)
            } else {
                "Unknown response"
            }

            val expiresAt = if (json.contains("\"expires_at\":\"")) {
                val start = json.indexOf("\"expires_at\":\"") + 14
                val end = json.indexOf("\"", start)
                if (end > start) json.substring(start, end) else null
            } else null

            val remainingDays = if (json.contains("\"remaining_days\":")) {
                val start = json.indexOf("\"remaining_days\":") + 17
                val end = json.indexOfAny(charArrayOf(',', '}'), start)
                if (end > start) {
                    json.substring(start, end).toIntOrNull() ?: 0
                } else 0
            } else 0

            ActivationCheckResponse(isValid, message, expiresAt, remainingDays)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing activation response: ${e.message}")
            ActivationCheckResponse(false, "Failed to parse server response")
        }
    }

    fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceInfo = "${androidId}_${android.os.Build.MODEL}_${android.os.Build.MANUFACTURER}"
        return hashDeviceInfo(deviceInfo).take(12)
    }

    private fun hashDeviceInfo(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
