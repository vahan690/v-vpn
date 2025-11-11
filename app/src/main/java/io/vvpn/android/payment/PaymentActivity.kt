package io.vvpn.android.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.vvpn.android.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.*

class PaymentActivity : AppCompatActivity() {

    private lateinit var monthlyButton: Button
    private lateinit var yearlyButton: Button
    private lateinit var activateLicenseButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var paymentManager: PaymentManager
    private lateinit var licenseManager: LicenseManager
    private lateinit var authManager: AuthManager

    private var paymentCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        paymentManager = PaymentManager(this)
        licenseManager = LicenseManager(this)
        authManager = AuthManager(this)

        monthlyButton = findViewById(R.id.monthlyButton)
        yearlyButton = findViewById(R.id.yearlyButton)
        activateLicenseButton = findViewById(R.id.activateLicenseButton)
        progressBar = findViewById(R.id.progressBar)

        monthlyButton.setOnClickListener {
            createPayment("monthly")
        }

        yearlyButton.setOnClickListener {
            createPayment("yearly")
        }

        activateLicenseButton.setOnClickListener {
            showLicenseInputDialog()
        }
    }

    private fun createPayment(planId: String) {
        progressBar.visibility = View.VISIBLE
        monthlyButton.isEnabled = false
        yearlyButton.isEnabled = false
        activateLicenseButton.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                // Get user info from AuthManager
                val user = authManager.getCurrentUser()

                val result = withContext(Dispatchers.IO) {
                    paymentManager.createPayment(planId, deviceId, user?.id, user?.email)
                }

                progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    val order = result.getOrNull()!!
                    showPaymentDialog(
                        order.paymentAddress,
                        order.amount,
                        order.orderId,
                        planId
                    )
                } else {
                    showError(result.exceptionOrNull()?.message ?: "Failed to create payment")
                    monthlyButton.isEnabled = true
                    yearlyButton.isEnabled = true
                    activateLicenseButton.isEnabled = true
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                monthlyButton.isEnabled = true
                yearlyButton.isEnabled = true
                activateLicenseButton.isEnabled = true
                showError("Network error: ${e.message}")
                Log.e("PaymentActivity", "Error creating payment", e)
            }
        }
    }

    private fun showPaymentDialog(address: String, amount: String, orderId: String, planId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_payment, null)

        val addressText = dialogView.findViewById<TextView>(R.id.paymentAddress)
        val amountText = dialogView.findViewById<TextView>(R.id.paymentAmount)
        val qrCodeImage = dialogView.findViewById<ImageView>(R.id.qrCodeImage)
        val copyButton = dialogView.findViewById<Button>(R.id.copyAddressButton)
        val statusText = dialogView.findViewById<TextView>(R.id.paymentStatus)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        addressText.text = address
        amountText.text = "$amount USDT"

        // Generate QR code
        generateQRCode(address, qrCodeImage)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Send Payment")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Payment Address", address)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        cancelButton.setOnClickListener {
            paymentCheckJob?.cancel()
            dialog.dismiss()
            monthlyButton.isEnabled = true
            yearlyButton.isEnabled = true
            activateLicenseButton.isEnabled = true
        }

        dialog.setOnDismissListener {
            paymentCheckJob?.cancel()
        }

        dialog.show()

        // Start checking for payment
        startPaymentCheck(orderId, planId, statusText, dialog)
    }

    private fun generateQRCode(text: String, imageView: ImageView) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("PaymentActivity", "Error generating QR code", e)
        }
    }

    private fun startPaymentCheck(orderId: String, planId: String, statusText: TextView, dialog: AlertDialog) {
        statusText.text = "Waiting for payment..."

        paymentCheckJob = CoroutineScope(Dispatchers.Main).launch {
            var attempts = 0
            val maxAttempts = 60 // 5 minutes (60 * 5 seconds)

            while (attempts < maxAttempts && isActive) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        paymentManager.checkPaymentStatus(orderId)
                    }

                    if (response.success && response.status == "completed") {
                        // Payment successful!
                        statusText.text = "Payment confirmed! Activating license..."

                        // Activate license
                        val licenseKey = response.licenseKey
                        if (licenseKey != null) {
                            val deviceId = android.provider.Settings.Secure.getString(
                                contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            )

                            // Calculate expiry date
                            val expiryMillis = if (planId == "monthly") {
                                System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                            } else {
                                System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)
                            }

                            // NEW: Save license with user email
                            val userEmail = authManager.getUserEmail()
                            licenseManager.saveLicense(
                                licenseKey, 
                                deviceId, 
                                expiryMillis.toString(), 
                                planId,
                                authManager.getStoredEmail() ?: "email@example.com"  // ← Use actual email
                            )

                            Log.d("PaymentActivity", "License saved successfully: $licenseKey for user: $userEmail")

                            dialog.dismiss()
                            showSuccess()
                        } else {
                            statusText.text = "Error: No license key received"
                        }
                        break
                    } else if (response.status == "expired") {
                        statusText.text = "Payment expired. Please try again."
                        delay(2000)
                        dialog.dismiss()
                        monthlyButton.isEnabled = true
                        yearlyButton.isEnabled = true
                        activateLicenseButton.isEnabled = true
                        break
                    }

                    attempts++
                    delay(5000) // Check every 5 seconds

                } catch (e: Exception) {
                    Log.e("PaymentActivity", "Error checking payment", e)
                    delay(5000)
                }
            }

            if (attempts >= maxAttempts) {
                statusText.text = "Payment check timeout. Please contact support if you sent payment."
            }
        }
    }

    private fun showLicenseInputDialog() {
        val input = EditText(this)
        input.hint = "Enter license key"

        AlertDialog.Builder(this)
            .setTitle("Enter License Key")
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val licenseKey = input.text.toString().trim()
                if (licenseKey.isNotEmpty()) {
                    activateLicense(licenseKey)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

private fun activateLicense(licenseKey: String) {
    progressBar.visibility = View.VISIBLE
    monthlyButton.isEnabled = false
    yearlyButton.isEnabled = false
    activateLicenseButton.isEnabled = false

    CoroutineScope(Dispatchers.Main).launch {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

            // Get user info
            val user = authManager.getCurrentUser()
            val userEmail = authManager.getUserEmail()

            val response = withContext(Dispatchers.IO) {
                paymentManager.verifyAndLinkLicense(licenseKey, deviceId, user?.id, userEmail)
            }

// ADD THIS LOGGING
Log.d("PaymentActivity", "Response received:")
Log.d("PaymentActivity", "  success: ${response.success}")
Log.d("PaymentActivity", "  isValid: ${response.isValid}")
Log.d("PaymentActivity", "  message: ${response.message}")
Log.d("PaymentActivity", "  licenseKey: ${response.licenseKey}")

            progressBar.visibility = View.GONE

if (response.success && response.isValid) {
    // Calculate expiry - simple 30 days for monthly
    val expiryMillis = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)

    // Save license
    licenseManager.saveLicense(
        licenseKey,
        deviceId,
        expiryMillis.toString(),
        response.planId ?: "monthly",
        userEmail
    )

    Log.d("PaymentActivity", "License activated: $licenseKey")

    // Show success dialog
    AlertDialog.Builder(this@PaymentActivity)
        .setTitle("Success!")
        .setMessage("Your VPN license has been activated successfully!")
        .setCancelable(false)
        .setPositiveButton("Continue") { _, _ ->
            // Start MainActivity directly instead of using result
            val intent = Intent(this@PaymentActivity, io.vvpn.android.ui.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        .show()
                
            } else {
                showError(response.message ?: "Invalid or expired license key")
                monthlyButton.isEnabled = true
                yearlyButton.isEnabled = true
                activateLicenseButton.isEnabled = true
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            monthlyButton.isEnabled = true
            yearlyButton.isEnabled = true
            activateLicenseButton.isEnabled = true
            showError("Error: ${e.message}")
            Log.e("PaymentActivity", "Error verifying license", e)
        }
    }
}

    private fun showSuccess() {
        progressBar.visibility = View.GONE

        Toast.makeText(this, "Subscription activated successfully!", Toast.LENGTH_LONG).show()

        // Return to MainActivity
        setResult(RESULT_OK)
        finish()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentCheckJob?.cancel()
    }
}
