import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:go_router/go_router.dart';
import 'package:vvpn/features/vvpn/notifier/vvpn_auth_notifier.dart';
import 'package:vvpn/gen/assets.gen.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class EnterLicensePage extends HookConsumerWidget {
  const EnterLicensePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final formKey = useMemoized(() => GlobalKey<FormState>());
    final licenseKeyController = useTextEditingController();
    final isLoading = useState(false);
    final errorMessage = useState<String?>(null);

    Future<void> activateLicense() async {
      if (!formKey.currentState!.validate()) return;

      errorMessage.value = null;
      isLoading.value = true;

      try {
        final result = await ref
            .read(vvpnAuthNotifierProvider.notifier)
            .activateLicense(licenseKeyController.text.trim());

        if (result) {
          if (context.mounted) {
            context.go('/');
          }
        } else {
          errorMessage.value = 'Invalid license key. Please try again.';
        }
      } catch (e) {
        errorMessage.value = 'Failed to activate license. Please try again.';
      } finally {
        isLoading.value = false;
      }
    }

    Future<void> switchUser() async {
      await ref.read(vvpnAuthNotifierProvider.notifier).logout();
      if (context.mounted) {
        context.go('/login');
      }
    }

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Form(
            key: formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 40),
                // Logo
                Center(
                  child: Container(
                    width: 100,
                    height: 100,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer,
                      shape: BoxShape.circle,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Assets.images.logo.svg(
                        colorFilter: ColorFilter.mode(
                          theme.colorScheme.primary,
                          BlendMode.srcIn,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 32),
                // Title
                Text(
                  'Activate License',
                  style: theme.textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  'Enter your license key to start using V-VPN',
                  style: theme.textTheme.bodyLarge?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 40),
                // License key field
                TextFormField(
                  controller: licenseKeyController,
                  decoration: InputDecoration(
                    labelText: 'License Key',
                    hintText: 'XXXX-XXXX-XXXX-XXXX',
                    prefixIcon: const Icon(FluentIcons.key_24_regular),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  textCapitalization: TextCapitalization.characters,
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'Please enter your license key';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                // Error message
                if (errorMessage.value != null)
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          FluentIcons.error_circle_24_filled,
                          color: theme.colorScheme.onErrorContainer,
                          size: 20,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            errorMessage.value!,
                            style: TextStyle(
                              color: theme.colorScheme.onErrorContainer,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: 24),
                // Activate button
                FilledButton(
                  onPressed: isLoading.value ? null : activateLicense,
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: isLoading.value
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Activate License'),
                ),
                const SizedBox(height: 16),
                // Switch User button
                OutlinedButton.icon(
                  onPressed: isLoading.value ? null : switchUser,
                  icon: const Icon(FluentIcons.person_swap_24_regular),
                  label: const Text('Switch User'),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
