import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:fpdart/fpdart.dart';
import 'package:vvpn/features/profile/data/profile_data_providers.dart';
import 'package:vvpn/features/profile/data/profile_repository.dart';
import 'package:vvpn/features/vvpn/data/vvpn_api_service.dart';
import 'package:vvpn/features/vvpn/model/vvpn_models.dart';
import 'package:vvpn/utils/custom_loggers.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'vvpn_repository.g.dart';

class VvpnRepository with InfraLogger {
  VvpnRepository({
    required this.apiService,
    required this.profileRepository,
  });

  final VvpnApiService apiService;
  final ProfileRepository profileRepository;

  final _secureStorage = const FlutterSecureStorage();

  static const _tokenKey = 'vvpn_auth_token';
  static const _userKey = 'vvpn_user';
  static const _deviceIdKey = 'vvpn_device_id';

  Timer? _heartbeatTimer;
  String? _currentDeviceId;

  /// Initialize repository - load saved token
  Future<VvpnAuthState> init() async {
    try {
      final token = await _secureStorage.read(key: _tokenKey);
      final userJson = await _secureStorage.read(key: _userKey);

      if (token != null && userJson != null) {
        apiService.setAuthToken(token);
        final user = VvpnUser.fromJson(jsonDecode(userJson));
        return VvpnAuthState(
          token: token,
          user: user,
          isAuthenticated: true,
        );
      }
    } catch (e, st) {
      loggy.error('Error loading auth state', e, st);
    }

    return const VvpnAuthState();
  }

  /// Login user
  Future<Either<VvpnFailure, VvpnAuthState>> login(
    String email,
    String password,
  ) async {
    try {
      final response = await apiService.login(email, password);

      // Save token and user
      await _secureStorage.write(key: _tokenKey, value: response.token);
      await _secureStorage.write(
        key: _userKey,
        value: jsonEncode(response.user.toJson()),
      );

      return right(VvpnAuthState(
        token: response.token,
        user: response.user,
        isAuthenticated: true,
      ));
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      return left(VvpnUnexpectedFailure(e, st));
    }
  }

  /// Register new user
  Future<Either<VvpnFailure, VvpnAuthState>> register(
    String email,
    String password,
    String fullName,
  ) async {
    try {
      final response = await apiService.register(email, password, fullName);

      // Save token and user
      await _secureStorage.write(key: _tokenKey, value: response.token);
      await _secureStorage.write(
        key: _userKey,
        value: jsonEncode(response.user.toJson()),
      );

      return right(VvpnAuthState(
        token: response.token,
        user: response.user,
        isAuthenticated: true,
      ));
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      return left(VvpnUnexpectedFailure(e, st));
    }
  }

  /// Logout user
  Future<void> logout() async {
    await _stopHeartbeat();
    await _secureStorage.delete(key: _tokenKey);
    await _secureStorage.delete(key: _userKey);
    apiService.setAuthToken(null);
  }

  /// Fetch server config and create Hysteria2 profile
  Future<Either<VvpnFailure, Unit>> fetchAndCreateProfile() async {
    try {
      final config = await apiService.getServerConfig();

      if (!config.success) {
        return left(const VvpnServerFailure('Failed to fetch server config'));
      }

      // Build Sing-box config
      final singboxConfig = _buildSingboxConfig(config);

      // Check if V-VPN profile already exists
      final existingProfile = await profileRepository.getByName('Unlock the World');

      if (existingProfile != null) {
        // Update existing profile
        await profileRepository.updateContent(
          existingProfile.id,
          jsonEncode(singboxConfig),
        ).run();
      } else {
        // Create new profile
        await profileRepository.addByContent(
          jsonEncode(singboxConfig),
          name: 'Unlock the World',
          markAsActive: true,
        ).run();
      }

      return right(unit);
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      return left(VvpnUnexpectedFailure(e, st));
    }
  }

  /// Build Sing-box configuration from V-VPN server config
  Map<String, dynamic> _buildSingboxConfig(VvpnServerConfig config) {
    return {
      "log": {
        "level": "info",
        "timestamp": true,
      },
      "dns": {
        "servers": [
          {
            "tag": "dns-remote",
            "address": "https://1.1.1.1/dns-query",
            "detour": "proxy",
          },
          {
            "tag": "dns-direct",
            "address": "https://1.1.1.1/dns-query",
            "detour": "direct",
          },
          {
            "tag": "dns-block",
            "address": "rcode://success",
          },
        ],
        "rules": [
          {
            "outbound": "any",
            "server": "dns-direct",
          },
        ],
        "final": "dns-remote",
        "strategy": "prefer_ipv4",
      },
      "inbounds": [
        {
          "type": "tun",
          "tag": "tun-in",
          "interface_name": "tun0",
          "inet4_address": "172.19.0.1/30",
          "mtu": 9000,
          "auto_route": true,
          "strict_route": true,
          "stack": "system",
          "sniff": true,
          "sniff_override_destination": true,
        },
        {
          "type": "mixed",
          "tag": "mixed-in",
          "listen": "127.0.0.1",
          "listen_port": 12334,
          "sniff": true,
          "sniff_override_destination": true,
        },
      ],
      "outbounds": [
        {
          "type": "hysteria2",
          "tag": "proxy",
          "server": "vpn-europe.vvpn.space",
          "server_port": int.tryParse(config.serverPort) ?? 443,
          "password": config.authPayload,
          "obfs": config.obfuscation.isNotEmpty
              ? {
                  "type": "salamander",
                  "password": config.obfuscation,
                }
              : null,
          "tls": {
            "enabled": true,
            "server_name": config.sni.isNotEmpty ? config.sni : "vpn-europe.vvpn.space",
            "insecure": config.allowInsecure,
          },
        },
        {
          "type": "direct",
          "tag": "direct",
        },
        {
          "type": "block",
          "tag": "block",
        },
        {
          "type": "dns",
          "tag": "dns-out",
        },
      ],
      "route": {
        "rules": [
          {
            "protocol": "dns",
            "outbound": "dns-out",
          },
          {
            "ip_is_private": true,
            "outbound": "direct",
          },
        ],
        "final": "proxy",
        "auto_detect_interface": true,
      },
      "experimental": {
        "clash_api": {
          "external_controller": "127.0.0.1:9090",
        },
      },
    };
  }

