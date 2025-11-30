import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:vvpn/core/preferences/general_preferences.dart';
import 'package:vvpn/core/router/routes.dart';
import 'package:vvpn/features/deep_link/notifier/deep_link_notifier.dart';
import 'package:vvpn/features/vvpn/notifier/vvpn_auth_notifier.dart';
import 'package:vvpn/utils/utils.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

part 'app_router.g.dart';

bool _debugMobileRouter = false;

final useMobileRouter =
    !PlatformUtils.isDesktop || (kDebugMode && _debugMobileRouter);
final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

// TODO: test and improve handling of deep link
@riverpod
GoRouter router(RouterRef ref) {
  final notifier = ref.watch(routerListenableProvider.notifier);
  final deepLink = ref.listen(
    deepLinkNotifierProvider,
    (_, next) async {
      if (next case AsyncData(value: final link?)) {
        await ref.state.push(AddProfileRoute(url: link.url).location);
      }
    },
  );
  final initialLink = deepLink.read();
  String initialLocation = const HomeRoute().location;
  if (initialLink case AsyncData(value: final link?)) {
    initialLocation = AddProfileRoute(url: link.url).location;
  }

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: initialLocation,
    debugLogDiagnostics: true,
    routes: [
      if (useMobileRouter) $mobileWrapperRoute else $desktopWrapperRoute,
      $introRoute,
      $loginRoute,
      $registerRoute,
      $enterLicenseRoute,
      $licenseRoute,
      $forgotPasswordRoute,
      $resetPasswordRoute,
    ],
    refreshListenable: notifier,
    redirect: notifier.redirect,
    observers: [
      SentryNavigatorObserver(),
    ],
  );
}

final tabLocations = [
  const HomeRoute().location,
  const SettingsRoute().location,
  const AboutRoute().location,
];

int getCurrentIndex(BuildContext context) {
  final String location = GoRouterState.of(context).uri.path;
  if (location == const HomeRoute().location) return 0;
  var index = 0;
  for (final tab in tabLocations.sublist(1)) {
    index++;
    if (location.startsWith(tab)) return index;
  }
  return 0;
}

void switchTab(int index, BuildContext context) {
  assert(index >= 0 && index < tabLocations.length);
  final location = tabLocations[index];
  return context.go(location);
}

@riverpod
class RouterListenable extends _$RouterListenable
    with AppLogger
    implements Listenable {
  VoidCallback? _routerListener;
  bool _introCompleted = false;
  bool _isAuthenticated = false;
  bool _hasActiveLicense = false;
  bool _authLoading = true;

  @override
  void build() {
    _introCompleted = ref.watch(Preferences.introCompleted);

    // Watch V-VPN auth state - handle loading state properly
    final authState = ref.watch(vvpnAuthNotifierProvider);
    _authLoading = authState.isLoading;
    _isAuthenticated = authState.valueOrNull?.isAuthenticated ?? false;

    // Check license status
    final licenseInfo = authState.valueOrNull?.licenseInfo;
    _hasActiveLicense = licenseInfo != null &&
        licenseInfo.isActive &&
        licenseInfo.expiryDate.isAfter(DateTime.now());

    ref.listenSelf((_, __) {
      loggy.debug("triggering listener");
      _routerListener?.call();
    });
  }

// ignore: avoid_build_context_in_providers
  String? redirect(BuildContext context, GoRouterState state) {
    final isIntro = state.uri.path == const IntroRoute().location;
    final isLogin = state.uri.path == const LoginRoute().location;
    final isRegister = state.uri.path == const RegisterRoute().location;
    final isForgotPassword = state.uri.path == const ForgotPasswordRoute().location;
    final isResetPassword = state.uri.path.startsWith('/reset-password');
    final isAuthRoute = isLogin || isRegister || isForgotPassword || isResetPassword;

    // First check intro - this doesn't depend on auth loading
    if (!_introCompleted) {
      return const IntroRoute().location;
    }

    // While auth is loading, allow navigation to login or stay on intro
    // This prevents the app from hanging on splash screen
    if (_authLoading) {
      if (isIntro) {
        return const LoginRoute().location;
      }
      // Don't redirect while loading - let user stay on current page
      return null;
    }

    if (isIntro) {
      // After intro, check auth
      if (!_isAuthenticated) {
        return const LoginRoute().location;
      }
      // Go directly to home after authentication (no license check)
      return const HomeRoute().location;
    }

    // Check authentication
    if (!_isAuthenticated && !isAuthRoute) {
      return const LoginRoute().location;
    } else if (_isAuthenticated && isAuthRoute) {
      // Authenticated user trying to access auth routes - go to home
      return const HomeRoute().location;
    }

    return null;
  }

  @override
  void addListener(VoidCallback listener) {
    _routerListener = listener;
  }

  @override
  void removeListener(VoidCallback listener) {
    _routerListener = null;
  }
}
