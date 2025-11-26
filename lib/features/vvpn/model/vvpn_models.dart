import 'package:freezed_annotation/freezed_annotation.dart';

part 'vvpn_models.freezed.dart';
part 'vvpn_models.g.dart';

/// User model from V-VPN API
@freezed
class VvpnUser with _$VvpnUser {
  const factory VvpnUser({
    required int id,
    required String email,
    @Default('user') String role,
    @JsonKey(name: 'full_name') String? fullName,
    @JsonKey(name: 'license_type') String? licenseType,
    @JsonKey(name: 'license_expires_at') DateTime? licenseExpiresAt,
    @JsonKey(name: 'license_key') String? licenseKey,
    @JsonKey(name: 'license_status') String? licenseStatus,
    @JsonKey(name: 'plan') String? plan,
  }) = _VvpnUser;

  factory VvpnUser.fromJson(Map<String, dynamic> json) => _$VvpnUserFromJson(json);
}

/// Login response from V-VPN API
@freezed
class VvpnLoginResponse with _$VvpnLoginResponse {
  const factory VvpnLoginResponse({
    required bool success,
    required String token,
    required VvpnUser user,
  }) = _VvpnLoginResponse;

  factory VvpnLoginResponse.fromJson(Map<String, dynamic> json) => _$VvpnLoginResponseFromJson(json);
}

/// Server config from V-VPN API
@freezed
class VvpnServerConfig with _$VvpnServerConfig {
  const factory VvpnServerConfig({
    required bool success,
    required String serverAddress,
    required String serverPort,
    required String authPayload,
    required String obfuscation,
    @Default('') String sni,
    @Default(true) bool allowInsecure,
  }) = _VvpnServerConfig;

  factory VvpnServerConfig.fromJson(Map<String, dynamic> json) => _$VvpnServerConfigFromJson(json);
}

/// Generic API response
@freezed
class VvpnApiResponse with _$VvpnApiResponse {
  const factory VvpnApiResponse({
    required bool success,
    String? message,
  }) = _VvpnApiResponse;

  factory VvpnApiResponse.fromJson(Map<String, dynamic> json) => _$VvpnApiResponseFromJson(json);
}

/// License info from /license/device/:deviceId API
@freezed
class VvpnLicenseInfo with _$VvpnLicenseInfo {
  const factory VvpnLicenseInfo({
    @JsonKey(name: 'license_key') required String licenseKey,
    @JsonKey(name: 'device_id') String? deviceId,
    @JsonKey(name: 'plan_id') required String planId,
    @JsonKey(name: 'expiry_date') required DateTime expiryDate,
    @JsonKey(name: 'is_active') required bool isActive,
    @JsonKey(name: 'user_email') required String userEmail,
    @JsonKey(name: 'last_connected_at') DateTime? lastConnectedAt,
  }) = _VvpnLicenseInfo;

  factory VvpnLicenseInfo.fromJson(Map<String, dynamic> json) => _$VvpnLicenseInfoFromJson(json);
}

/// License response from API
@freezed
class VvpnLicenseResponse with _$VvpnLicenseResponse {
  const factory VvpnLicenseResponse({
    required bool success,
    VvpnLicenseInfo? license,
    @Default([]) List<VvpnLicenseInfo> licenses,
  }) = _VvpnLicenseResponse;

  factory VvpnLicenseResponse.fromJson(Map<String, dynamic> json) => _$VvpnLicenseResponseFromJson(json);
}

/// Auth state for the app
@freezed
class VvpnAuthState with _$VvpnAuthState {
  const factory VvpnAuthState({
    String? token,
    VvpnUser? user,
    VvpnLicenseInfo? licenseInfo,
    @Default(false) bool isAuthenticated,
    @Default(false) bool isLoading,
    String? error,
  }) = _VvpnAuthState;
}

/// V-VPN API failure types
sealed class VvpnFailure {
  const VvpnFailure();
}

class VvpnNetworkFailure extends VvpnFailure {
  final String message;
  const VvpnNetworkFailure(this.message);
}

class VvpnAuthFailure extends VvpnFailure {
  final String message;
  const VvpnAuthFailure(this.message);
}

class VvpnServerFailure extends VvpnFailure {
  final String message;
  const VvpnServerFailure(this.message);
}

class VvpnUnexpectedFailure extends VvpnFailure {
  final Object error;
  final StackTrace stackTrace;
  const VvpnUnexpectedFailure(this.error, this.stackTrace);
}
