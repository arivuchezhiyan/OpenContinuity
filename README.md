# OpenContinuity

A complete Windows ↔ Android ecosystem integration platform with 12 core features.

## Features

1. **Cross-Device Clipboard Sync** - Seamlessly copy and paste between devices
2. **Instant File Transfer** - Quick drag-and-drop file sharing
3. **Notification Sync** - View phone notifications on your PC
4. **SMS from PC** - Send and receive text messages
5. **Phone Camera as Webcam** - Use your phone as a high-quality webcam
6. **Screen Mirroring** - Mirror and control your phone screen
7. **Phone Battery Monitor** - Keep track of your phone's battery
8. **PC Unlock using Phone** - Secure proximity-based unlock
9. **Remote Phone Control** - Control your phone from your PC
10. **Phone as Touchpad** - Use your phone as a trackpad
11. **Screenshot Sync** - Instantly receive phone screenshots
12. **Secure Device Pairing** - QR code and PIN-based pairing

## Project Structure

```text
OpenContinuity/
├── android/                 # Android app (Kotlin + Jetpack Compose)
│   └── app/
│       └── src/main/
│           ├── java/com/opencontinuity/
│           │   ├── core/           # Protocol, security, connection
│           │   ├── features/       # Feature implementations
│           │   ├── services/       # Background services
│           │   └── ui/            # Compose UI
│           └── res/               # Android resources
├── windows/                 # Windows app (Electron + React + TypeScript)
│   └── src/
│       ├── main/            # Electron main process
│       ├── renderer/        # React UI
│       └── features/        # Feature implementations
└── shared/                  # Shared protocol definitions
```

## Getting Started

### Android App

1. Open the `android/` directory in Android Studio
2. Sync Gradle files
3. Run on a device or emulator (API 26+)

### Windows App

1. Navigate to `windows/` directory
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run in development mode:
   ```bash
   npm run dev
   ```
4. Build for production:
   ```bash
   npm run build
   ```

## Requirements

### Android
- Android 8.0 (API 26) or higher
- Network connectivity (same WiFi network)
- Permissions vary by feature

### Windows
- Windows 10/11
- Node.js 18+
- Network connectivity

## Security

- End-to-end encryption using AES-256-GCM
- ECDH key exchange for secure key derivation
- QR code pairing with verification codes
- Keys stored securely (Android Keystore / Windows Credential Manager)

## Architecture

- **Transport**: WebSocket (real-time) + HTTP (file transfers)
- **Discovery**: mDNS/DNS-SD for automatic device discovery
- **Protocol**: JSON-based message format with typed payloads

## License

MIT License
