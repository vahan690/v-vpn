package com.vvpn.android.payment

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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

    // Create BSC payment
    suspend fun createBscPayment(planId: String, deviceId: String, token: String): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BSC_BASE_URL/api/create-order")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val jsonBody = JSONObject().apply {
                put("deviceId", deviceId)
                put("planId", planId)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Create BSC payment response ($responseCode): $responseBody")

            if (responseCode == 200) {
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
            val url = URL("$BSC_BASE_URL/api/check-payment/$orderId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Check BSC payment status response ($responseCode): $responseBody")

            if (responseCode == 200) {
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
            val url = URL("$LICENSE_API_URL/api/license/verify/$deviceId/$licenseKey")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Verify license response ($responseCode): $responseBody")

            if (responseCode == 200) {
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
            val url = URL("$LICENSE_API_URL/api/license/verify-and-link")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val jsonBody = JSONObject().apply {
                put("licenseKey", licenseKey)
                put("deviceId", deviceId)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Verify and link license response ($responseCode): $responseBody")

            if (responseCode == 200) {
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
