# Changelog — OpenContinuity upgrades

## Connection & reliability

- Relaxed WebSocket ping intervals (10s / 30s timeout on Android).
- Wi‑Fi `WIFI_MODE_FULL_HIGH_PERF` while the foreground service runs.
- Application `HEARTBEAT` / `HEARTBEAT_ACK` and session restore with `deviceId`.
- Windows `SessionManager` wired at startup; faster reconnect base delay (1s).
- Android: `serverLock`, TCP health check on :8766, broadcast heartbeat every 10s.
- Foreground service: exact-alarm restart fallback, 6h wake lock, feature stop on restart.

## Features completed in this pass

- **Activity log** — `ActivityLogManager` + Dashboard live updates via IPC.
- **Auto-start** — Windows login item via `app.setLoginItemSettings`.
- **Drag & drop** — IPC wired to `DragDropManager` (`startEdgeDrag`, accept/reject).
- **Screenshots / touchpad** — Renderer routes and pages.
- **PC wake preview** — `ProximityUnlockManager` + Android “Wake PC display” in Settings.
- **Note sync (reverse)** — `NoteSyncManager` + `NoteTakerScreen` applies incoming PC strokes.
- **Preload / types** — `activity`, `dragdrop`, `onScreenshotSaved`, `onActivityUpdated`.
- **Tests** — Jest `ConnectionManager` tests updated for handshake + 20s heartbeat.

## Pairing

- Pairing code validated against `activePairingCode` (no longer accepts any code).

## Deferred (not in scope)

- BLE transport, cloud relay, full lock-screen bypass (Windows Hello companion APIs).
