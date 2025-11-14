package com.vvpn.android.payment

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.*

class LicenseManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "vpn_license_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveLicense(licenseKey: String, deviceId: String, expiryDate: String, planId: String, userEmail: String) {
        val editor = prefs.edit()
        editor.putString("license_key", licenseKey)
        editor.putString("device_id", deviceId)
        editor.putString("expiry_date", expiryDate)
        editor.putString("plan_id", planId)
        editor.putString("user_email", userEmail)
        editor.apply()

        Log.d("LicenseManager", "License saved: $licenseKey for user: $userEmail")
    }

    fun isLicenseValid(): Boolean {
        val licenseKey = prefs.getString("license_key", null)
        val expiryDate = prefs.getString("expiry_date", null)

        if (licenseKey == null || expiryDate == null) {
            Log.d("LicenseManager", "No license found")
            return false
        }

        return try {
            // Try parsing as milliseconds first (our format)
            val expiryMillis = expiryDate.toLongOrNull()
            if (expiryMillis != null) {
                val now = System.currentTimeMillis()
                val isValid = expiryMillis > now
                Log.d("LicenseManager", "License valid: $isValid (expires: ${Date(expiryMillis)})")
                return isValid
            }

            // Fallback: try parsing as ISO date string (backend format)
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val expiry = format.parse(expiryDate)
            val now = Date()
            val isValid = expiry?.after(now) ?: false
            Log.d("LicenseManager", "License valid: $isValid (expires: $expiryDate)")
            isValid
        } catch (e: Exception) {
            Log.e("LicenseManager", "Error parsing expiry date: ${e.message}")
            false
        }
    }

    fun getLicenseInfo(): Map<String, String> {
        return mapOf(
            "license_key" to (prefs.getString("license_key", "") ?: ""),
            "device_id" to (prefs.getString("device_id", "") ?: ""),
            "expiry_date" to (prefs.getString("expiry_date", "") ?: ""),
            "plan_id" to (prefs.getString("plan_id", "") ?: ""),
            "user_email" to (prefs.getString("user_email", "") ?: "")
        )
    }

    fun clearLicense() {
        prefs.edit().clear().apply()
        Log.d("LicenseManager", "License cleared")
    }

    fun getDaysRemaining(): Int {
        val expiryDate = prefs.getString("expiry_date", null) ?: return 0

        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val expiry = format.parse(expiryDate)
            val now = Date()
            val diff = expiry!!.time - now.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }
}
