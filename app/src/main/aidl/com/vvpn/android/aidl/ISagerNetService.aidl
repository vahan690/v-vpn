package com.vvpn.android.aidl;

import com.vvpn.android.aidl.ISagerNetServiceCallback;
import com.vvpn.android.aidl.ProxySet;
import com.vvpn.android.aidl.URLTestResult;
import com.vvpn.android.aidl.Connections;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);

  int urlTest(String tag);

  Connections queryConnections();
  long queryMemory();
  int queryGoroutines();
  oneway void closeConnection(String id);
  oneway void resetNetwork();
  List<String> getClashModes();
  String getClashMode();
  oneway void setClashMode(String mode);

  List<ProxySet> queryProxySet();
  boolean groupSelect(in String group, String proxy);
  URLTestResult groupURLTest(String tag, int timeout);
}
