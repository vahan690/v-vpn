package io.nekohasekai.sagernet.payment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import io.nekohasekai.sagernet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var registerButton: Button
    private lateinit var backToLoginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        authManager = AuthManager(this)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        registerButton = findViewById(R.id.registerButton)
        backToLoginButton = findViewById(R.id.backToLoginButton)
        progressBar = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (validateInput(email, password, confirmPassword)) {
                register(email, password)
            }
        }

        backToLoginButton.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email")
            return false
        }

        if (password.isEmpty()) {
            showError("Please enter a password")
            return false
        }

        if (password.length < 8) {
            showError("Password must be at least 8 characters")
            return false
        }

        if (password != confirmPassword) {
            showError("Passwords do not match")
            return false
        }

        return true
    }

    private fun register(email: String, password: String) {
        progressBar.visibility = View.VISIBLE
        registerButton.isEnabled = false
        backToLoginButton.isEnabled = false
        errorText.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    authManager.register(email, password)
                }

                progressBar.visibility = View.GONE
                registerButton.isEnabled = true
                backToLoginButton.isEnabled = true

                if (response.success) {
                    // Registration successful, new user has no license - go to payment screen
                    val intent = Intent(this@RegisterActivity, PaymentActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    showError(response.message ?: "Registration failed")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                registerButton.isEnabled = true
                backToLoginButton.isEnabled = true
                showError("Network error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}
