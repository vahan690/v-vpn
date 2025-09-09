package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ActivityActivationBinding
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*

class ActivationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivationBinding
    private val activationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()

        // Enhanced activation check - validate integrity
        if (isActivated() && !isExpired() && validateActivationIntegrity()) {
            startMainActivity()
            return
        } else if (isActivated()) {
            // Clear invalid activation data
            clearInvalidActivation()
        }
    }

    private fun setupUI() {
        binding.activateButton.setOnClickListener {
            val code = binding.activationCodeInput.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter activation code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            validateActivationCode(code)
        }

        binding.deviceIdText.text = "Device ID: ${generateDeviceId()}"
    }

    private fun validateActivationIntegrity(): Boolean {
        try {
            val currentDeviceId = generateDeviceId()
            val storedDeviceId = DataStore.deviceId
            
            // Check device ID consistency
            if (storedDeviceId.isEmpty() || currentDeviceId != storedDeviceId) {
                Log.w("ActivationActivity", "Device ID mismatch - Current: $currentDeviceId, Stored: $storedDeviceId")
                return false
            }
            
            // Check if activation code hash exists
            if (DataStore.activationCodeHash.isEmpty()) {
                Log.w("ActivationActivity", "Missing activation code hash")
                return false
            }
            
            Log.d("ActivationActivity", "Activation integrity validation passed")
            return true
        } catch (e: Exception) {
            Log.e("ActivationActivity", "Error validating activation integrity: ${e.message}")
            return false
        }
    }

    private fun clearInvalidActivation() {
        DataStore.clearActivation()
        Toast.makeText(this, "Activation data was invalid and has been cleared. Please reactivate.", Toast.LENGTH_LONG).show()
        Log.i("ActivationActivity", "Cleared invalid activation data")
    }

    private fun validateActivationCode(code: String) {
        binding.activateButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        activationScope.launch {
            try {
                val isValid = withContext(Dispatchers.IO) {
                    validateCodeWithServer(code)
                }

                if (isValid) {
                    MaterialAlertDialogBuilder(this@ActivationActivity)
                        .setTitle("Activation Successful")
                        .setMessage("Your VPN service has been activated for 30 days!")
                        .setPositiveButton("Continue") { _, _ ->
                            startMainActivity()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    Toast.makeText(this@ActivationActivity,
                        "Invalid activation code", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ActivationActivity,
                    "Activation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.activateButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun validateCodeWithServer(code: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://vpn-api.vahansahakyan.com/api/activate")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000 // 15 seconds
                connection.readTimeout = 15000

                // Create JSON payload with current device ID
                val currentDeviceId = generateDeviceId()
                val jsonPayload = """
                    {
                        "code": "$code",
                        "device_id": "$currentDeviceId"
                    }
                """.trimIndent()

                Log.d("ActivationActivity", "Sending activation request for device: $currentDeviceId")

                // Send request
                connection.outputStream.use { os ->
                    os.write(jsonPayload.toByteArray())
                }

                // Read response
                val responseCode = connection.responseCode
                val responseText = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                }

                if (responseCode == 200) {
                    // Parse JSON response
                    val response = parseActivationResponse(responseText)
                    if (response.success) {
                        // Store server-provided expiry date with enhanced validation
                        saveActivationFromServer(code, response.expiresAt)
                        return@withContext true
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ActivationActivity, response.message, Toast.LENGTH_LONG).show()
                        }
                        return@withContext false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ActivationActivity, "Server error: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext false
                }

            } catch (e: Exception) {
                Log.e("ActivationActivity", "Network error during activation: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActivationActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
        }
    }

    data class ActivationResponse(
        val success: Boolean,
        val message: String,
        val expiresAt: String? = null
    )

    private fun parseActivationResponse(json: String): ActivationResponse {
        return try {
            val success = json.contains("\"success\":true")
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

            ActivationResponse(success, message, expiresAt)
        } catch (e: Exception) {
            Log.e("ActivationActivity", "Error parsing activation response: ${e.message}")
            ActivationResponse(false, "Failed to parse server response")
        }
    }

    private fun saveActivationFromServer(code: String, serverExpiresAt: String?) {
        val expirationTime = if (serverExpiresAt != null) {
            try {
                // Parse ISO 8601 date from server
                val serverDate = java.time.Instant.parse(serverExpiresAt)
                serverDate.toEpochMilli()
            } catch (e: Exception) {
                Log.w("ActivationActivity", "Failed to parse server expiry date, using default: ${e.message}")
                // Fallback to 30 days if parsing fails
                System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
            }
        } else {
            // Default 30 days
            System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        }

        val currentDeviceId = generateDeviceId()
        
        // Save activation with current device ID and enhanced logging
        DataStore.isActivated = true
        DataStore.activationExpiry = expirationTime
        DataStore.deviceId = currentDeviceId
        DataStore.activationCodeHash = hashCode(code)
        
        // Log successful activation for debugging
        Log.i("ActivationActivity", "Activation saved successfully")
        Log.d("ActivationActivity", "Device ID: $currentDeviceId")
        Log.d("ActivationActivity", "Expiry: ${java.util.Date(expirationTime)}")
        Log.d("ActivationActivity", "Code hash: ${hashCode(code).take(8)}...")
    }

    private fun saveActivation(code: String) {
        val expirationTime = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days
        val currentDeviceId = generateDeviceId()

        DataStore.isActivated = true
        DataStore.activationExpiry = expirationTime
        DataStore.deviceId = currentDeviceId
        DataStore.activationCodeHash = hashCode(code)
        
        Log.i("ActivationActivity", "Local activation saved with device ID: $currentDeviceId")
    }

    private fun isActivated(): Boolean {
        return DataStore.isActivated
    }

    private fun isExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expired = currentTime > DataStore.activationExpiry
        if (expired) {
            Log.d("ActivationActivity", "Activation has expired")
        }
        return expired
    }

    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceInfo = "${androidId}_${android.os.Build.MODEL}_${android.os.Build.MANUFACTURER}"
        return hashCode(deviceInfo).take(12) // Take first 12 characters of hash
    }

    private fun hashCode(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        activationScope.cancel()
    }

    // Additional debugging method - can be called to check current activation state
    private fun debugActivationState() {
        Log.d("ActivationActivity", "=== Activation Debug Info ===")
        Log.d("ActivationActivity", "Is Activated: ${DataStore.isActivated}")
        Log.d("ActivationActivity", "Device ID: ${DataStore.deviceId}")
        Log.d("ActivationActivity", "Current Device ID: ${generateDeviceId()}")
        Log.d("ActivationActivity", "Expiry: ${java.util.Date(DataStore.activationExpiry)}")
        Log.d("ActivationActivity", "Code Hash: ${DataStore.activationCodeHash.take(8)}...")
        Log.d("ActivationActivity", "Is Expired: ${isExpired()}")
        Log.d("ActivationActivity", "Integrity Valid: ${validateActivationIntegrity()}")
        Log.d("ActivationActivity", "===========================")
    }
}
