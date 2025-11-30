import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:vvpn/core/localization/translations.dart';
import 'package:vvpn/core/model/constants.dart';
import 'package:vvpn/core/model/failures.dart';
import 'package:vvpn/core/theme/theme_extensions.dart';
import 'package:vvpn/core/widget/animated_text.dart';
import 'package:vvpn/features/config_option/data/config_option_repository.dart';
import 'package:vvpn/features/config_option/notifier/config_option_notifier.dart';
import 'package:vvpn/features/connection/model/connection_status.dart';
import 'package:vvpn/features/connection/notifier/connection_notifier.dart';
import 'package:vvpn/features/connection/widget/experimental_feature_notice.dart';
import 'package:vvpn/features/profile/notifier/active_profile_notifier.dart';
import 'package:vvpn/features/proxy/active/active_proxy_notifier.dart';
import 'package:vvpn/features/vvpn/notifier/vvpn_auth_notifier.dart';
import 'package:vvpn/gen/assets.gen.dart';
import 'package:vvpn/utils/alerts.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

// TODO: rewrite
class ConnectionButton extends HookConsumerWidget {
  const ConnectionButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider);
    final connectionStatus = ref.watch(connectionNotifierProvider);
    final activeProxy = ref.watch(activeProxyNotifierProvider);
    final delay = activeProxy.valueOrNull?.urlTestDelay ?? 0;

    final requiresReconnect = ref.watch(configOptionNotifierProvider).valueOrNull;
    final today = DateTime.now();

    ref.listen(
      connectionNotifierProvider,
      (_, next) {
        if (next case AsyncError(:final error)) {
          CustomAlertDialog.fromErr(t.presentError(error)).show(context);
        }
        if (next case AsyncData(value: Disconnected(:final connectionFailure?))) {
          CustomAlertDialog.fromErr(t.presentError(connectionFailure)).show(context);
        }
      },
    );

    final buttonTheme = Theme.of(context).extension<ConnectionButtonTheme>()!;

    Future<bool> showExperimentalNotice() async {
      final hasExperimental = ref.read(ConfigOptions.hasExperimentalFeatures);
      final canShowNotice = !ref.read(disableExperimentalFeatureNoticeProvider);
      if (hasExperimental && canShowNotice && context.mounted) {
        return await const ExperimentalFeatureNoticeDialog().show(context) ?? false;
      }
      return true;
    }

    Future<bool> checkLicenseAndShowDialog() async {
      // Refresh license info from server
      await ref.read(vvpnAuthNotifierProvider.notifier).refreshLicenseInfo();

      final hasLicense = ref.read(vvpnAuthNotifierProvider.notifier).hasActiveLicense;
      if (!hasLicense && context.mounted) {
        // Show connection failed dialog - neutral wording for App Store
        await showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Connection Failed'),
            content: const Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Unable to establish VPN connection. This may be due to a server configuration issue with your account.'),
                SizedBox(height: 16),
                Text('Please contact our support team for assistance.'),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK'),
              ),
              TextButton.icon(
                onPressed: () async {
                  final uri = Uri.parse('mailto:${Constants.supportEmail}?subject=VPN Connection Issue');
                  if (await canLaunchUrl(uri)) {
                    await launchUrl(uri);
                  }
                },
                icon: const Icon(FluentIcons.mail_24_regular, size: 18),
                label: const Text('Email'),
              ),
              TextButton.icon(
                onPressed: () async {
                  final uri = Uri.parse(Constants.telegramChannelUrl);
                  if (await canLaunchUrl(uri)) {
                    await launchUrl(uri, mode: LaunchMode.externalApplication);
                  }
                },
                icon: const Icon(FluentIcons.chat_24_regular, size: 18),
                label: const Text('Telegram'),
              ),
            ],
          ),
        );
        return false;
      }
      return true;
    }

    Future<bool> checkDeviceConnectionAndShowDialog() async {
      // Try to register this device's connection
      final canConnect = await ref.read(vvpnAuthNotifierProvider.notifier).connectDevice();

      if (!canConnect && context.mounted) {
        // Another device is already connected
        await showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Connection Limit Reached'),
            content: const Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('You already have an active VPN connection on another device.'),
                SizedBox(height: 16),
                Text('Please disconnect from the other device first, or wait a moment and try again.'),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK'),
              ),
            ],
          ),
        );
        return false;
      }
      return true;
    }

    return _ConnectionButton(
      onTap: switch (connectionStatus) {
        AsyncData(value: Disconnected()) || AsyncError() => () async {
            // Check license before connecting
            if (!await checkLicenseAndShowDialog()) return;
            // Check if another device is already connected
            if (!await checkDeviceConnectionAndShowDialog()) return;
            if (await showExperimentalNotice()) {
              return await ref.read(connectionNotifierProvider.notifier).toggleConnection();
            }
          },
        AsyncData(value: Connected()) => () async {
            if (requiresReconnect == true && await showExperimentalNotice()) {
              return await ref.read(connectionNotifierProvider.notifier).reconnect(await ref.read(activeProfileProvider.future));
            }
            return await ref.read(connectionNotifierProvider.notifier).toggleConnection();
          },
        _ => () {},
      },
      enabled: switch (connectionStatus) {
        AsyncData(value: Connected()) || AsyncData(value: Disconnected()) || AsyncError() => true,
        _ => false,
      },
      label: switch (connectionStatus) {
        AsyncData(value: Connected()) when requiresReconnect == true => t.connection.reconnect,
        AsyncData(value: Connected()) when delay <= 0 || delay >= 65000 => t.connection.connecting,
        AsyncData(value: final status) => status.present(t),
        _ => "",
      },
      buttonColor: switch (connectionStatus) {
        AsyncData(value: Connected()) when requiresReconnect == true => Colors.teal,
        AsyncData(value: Connected()) when delay <= 0 || delay >= 65000 => Color.fromARGB(255, 185, 176, 103),
        AsyncData(value: Connected()) => buttonTheme.connectedColor!,
        AsyncData(value: _) => buttonTheme.idleColor!,
        _ => Colors.red,
      },
      image: switch (connectionStatus) {
        AsyncData(value: Connected()) when requiresReconnect == true => Assets.images.disconnectNorouz,
        AsyncData(value: Connected()) => Assets.images.connectNorouz,
        AsyncData(value: _) => Assets.images.disconnectNorouz,
        _ => Assets.images.disconnectNorouz,
        AsyncData(value: Disconnected()) || AsyncError() => Assets.images.disconnectNorouz,
        AsyncData(value: Connected()) => Assets.images.connectNorouz,
        _ => Assets.images.disconnectNorouz,
      },
      useImage: today.day >= 19 && today.day <= 23 && today.month == 3,
    );
  }
}

