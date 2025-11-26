import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_displaymode/flutter_displaymode.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:vvpn/core/analytics/analytics_controller.dart';
import 'package:vvpn/core/app_info/app_info_provider.dart';
import 'package:vvpn/core/directories/directories_provider.dart';
import 'package:vvpn/core/logger/logger.dart';
import 'package:vvpn/core/logger/logger_controller.dart';
import 'package:vvpn/core/model/environment.dart';
import 'package:vvpn/core/preferences/general_preferences.dart';
import 'package:vvpn/core/preferences/preferences_migration.dart';
import 'package:vvpn/core/preferences/preferences_provider.dart';
import 'package:vvpn/features/app/widget/app.dart';
import 'package:vvpn/features/auto_start/notifier/auto_start_notifier.dart';
import 'package:vvpn/features/deep_link/notifier/deep_link_notifier.dart';

import 'package:vvpn/features/log/data/log_data_providers.dart';
import 'package:vvpn/features/profile/data/profile_data_providers.dart';
import 'package:vvpn/features/profile/notifier/active_profile_notifier.dart';
import 'package:vvpn/features/system_tray/notifier/system_tray_notifier.dart';
import 'package:vvpn/features/window/notifier/window_notifier.dart';
import 'package:vvpn/singbox/service/singbox_service_provider.dart';
import 'package:vvpn/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

Future<void> lazyBootstrap(
  WidgetsBinding widgetsBinding,
  Environment env,
) async {
  FlutterNativeSplash.preserve(widgetsBinding: widgetsBinding);

  LoggerController.preInit();
  FlutterError.onError = Logger.logFlutterError;
  WidgetsBinding.instance.platformDispatcher.onError = Logger.logPlatformDispatcherError;

  final stopWatch = Stopwatch()..start();

  final container = ProviderContainer(
    overrides: [
      environmentProvider.overrideWithValue(env),
    ],
  );

  await _init(
    "directories",
    () => container.read(appDirectoriesProvider.future),
    timeout: 5000,
  );
  LoggerController.init(container.read(logPathResolverProvider).appFile().path);

  final appInfo = await _init(
    "app info",
    () => container.read(appInfoProvider.future),
    timeout: 3000,
  );
  await _init(
    "preferences",
    () => container.read(sharedPreferencesProvider.future),
    timeout: 5000,
  );

  final enableAnalytics = await container.read(analyticsControllerProvider.future).timeout(const Duration(seconds: 3), onTimeout: () => false);
  if (enableAnalytics) {
    await _safeInit(
      "analytics",
      () => container.read(analyticsControllerProvider.notifier).enableAnalytics(),
      timeout: 3000,
    );
  }

  await _safeInit(
    "preferences migration",
    () async {
      try {
        await PreferencesMigration(
          sharedPreferences: container.read(sharedPreferencesProvider).requireValue,
        ).migrate();
      } catch (e, stackTrace) {
        Logger.bootstrap.error("preferences migration failed", e, stackTrace);
        if (env == Environment.dev) rethrow;
        Logger.bootstrap.info("clearing preferences");
        await container.read(sharedPreferencesProvider).requireValue.clear();
      }
    },
    timeout: 5000,
  );

  final debug = container.read(debugModeNotifierProvider) || kDebugMode;

  if (PlatformUtils.isDesktop) {
    await _init(
      "window controller",
      () => container.read(windowNotifierProvider.future),
    );

    final silentStart = container.read(Preferences.silentStart);
    Logger.bootstrap.debug("silent start [${silentStart ? "Enabled" : "Disabled"}]");
    if (!silentStart) {
      await container.read(windowNotifierProvider.notifier).open(focus: false);
    } else {
      Logger.bootstrap.debug("silent start, remain hidden accessible via tray");
    }
    await _init(
      "auto start service",
      () => container.read(autoStartNotifierProvider.future),
    );
  }
  await _safeInit(
    "logs repository",
    () => container.read(logRepositoryProvider.future),
    timeout: 3000,
  );
  await _safeInit("logger controller", () => LoggerController.postInit(debug), timeout: 2000);

  Logger.bootstrap.info(appInfo.format());

  await _safeInit(
    "profile repository",
    () => container.read(profileRepositoryProvider.future),
    timeout: 5000,
  );

  await _safeInit(
    "active profile",
    () => container.read(activeProfileProvider.future),
    timeout: 1000,
  );
  await _safeInit(
    "deep link service",
    () => container.read(deepLinkNotifierProvider.future),
    timeout: 1000,
  );
  await _safeInit(
    "sing-box",
    () => container.read(singboxServiceProvider).init(),
    timeout: 5000,
  );
  if (PlatformUtils.isDesktop) {
    await _safeInit(
      "system tray",
      () => container.read(systemTrayNotifierProvider.future),
      timeout: 1000,
    );
  }

  if (Platform.isAndroid) {
    await _safeInit(
      "android display mode",
      () async {
        await FlutterDisplayMode.setHighRefreshRate();
      },
    );
  }

  Logger.bootstrap.info("bootstrap took [${stopWatch.elapsedMilliseconds}ms]");
  stopWatch.stop();

  runApp(
    ProviderScope(
      parent: container,
      child: SentryUserInteractionWidget(
        child: const App(),
      ),
    ),
  );

  FlutterNativeSplash.remove();
}

Future<T> _init<T>(
  String name,
  Future<T> Function() initializer, {
  int? timeout,
}) async {
  final stopWatch = Stopwatch()..start();
  Logger.bootstrap.info("initializing [$name]");
  Future<T> func() => timeout != null ? initializer().timeout(Duration(milliseconds: timeout)) : initializer();
  try {
    final result = await func();
    Logger.bootstrap.debug("[$name] initialized in ${stopWatch.elapsedMilliseconds}ms");
    return result;
  } catch (e, stackTrace) {
    Logger.bootstrap.error("[$name] error initializing", e, stackTrace);
    rethrow;
  } finally {
    stopWatch.stop();
  }
}

Future<T?> _safeInit<T>(
  String name,
  Future<T> Function() initializer, {
  int? timeout,
}) async {
  try {
    return await _init(name, initializer, timeout: timeout);
  } catch (e) {
    return null;
  }
}
