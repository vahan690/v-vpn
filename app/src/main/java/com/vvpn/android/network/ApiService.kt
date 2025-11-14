package com.vvpn.android.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<ForgotPasswordResponse>
    
    @GET("auth/verify-reset-token/{token}")
    suspend fun verifyResetToken(
        @Path("token") token: String
    ): Response<VerifyTokenResponse>
    
    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<ResetPasswordResponse>
    
    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>
    
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
}

// Request Models
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, val new_password: String)
data class RegisterRequest(val email: String, val password: String, val full_name: String)
data class LoginRequest(val email: String, val password: String)

// Response Models
data class ForgotPasswordResponse(val success: Boolean, val message: String)
data class VerifyTokenResponse(val success: Boolean, val message: String, val email: String?)
data class ResetPasswordResponse(val success: Boolean, val message: String)
data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val token: String?,
    val user: User?
)
data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val token: String?,
    val user: User?
)
data class User(val id: Int, val email: String, val full_name: String?)
