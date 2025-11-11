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
        private const val BASE_URL = "https://api.vvpn.space"  // ← UPDATED
        private const val PREFS_NAME = "vpn_auth"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class AuthResponse(
        val success: Boolean,
        val token: String?,
        val userId: Int?,
        val email: String?,
        val message: String?
    )

    data class User(
        val id: Int,
        val email: String
    )

    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_TOKEN, null) != null
    }

    // Get JWT token
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    // Get auth token (alias for Android app compatibility)
    fun getAuthToken(): String? {
        return getToken()
    }

    // Get current user
    fun getCurrentUser(): User? {
        val userId = prefs.getInt(KEY_USER_ID, -1)
        val email = prefs.getString(KEY_EMAIL, null)

        return if (userId != -1 && email != null) {
            User(userId, email)
        } else {
            null
        }
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_EMAIL, "") ?: ""
    }

    fun getStoredEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    // Save auth data
    private fun saveAuth(token: String, userId: Int, email: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            apply()
        }
        Log.d(TAG, "Auth saved for user: $email")
    }

    // Clear auth data (logout)
    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "User logged out")
    }

    // Register new user
    suspend fun register(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/auth/register")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

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
                if (responseCode in 200..201) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Register response ($responseCode): $responseBody")

            if (responseCode in 200..201) {
                val json = JSONObject(responseBody)
                val token = json.getString("token")
                val user = json.getJSONObject("user")

                val userId = user.getInt("id")
                val userEmail = user.getString("email")

                saveAuth(token, userId, userEmail)

                AuthResponse(
                    success = true,
                    token = token,
                    userId = userId,
                    email = userEmail,
                    message = "Account created successfully"
                )
            } else {
                val json = JSONObject(responseBody)
                AuthResponse(
                    success = false,
                    token = null,
                    userId = null,
                    email = null,
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
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

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

                saveAuth(token, userId, userEmail)

                AuthResponse(
                    success = true,
                    token = token,
                    userId = userId,
                    email = userEmail,
                    message = "Login successful"
                )
            } else {
                val json = JSONObject(responseBody)
                AuthResponse(
                    success = false,
                    token = null,
                    userId = null,
                    email = null,
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
                message = e.message ?: "Network error"
            )
        }
    }
}
