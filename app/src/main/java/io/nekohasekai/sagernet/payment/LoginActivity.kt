package io.nekohasekai.sagernet.payment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.nekohasekai.sagernet.R
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
    private lateinit var paymentManager: PaymentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager(this)
        paymentManager = PaymentManager(this)

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
                    Log.d("LoginActivity", "Login successful for ${response.email}")
                    
                    // Check for existing license on this device
                    checkExistingLicense()
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

    private fun checkExistingLicense() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val licenseManager = LicenseManager(this@LoginActivity)
                val existingLicense = licenseManager.getLicenseInfo()
                val licenseKey = existingLicense["license_key"] ?: ""

                if (licenseKey.isNotEmpty()) {
                    // Verify existing license with backend
                    val deviceId = paymentManager.getDeviceId()
                    val verifyResponse = withContext(Dispatchers.IO) {
                        paymentManager.verifyLicense(licenseKey, deviceId)
                    }

                    if (verifyResponse.success && verifyResponse.isValid) {
                        Log.d("LoginActivity", "Existing license is valid")
                        
                        // Update license info if needed
                        if (verifyResponse.expiryDate != null && verifyResponse.planId != null) {
                            licenseManager.saveLicense(
                                licenseKey,
                                deviceId,
                                verifyResponse.expiryDate,
                                verifyResponse.planId,
                                authManager.getUserEmail()
                            )
                        }
                        
                        setResult(RESULT_OK)
                        finish()
                        return@launch
                    } else {
                        Log.d("LoginActivity", "Existing license is invalid: ${verifyResponse.message}")
                    }
                }

                // No valid license found, return to main screen
                Log.d("LoginActivity", "No valid license found")
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e("LoginActivity", "Error checking license", e)
                // Still return OK since login was successful
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
