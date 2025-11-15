package com.vvpn.android.payment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vvpn.android.R
import com.vvpn.android.ui.MainActivity
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

                    // Clear any cached license from previous user
                    val licenseManager = LicenseManager(this@LoginActivity)
                    licenseManager.clearLicense()
                    Log.d("LoginActivity", "Cleared cached license, launching MainActivity to fetch fresh data")

                    // Launch MainActivity with FROM_LOGIN flag
                    val intent = android.content.Intent(this@LoginActivity, MainActivity::class.java)
                    intent.putExtra("FROM_LOGIN", true)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
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

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}
