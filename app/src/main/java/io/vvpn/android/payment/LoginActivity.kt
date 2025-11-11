package io.vvpn.android.payment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.vvpn.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var forgotPasswordButton: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager(this)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        forgotPasswordButton = findViewById(R.id.forgotPasswordButton)
        progressBar = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (validateInput(email, password)) {
                login(email, password)
            }
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordButton.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email")
            return false
        }

        if (password.isEmpty()) {
            showError("Please enter your password")
            return false
        }

        return true
    }

    private fun login(email: String, password: String) {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false
        registerButton.isEnabled = false
        forgotPasswordButton.isEnabled = false
        errorText.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    authManager.login(email, password)
                }

                progressBar.visibility = View.GONE
                loginButton.isEnabled = true
                registerButton.isEnabled = true
                forgotPasswordButton.isEnabled = true

                if (response.success) {
                    // Login successful, now fetch and restore license
                    Log.d("LoginActivity", "Login successful for ${response.email}")
                    fetchAndRestoreLicense()
                } else {
                    showError(response.message ?: "Login failed")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true
                registerButton.isEnabled = true
                forgotPasswordButton.isEnabled = true
                showError("Network error: ${e.message}")
                Log.e("LoginActivity", "Login error", e)
            }
        }
    }

private fun fetchAndRestoreLicense() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val licenseResponse = withContext(Dispatchers.IO) {
                authManager.fetchActiveLicense()
            }

            if (licenseResponse.success && licenseResponse.hasLicense && licenseResponse.license != null) {
                val license = licenseResponse.license!!
                Log.d("LoginActivity", "License found: ${license.licenseKey}")

                // Restore license locally
                val licenseManager = LicenseManager(this@LoginActivity)

                // Calculate expiry (30 days from now for monthly)
                val expiryMillis = if (license.planId == "monthly") {
                    System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                } else {
                    System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)
                }

                val email = emailInput.text.toString().trim()

                val currentDeviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                licenseManager.saveLicense(
                    license.licenseKey,
                    currentDeviceId,
                    expiryMillis.toString(),
                    license.planId,
                    email
                )

                Log.d("LoginActivity", "License saved successfully")

                // Verify it was saved
                val savedInfo = licenseManager.getLicenseInfo()
                Log.d("LoginActivity", "Verification - license_key: ${savedInfo["license_key"]}")
                Log.d("LoginActivity", "Verification - expiry_date: ${savedInfo["expiry_date"]}")
                Log.d("LoginActivity", "Verification - isValid: ${licenseManager.isLicenseValid()}")

                // Wait a bit longer to ensure everything is saved
                kotlinx.coroutines.delay(800)

                // Return success to MainActivity
                setResult(RESULT_OK)
                finish()
            } else {
                // No active license, go to payment screen
                Log.d("LoginActivity", "No active license found")
                setResult(RESULT_OK)
                finish()
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error fetching license", e)
            // On error, still return OK since login was successful
            setResult(RESULT_OK)
            finish()
        }
    }
}

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}
