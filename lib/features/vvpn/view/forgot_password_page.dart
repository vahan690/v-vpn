import 'package:flutter/material.dart';
import 'package:gap/gap.dart';
import 'package:go_router/go_router.dart';
import 'package:vvpn/features/vvpn/data/vvpn_repository.dart';
import 'package:vvpn/features/vvpn/model/vvpn_models.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:flutter_hooks/flutter_hooks.dart';

class ForgotPasswordPage extends HookConsumerWidget {
  const ForgotPasswordPage({super.key});

  static const routeName = '/forgot-password';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final emailController = useTextEditingController();
    final formKey = useMemoized(() => GlobalKey<FormState>());
    final isLoading = useState(false);
    final errorMessage = useState<String?>(null);
    final successMessage = useState<String?>(null);

    Future<void> sendResetCode() async {
      if (!(formKey.currentState?.validate() ?? false)) return;

      isLoading.value = true;
      errorMessage.value = null;
      successMessage.value = null;

      try {
        final repository = await ref.read(vvpnRepositoryProvider.future);
        final result = await repository.forgotPassword(emailController.text.trim());

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
            successMessage.value = message;
            // Navigate to reset password page with email
            context.push('/reset-password', extra: emailController.text.trim());
          },
        );
      } finally {
        isLoading.value = false;
      }
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Forgot Password'),
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
                    Icons.lock_reset_rounded,
                    size: 80,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                  const Gap(16),
                  Text(
                    'Reset Password',
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  const Gap(8),
                  Text(
                    'Enter your email address and we\'ll send you a code to reset your password.',
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  const Gap(32),

                  // Email field
                  TextFormField(
                    controller: emailController,
                    keyboardType: TextInputType.emailAddress,
                    autocorrect: false,
                    textInputAction: TextInputAction.done,
                    decoration: const InputDecoration(
                      labelText: 'Email',
                      prefixIcon: Icon(Icons.email_outlined),
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Please enter your email';
                      }
                      if (!value.contains('@')) {
                        return 'Please enter a valid email';
                      }
                      return null;
                    },
                    onFieldSubmitted: (_) => sendResetCode(),
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

                  // Success message
                  if (successMessage.value != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        successMessage.value!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onPrimaryContainer,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  if (successMessage.value != null) const Gap(16),

                  // Send Code button
                  FilledButton(
                    onPressed: isLoading.value ? null : sendResetCode,
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
                          : const Text('Send Reset Code'),
                    ),
                  ),
                  const Gap(16),

                  // Back to login link
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'Remember your password? ',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                      TextButton(
                        onPressed: () {
                          context.pop();
                        },
                        child: const Text('Sign In'),
                      ),
                    ],
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
