import 'package:dio/dio.dart';
import 'package:vvpn/features/vvpn/model/vvpn_models.dart';
import 'package:vvpn/utils/custom_loggers.dart';

class VvpnApiService with InfraLogger {
  VvpnApiService() {
    _dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 30),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
      ),
    );

    _dio.interceptors.add(
      LogInterceptor(
        requestBody: true,
        responseBody: true,
        logPrint: (obj) => loggy.debug(obj.toString()),
      ),
    );
  }

  static const String baseUrl = 'https://api.vvpn.space/api';

  late final Dio _dio;
  String? _authToken;

  void setAuthToken(String? token) {
    _authToken = token;
    if (token != null) {
      _dio.options.headers['Authorization'] = 'Bearer $token';
    } else {
      _dio.options.headers.remove('Authorization');
    }
  }

  String? get authToken => _authToken;

  /// Login with email and password
  Future<VvpnLoginResponse> login(String email, String password) async {
    try {
      final response = await _dio.post(
        '/auth/login',
        data: {
          'email': email,
          'password': password,
        },
      );

      final loginResponse = VvpnLoginResponse.fromJson(response.data);
      setAuthToken(loginResponse.token);
      return loginResponse;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Register a new user
  Future<VvpnLoginResponse> register(String email, String password, String fullName) async {
    try {
      final response = await _dio.post(
        '/auth/register',
        data: {
          'email': email,
          'password': password,
          'full_name': fullName,
        },
      );

      final loginResponse = VvpnLoginResponse.fromJson(response.data);
      setAuthToken(loginResponse.token);
      return loginResponse;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Get server configuration
  Future<VvpnServerConfig> getServerConfig() async {
    try {
      final response = await _dio.get('/server/config');
      return VvpnServerConfig.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Connect device (for single-device enforcement)
  Future<VvpnApiResponse> connectDevice(String deviceId, String deviceName) async {
    try {
      final response = await _dio.post(
        '/license/connect',
        data: {
          'deviceId': deviceId,
          'deviceName': deviceName,
        },
      );
      return VvpnApiResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Send heartbeat (call every 30s while connected)
  Future<VvpnApiResponse> heartbeat(String deviceId) async {
    try {
      final response = await _dio.post(
        '/license/heartbeat',
        data: {
          'deviceId': deviceId,
        },
      );
      return VvpnApiResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Disconnect device
  Future<VvpnApiResponse> disconnectDevice(String deviceId) async {
    try {
      final response = await _dio.post(
        '/license/disconnect',
        data: {
          'deviceId': deviceId,
        },
      );
      return VvpnApiResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Get user profile with license info
  Future<VvpnUser> getUserProfile() async {
    try {
      final response = await _dio.get('/user/profile');
      return VvpnUser.fromJson(response.data['user'] ?? response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Get license info by device ID
  /// GET /license/device/:deviceId
  Future<VvpnLicenseResponse> getLicenseByDevice(String deviceId) async {
    try {
      final response = await _dio.get('/license/device/$deviceId');
      return VvpnLicenseResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  /// Activate license key for device
  /// POST /license/activate
  Future<VvpnLicenseResponse> activateLicense(String licenseKey, String deviceId, String deviceName) async {
    try {
      final response = await _dio.post(
        '/license/activate',
        data: {
          'licenseKey': licenseKey,
          'deviceId': deviceId,
          'deviceName': deviceName,
        },
      );
      return VvpnLicenseResponse.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  VvpnFailure _handleDioError(DioException e) {
    loggy.error('API Error: ${e.message}', e);

    if (e.response?.statusCode == 401) {
      return VvpnAuthFailure(
        e.response?.data?['message'] ?? 'Authentication failed',
      );
    }

    if (e.response?.statusCode == 403) {
      return VvpnAuthFailure(
        e.response?.data?['message'] ?? 'Access denied',
      );
    }

    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.connectionError) {
      return const VvpnNetworkFailure('Network connection failed');
    }

    if (e.response != null) {
      final message = e.response?.data?['message'] ?? 'Server error';
      return VvpnServerFailure(message);
    }

    return VvpnNetworkFailure(e.message ?? 'Unknown error');
  }
}
