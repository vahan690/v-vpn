package com.vvpn.android.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.vvpn.android.R
import com.vvpn.android.payment.LicenseManager
import java.text.SimpleDateFormat
import java.util.*

class LicenseFragment : ToolbarFragment(R.layout.fragment_license) {

    private lateinit var licenseManager: LicenseManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle("License Information")

        licenseManager = LicenseManager(requireContext())
        val licenseInfo = licenseManager.getLicenseInfo()

        val licenseKey = licenseInfo["license_key"] ?: "No license"
        val expiryDateStr = licenseInfo["expiry_date"] ?: ""
        val planId = licenseInfo["plan_id"] ?: "Unknown"
        val boundEmail = licenseInfo["user_email"] ?: "No account"  // ✅ Fixed key name

        // Format expiry date - handle ISO date string properly
        val expiryDate = try {
            if (expiryDateStr.isNotEmpty()) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(expiryDateStr)
                val displayFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                displayFormat.format(date)
            } else {
                "Invalid date"
            }
        } catch (e: Exception) {
            "Invalid date"
        }

        // Calculate days remaining using LicenseManager method
        val daysRemaining = try {
            val days = licenseManager.getDaysRemaining()
            if (days > 0) "$days days" else "Expired"
        } catch (e: Exception) {
            "Unknown"
        }

        // Update UI
        view.findViewById<TextView>(R.id.license_key_text).text = licenseKey
        view.findViewById<TextView>(R.id.expiry_date_text).text = expiryDate
        view.findViewById<TextView>(R.id.days_remaining_text).text = daysRemaining
        view.findViewById<TextView>(R.id.plan_text).text = planId.capitalize()
        view.findViewById<TextView>(R.id.bound_email_text).text = boundEmail  // ✅ Fixed

        // Status indicator using LicenseManager validation
        val statusText = view.findViewById<TextView>(R.id.license_status_text)
        if (licenseManager.isLicenseValid()) {
            statusText.text = "✓ Active"
            statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            statusText.text = "✗ Expired"
            statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        }
    }
}
