import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:gap/gap.dart';
import 'package:go_router/go_router.dart';
import 'package:vvpn/features/vvpn/data/vvpn_repository.dart';
import 'package:vvpn/features/vvpn/model/vvpn_models.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:flutter_hooks/flutter_hooks.dart';

class ResetPasswordPage extends HookConsumerWidget {
  const ResetPasswordPage({super.key, this.email});

  final String? email;
  static const routeName = '/reset-password';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final codeController = useTextEditingController();
    final passwordController = useTextEditingController();
    final confirmPasswordController = useTextEditingController();
    final formKey = useMemoized(() => GlobalKey<FormState>());
    final isLoading = useState(false);
    final errorMessage = useState<String?>(null);
    final obscurePassword = useState(true);
    final obscureConfirmPassword = useState(true);

    Future<void> resetPassword() async {
      if (!(formKey.currentState?.validate() ?? false)) return;

      isLoading.value = true;
      errorMessage.value = null;

      try {
        final repository = await ref.read(vvpnRepositoryProvider.future);
        final result = await repository.resetPassword(
          codeController.text.trim(),
          passwordController.text,
        );

        result.fold(
          (failure) {
            errorMessage.value = switch (failure) {
              VvpnNetworkFailure(:final message) => message,
              VvpnAuthFailure(:final message) => message,
              VvpnServerFailure(:final message) => message,
              VvpnUnexpectedFailure(:final error) => error.toString(),
            };
          },
          (message) {
            // Show success dialog and navigate to login
            showDialog(
              context: context,
              barrierDismissible: false,
              builder: (context) => AlertDialog(
                icon: Icon(
                  Icons.check_circle_outline,
                  size: 48,
                  color: Theme.of(context).colorScheme.primary,
                ),
                title: const Text('Password Reset'),
                content: Text(message),
                actions: [
                  FilledButton(
                    onPressed: () {
                      Navigator.of(context).pop();
                      context.go('/login');
                    },
                    child: const Text('Sign In'),
                  ),
                ],
              ),
            );
          },
        );
      } finally {
        isLoading.value = false;
      }
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Reset Password'),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Icon(
                    Icons.password_rounded,
                    size: 80,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                  const Gap(16),
                  Text(
                    'Enter Reset Code',
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  const Gap(8),
                  Text(
                    email != null
                        ? 'We sent a 6-digit code to $email'
                        : 'Enter the 6-digit code sent to your email',
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  const Gap(32),

                  // Code field
                  TextFormField(
                    controller: codeController,
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.next,
                    maxLength: 6,
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                    ],
                    decoration: const InputDecoration(
                      labelText: 'Reset Code',
                      prefixIcon: Icon(Icons.pin_outlined),
                      border: OutlineInputBorder(),
                      counterText: '',
                      hintText: '000000',
                    ),
                    style: const TextStyle(
                      letterSpacing: 8,
                      fontSize: 20,
                    ),
                    textAlign: TextAlign.center,
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Please enter the reset code';
                      }
                      if (value.length != 6) {
                        return 'Code must be 6 digits';
                      }
                      return null;
                    },
                  ),
                  const Gap(16),

                  // New Password field
                  TextFormField(
                    controller: passwordController,
                    obscureText: obscurePassword.value,
                    textInputAction: TextInputAction.next,
                    decoration: InputDecoration(
                      labelText: 'New Password',
                      prefixIcon: const Icon(Icons.lock_outlined),
                      border: const OutlineInputBorder(),
                      suffixIcon: IconButton(
                        icon: Icon(
                          obscurePassword.value
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                        onPressed: () {
                          obscurePassword.value = !obscurePassword.value;
                        },
                      ),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Please enter a new password';
                      }
                      if (value.length < 6) {
                        return 'Password must be at least 6 characters';
                      }
                      return null;
                    },
                  ),
                  const Gap(16),

                  // Confirm Password field
                  TextFormField(
                    controller: confirmPasswordController,
                    obscureText: obscureConfirmPassword.value,
                    textInputAction: TextInputAction.done,
                    decoration: InputDecoration(
                      labelText: 'Confirm New Password',
                      prefixIcon: const Icon(Icons.lock_outlined),
                      border: const OutlineInputBorder(),
                      suffixIcon: IconButton(
                        icon: Icon(
                          obscureConfirmPassword.value
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                        onPressed: () {
                          obscureConfirmPassword.value = !obscureConfirmPassword.value;
                        },
                      ),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Please confirm your new password';
                      }
                      if (value != passwordController.text) {
                        return 'Passwords do not match';
                      }
                      return null;
                    },
                    onFieldSubmitted: (_) => resetPassword(),
                  ),
                  const Gap(24),

                  // Error message
                  if (errorMessage.value != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        errorMessage.value!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onErrorContainer,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  if (errorMessage.value != null) const Gap(16),

                  // Reset Password button
                  FilledButton(
                    onPressed: isLoading.value ? null : resetPassword,
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: isLoading.value
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                              ),
                            )
                          : const Text('Reset Password'),
                    ),
                  ),
                  const Gap(16),

                  // Back to login link
                  TextButton(
                    onPressed: () {
                      context.go('/login');
                    },
                    child: const Text('Back to Sign In'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
