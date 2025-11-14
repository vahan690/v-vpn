package com.vvpn.android.payment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vvpn.android.R
import com.vvpn.android.network.ResetPasswordRequest
import com.vvpn.android.network.RetrofitClient
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var etToken: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnReset: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvMessage: TextView

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_reset_password)

    initViews()

    // Handle deep link token from Intent data (when opened from email)
    intent.data?.let { uri ->
        val token = uri.getQueryParameter("token")
        token?.let {
            etToken.setText(it)
            Toast.makeText(this, "Reset code loaded from email!", Toast.LENGTH_LONG).show()
        }
    }
    
    // Also handle token from extras (when opened from ForgotPasswordActivity)
    intent.getStringExtra("token")?.let {
        etToken.setText(it)
        Toast.makeText(this, "Reset code loaded from email", Toast.LENGTH_SHORT).show()
    }

    setupListeners()
}

    private fun initViews() {
        etToken = findViewById(R.id.etToken)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnReset = findViewById(R.id.btnReset)
        progressBar = findViewById(R.id.progressBar)
        tvMessage = findViewById(R.id.tvMessage)

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        btnReset.setOnClickListener {
            val token = etToken.text.toString().trim().replace("\\s".toRegex(), "")
            val password = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (validateInputs(token, password, confirmPassword)) {
                resetPassword(token, password)
            }
        }
    }

    private fun validateInputs(token: String, password: String, confirmPassword: String): Boolean {
        return when {
            token.isEmpty() -> {
                etToken.error = "Reset code is required"
                false
            }
            token.length != 64 -> {
                etToken.error = "Invalid reset code"
                false
            }
            password.isEmpty() -> {
                etNewPassword.error = "Password is required"
                false
            }
            password.length < 8 -> {
                etNewPassword.error = "Password must be at least 8 characters"
                false
            }
            password != confirmPassword -> {
                etConfirmPassword.error = "Passwords do not match"
                false
            }
            else -> true
        }
    }

    private fun resetPassword(token: String, password: String) {
        progressBar.visibility = View.VISIBLE
        btnReset.isEnabled = false
        tvMessage.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.resetPassword(
                    ResetPasswordRequest(token, password)
                )

                progressBar.visibility = View.GONE
                btnReset.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        "Password reset successfully!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Go back to login
                    val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()

                } else {
                    tvMessage.apply {
                        text = response.body()?.message ?: "Failed to reset password"
                        setTextColor(getColor(android.R.color.holo_red_dark))
                        visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnReset.isEnabled = true

                tvMessage.apply {
                    text = "Network error: ${e.localizedMessage}"
                    setTextColor(getColor(android.R.color.holo_red_dark))
                    visibility = View.VISIBLE
                }
            }
        }
    }
}
