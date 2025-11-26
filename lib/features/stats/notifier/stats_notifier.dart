import 'package:vvpn/features/connection/notifier/connection_notifier.dart';
import 'package:vvpn/features/stats/data/stats_data_providers.dart';
import 'package:vvpn/features/stats/model/stats_entity.dart';
import 'package:vvpn/utils/custom_loggers.dart';
import 'package:vvpn/utils/riverpod_utils.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'stats_notifier.g.dart';

@riverpod
class StatsNotifier extends _$StatsNotifier with AppLogger {
  @override
  Stream<StatsEntity> build() async* {
    ref.disposeDelay(const Duration(seconds: 10));
    final serviceRunning = await ref.watch(serviceRunningProvider.future);
    if (serviceRunning) {
      yield* ref
          .watch(statsRepositoryProvider)
          .watchStats()
          .map((event) => event.getOrElse((_) => StatsEntity.empty()));
    } else {
      yield* Stream.value(StatsEntity.empty());
    }
  }
}
