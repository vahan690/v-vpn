# Advanced Security Features - V-VPN

## Overview

This document covers the advanced security features implemented in V-VPN to make reverse engineering and cracking extremely difficult:

1. **String Obfuscation** - Hides sensitive strings from static analysis
2. **Anti-Debugging** - Detects and blocks debugging attempts
3. **Network Security Configuration** - XML-based network hardening

---

## 1. String Obfuscation

### What It Does

Encrypts sensitive strings in your APK to prevent attackers from finding:
- API URLs
- Package names for detection
- Security-related constants
- Any other sensitive strings

When decompiled, attackers see gibberish instead of plaintext:

**Before (Plaintext):**
```kotlin
val apiUrl = "https://api.vvpn.space"
```

**After (Obfuscated):**
```kotlin
val apiUrl = StringObfuscator.deobfuscate("Ij8fFhEVGAQcFwIwEhcTEhMZBQ==")
```

### Implementation Files

**`StringObfuscator.kt`** - Obfuscation engine
- Simple XOR-based obfuscation with Base64 encoding
- Not cryptographically secure, but makes static analysis much harder
- Combined with ProGuard, very difficult to reverse

**`ObfuscatedStrings.kt`** - Pre-obfuscated constants
- Common strings already obfuscated
- Easy to add new obfuscated strings

### How to Use

#### Adding New Obfuscated Strings:

1. **Generate obfuscated version:**
   ```kotlin
   val obfuscated = ObfuscatedStrings.generate("your-secret-string")
   println("Obfuscated: $obfuscated")
   ```

2. **Add to ObfuscatedStrings.kt:**
   ```kotlin
   val MY_SECRET by lazy { StringObfuscator.deobfuscate("...") }
   ```

3. **Use in your code:**
   ```kotlin
   val secret = ObfuscatedStrings.MY_SECRET
   ```

#### Currently Obfuscated:

- `API_BASE_URL` - "https://api.vvpn.space"
- `BSC_BASE_URL` - "https://bsc.vvpn.space"
- `MAGISK_PACKAGE` - "com.topjohnwu.magisk"
- `SUPERSU_PACKAGE` - "eu.chainfire.supersu"
- Security dialog strings

### Customization

**Change the obfuscation key** in `StringObfuscator.kt`:

```kotlin
private val KEY = byteArrayOf(
    // Replace with your own random bytes
    0x56, 0x76, 0x70, 0x6E.toByte(),
    // ... more bytes ...
)
```

This makes your obfuscation unique to your app.

---

## 2. Anti-Debugging

### What It Does

Detects when someone is trying to debug/analyze your app:

✅ **Basic Checks:**
- `Debug.isDebuggerConnected()`
- `Debug.waitingForDebugger()`

✅ **Advanced Checks:**
- TracerPid monitoring (kernel-level detection)
- Timing anomaly detection (debuggers slow down execution)
- Debugger port scanning (JDWP, Frida ports)
- Thread count anomaly
- ADB and USB debugging detection

### Implementation

**`AntiDebug.kt`** - Anti-debugging toolkit

### Usage Examples

#### Passive Monitoring (Current Default):

Integrated into `SecurityManager` - logs debugging attempts but allows app to continue:

```kotlin
val result = AntiDebug.performAntiDebugChecks(context)
if (result.isDebugging) {
    Log.w("Security", "Debugging detected: ${result.indicators}")
    // App continues, but activity is logged
}
```

#### Aggressive Enforcement:

Block app immediately if debugging detected:

```kotlin
AntiDebug.enforceAntiDebug(context)
// If debugging detected, app exits immediately
```

### Current Status

**Integrated into SecurityManager** with WARNING policy:
- Detects debugging attempts
- Logs all indicators
- Allows app to continue
- Can be changed to STRICT to block

### Detected Indicators

When debugging is detected, you'll see logs like:

```
W/SecurityManager: Security threats detected:
W/SecurityManager:   - [HIGH] DEBUGGER_ATTACHED: Debugging detected
W/SecurityManager:     Details: Debugger connected (basic check), TracerPid detected, Debugger ports open
```

### Configuration

**To enable aggressive anti-debugging:**

In `MainActivity.kt`:
```kotlin
private fun performSecurityCheck() {
    SecurityManager.setPolicy(SecurityManager.Policy.STRICT)  // ← Change to STRICT
    // ...
}
```

With STRICT policy, app will exit if debugging is detected.

---

## 3. Network Security Configuration

### What It Does

XML-based network security hardening that:

✅ **Disables cleartext (HTTP) traffic** - Forces HTTPS only
✅ **Certificate pinning** - Backup layer (redundant with OkHttp pinning)
✅ **Blocks user-installed CA certificates** - Prevents MITM even with root
✅ **Enforces TLS 1.2+** - No old, vulnerable protocols

### Implementation

**`network_security_config.xml`** - Android Network Security Configuration

**`AndroidManifest.xml`** - Updated:
```xml
android:networkSecurityConfig="@xml/network_security_config"
android:usesCleartextTraffic="false"
```

### Features

#### 1. Base Configuration

```xml
<base-config cleartextTrafficPermitted="false">
    <trust-anchors>
        <!-- Only trust system CA certificates -->
        <certificates src="system" />
    </trust-anchors>
</base-config>
```

**Effect:** All HTTP traffic blocked, only system CAs trusted.

#### 2. Domain-Specific Pinning

