package io.vvpn.android.aidl;

import io.vvpn.android.aidl.SpeedDisplayData;
import io.vvpn.android.aidl.TrafficData;

oneway interface ISagerNetServiceCallback {
  void stateChanged(int state, String profileName, String msg);
  void missingPlugin(String profileName, String pluginName);
  void cbSpeedUpdate(in SpeedDisplayData stats);
  void cbTrafficUpdate(in TrafficData stats);
}
