package com.vvpn.android.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vvpn.android.R
import com.vvpn.android.network.DeviceManager
import kotlinx.coroutines.*

class PaymentActivity : AppCompatActivity() {

    private lateinit var networkSelector: RadioGroup
    private lateinit var bscRadio: RadioButton
    private lateinit var monthlyButton: Button
    private lateinit var yearlyButton: Button
    private lateinit var enterLicenseButton: Button
    private lateinit var logoutButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    private lateinit var authManager: AuthManager
    private lateinit var paymentManager: PaymentManager
    private lateinit var licenseManager: LicenseManager

    private var currentOrderId: String? = null
    private var pollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide action bar to prevent overlap issues
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_payment)

        authManager = AuthManager(this)
        paymentManager = PaymentManager(this)
        licenseManager = LicenseManager(this)

        networkSelector = findViewById(R.id.networkSelector)
        bscRadio = findViewById(R.id.bscRadio)
        monthlyButton = findViewById(R.id.monthlyButton)
        yearlyButton = findViewById(R.id.yearlyButton)
        enterLicenseButton = findViewById(R.id.enterLicenseButton)
        logoutButton = findViewById(R.id.logoutButton)
        progressBar = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)

        // Always select BSC (no TRON option)
        bscRadio.isChecked = true
        networkSelector.visibility = View.VISIBLE

        monthlyButton.setOnClickListener {
            createPaymentOrder("monthly")
        }

        yearlyButton.setOnClickListener {
            createPaymentOrder("yearly")
        }

        enterLicenseButton.setOnClickListener {
            showEnterLicenseDialog()
        }

        logoutButton.setOnClickListener {
            handleLogout()
        }
    }

    private fun createPaymentOrder(planId: String) {
        val token = authManager.getAuthToken()
        if (token == null) {
            showError("Please login first")
            return
        }

        progressBar.visibility = View.VISIBLE
        monthlyButton.isEnabled = false
        yearlyButton.isEnabled = false
        errorText.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val deviceId = paymentManager.getDeviceId()
                val result = withContext(Dispatchers.IO) {
                    paymentManager.createBscPayment(planId, deviceId, token)
                }

                progressBar.visibility = View.GONE
                monthlyButton.isEnabled = true
                yearlyButton.isEnabled = true

                if (result.isSuccess) {
                    val order = result.getOrNull()!!
                    currentOrderId = order.orderId
                    showBscPaymentDialog(order)
                } else {
                    showError(result.exceptionOrNull()?.message ?: "Failed to create order")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                monthlyButton.isEnabled = true
                yearlyButton.isEnabled = true
                showError("Error: ${e.message}")
                Log.e("PaymentActivity", "Order creation error", e)
            }
        }
    }

    private fun showBscPaymentDialog(order: PaymentManager.PaymentOrder) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_bsc_payment, null)

        val networkText = dialogView.findViewById<TextView>(R.id.networkText)
        val contractText = dialogView.findViewById<TextView>(R.id.contractText)
        val amountText = dialogView.findViewById<TextView>(R.id.amountText)
        val addressText = dialogView.findViewById<TextView>(R.id.addressText)
        val qrCodeImage = dialogView.findViewById<ImageView>(R.id.qrCodeImage)
        val copyButton = dialogView.findViewById<Button>(R.id.copyAddressButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val statusText = dialogView.findViewById<TextView>(R.id.statusText)

        networkText.text = "Network: ${order.network} (BEP20)"
        contractText.text = "Contract: ${order.usdtContract}"
        amountText.text = "${order.amount} ${order.currency}"
        addressText.text = order.paymentAddress

        // Generate QR Code
        try {
            val qrBitmap = generateQRCode(order.paymentAddress, 512, 512)
            qrCodeImage.setImageBitmap(qrBitmap)
        } catch (e: Exception) {
            Log.e("PaymentActivity", "QR generation error", e)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BSC Address", order.paymentAddress)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Address copied!", Toast.LENGTH_SHORT).show()
        }

        cancelButton.setOnClickListener {
            pollingJob?.cancel()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            pollingJob?.cancel()
        }

        dialog.show()

        // Start polling for payment status
        startPaymentPolling(order.orderId, statusText, dialog)
    }

    private fun generateQRCode(content: String, width: Int, height: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    private fun startPaymentPolling(orderId: String, statusText: TextView, dialog: AlertDialog) {
        pollingJob = CoroutineScope(Dispatchers.Main).launch {
            var attempts = 0
            val maxAttempts = 180 // 30 minutes (10 sec intervals)

            while (attempts < maxAttempts && isActive) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        paymentManager.checkPaymentStatus(orderId)
                    }

                    if (response.success) {
                        when (response.status) {
                            "completed" -> {
                                if (response.licenseKey != null) {
                                    saveLicenseAndFinish(response.licenseKey)
                                    dialog.dismiss()
                                    return@launch
                                }
                            }
                            "pending" -> {
                                statusText.text = "Waiting for payment on BSC..."
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PaymentActivity", "Polling error", e)
                }

                attempts++
                delay(10000) // Check every 10 seconds
            }

            if (attempts >= maxAttempts) {
                statusText.text = "Payment timeout. Please try again."
            }
        }
    }

    private fun saveLicenseAndFinish(licenseKey: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val deviceId = paymentManager.getDeviceId()
                val token = authManager.getAuthToken()
                val currentUser = authManager.getCurrentUser()

                if (token == null || currentUser == null) {
                    showError("Authentication error. Please login again.")
                    return@launch
                }

                // Use verifyAndLinkLicense to bind the license to the user
                val response = withContext(Dispatchers.IO) {
                    paymentManager.verifyAndLinkLicense(
                        licenseKey,
                        deviceId,
                        currentUser.id,
                        currentUser.email,
                        token
                    )
                }

                if (response.isValid && response.expiryDate != null && response.planId != null) {
                    licenseManager.saveLicense(
                        licenseKey,
                        deviceId,
                        response.expiryDate,
                        response.planId,
                        currentUser.email
                    )

                // Show success message and stay on payment screen
                runOnUiThread {
                    Toast.makeText(this@PaymentActivity, "License activated successfully!", Toast.LENGTH_LONG).show()
                }
                // Auto-close after 2 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    setResult(RESULT_OK)
                    finish()
                }, 2000)
                } else {
                    showError("License verification failed")
                }
            } catch (e: Exception) {
                Log.e("PaymentActivity", "License save error", e)
                showError("Error saving license: ${e.message}")
            }
        }
    }

    private fun showEnterLicenseDialog() {
        val input = EditText(this)
        input.hint = "XXXX-XXXX-XXXX-XXXX"

        AlertDialog.Builder(this)
            .setTitle("Enter License Key")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val licenseKey = input.text.toString().trim().uppercase()
                if (licenseKey.isNotEmpty()) {
                    verifyManualLicense(licenseKey)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyManualLicense(licenseKey: String) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val deviceId = paymentManager.getDeviceId()
                val token = authManager.getAuthToken()
                val currentUser = authManager.getCurrentUser()

                if (token == null || currentUser == null) {
                    progressBar.visibility = View.GONE
                    showError("Authentication error. Please login again.")
                    return@launch
                }

                // Use verifyAndLinkLicense to bind the license to the user
                val response = withContext(Dispatchers.IO) {
                    paymentManager.verifyAndLinkLicense(
                        licenseKey,
                        deviceId,
                        currentUser.id,
                        currentUser.email,
                        token
                    )
                }

                progressBar.visibility = View.GONE

                if (response.isValid && response.expiryDate != null && response.planId != null) {
                    licenseManager.saveLicense(
                        licenseKey,
                        deviceId,
                        response.expiryDate,
                        response.planId,
                        currentUser.email
                    )

                // Show success message and stay on payment screen
                runOnUiThread {
                    Toast.makeText(this@PaymentActivity, "License activated successfully!", Toast.LENGTH_LONG).show()
                }
                // Auto-close after 2 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    setResult(RESULT_OK)
                    finish()
                }, 2000)
                } else {
                    showError(response.message ?: "Invalid license key")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showError("Verification error: ${e.message}")
                Log.e("PaymentActivity", "License verification error", e)
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun handleLogout() {
        AlertDialog.Builder(this)
            .setTitle("Switch Account")
            .setMessage("Are you sure you want to logout and switch to a different account?")
            .setPositiveButton("Yes") { _, _ ->
                // Clear auth and license data
                authManager.logout()
                DeviceManager.resetState()
                licenseManager.clearLicense()

                // Cancel any ongoing operations
                pollingJob?.cancel()

                // Navigate back to login
                val intent = android.content.Intent(this, LoginActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}
