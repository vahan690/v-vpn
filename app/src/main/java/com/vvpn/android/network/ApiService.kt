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

    // Device Management Endpoints
    @POST("license/connect")
    suspend fun connectDevice(
        @Header("Authorization") token: String,
        @Body request: ConnectDeviceRequest
    ): Response<ConnectDeviceResponse>

    @POST("license/disconnect")
    suspend fun disconnectDevice(
        @Header("Authorization") token: String,
        @Body request: DisconnectDeviceRequest
    ): Response<DisconnectDeviceResponse>

    @GET("license/devices")
    suspend fun getDevices(
        @Header("Authorization") token: String
    ): Response<DevicesResponse>
}

// Request Models
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val code: String, val new_password: String)
data class RegisterRequest(val email: String, val password: String, val full_name: String)
data class LoginRequest(val email: String, val password: String)
data class ConnectDeviceRequest(val deviceId: String, val deviceName: String?)
data class DisconnectDeviceRequest(val deviceId: String)

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

// Device Management Response Models
data class ConnectDeviceResponse(
    val success: Boolean,
    val error: String?,
    val message: String?,
    val device: DeviceInfo?,
    val license: LicenseInfo?,
    val activeDevice: ActiveDeviceInfo?
)

data class DisconnectDeviceResponse(
    val success: Boolean,
    val message: String?
)

data class DevicesResponse(
    val success: Boolean,
    val devices: List<DeviceInfo>?
)

data class DeviceInfo(
    val device_id: String,
    val device_name: String?,
    val is_active: Boolean,
    val license_key: String?,
    val last_connected_at: String?,
    val connection_count: Int?
)

data class LicenseInfo(
    val licenseKey: String,
    val planId: String,
    val expiryDate: String,
    val isActive: Boolean
)

data class ActiveDeviceInfo(
    val deviceId: String,
    val deviceName: String?
)
