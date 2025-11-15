# V-VPN 🛡️

**Secure VPN Application for Android**

V-VPN is a modern, secure VPN application powered by Hysteria2 protocol, designed for simplicity, privacy, and freedom.

[![Latest Release](https://img.shields.io/github/v/release/vahan690/v-vpn)](https://github.com/vahan690/v-vpn/releases/latest)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://www.android.com/)

---

## ✨ Features

- 🚀 **High Performance** - Powered by Hysteria2 v2.6.2-0 protocol
- 🔐 **Secure & Private** - End-to-end encryption
- 💳 **Multiple Payment Options** - BSC/USDT crypto payments or license keys
- 📱 **User Portal** - Manage your licenses and subscriptions
- ⚡ **Fast & Reliable** - Optimized for modern Android devices
- 🎯 **Simple Interface** - Easy to use, no complicated setup

---

## 📥 Download

**Latest Version: v1.0.0**

Download the APK for your device:

| Architecture | Download | Size |
|--------------|----------|------|
| **ARM64-v8a** ⭐ | [Download](https://github.com/vahan690/v-vpn/releases/download/v1.0.0/v-vpn-1.0.0-arm64-v8a.apk) | 20MB |
| ARMv7 | [Download](https://github.com/vahan690/v-vpn/releases/download/v1.0.0/v-vpn-1.0.0-armeabi-v7a.apk) | 21MB |
| x86_64 | [Download](https://github.com/vahan690/v-vpn/releases/download/v1.0.0/v-vpn-1.0.0-x86_64.apk) | 21MB |
| x86 | [Download](https://github.com/vahan690/v-vpn/releases/download/v1.0.0/v-vpn-1.0.0-x86.apk) | 21MB |

**Recommended:** ARM64-v8a for most modern Android devices (2016+)

**[View All Releases](https://github.com/vahan690/v-vpn/releases)**

---

## 🚀 Quick Start

### Installation

1. Download the appropriate APK for your device
2. Enable "Install from Unknown Sources" in Android Settings
3. Open the APK file and install
4. Launch V-VPN

### Activation

**Option 1: License Key**
1. Login or register an account
2. Enter your license key
3. Start using V-VPN

**Option 2: Crypto Payment**
1. Login or register an account
2. Choose a subscription plan
3. Pay with BSC/USDT
4. License automatically activated

---

## 🎯 Requirements

- **Android:** 5.0 (Lollipop) or higher
- **Architecture:** ARM64, ARMv7, x86, or x86_64
- **Permissions:** VPN, Network access

---

## 🌐 User Portal

Access your personal dashboard at **[user.vvpn.space](https://user.vvpn.space)**

**Features:**
- View your active licenses
- Check license expiry dates
- See payment history
- Get expiry warnings

---

## 🛠️ Build from Source

### Prerequisites

- JDK 21
- Android SDK
- Android NDK 28.2.13676358
- Go (for libcore compilation)

### Clone Repository

```bash
git clone https://github.com/vahan690/v-vpn.git
cd v-vpn
```

### Build libcore

```bash
make libcore
```

### Build APK

```bash
# Set environment variables
echo "sdk.dir=${ANDROID_HOME}" > local.properties

# Download geo assets
make assets

# Build release APK
./gradlew assembleRelease
```

APK files will be in `app/build/outputs/apk/foss/release/`

---

## 📚 Documentation

- **[Release Documentation](./release/README.md)** - Detailed release information
- **[Admin Panel Guide](./ADMIN_PANEL_GUIDE.md)** - For administrators
- **[Testing Checklist](./TESTING_CHECKLIST.md)** - QA testing guide
- **[License Flow](./COMPLETE_LICENSE_FLOW_SUMMARY.md)** - How licensing works

---

## 🔒 Security

V-VPN takes security seriously:

- ✅ JWT-based authentication
- ✅ License binding to user accounts
- ✅ Device ID tracking
- ✅ HTTPS/TLS encryption
- ✅ No logging policy

---

## 🎨 Architecture

### Android App
- **Package:** com.vvpn.android
- **Protocol:** Hysteria2 v2.6.2-0
- **Language:** Kotlin
- **Min SDK:** 21 (Android 5.0)
- **Target SDK:** 34 (Android 14)

### Backend Services
- **License API** - User authentication and license management
- **BSC Payment API** - Crypto payment processing
- **Admin Portal** - System administration
- **User Portal** - User self-service

---

## 📊 What's New in v1.0.0

### License System
- ✅ Manual license entry with user binding
- ✅ BSC crypto payment integration
- ✅ Persistent license storage
- ✅ JWT authentication

### Performance
- ✅ Coroutines for reliable network operations
- ✅ HTTP logging disabled in release
- ✅ Extended network timeouts

### Portals
- ✅ Admin panel with revenue analytics
- ✅ User portal for license management
- ✅ Role-based access control

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

---

## 📄 License

This project is licensed under the **GPL-3.0 License** - see the [LICENSE](./LICENSE) file for details.

---

## 🙏 Acknowledgements

V-VPN is built on top of excellent open-source projects:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box) - Universal proxy platform
- [Hysteria](https://hysteria.network/) - Modern proxy protocol
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet) - Android proxy client
- [RikkaApps/RikkaX](https://github.com/RikkaApps/RikkaX) - Android utilities

---

## 📞 Support

- **GitHub Issues:** [Report bugs or request features](https://github.com/vahan690/v-vpn/issues)
- **User Portal:** [https://user.vvpn.space](https://user.vvpn.space)

---

## 🌟 Star History

If you find V-VPN useful, please give us a star! ⭐

---

**Built with privacy and freedom in mind** 🛡️

© 2025 V-VPN Project
