import 'package:vvpn/features/vvpn/data/vvpn_repository.dart';
import 'package:vvpn/features/vvpn/model/vvpn_models.dart';
import 'package:vvpn/utils/custom_loggers.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'vvpn_auth_notifier.g.dart';

@Riverpod(keepAlive: true)
class VvpnAuthNotifier extends _$VvpnAuthNotifier with AppLogger {
  @override
  Future<VvpnAuthState> build() async {
    final repository = await ref.watch(vvpnRepositoryProvider.future);
    var authState = await repository.init();

    // If already authenticated, ensure profile exists and fetch license info
    if (authState.isAuthenticated) {
      _fetchServerConfig();
      // Fetch license info
      final licenseResult = await repository.fetchLicenseInfo();
      licenseResult.fold(
        (failure) => loggy.warning('Failed to fetch license info: ${_getErrorMessage(failure)}'),
        (licenseInfo) {
          if (licenseInfo != null) {
            authState = authState.copyWith(licenseInfo: licenseInfo);
          }
        },
      );
    }

    return authState;
  }

  Future<void> login(String email, String password) async {
    state = const AsyncValue.loading();

    final repository = await ref.read(vvpnRepositoryProvider.future);
    final result = await repository.login(email, password);

    state = result.fold(
      (failure) => AsyncValue.error(_getErrorMessage(failure), StackTrace.current),
      (authState) => AsyncValue.data(authState),
    );

    // If login successful, fetch profile and license info
    if (result.isRight()) {
      await _fetchServerConfig();
      await _fetchLicenseInfo();
    }
  }

  Future<void> register(String email, String password, String fullName) async {
    state = const AsyncValue.loading();

    final repository = await ref.read(vvpnRepositoryProvider.future);
    final result = await repository.register(email, password, fullName);

    state = result.fold(
      (failure) => AsyncValue.error(_getErrorMessage(failure), StackTrace.current),
      (authState) => AsyncValue.data(authState),
    );

    // If register successful, fetch and create profile
    if (result.isRight()) {
      await _fetchServerConfig();
    }
  }

  Future<void> logout() async {
    final repository = await ref.read(vvpnRepositoryProvider.future);
    await repository.logout();
    state = const AsyncValue.data(VvpnAuthState());
  }

  Future<void> _fetchServerConfig() async {
    final repository = await ref.read(vvpnRepositoryProvider.future);
    final result = await repository.fetchAndCreateProfile();

    result.fold(
      (failure) => loggy.error('Failed to fetch server config: ${_getErrorMessage(failure)}'),
      (_) => loggy.info('Server config fetched and profile created'),
    );
  }

  Future<void> refreshServerConfig() async {
    final currentState = state.valueOrNull;
    if (currentState == null || !currentState.isAuthenticated) {
      return;
    }

    await _fetchServerConfig();
  }

  /// Fetch license info from API
  Future<void> _fetchLicenseInfo() async {
    final repository = await ref.read(vvpnRepositoryProvider.future);
    final result = await repository.fetchLicenseInfo();

    result.fold(
      (failure) => loggy.warning('Failed to fetch license info: ${_getErrorMessage(failure)}'),
      (licenseInfo) {
        if (licenseInfo != null) {
          final currentState = state.valueOrNull;
          if (currentState != null) {
            state = AsyncValue.data(currentState.copyWith(licenseInfo: licenseInfo));
          }
        }
      },
    );
  }

  /// Refresh license info
  Future<void> refreshLicenseInfo() async {
    final currentState = state.valueOrNull;
    if (currentState == null || !currentState.isAuthenticated) {
      return;
    }

    await _fetchLicenseInfo();
  }

  /// Activate license key
  Future<bool> activateLicense(String licenseKey) async {
    final repository = await ref.read(vvpnRepositoryProvider.future);
    final result = await repository.activateLicense(licenseKey);

    return result.fold(
      (failure) {
        loggy.error('Failed to activate license: ${_getErrorMessage(failure)}');
        return false;
      },
      (licenseInfo) {
        if (licenseInfo != null && licenseInfo.isActive) {
          final currentState = state.valueOrNull;
          if (currentState != null) {
            state = AsyncValue.data(currentState.copyWith(licenseInfo: licenseInfo));
          }
          return true;
        }
        return false;
      },
    );
  }

  /// Check if user has active license
  bool get hasActiveLicense {
    final licenseInfo = state.valueOrNull?.licenseInfo;
    if (licenseInfo == null) return false;
    if (!licenseInfo.isActive) return false;
    if (licenseInfo.expiryDate.isBefore(DateTime.now())) return false;
    return true;
  }

  /// Connect device when VPN starts
  Future<bool> connectDevice() async {
    final repository = await ref.read(vvpnRepositoryProvider.future);
    final result = await repository.connectDevice();

    return result.fold(
      (failure) {
        loggy.error('Failed to connect device: ${_getErrorMessage(failure)}');
        return false;
      },
      (_) {
        loggy.info('Device connected');
        return true;
      },
    );
  }

  /// Disconnect device when VPN stops
  Future<void> disconnectDevice() async {
    final repository = await ref.read(vvpnRepositoryProvider.future);
    await repository.disconnectDevice();
  }

  String _getErrorMessage(VvpnFailure failure) {
    return switch (failure) {
      VvpnNetworkFailure(:final message) => message,
      VvpnAuthFailure(:final message) => message,
      VvpnServerFailure(:final message) => message,
      VvpnUnexpectedFailure(:final error) => error.toString(),
    };
  }
}
