# V-VPN Release v1.0.0

**Release Date:** November 14, 2025
**Build Type:** Production Release (FOSS)

---

## 📦 Release Files

### Android APKs

| Architecture | File | Size | Recommended For |
|--------------|------|------|-----------------|
| ARM64-v8a | `v-vpn-1.0.0-arm64-v8a.apk` | 20MB | Most modern devices (2016+) |
| ARMv7 | `v-vpn-1.0.0-armeabi-v7a.apk` | 21MB | Older ARM devices |
| x86_64 | `v-vpn-1.0.0-x86_64.apk` | 21MB | Intel/AMD 64-bit devices |
| x86 | `v-vpn-1.0.0-x86.apk` | 21MB | Intel/AMD 32-bit devices |

**Recommended:** Use `v-vpn-1.0.0-arm64-v8a.apk` for most Android phones and tablets.

---

## ✨ What's New in v1.0.0

### License Activation System
- ✅ Fixed manual license entry binding to user accounts
- ✅ Fixed BSC crypto payment license activation
- ✅ Licenses now persist after app restart
- ✅ Proper user authentication with JWT tokens

### Performance Improvements
- ✅ Replaced Thread with Coroutines for reliability in release builds
- ✅ Disabled HTTP body logging in release builds for better performance
- ✅ Increased network timeout from 10s to 30s for better stability

### Backend Integration
- ✅ New `/api/license/verify-and-link` endpoint for proper license binding
- ✅ Automatic license fetching after user login
- ✅ Support for both manual entry and BSC payment flows

### Package Refactoring
- ✅ Complete package rename: `io.nekohasekai.sagernet` → `com.vvpn.android`
- ✅ Clean namespace for V-VPN branding
- ✅ Removed unused protocol implementations (keeping only Hysteria2)

---

## 🔧 Installation

### Method 1: ADB Install (Recommended for Testing)
```bash
adb install v-vpn-1.0.0-arm64-v8a.apk
```

### Method 2: Manual Install
1. Transfer APK to your Android device
2. Enable "Install from Unknown Sources" in Settings
3. Open the APK file and tap Install

---

## 🧪 Testing Checklist

### Manual License Entry
- [ ] Login with user account
- [ ] Enter license key manually
- [ ] Verify license activates successfully
- [ ] Restart app and verify license persists
- [ ] Check database shows correct user binding

### BSC Crypto Payment
- [ ] Login with user account
- [ ] Purchase subscription via BSC
- [ ] Wait for blockchain confirmation
- [ ] Verify license auto-activates
- [ ] Check database shows correct user binding

### App Stability
- [ ] No crashes on startup
- [ ] No ANR (Application Not Responding) errors
- [ ] VPN connects successfully
- [ ] HTTP logging disabled (check logcat)

---

## 🔐 Security Features

- JWT token-based authentication
- Licenses bound to user accounts
- Device ID tracking
- Secure HTTPS communication with backend

---

## 🐛 Known Issues

None reported in this release.

---

## 📊 Build Information

**Version Code:** 100
**Version Name:** 1.0.0
**Package Name:** com.vvpn.android
**Min SDK:** 21 (Android 5.0)
**Target SDK:** 34 (Android 14)
**Build Tool:** Gradle 8.x
**Protocol:** Hysteria2 v2.6.2-0

**ProGuard:** Enabled
**Code Shrinking:** Enabled
**Resource Shrinking:** Enabled

---

## 📝 Backend Requirements

This release requires the following backend services:

### License API (192.168.11.202)
- `/api/license/verify-and-link` - License activation endpoint
- `/api/license/device/:deviceId` - Fetch user licenses
- `/api/auth/login` - User authentication

### BSC Payment API (192.168.11.201)
- `/api/create-order` - Create payment order
- `/api/check-payment` - Check payment status
- Monitor service for blockchain confirmations

### Database (192.168.11.200)
- PostgreSQL with users, licenses, and orders tables
- `role` column in users table
- Foreign key: `licenses.user_id` → `users.id`

---

## 🚀 What's Next

### Planned for v1.1.0
- User portal for viewing licenses
- License renewal notifications
- Enhanced error messages
- Connection statistics

---

## 📞 Support

For issues or questions:
- Check backend logs: `pm2 logs license-api`
- Check app logs: `adb logcat | grep MainActivity`
- Verify database: Check license binding with SQL queries

---

**Built with V-VPN** 🛡️
