# V-VPN

A secure, fast, and easy-to-use VPN application for iOS built with Flutter.

## Features

- Fast and secure VPN connection
- Multiple protocol support (VMess, VLESS, Trojan, Shadowsocks, Reality, TUIC, Hysteria, etc.)
- Import configs via QR code or URL
- Real-time connection statistics
- Dark/Light theme support
- Multi-language support

## Requirements

- iOS 13.0+
- Xcode 15.0+
- Flutter 3.24.0+
- CocoaPods

## Getting Started

### Clone the repository

```bash
git clone https://github.com/vahan690/v-vpn.git
cd v-vpn
```

### Install dependencies

```bash
flutter pub get
cd ios && pod install && cd ..
```

### Build for iOS

```bash
# Debug build
flutter build ios --debug

# Release build
flutter build ios --release
```

### Run on device

```bash
flutter run -d <device_id>
```

## Project Structure

```
v-vpn/
├── lib/                    # Dart source code
│   ├── core/              # Core utilities and models
│   ├── features/          # Feature modules
│   ├── gen/               # Generated code
│   ├── singbox/           # Sing-box service integration
│   └── utils/             # Utility functions
├── ios/                    # iOS native code
│   ├── Runner/            # Main app
│   └── VVPNPacketTunnel/  # Network Extension
├── assets/                 # Images, fonts, translations
└── pubspec.yaml           # Flutter dependencies
```

## Configuration

### Bundle Identifier
- Main App: `app.vvpn.space`
- Network Extension: `app.vvpn.space.PacketTunnel`

### URL Scheme
The app supports the `vvpn://` URL scheme for importing configurations.

## Building for App Store

1. Open `ios/Runner.xcworkspace` in Xcode
2. Select your development team
3. Archive the project (Product > Archive)
4. Upload to App Store Connect

## License

This project is based on [Hiddify](https://github.com/hiddify/hiddify-next) and uses the [sing-box](https://github.com/SagerNet/sing-box) core.

## Acknowledgments

- [Hiddify](https://github.com/hiddify/hiddify-next) - Original project
- [sing-box](https://github.com/SagerNet/sing-box) - VPN core engine
