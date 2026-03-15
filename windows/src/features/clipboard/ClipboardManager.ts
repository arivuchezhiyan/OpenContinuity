/**
 * Clipboard Manager - handles clipboard synchronization
 */

import { clipboard, nativeImage, BrowserWindow } from 'electron';
import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, generateMessageId } from '../../shared/protocol';
import crypto from 'crypto';

export interface ClipboardContent {
  type: 'text' | 'html' | 'image';
  text?: string;
  html?: string;
  imageBase64?: string;
}

export class ClipboardManager extends EventEmitter {
  private connectionManager: ConnectionManager;
  private lastHash: string = '';
  private pollInterval: NodeJS.Timeout | null = null;
  private isEnabled: boolean = true;
  private ignoreNextChange: boolean = false;
  /** Local device ID — injected at construction so we can tag outgoing messages */
  private readonly localDeviceId: string;

  constructor(connectionManager: ConnectionManager, localDeviceId: string = '') {
    super();
    this.connectionManager = connectionManager;
    this.localDeviceId = localDeviceId;

    // Listen for clipboard sync from phone
    connectionManager.on(`message:${MessageType.CLIPBOARD_SYNC}`, (message) => {
      this.handleIncomingClipboard(message.payload);
    });

    // When a fresh connection is established, we do NOT reset the hash.
    // Sync will only happen if the laptop clipboard actually changes after connection.
    connectionManager.on('connected', () => {
      console.log('Connection established — waiting for clipboard changes');
    });
  }

  start(): void {
    if (this.pollInterval) return;

    // Poll every 200ms — fast enough for near-instant sync without perceptible CPU cost
    this.pollInterval = setInterval(() => {
      this.checkClipboard();
    }, 200);

    console.log('Clipboard sync started (200ms polling)');
  }

  stop(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
    console.log('Clipboard sync stopped');
  }

  setEnabled(enabled: boolean): void {
    this.isEnabled = enabled;
    if (!enabled) {
      this.stop();
    }
  }

  private checkClipboard(): void {
    if (!this.isEnabled || !this.connectionManager.isConnected()) return;
    if (this.ignoreNextChange) {
      this.ignoreNextChange = false;
      return;
    }

    try {
      const content = this.getCurrentContent();
      const hash = this.hashContent(content);

      if (hash !== this.lastHash) {
        this.lastHash = hash;
        this.sendClipboardToPhone(content);
      }
    } catch (error) {
      console.error('Error checking clipboard:', error);
    }
  }

  private getCurrentContent(): ClipboardContent {
    const text = clipboard.readText();
    const html = clipboard.readHTML();
    const image = clipboard.readImage();

    // Check for image first
    if (!image.isEmpty()) {
      return {
        type: 'image',
        imageBase64: image.toPNG().toString('base64')
      };
    }

    // Check for HTML
    if (html && html !== text) {
      return {
        type: 'html',
        html,
        text
      };
    }

    // Default to text
    return {
      type: 'text',
      text
    };
  }

  private hashContent(content: ClipboardContent): string {
    const str = JSON.stringify(content);
    return crypto.createHash('sha256').update(str).digest('hex');
  }

  private sendClipboardToPhone(content: ClipboardContent): void {
    console.log('Sending clipboard to phone:', content.type);

    const contentHash = this.hashContent(content);

    this.connectionManager.send({
      type: MessageType.CLIPBOARD_SYNC,
      payload: {
        contentType: content.type,
        textContent: content.text,
        htmlContent: content.html,
        imageBase64: content.imageBase64,
        imageMimeType: content.type === 'image' ? 'image/png' : undefined,
        /** Echo prevention: Android will discard any message with its own deviceId */
        deviceId: this.localDeviceId,
        contentHash
      },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    this.emit('clipboardSent', content);
  }

  private handleIncomingClipboard(payload: any): void {
    if (!this.isEnabled) return;

    // Echo prevention: ignore clipboard syncs that originated from this device
    if (payload.deviceId && payload.deviceId === this.localDeviceId) {
      console.log('Clipboard echo suppressed (same deviceId)');
      return;
    }

    // Support both old { type } and new { contentType } formats
    const contentType = payload.contentType || payload.type;
    const text = payload.textContent || payload.text;
    const html = payload.htmlContent || payload.html;
    const imageBase64 = payload.imageBase64;

    console.log('Received clipboard from phone:', contentType);

    try {
      this.ignoreNextChange = true;

      if (contentType === 'image' && imageBase64) {
        const image = nativeImage.createFromBuffer(
          Buffer.from(imageBase64, 'base64')
        );
        clipboard.writeImage(image);
      } else if (contentType === 'html' && html) {
        clipboard.writeHTML(html);
      } else if (text) {
        clipboard.writeText(text);
      }

      // Update hash to prevent echo
      const content = this.getCurrentContent();
      this.lastHash = this.hashContent(content);

      this.emit('clipboardReceived', payload);

      // Notify renderer
      BrowserWindow.getAllWindows().forEach(window => {
        window.webContents.send('clipboard:updated', payload);
      });
    } catch (error) {
      console.error('Error setting clipboard:', error);
    }
  }

  // Manual paste (for testing)
  paste(content: ClipboardContent): void {
    this.ignoreNextChange = true;

    if (content.type === 'image' && content.imageBase64) {
      const image = nativeImage.createFromBuffer(
        Buffer.from(content.imageBase64, 'base64')
      );
      clipboard.writeImage(image);
    } else if (content.type === 'html' && content.html) {
      clipboard.writeHTML(content.html);
    } else if (content.text) {
      clipboard.writeText(content.text);
    }

    this.lastHash = this.hashContent(content);
  }
}
