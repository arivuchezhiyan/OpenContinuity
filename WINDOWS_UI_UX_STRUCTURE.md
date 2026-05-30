# OpenContinuity - Windows Client: UI/UX & Structural Overview

## 🏗️ Architecture & Tech Stack

**Framework:** Electron + React (Bootstrapped with Vite)
**Language:** TypeScript
**Styling:** Tailwind CSS (with Dark Mode support via `dark:` modifiers)
**Icons:** Heroicons (`@heroicons/react`)
**State Management:** React Context API (e.g., `ConnectionContext`)
**IPC:** ContextBridge via `preload.ts` to seamlessly communicate between React (Renderer) and Node/Electron (Main).

---

## 📂 Directory Structure (`windows/src/`)

- **`main/`** (Backend logic runs here)
  - `index.ts`: Application entry point and tray icon setup.
  - `preload.ts`: The bridge linking frontend actions to backend logic.
  - `ipc/`: Inter-Process Communication handlers (e.g., handling "Save As", filesystem access).
  - `connection/`: WebSocket / Server setup for raw socket handling.
  - `discovery/`: mDNS and UDP beacon discovery for local devices.
  - `security/`: Crypto and session token storage for secure device pairing.

- **`renderer/`** (Frontend UI/UX logic)
  - `App.tsx` & `main.tsx`: React runtime initialization.
  - `components/`: Reusable local UI components (`Layout.tsx`, navigation menus).
  - `pages/`: Independent application views (Dashboard, File Transfer, SMS, etc.).
  - `contexts/`: Global application state (`ConnectionContext`).
  - `styles/`: Global CSS and Tailwind directives.

---

## 🎨 UI/UX Design System

The Windows app adopts a **clean, modern native desktop feel** resembling standard system utilities:
- **Navigation:** A static persistent layout (Sidebar) routing to different internal panels.
- **Micro-interactions:** Heavy use of Heroicons alongside hover-state transitions (`hover:shadow-md`, `hover:bg-primary-600`) makes elements feel tactile and responsive. 
- **Dark Mode Support:** All components elegantly swap to a deeper gray/black palette using Tailwind's `dark:bg-gray-800` and `dark:text-white` classes.
- **Feedback:** Toast notifications and native Electron tray notifications to alert you to system statuses.

---

## 📱 Core Pages & Functionalities

### 1. Dashboard (`Dashboard.tsx`)
- **UX Role:** The landing zone. Displays the current connected device, connection status, and battery percentage.
- **Functionality:** High-level overview to confirm the user is actively synced to their phone.

### 2. Pairing (`Pairing.tsx`)
- **UX Role:** First-time user setup.
- **Functionality:** Presents an auto-generated QR code (`qrcode` library) utilizing `generateQR()`. The user scans this with Android to automatically exchange public keys and pair the secure socket session.

### 3. File Transfer (`FileTransfer.tsx`)
- **UX Role:** A drag-and-drop bucket mapping directly to internal file systems.
- **Functionality:**
  - **Drag & Drop:** Features an `animate-fade-in` dropzone changing colors on `dragOver`.
  - **Progress Tracking:** Maps real-time percentages for inbound/outbound files.
  - **File Operations:** Embedded actions to "Open", "Show in Folder", and "Save As...".

### 4. SMS & Notifications (`SMS.tsx`, `Notifications.tsx`)
- **UX Role:** Message synchronization pane.
- **Functionality:** Lists active SMS threads via lazy-loading and real-time updating arrays. Features inbound desktop notifications matching Android's push alerts natively in Windows Action Center.

### 5. Utilities (`Clipboard`, `NoteMaker.tsx`, `ScreenMirror.tsx`)
- **UX Role:** Productivity enhancers running silently or natively.
- **Clipboard:** Runs hidden in the background sync loop; any `CTRL+C` on Windows reflects on Android, and vice-versa.
- **Screen Mirror:** Pulls frame streams across the local network and paints them to a `canvas` or `video` tag.