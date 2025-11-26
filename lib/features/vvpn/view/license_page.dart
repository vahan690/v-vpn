import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:vvpn/core/localization/translations.dart';
import 'package:vvpn/features/vvpn/model/vvpn_models.dart';
import 'package:vvpn/features/vvpn/notifier/vvpn_auth_notifier.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:intl/intl.dart';

class VvpnLicensePage extends HookConsumerWidget {
  const VvpnLicensePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider);
    final theme = Theme.of(context);
    final authState = ref.watch(vvpnAuthNotifierProvider);
    final user = authState.valueOrNull?.user;
    final licenseInfo = authState.valueOrNull?.licenseInfo;

    return Scaffold(
      appBar: AppBar(
        title: const Text('License Information'),
        actions: [
          IconButton(
            icon: const Icon(FluentIcons.arrow_sync_24_regular),
            onPressed: () async {
              await ref.read(vvpnAuthNotifierProvider.notifier).refreshLicenseInfo();
            },
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // License status card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: theme.colorScheme.primaryContainer,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Icon(
                            FluentIcons.certificate_24_filled,
                            color: theme.colorScheme.onPrimaryContainer,
                            size: 32,
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'License Status',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              if (licenseInfo != null)
                                _StatusChip(
                                  label: licenseInfo.isActive ? 'Active' : 'Inactive',
                                  color: licenseInfo.isActive ? Colors.green : theme.colorScheme.error,
                                )
                              else
                                _StatusChip(
                                  label: 'No License',
                                  color: theme.colorScheme.error,
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // License details card
            if (licenseInfo != null) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'License Details',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 16),
                      _DetailRow(
                        icon: FluentIcons.key_24_regular,
                        label: 'License Key',
                        value: licenseInfo.licenseKey,
                      ),
                      const Divider(),
                      _DetailRow(
                        icon: FluentIcons.ticket_diagonal_24_regular,
                        label: 'Plan',
                        value: _formatPlanId(licenseInfo.planId),
                      ),
                      const Divider(),
                      _DetailRow(
                        icon: FluentIcons.mail_24_regular,
                        label: 'Bound to Account',
                        value: licenseInfo.userEmail,
                      ),
                      const Divider(),
                      _DetailRow(
                        icon: FluentIcons.calendar_24_regular,
                        label: 'Expires On',
                        value: _formatExpiresOn(licenseInfo.expiryDate),
                      ),
                      const Divider(),
                      _DetailRow(
                        icon: FluentIcons.clock_24_regular,
                        label: 'Time Remaining',
                        value: _getTimeRemaining(licenseInfo.expiryDate),
                        valueColor: _getTimeRemainingColor(licenseInfo.expiryDate, theme),
                      ),
                    ],
                  ),
                ),
              ),
            ] else if (user != null) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Account Information',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 16),
                      _DetailRow(
                        icon: FluentIcons.mail_24_regular,
                        label: 'Email',
                        value: user.email,
                      ),
                      const SizedBox(height: 24),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.errorContainer,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          children: [
                            Icon(
                              FluentIcons.warning_24_filled,
                              color: theme.colorScheme.onErrorContainer,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                'You do not have an active license. Please purchase a license to use V-VPN.',
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: theme.colorScheme.onErrorContainer,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ] else ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Center(
                    child: Text(
                      'Not logged in',
                      style: theme.textTheme.bodyLarge,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _formatPlanId(String planId) {
    switch (planId.toLowerCase()) {
      case 'monthly':
        return 'Monthly';
      case 'yearly':
        return 'Yearly';
      case 'weekly':
        return 'Weekly';
      case 'lifetime':
        return 'Lifetime';
      default:
        if (planId.isEmpty) return 'Free';
        return planId.substring(0, 1).toUpperCase() + planId.substring(1);
    }
  }

  String _formatExpiresOn(DateTime expiresAt) {
    final dateFormat = DateFormat('MMM dd, yyyy');
    final timeFormat = DateFormat('hh:mm a');
    return '${dateFormat.format(expiresAt)} at ${timeFormat.format(expiresAt)}';
  }

  String _getTimeRemaining(DateTime expiresAt) {
    final now = DateTime.now();
    final difference = expiresAt.difference(now);

    if (difference.isNegative) {
      return 'Expired';
    }

    final days = difference.inDays;
    if (days == 0) {
      final hours = difference.inHours;
      if (hours == 0) {
        return '${difference.inMinutes} minutes';
      }
      return '$hours hours';
    } else if (days == 1) {
      return '1 day';
    } else {
      return '$days days';
    }
  }

  Color? _getTimeRemainingColor(DateTime expiresAt, ThemeData theme) {
    final now = DateTime.now();
    final difference = expiresAt.difference(now);

    if (difference.isNegative) {
      return theme.colorScheme.error;
    }

    final days = difference.inDays;
    if (days <= 3) {
      return Colors.orange;
    } else if (days <= 7) {
      return Colors.amber;
    }
    return Colors.green;
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({
    required this.label,
    required this.color,
  });

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.2),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.icon,
    required this.label,
    required this.value,
    this.valueColor,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Icon(
            icon,
            size: 20,
            color: theme.colorScheme.onSurfaceVariant,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: valueColor,
                    fontWeight: valueColor != null ? FontWeight.w600 : null,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
