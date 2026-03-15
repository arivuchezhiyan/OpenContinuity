/**
 * Session Manager
 *
 * Maintains the active device session across reconnects.
 * Responsibilities:
 *   - One session per paired device
 *   - 5-second heartbeat ping, 10-second timeout → reconnect
 *   - Stores per-session encryption key derived during pairing
 *   - Emits 'sessionStarted', 'sessionRestored', 'sessionExpired' events
 */

import { EventEmitter } from 'events';
import crypto from 'crypto';

export interface DeviceSession {
  /** Stable UUID assigned at first pairing */
  deviceId: string;
  deviceName: string;
  /** Runtime WebSocket session token (changes on reconnect) */
  sessionToken: string;
  /** AES-256 key derived via ECDH + HKDF — persists across reconnects */
  encryptionKey?: Buffer;
  capabilities: string[];
  connectedAt: number;
  /** Updated on every heartbeat_ack */
  lastHeartbeat: number;
  reconnectCount: number;
}

export class SessionManager extends EventEmitter {
  /** One session per deviceId */
  private sessions = new Map<string, DeviceSession>();

  /** Watchdog checks heartbeats every 5 s */
  private readonly HEARTBEAT_INTERVAL_MS = 5_000;
  /** Session is considered dead if no heartbeat within 10 s */
  private readonly HEARTBEAT_TIMEOUT_MS = 10_000;

  private watchdog: NodeJS.Timeout | null = null;

  // ─────────────────────────────────────────── lifecycle ──────────────────

  start(): void {
    if (this.watchdog) return;
    this.watchdog = setInterval(() => this.checkHeartbeats(), this.HEARTBEAT_INTERVAL_MS);
  }

  stop(): void {
    if (this.watchdog) {
      clearInterval(this.watchdog);
      this.watchdog = null;
    }
  }

  // ───────────────────────────────────────── session CRUD ─────────────────

  /**
   * Create a brand-new session when a device connects for the first time.
   */
  createSession(
    deviceId: string,
    deviceName: string,
    capabilities: string[],
    encryptionKey?: Buffer
  ): DeviceSession {
    const session: DeviceSession = {
      deviceId,
      deviceName,
      sessionToken: crypto.randomBytes(32).toString('base64'),
      encryptionKey,
      capabilities,
      connectedAt: Date.now(),
      lastHeartbeat: Date.now(),
      reconnectCount: 0
    };
    this.sessions.set(deviceId, session);
    this.emit('sessionStarted', session);
    console.log(`[SessionManager] Session started: ${deviceName} (${deviceId})`);
    return session;
  }

  /**
   * Restore an existing session when a previously-paired device reconnects.
   * Preserves the encryption key so the connection is immediately encrypted.
   */
  restoreSession(deviceId: string, deviceName: string): DeviceSession | null {
    const existing = this.sessions.get(deviceId);
    if (!existing) return null;

    existing.deviceName    = deviceName;
    existing.sessionToken  = crypto.randomBytes(32).toString('base64');
    existing.lastHeartbeat = Date.now();
    existing.reconnectCount++;

    this.emit('sessionRestored', existing);
    console.log(`[SessionManager] Session restored: ${deviceName} (reconnect #${existing.reconnectCount})`);
    return existing;
  }

  getSession(deviceId: string): DeviceSession | undefined {
    return this.sessions.get(deviceId);
  }

  getSessionByToken(token: string): DeviceSession | undefined {
    for (const s of this.sessions.values()) {
      if (s.sessionToken === token) return s;
    }
    return undefined;
  }

  getAllSessions(): DeviceSession[] {
    return Array.from(this.sessions.values());
  }

  setEncryptionKey(deviceId: string, key: Buffer): void {
    const s = this.sessions.get(deviceId);
    if (s) s.encryptionKey = key;
  }

  endSession(deviceId: string): void {
    const s = this.sessions.get(deviceId);
    if (s) {
      this.sessions.delete(deviceId);
      this.emit('sessionEnded', s);
      console.log(`[SessionManager] Session ended: ${s.deviceName}`);
    }
  }

  // ─────────────────────────────────────── heartbeat tracking ─────────────

  touchHeartbeat(deviceId: string): void {
    const s = this.sessions.get(deviceId);
    if (s) s.lastHeartbeat = Date.now();
  }

  private checkHeartbeats(): void {
    const now = Date.now();
    for (const [deviceId, session] of this.sessions) {
      const elapsed = now - session.lastHeartbeat;
      if (elapsed > this.HEARTBEAT_TIMEOUT_MS) {
        console.warn(
          `[SessionManager] Heartbeat timeout for ${session.deviceName} (${elapsed}ms) — emitting sessionExpired`
        );
        this.emit('sessionExpired', session);
        // Don't remove: ConnectionManager will call endSession after it attempts reconnect
      }
    }
  }
}