```xml
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">api.vvpn.space</domain>
    <domain includeSubdomains="true">bsc.vvpn.space</domain>

    <pin-set expiration="2026-02-04">
        <pin digest="SHA-256">WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=</pin>
        <pin digest="SHA-256">f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=</pin>
    </pin-set>
</domain-config>
```

**Effect:** Certificate pinning for V-VPN API servers.

#### 3. Debug Overrides

```xml
<debug-overrides>
    <trust-anchors>
        <!-- Trust user CAs ONLY in debug builds -->
        <certificates src="user" />
        <certificates src="system" />
    </trust-anchors>
</debug-overrides>
```

**Effect:** Debug builds can use Charles Proxy, etc. for testing.

### Certificate Rotation

**IMPORTANT:** When renewing SSL certificates, update pins in:

1. `network_security_config.xml` (lines 47-53)
2. `SecureHttpClient.kt` (OkHttp pinning)

Both must be updated together!

### Testing

**Test that HTTP is blocked:**
```bash
# This should FAIL
adb shell am start -a android.intent.action.VIEW -d "http://api.vvpn.space"
```

**Test that user CAs are blocked (release build):**
1. Install Charles Proxy certificate
2. Try to intercept API traffic
3. App should refuse connection (SSL error)

---

## Combined Security Layers

Your app now has **6 layers of security**:

1. ✅ **Server Config API** - Credentials not in APK
2. ✅ **ProGuard/R8 Obfuscation** - Code obfuscated
3. ✅ **Certificate Pinning** (OkHttp + XML) - Double MITM protection
4. ✅ **Root/Tamper Detection** - Blocks compromised devices
5. ✅ **String Obfuscation** - Sensitive strings encrypted
6. ✅ **Anti-Debugging** - Blocks analysis attempts
7. ✅ **Network Security Config** - Network hardening

---

## Security Policy Recommendations

### Development/Testing:
```kotlin
SecurityManager.setPolicy(SecurityManager.Policy.PERMISSIVE)
```
- No blocking
- Logs all threats
- Good for testing on rooted devices/emulators

### Initial Release (Recommended):
```kotlin
SecurityManager.setPolicy(SecurityManager.Policy.WARNING)
```
- Logs all threats
- Doesn't block users
- Collect data on detection rates

### Strict Production (After Monitoring):
```kotlin
SecurityManager.setPolicy(SecurityManager.Policy.STRICT)
```
- Blocks HIGH severity threats
- Best anti-cracking protection
- May lose some legitimate users (rooted devices)

---

## Cracker's Perspective

What an attacker needs to bypass:

### Level 1: Static Analysis
1. ❌ Decompile APK → Get obfuscated code (hard to read)
2. ❌ Extract strings → Get encrypted strings (can't find API URLs)
3. ❌ Find license code → Scattered across obfuscated classes

### Level 2: Dynamic Analysis
4. ❌ Attach debugger → Detected and logged/blocked
5. ❌ Use Frida → Detected by multiple checks
6. ❌ Use Xposed → Detected immediately
7. ❌ Root device → Device blocked (if STRICT policy)

### Level 3: Network Analysis
8. ❌ MITM with proxy → Certificate pinning blocks
9. ❌ Install custom CA → Network config blocks user CAs
10. ❌ Modify SSL → Two layers of pinning to bypass

### Level 4: Code Modification
11. ❌ Modify APK → Signature check fails
12. ❌ Re-sign APK → Signature SHA-256 doesn't match
13. ❌ Patch security checks → Must find and patch 6+ different systems

**Conclusion:** Attackers must bypass ALL layers - very difficult and time-consuming.

---

## Monitoring & Analytics

### What to Log

Log security events to your backend for analysis:

```kotlin
// In SecurityManager.performSecurityCheck()
if (!securityStatus.isSecure) {
    // Send to analytics
    sendToBackend(mapOf(
        "event" to "security_threat",
        "threats" to securityStatus.threats.map { it.description },
        "policy" to currentPolicy.toString(),
        "timestamp" to System.currentTimeMillis()
    ))
}
```

### Metrics to Track

- **Root detection rate** - % of users on rooted devices
- **Tamper detection rate** - % of users with modified APKs
- **Debug detection rate** - % of debugging attempts
- **Policy impact** - User retention with WARNING vs STRICT

This helps you decide whether to use STRICT policy.

---

## Troubleshooting

### Issue: App crashes on startup in release build

**Cause:** Network Security Config preventing connections

**Fix:** Check that your API domains are in `network_security_config.xml`

### Issue: Certificate pinning failures

**Cause:** SSL certificate was renewed but pins not updated

**Fix:** Update pins in both `network_security_config.xml` and `SecureHttpClient.kt`

### Issue: False positive root detection

**Cause:** Some legitimate devices trigger detection

**Solution:** Use WARNING policy instead of STRICT

### Issue: String obfuscation not working

**Cause:** ProGuard might be optimizing away the obfuscation

**Fix:** Check `proguard-rules.pro` has:
```proguard
-keep class com.vvpn.android.security.StringObfuscator { *; }
```

---

## Future Enhancements

Possible additions for even stronger security:

1. **Native Code (C++) Protection** - Move critical logic to NDK
2. **Code Integrity Checks** - Runtime verification of code integrity
3. **Encrypted DEX** - Encrypt the entire app binary
4. **SafetyNet Attestation** - Google's device integrity API
5. **Custom Obfuscation** - Beyond R8, custom code transformations
6. **Control Flow Obfuscation** - Make code flow hard to follow
7. **Dynamic String Decryption** - Decrypt strings only when needed

---

**Last Updated:** 2025-11-15
**Author:** Claude Code
**V-VPN Security Team**
