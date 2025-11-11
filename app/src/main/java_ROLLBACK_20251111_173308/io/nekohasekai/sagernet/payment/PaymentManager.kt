package io.nekohasekai.sagernet.payment

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
        private const val BASE_URL = "https://tron.vvpn.space"
    }

    data class PaymentOrder(
        val orderId: String,
        val paymentAddress: String,
        val amount: String,
        val currency: String,
        val planId: String
    )

    data class LicenseResponse(
        val success: Boolean,
        val licenseKey: String?,
        val deviceId: String?,
        val planId: String?,
        val expiryDate: String?,
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

    data class PaymentStatusResponse(
        val success: Boolean,
        val status: String,
        val licenseKey: String?,
        val message: String?
    )

    suspend fun createPayment(planId: String, deviceId: String, userId: Int? = null, userEmail: String? = null): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/payment/create")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

	    val jsonBody = JSONObject().apply {
	        put("device_id", deviceId)
	        put("plan_id", planId)
	        put("plan_name", if (planId == "monthly") "Monthly Plan" else "Yearly Plan")
	        put("amount_usd", if (planId == "monthly") 5.0 else 50.0)
	        if (userId != null) put("user_id", userId)
	        if (userEmail != null) put("user_email", userEmail)
	    }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Create payment response ($responseCode): $responseBody")

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                Result.success(PaymentOrder(
                    orderId = json.getString("order_id"),
                    paymentAddress = json.getString("payment_address"),
                    amount = json.getDouble("amount_usd").toString(),
                    currency = json.optString("currency", "USDT"),
                    planId = planId
                ))
            } else {
                val json = JSONObject(responseBody)
                Result.failure(Exception(json.optString("error", "Unknown error")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun checkPaymentStatus(orderId: String): PaymentStatusResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/order/$orderId/status")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Check payment status response ($responseCode): $responseBody")

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                PaymentStatusResponse(
                    success = true,
                    status = json.getString("status"),
                    licenseKey = json.optString("license_key", null),
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
            Log.e(TAG, "Error checking payment status: ${e.message}", e)
            PaymentStatusResponse(
                success = false,
                status = "error",
                licenseKey = null,
                message = e.message
            )
        }
    }

    suspend fun verifyLicense(licenseKey: String, deviceId: String): VerifyLicenseResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/license/verify/$deviceId/$licenseKey")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val responseBody = BufferedReader(InputStreamReader(
                if (responseCode == 200) connection.inputStream else connection.errorStream
            )).use { it.readText() }

            Log.d(TAG, "Verify license response ($responseCode): $responseBody")

            if (responseCode == 200) {
                val json = JSONObject(responseBody)
                VerifyLicenseResponse(
                    success = json.getBoolean("success"),
                    isValid = json.optBoolean("isValid", json.optBoolean("is_valid", false)),
                    message = json.optString("message", null),
                    licenseKey = json.optString("license_key", null),
                    planId = json.optString("plan_id", null),
                    expiryDate = json.optString("expiry_date", null)
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

    fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
// Verify and link license to user account
suspend fun verifyAndLinkLicense(licenseKey: String, deviceId: String, userId: Int?, userEmail: String): VerifyLicenseResponse = withContext(Dispatchers.IO) {
    try {
        val url = URL("$BASE_URL/api/license/verify-and-link")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        val jsonBody = JSONObject().apply {
            put("license_key", licenseKey)
            put("device_id", deviceId)
            if (userId != null) put("user_id", userId)
	    put("user_email", userEmail)
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
                isValid = json.optBoolean("isValid", json.optBoolean("is_valid", false)),
                message = json.optString("message", null),
                licenseKey = json.optString("license_key", null),
                planId = json.optString("plan_id", null),
                expiryDate = json.optString("expiry_date", null)
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
}
