package com.vvpn.android.payment

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PaymentManager(private val context: Context) {

    companion object {
        private const val TAG = "PaymentManager"
        private const val BSC_BASE_URL = "https://bsc.vvpn.space"
        private const val LICENSE_API_URL = "https://api.vvpn.space"
    }

    data class PaymentOrder(
        val orderId: String,
        val paymentAddress: String,
        val amount: String,
        val currency: String,
        val planId: String,
        val usdtContract: String,
        val network: String,
        val chainId: Int
    )

    data class PaymentStatusResponse(
        val success: Boolean,
        val status: String,
        val licenseKey: String?,
        val message: String?
    )

    data class VerifyLicenseResponse(
        val success: Boolean,
        val isValid: Boolean,
        val message: String?,
        val licenseKey: String?,
        val planId: String?,
        val expiryDate: String?
    )

    data class ServerConfigResponse(
        val success: Boolean,
        val serverAddress: String,
        val serverPort: String,
        val authPayload: String,
        val obfuscation: String,
        val sni: String?,
        val allowInsecure: Boolean,
        val message: String?
    )

    // Fetch VPN server configuration from backend
    suspend fun fetchServerConfig(token: String): Result<ServerConfigResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$LICENSE_API_URL/api/server/config")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = SecureHttpClient.client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Fetch server config response (${response.code}): $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                Result.success(ServerConfigResponse(
                    success = json.getBoolean("success"),
                    serverAddress = json.getString("serverAddress"),
                    serverPort = json.getString("serverPort"),
                    authPayload = json.getString("authPayload"),
                    obfuscation = json.getString("obfuscation"),
                    sni = json.optString("sni", ""),
                    allowInsecure = json.optBoolean("allowInsecure", true),
                    message = null
                ))
            } else {
                val json = JSONObject(responseBody)
                Result.failure(Exception(json.optString("error", "Failed to fetch server config")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching server config: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Create BSC payment
    suspend fun createBscPayment(planId: String, deviceId: String, token: String): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("deviceId", deviceId)
                put("planId", planId)
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BSC_BASE_URL/api/create-order")
                .header("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = SecureHttpClient.client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Create BSC payment response (${response.code}): $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val order = json.getJSONObject("order")

                Result.success(PaymentOrder(
                    orderId = order.getString("orderId"),
                    paymentAddress = order.getString("paymentAddress"),
                    amount = order.getString("amount"),
                    currency = "USDT",
                    planId = order.getString("planId"),
                    usdtContract = order.getString("usdtContract"),
                    network = order.getString("network"),
                    chainId = order.getInt("chainId")
                ))
            } else {
                val json = JSONObject(responseBody)
                Result.failure(Exception(json.optString("error", "Unknown error")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating BSC payment: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Check BSC payment status
    suspend fun checkPaymentStatus(orderId: String): PaymentStatusResponse = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BSC_BASE_URL/api/check-payment/$orderId")
                .get()
                .build()

            val response = SecureHttpClient.client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Check BSC payment status response (${response.code}): $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val order = json.getJSONObject("order")

                val licenseKey = if (json.has("license") && !json.isNull("license")) {
                    json.getJSONObject("license").getString("key")
                } else null

                PaymentStatusResponse(
                    success = true,
                    status = order.getString("status"),
                    licenseKey = licenseKey,
                    message = null
                )
            } else {
                val json = JSONObject(responseBody)
                PaymentStatusResponse(
                    success = false,
                    status = "error",
                    licenseKey = null,
                    message = json.optString("error", "Unknown error")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking BSC payment status: ${e.message}", e)
            PaymentStatusResponse(
                success = false,
                status = "error",
                licenseKey = null,
                message = e.message
            )
        }
    }

    // Verify license
    suspend fun verifyLicense(licenseKey: String, deviceId: String): VerifyLicenseResponse = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$LICENSE_API_URL/api/license/verify/$deviceId/$licenseKey")
                .get()
                .build()

            val response = SecureHttpClient.client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Verify license response (${response.code}): $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                VerifyLicenseResponse(
                    success = json.getBoolean("success"),
                    isValid = json.getBoolean("isValid"),
                    message = json.optString("message", null),
                    licenseKey = if (json.has("license")) json.getJSONObject("license").optString("licenseKey") else null,
                    planId = if (json.has("license")) json.getJSONObject("license").optString("planId") else null,
                    expiryDate = if (json.has("license")) json.getJSONObject("license").optString("expiryDate") else null
                )
            } else {
                val json = JSONObject(responseBody)
                VerifyLicenseResponse(
                    success = false,
                    isValid = false,
                    message = json.optString("error", "Verification failed"),
                    licenseKey = null,
                    planId = null,
                    expiryDate = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying license: ${e.message}", e)
            VerifyLicenseResponse(
                success = false,
                isValid = false,
                message = e.message,
                licenseKey = null,
                planId = null,
                expiryDate = null
            )
        }
    }

    // Verify and link license
    suspend fun verifyAndLinkLicense(licenseKey: String, deviceId: String, userId: Int?, userEmail: String, token: String): VerifyLicenseResponse = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("licenseKey", licenseKey)
                put("deviceId", deviceId)
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$LICENSE_API_URL/api/license/verify-and-link")
                .header("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = SecureHttpClient.client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Verify and link license response (${response.code}): $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                VerifyLicenseResponse(
                    success = json.getBoolean("success"),
                    isValid = json.getBoolean("isValid"),
                    message = json.optString("message", null),
                    licenseKey = if (json.has("license")) json.getJSONObject("license").optString("licenseKey") else null,
                    planId = if (json.has("license")) json.getJSONObject("license").optString("planId") else null,
                    expiryDate = if (json.has("license")) json.getJSONObject("license").optString("expiryDate") else null
                )
            } else {
                val json = JSONObject(responseBody)
                VerifyLicenseResponse(
                    success = false,
                    isValid = false,
                    message = json.optString("error", "Verification failed"),
                    licenseKey = null,
                    planId = null,
                    expiryDate = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying and linking license: ${e.message}", e)
            VerifyLicenseResponse(
                success = false,
                isValid = false,
                message = e.message,
                licenseKey = null,
                planId = null,
                expiryDate = null
            )
        }
    }

    fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
}
