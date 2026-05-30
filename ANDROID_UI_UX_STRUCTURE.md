# OpenContinuity - Android Client: UI/UX & Structural Overview

## 🏗️ Architecture & Tech Stack

**Framework:** Native Android SDK (API 24+)
**Language:** Kotlin
**UI Toolkit:** Jetpack Compose (Modern Declarative UI)
**Design System:** Material Design 3 (MD3)
**Concurrency:** Kotlin Coroutines (`Dispatchers.IO`, `SupervisorJob`) + `StateFlow`
**Build System:** Gradle (Kotlin DSL scripts)

---

## 📂 Directory Structure (`android/app/src/main/java/com/opencontinuity/`)

- **`core/`** (System architecture & protocol)
  - `connection/`: Raw socket implementation, device bridging, broadcasting.
  - `protocol/`: Defined via pure Kotlin Data Classes serialized dynamically using `kotlinx.serialization` (Message Types, Handshakes).
- **`features/`** (Encapsulated module logic per feature)
  - `filetransfer/`: Chunking logic (`FileTransferManager.kt`), SHA-256 validation, generic file tracking maps.
  - `clipboard/`: Background listener for system clipboard.
  - `notifications/`: `NotificationListenerService` subclass to hijack Android notifications and stream them over LAN.
  - `sms/`, `battery/`, `input/`: Respective service managers linking Android hardware statuses to event streams.
- **`ui/`** (Composables for visual rendering)
  - `screens/`: Application views (e.g., `FileTransferScreen.kt`). 
  - `theme/`: Material 3 theme configurations (Colors, Typography, Shapes).

---

## 🎨 UI/UX Design System

The Android port aligns heavily with Google's **Material Design 3**:
- **Dynamic Theming:** Adopts `MaterialTheme.colorScheme` elements, which naturally ties the app into Android 12+ "Material You" personalized colors.
- **Widgets:** Uses modern nested UI structures (`Scaffold`, `TopAppBar`, `FloatingActionButton`, `Card`, and `ElevatedButton`).
- **Reactive State:** By utilizing `collectAsState()` linked to backend `MutableStateFlow` bindings, the UI updates synchronously (e.g., File Progress bars updating millisecond-by-millisecond without stuttering).
- **Icons:** Relies heavily on `androidx.compose.material.icons` (filled + outlined) for concise visual definitions.

---

## 📱 Core Screens & Functionalities

### 1. Connection & Pairing flow
- **UX Role:** Establish initial network handshakes. 
- **Functionality:** Features a camera integration to scan the Windows QR code. Translates the barcode payload into pairing requests mapped through `HandshakePayload`.

### 2. File Transfer (`FileTransferScreen.kt`)
- **UX Role:** Clean, simplistic list summarizing heavy I/O operations.
- **Functionality:**
  - Employs a `FloatingActionButton` tied to an `ActivityResultContracts.GetContent()` launcher to open Android's native file picker (`*/*`).
  - Active transfers visually spawn as `Card` composables inside a `LazyColumn`.
  - Visual distinction using `LinearProgressIndicator` bound to a 0.0-1.0 float value.
  - Granular control over file placement through Android's `ContentResolver` mapped to a native "Save As..." system file picker when an inbound payload is completed.

### 3. Background Services (Silent UX)
- **UX Role:** The true magic of "Continuity". Much of the app exists intentionally *without* a UI footprint.
- **Functionality:**
  - **Clipboard sync:** Monitors text/HTML drops in background, serializes them, hashes them (to prevent infinite loops pinging back and forth), and sends them to Windows.
  - **Input simulation:** Permits utilizing the Android Phone touchscreen as a raw Touchpad/Keyboard matrix for the target PC.
  - **SMS / Notification mirrors:** Background broadcast receivers instantly pipe inbound events downstream natively without interfering with the user's focus on the Windows machine.