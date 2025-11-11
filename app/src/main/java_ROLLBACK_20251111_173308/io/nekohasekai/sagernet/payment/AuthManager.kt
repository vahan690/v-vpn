package io.nekohasekai.sagernet.payment

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val BASE_URL = "https://tron.vvpn.space"
        private const val PREFS_NAME = "vpn_auth"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_FULL_NAME = "full_name"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class AuthResponse(
        val success: Boolean,
        val token: String?,
        val userId: Int?,
        val email: String?,
        val fullName: String?,
        val message: String?
    )

    data class User(
        val id: Int,
        val email: String,
        val fullName: String?
    )

    data class LicenseData(
        val licenseKey: String,
        val deviceId: String,
        val planId: String,
        val expiryDate: String
    )

    data class ActiveLicenseResponse(
        val success: Boolean,
        val hasLicense: Boolean,
        val license: LicenseData?
    )

    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_TOKEN, null) != null
    }

    // Get JWT token
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    // Get current user
    fun getCurrentUser(): User? {
        val userId = prefs.getInt(KEY_USER_ID, -1)
        val email = prefs.getString(KEY_EMAIL, null)

        return if (userId != -1 && email != null) {
            User(userId, email, prefs.getString(KEY_FULL_NAME, null))
        } else {
            null
        }
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_EMAIL, "") ?: ""
    }

    // NEW METHOD: Get stored email (nullable version)
    fun getStoredEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    // Save auth data
    private fun saveAuth(token: String, userId: Int, email: String, fullName: String?) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            putString(KEY_FULL_NAME, fullName)
            apply()
        }
    }

    // Clear auth data (logout)
    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "User logged out")
    }

    // Register new user
    suspend fun register(email: String, password: String, fullName: String?): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/auth/register")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", password)
                if (fullName != null) put("full_name", fullName)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Register response ($responseCode): $responseBody")

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                val token = json.getString("token")
                val user = json.getJSONObject("user")

                val userId = user.getInt("id")
                val userEmail = user.getString("email")
                val userFullName = user.optString("full_name", null)

                saveAuth(token, userId, userEmail, userFullName)

                AuthResponse(
                    success = true,
                    token = token,
                    userId = userId,
                    email = userEmail,
                    fullName = userFullName,
                    message = "Account created successfully"
                )
            } else {
                val json = JSONObject(responseBody)
                AuthResponse(
                    success = false,
                    token = null,
                    userId = null,
                    email = null,
                    fullName = null,
                    message = json.optString("error", "Registration failed")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error: ${e.message}", e)
            AuthResponse(
                success = false,
                token = null,
                userId = null,
                email = null,
                fullName = null,
                message = e.message ?: "Network error"
            )
        }
    }

    // Login user
    suspend fun login(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/auth/login")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Login response ($responseCode): $responseBody")

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                val token = json.getString("token")
                val user = json.getJSONObject("user")

                val userId = user.getInt("id")
                val userEmail = user.getString("email")
                val userFullName = user.optString("full_name", null)

                saveAuth(token, userId, userEmail, userFullName)

                AuthResponse(
                    success = true,
                    token = token,
                    userId = userId,
                    email = userEmail,
                    fullName = userFullName,
                    message = "Login successful"
                )
            } else {
                val json = JSONObject(responseBody)
                AuthResponse(
                    success = false,
                    token = null,
                    userId = null,
                    email = null,
                    fullName = null,
                    message = json.optString("error", "Login failed")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}", e)
            AuthResponse(
                success = false,
                token = null,
                userId = null,
                email = null,
                fullName = null,
                message = e.message ?: "Network error"
            )
        }
    }

    // Fetch user's active license from backend
    suspend fun fetchActiveLicense(): ActiveLicenseResponse = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext ActiveLicenseResponse(
                success = false,
                hasLicense = false,
                license = null
            )

            val url = URL("$BASE_URL/api/auth/active-license")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Fetch active license response ($responseCode): $responseBody")

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                val hasLicense = json.getBoolean("has_license")

                if (hasLicense && !json.isNull("license")) {
                    val licenseJson = json.getJSONObject("license")
                    ActiveLicenseResponse(
                        success = true,
                        hasLicense = true,
                        license = LicenseData(
                            licenseKey = licenseJson.getString("license_key"),
                            deviceId = licenseJson.getString("device_id"),
                            planId = licenseJson.getString("plan_id"),
                            expiryDate = licenseJson.getString("expiry_date")
                        )
                    )
                } else {
                    ActiveLicenseResponse(
                        success = true,
                        hasLicense = false,
                        license = null
                    )
                }
            } else {
                ActiveLicenseResponse(
                    success = false,
                    hasLicense = false,
                    license = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active license: ${e.message}", e)
            ActiveLicenseResponse(
                success = false,
                hasLicense = false,
                license = null
            )
        }
    }
}
