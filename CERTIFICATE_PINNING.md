# Certificate Pinning Implementation - V-VPN

## Overview

Certificate pinning has been implemented to prevent Man-in-the-Middle (MITM) attacks on all API communications. This is a critical security feature that ensures your app only communicates with your legitimate backend servers, even if an attacker has root access or installs custom CA certificates on the device.

---

## What is Certificate Pinning?

Certificate pinning validates that the SSL certificate presented by the server matches a specific certificate (or its public key) that's hardcoded in the app. This prevents attackers from intercepting encrypted traffic, even if they:

- Have root access to the device
- Install custom CA certificates
- Use proxy tools like Charles Proxy, Burp Suite, mitmproxy
- Perform DNS hijacking or ARP spoofing

---

## Implementation Details

### Files Modified/Created

1. **NEW: `SecureHttpClient.kt`**
   - Singleton OkHttpClient with certificate pinning configured
   - Contains SHA-256 certificate pins for `api.vvpn.space` and `bsc.vvpn.space`
   - Includes backup pins for Google Trust Services intermediate CA

2. **UPDATED: `PaymentManager.kt`**
   - Refactored from HttpURLConnection to OkHttp with certificate pinning
   - All API calls now use `SecureHttpClient.client`
   - Methods updated: `fetchServerConfig`, `createBscPayment`, `checkPaymentStatus`, `verifyLicense`, `verifyAndLinkLicense`

3. **UPDATED: `AuthManager.kt`**
   - Refactored from HttpURLConnection to OkHttp with certificate pinning
   - Methods updated: `register`, `login`

4. **UPDATED: `MainActivity.kt`**
   - License fetching now uses `SecureHttpClient` with certificate pinning
   - Function updated: `fetchAndSaveLicenses`

5. **UPDATED: `proguard-rules.pro`**
   - Added rules to protect certificate pinning configuration from obfuscation
   - Ensures pinning works correctly in release builds

---

## Current Certificate Pins

### Primary Certificate Pin (Both Domains)
```
sha256/WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=
```

This pin is for the SSL certificate currently used by:
- `api.vvpn.space`
- `bsc.vvpn.space`

### Backup Pin (Intermediate CA)
```
sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=
```

This is the pin for **Google Trust Services WE1** intermediate CA. This provides resilience during certificate rotation.

---

## Certificate Rotation Guide

