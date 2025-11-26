import 'dart:io';

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:vvpn/core/haptic/haptic_service.dart';
import 'package:vvpn/core/localization/translations.dart';
import 'package:vvpn/core/preferences/general_preferences.dart';
import 'package:vvpn/features/auto_start/notifier/auto_start_notifier.dart';
import 'package:vvpn/features/common/general_pref_tiles.dart';
import 'package:vvpn/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class GeneralSettingTiles extends HookConsumerWidget {
  const GeneralSettingTiles({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return const Column(
      children: [
        LocalePrefTile(),
        ThemeModePrefTile(),
      ],
    );
  }
}