class _ConnectionButton extends StatelessWidget {
  const _ConnectionButton({
    required this.onTap,
    required this.enabled,
    required this.label,
    required this.buttonColor,
    required this.image,
    required this.useImage,
  });

  final VoidCallback onTap;
  final bool enabled;
  final String label;
  final Color buttonColor;
  final AssetGenImage image;
  final bool useImage;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Semantics(
          button: true,
          enabled: enabled,
          label: label,
          child: Container(
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  blurRadius: 16,
                  color: buttonColor.withOpacity(0.5),
                ),
              ],
            ),
            width: 148,
            height: 148,
            child: Material(
              key: const ValueKey("home_connection_button"),
              shape: const CircleBorder(),
              color: Colors.white,
              child: InkWell(
                onTap: onTap,
                child: Padding(
                  padding: const EdgeInsets.all(36),
                  child: TweenAnimationBuilder(
                    tween: ColorTween(end: buttonColor),
                    duration: const Duration(milliseconds: 250),
                    builder: (context, value, child) {
                      if (useImage) {
                        return image.image(filterQuality: FilterQuality.medium);
                      } else {
                        return Assets.images.logo.svg(
                          colorFilter: ColorFilter.mode(
                            value!,
                            BlendMode.srcIn,
                          ),
                        );
                      }
                    },
                  ),
                ),
              ),
            ).animate(target: enabled ? 0 : 1).blurXY(end: 1),
          ).animate(target: enabled ? 0 : 1).scaleXY(end: .88, curve: Curves.easeIn),
        ),
        const Gap(16),
        ExcludeSemantics(
          child: AnimatedText(
            label,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
      ],
    );
  }
}
