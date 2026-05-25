/**
 * Proximity unlock preview: wakes the PC display when the phone sends an unlock request.
 * Full lock-screen bypass requires Windows Hello companion APIs (not available here).
 */

import { EventEmitter } from 'events';
import { exec } from 'child_process';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, ProtocolMessage } from '../../shared/protocol';

export class ProximityUnlockManager extends EventEmitter {
  private connectionManager: ConnectionManager;
  private enabled = false;

  constructor(connectionManager: ConnectionManager) {
    super();
    this.connectionManager = connectionManager;
    this.connectionManager.on(`message:${MessageType.UNLOCK_REQUEST}`, (message) => {
      if (!this.enabled) return;
      this.handleUnlockRequest(message as ProtocolMessage);
    });
  }

  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  private handleUnlockRequest(message: ProtocolMessage): void {
    const payload = message.payload as { deviceName?: string; reason?: string };
    this.wakeDisplay();
    this.emit('unlockRequested', payload);
    console.log('[ProximityUnlock] Wake display requested from phone');
  }

  /** Keep display awake / turn screen on (does not bypass lock screen PIN). */
  private wakeDisplay(): void {
    if (process.platform !== 'win32') return;
    const ps = `
      Add-Type @"
        using System;
        using System.Runtime.InteropServices;
        public class Power {
          [DllImport("kernel32.dll")]
          public static extern uint SetThreadExecutionState(uint es);
        }
"@
      [Power]::SetThreadExecutionState(0x80000003) | Out-Null
    `.replace(/\n/g, ' ');
    exec(`powershell -NoProfile -Command "${ps.replace(/"/g, '\\"')}"`, (err) => {
      if (err) console.warn('[ProximityUnlock] wake display failed:', err.message);
    });
  }
}
