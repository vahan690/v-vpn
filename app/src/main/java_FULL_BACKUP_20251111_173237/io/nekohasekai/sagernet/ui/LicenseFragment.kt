package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.payment.LicenseManager
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
        val expiryDateStr = licenseInfo["expiry_date"] ?: "0"
        val planId = licenseInfo["plan_id"] ?: "Unknown"
        val boundEmail = licenseInfo["bound_email"] ?: "No account"  // ← ADD THIS LINE

        // Format expiry date
        val expiryDate = try {
            val expiryMillis = expiryDateStr.toLong()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            dateFormat.format(Date(expiryMillis))
        } catch (e: Exception) {
            "Invalid date"
        }

        // Calculate days remaining
        val daysRemaining = try {
            val expiryMillis = expiryDateStr.toLong()
            val currentMillis = System.currentTimeMillis()
            val daysLeft = ((expiryMillis - currentMillis) / (1000 * 60 * 60 * 24)).toInt()
            if (daysLeft > 0) "$daysLeft days" else "Expired"
        } catch (e: Exception) {
            "Unknown"
        }

        // Update UI
        view.findViewById<TextView>(R.id.license_key_text).text = licenseKey
        view.findViewById<TextView>(R.id.expiry_date_text).text = expiryDate
        view.findViewById<TextView>(R.id.days_remaining_text).text = daysRemaining
        view.findViewById<TextView>(R.id.plan_text).text = planId
        view.findViewById<TextView>(R.id.bound_email_text).text = boundEmail  // ← ADD THIS LINE

        // Status indicator
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
