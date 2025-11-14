package com.vvpn.android.payment

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vvpn.android.R
import com.vvpn.android.network.ForgotPasswordRequest
import com.vvpn.android.network.RetrofitClient
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnSendCode: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        btnSendCode = findViewById(R.id.btnSendCode)
        progressBar = findViewById(R.id.progressBar)
        tvMessage = findViewById(R.id.tvMessage)

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        btnSendCode.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (validateEmail(email)) {
                sendResetCode(email)
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        return when {
            email.isEmpty() -> {
                etEmail.error = "Email is required"
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "Invalid email address"
                false
            }
            else -> true
        }
    }

    private fun sendResetCode(email: String) {
        progressBar.visibility = View.VISIBLE
        btnSendCode.isEnabled = false
        tvMessage.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.forgotPassword(
                    ForgotPasswordRequest(email)
                )

                progressBar.visibility = View.GONE
                btnSendCode.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    tvMessage.apply {
                        text = "Check your email for the reset code"
                        setTextColor(getColor(android.R.color.holo_green_dark))
                        visibility = View.VISIBLE
                    }

                    // Navigate to Reset Password screen
                    val intent = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java)
                    startActivity(intent)

                } else {
                    tvMessage.apply {
                        text = response.body()?.message ?: "Failed to send reset code"
                        setTextColor(getColor(android.R.color.holo_red_dark))
                        visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnSendCode.isEnabled = true

                tvMessage.apply {
                    text = "Network error: ${e.localizedMessage}"
                    setTextColor(getColor(android.R.color.holo_red_dark))
                    visibility = View.VISIBLE
                }
            }
        }
    }
}