**IMPORTANT:** When your SSL certificates are renewed (typically every 90 days for Let's Encrypt), you MUST update the certificate pins in the app. Failure to do so will cause the app to fail connecting to your API.

### Step 1: Get New Certificate Pin (BEFORE Certificate Renewal)

Run this command to get the SHA-256 pin of your current or new certificate:

```bash
echo | openssl s_client -servername api.vvpn.space -connect api.vvpn.space:443 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

**Output example:** `WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=`

### Step 2: Add New Pin as Backup (BEFORE Renewal)

**CRITICAL:** Add the new certificate pin to `SecureHttpClient.kt` BEFORE renewing your SSL certificate:

```kotlin
private val certificatePinner = CertificatePinner.Builder()
    // OLD certificate (current production)
    .add("api.vvpn.space", "sha256/WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=")
    .add("bsc.vvpn.space", "sha256/WsauAvtpqgBjig/NhGyq5M1Qy0rruP1ebXu8ZZsxunM=")

    // NEW certificate (backup - add BEFORE renewal)
    .add("api.vvpn.space", "sha256/<NEW_PIN_HERE>")
    .add("bsc.vvpn.space", "sha256/<NEW_PIN_HERE>")

    // Intermediate CA backup
    .add("api.vvpn.space", "sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=")
    .add("bsc.vvpn.space", "sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=")
    .build()
```

### Step 3: Release App Update with Both Pins

1. Build and release new APK with both old and new pins
2. Wait for majority of users to update (check Google Play Console)

### Step 4: Renew SSL Certificate

Once most users have the updated app:
1. Renew your SSL certificate on your server
2. The app will now accept both old and new certificates

### Step 5: Remove Old Pin (Next Update)

In the next app update, remove the old pin:

```kotlin
private val certificatePinner = CertificatePinner.Builder()
    // Only keep new certificate
    .add("api.vvpn.space", "sha256/<NEW_PIN_HERE>")
    .add("bsc.vvpn.space", "sha256/<NEW_PIN_HERE>")

    // Keep intermediate CA backup
    .add("api.vvpn.space", "sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=")
    .add("bsc.vvpn.space", "sha256/f8NnEFzxsikbfSZmUzDMhQnlMqVeQkSQ5SXSjytHE2Y=")
    .build()
```

---

## Testing Certificate Pinning

### Test 1: Normal Operation
1. Build release APK with certificate pinning
2. Install on test device
3. Test all API operations (login, payment, license verification, VPN connection)
4. All should work normally

### Test 2: MITM Detection (Optional)
1. Install a proxy tool (Charles Proxy, Burp Suite, mitmproxy)
2. Configure device to use the proxy
3. Install proxy's CA certificate on device
4. Try to use the app
5. **Expected Result:** App should FAIL to connect, showing SSL errors in logs

This confirms certificate pinning is working and preventing MITM attacks.

### Test 3: Certificate Expiry Simulation
Before deploying to production, test the rotation process on a staging server:
1. Get staging server certificate pin
2. Add to app as backup pin
3. Test app connects successfully
4. Change staging certificate
5. Verify app still connects (using backup pin)

---

## Troubleshooting

### Error: `Certificate pinning failure!`

**Cause:** Certificate on server doesn't match any pin in the app.

**Solutions:**
1. Check server certificate: `openssl s_client -servername api.vvpn.space -connect api.vvpn.space:443`
2. Get current certificate pin (command in Step 1 above)
3. Update `SecureHttpClient.kt` with correct pin
4. Rebuild and reinstall app

### Error: `SSLHandshakeException`

**Cause:** TLS/SSL negotiation failed

**Check:**
1. Server is using valid SSL certificate
2. Certificate is not expired
3. Server supports TLS 1.2 or higher
4. Certificate chain is complete

### App Fails After Certificate Renewal

**Cause:** Certificate was renewed but app wasn't updated with new pin.

**Prevention:**
- Always add new pin BEFORE renewing certificate
- Never remove old pin until new pin is deployed to users

**Emergency Fix:**
1. Revert to old certificate temporarily
2. Release app update with both pins
3. Wait for users to update
4. Switch to new certificate

---

## Security Best Practices

### ✅ DO:
- Add new pins BEFORE renewing certificates
- Keep backup pins (intermediate CA)
- Test certificate rotation on staging environment first
- Monitor certificate expiry dates (set alerts 30 days before)
- Use automated cert renewal (Let's Encrypt with auto-renewal)

### ❌ DON'T:
- Remove old pins immediately after certificate renewal
- Disable certificate pinning for "debugging" in production builds
- Use self-signed certificates in production
- Forget to update pins before certificate expires
- Rely solely on primary certificate pin (always have backup pins)

---

## Certificate Expiry Monitoring

Set up monitoring to alert you 30 days before certificate expiry:

```bash
# Check certificate expiry
echo | openssl s_client -servername api.vvpn.space -connect api.vvpn.space:443 2>/dev/null | openssl x509 -noout -dates
```

**Output example:**
```
notBefore=Nov  6 10:37:13 2025 GMT
notAfter=Feb  4 10:37:12 2026 GMT
```

---

## Impact on Security

Certificate pinning provides protection against:

✅ **Man-in-the-Middle (MITM) attacks**
✅ **SSL stripping attacks**
✅ **Compromised Certificate Authorities**
✅ **Rogue proxy servers**
✅ **DNS hijacking with fake certificates**
✅ **Traffic interception on public WiFi**
✅ **App debugging with proxy tools (makes reverse engineering harder)**

---

## Backup Plan

If certificate pinning causes issues in production:

1. **Emergency disable** (requires app update):
   - Temporarily use `SecureHttpClient.insecureClient` (NOT RECOMMENDED)
   - This is only for emergency recovery

2. **Better approach:**
   - Always test on staging environment first
   - Have rollback APK ready
   - Monitor error rates after release
   - Use gradual rollout (10% → 50% → 100%)

---

## Additional Resources

- OkHttp Certificate Pinning: https://square.github.io/okhttp/features/https/#certificate-pinning
- OWASP Certificate Pinning: https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning
- Let's Encrypt Certificate Lifecycle: https://letsencrypt.org/docs/

---

**Last Updated:** 2025-11-15
**Author:** Claude Code
**V-VPN Security Team**
