import 'package:vvpn/core/preferences/preferences_provider.dart';
import 'package:vvpn/features/config_option/data/config_option_repository.dart';

import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'config_option_data_providers.g.dart';

@Riverpod(keepAlive: true)
ConfigOptionRepository configOptionRepository(
  ConfigOptionRepositoryRef ref,
) {
  return ConfigOptionRepository(
    preferences: ref.watch(sharedPreferencesProvider).requireValue,
    getConfigOptions: () => ref.read(ConfigOptions.singboxConfigOptions.future),
  );
}