  /// Get or create device ID
  Future<String> getDeviceId() async {
    var deviceId = await _secureStorage.read(key: _deviceIdKey);

    if (deviceId == null) {
      final deviceInfo = DeviceInfoPlugin();
      if (Platform.isIOS) {
        final iosInfo = await deviceInfo.iosInfo;
        deviceId = iosInfo.identifierForVendor ?? DateTime.now().millisecondsSinceEpoch.toString();
      } else if (Platform.isAndroid) {
        final androidInfo = await deviceInfo.androidInfo;
        deviceId = androidInfo.id;
      } else {
        deviceId = DateTime.now().millisecondsSinceEpoch.toString();
      }

      await _secureStorage.write(key: _deviceIdKey, value: deviceId);
    }

    return deviceId;
  }

  /// Get device name
  Future<String> getDeviceName() async {
    final deviceInfo = DeviceInfoPlugin();
    if (Platform.isIOS) {
      final iosInfo = await deviceInfo.iosInfo;
      return iosInfo.name;
    } else if (Platform.isAndroid) {
      final androidInfo = await deviceInfo.androidInfo;
      return androidInfo.model;
    }
    return 'Unknown Device';
  }

  /// Connect device to V-VPN
  Future<Either<VvpnFailure, Unit>> connectDevice() async {
    try {
      final deviceId = await getDeviceId();
      final deviceName = await getDeviceName();

      final response = await apiService.connectDevice(deviceId, deviceName);

      if (!response.success) {
        return left(VvpnServerFailure(response.message ?? 'Failed to connect device'));
      }

      _currentDeviceId = deviceId;
      _startHeartbeat();

      return right(unit);
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      return left(VvpnUnexpectedFailure(e, st));
    }
  }

  /// Disconnect device from V-VPN
  Future<Either<VvpnFailure, Unit>> disconnectDevice() async {
    try {
      await _stopHeartbeat();

      if (_currentDeviceId != null) {
        await apiService.disconnectDevice(_currentDeviceId!);
        _currentDeviceId = null;
      }

      return right(unit);
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      return left(VvpnUnexpectedFailure(e, st));
    }
  }

  void _startHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(
      const Duration(seconds: 30),
      (_) async {
        if (_currentDeviceId != null) {
          try {
            await apiService.heartbeat(_currentDeviceId!);
            loggy.debug('Heartbeat sent');
          } catch (e) {
            loggy.error('Heartbeat failed', e);
          }
        }
      },
    );
  }

  Future<void> _stopHeartbeat() async {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
  }

  /// Fetch license info for current device
  Future<Either<VvpnFailure, VvpnLicenseInfo?>> fetchLicenseInfo() async {
    try {
      final deviceId = await getDeviceId();
      final response = await apiService.getLicenseByDevice(deviceId);

      if (!response.success) {
        return left(const VvpnServerFailure('Failed to fetch license info'));
      }

      // Return the first license from the response
      final license = response.license ?? response.licenses.firstOrNull;
      return right(license);
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      loggy.error('Error fetching license info', e, st);
      return left(VvpnUnexpectedFailure(e, st));
    }
  }

  /// Activate license key for current device
  Future<Either<VvpnFailure, VvpnLicenseInfo?>> activateLicense(String licenseKey) async {
    try {
      final deviceId = await getDeviceId();
      final deviceName = await getDeviceName();
      final response = await apiService.activateLicense(licenseKey, deviceId, deviceName);

      if (!response.success) {
        return left(const VvpnServerFailure('Failed to activate license'));
      }

      // Return the activated license
      final license = response.license ?? response.licenses.firstOrNull;
      return right(license);
    } on VvpnFailure catch (e) {
      return left(e);
    } catch (e, st) {
      loggy.error('Error activating license', e, st);
      return left(VvpnUnexpectedFailure(e, st));
    }
  }
}

@Riverpod(keepAlive: true)
VvpnApiService vvpnApiService(VvpnApiServiceRef ref) {
  return VvpnApiService();
}

@Riverpod(keepAlive: true)
Future<VvpnRepository> vvpnRepository(VvpnRepositoryRef ref) async {
  final profileRepo = await ref.watch(profileRepositoryProvider.future);
  return VvpnRepository(
    apiService: ref.watch(vvpnApiServiceProvider),
    profileRepository: profileRepo,
  );
}
